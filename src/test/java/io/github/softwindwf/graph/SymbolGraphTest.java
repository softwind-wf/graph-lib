package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SymbolGraph} 符号图测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>名字 ↔ 编号双向映射、邻接查询返回名字、度数含义(平行边按重数、自环计 2);</li>
 *   <li>与 {@link UndirectedGraph} 的一致性:V、E、邻接关系、边集合完全对应;</li>
 *   <li>真实数据 {@code routes.txt}(11 个机场、14 条航线,含两条平行边)逐项核对,
 *       并真的把图交给 {@link BreadthFirstTraversal} 求"最少中转次数";</li>
 *   <li>建模期校验:空名、重名、未登记名字加边、越界编号;</li>
 *   <li>孤立顶点:能通过 API 登记,度数为 0,也在内核图中占一个编号。</li>
 * </ol>
 */
@DisplayName("SymbolGraph 符号图测试")
class SymbolGraphTest {

    private static final String ROUTES = "routes.txt";

    /** routes.txt 中机场名按首次出现的顺序 */
    private static final String[] ROUTE_NAMES = {
        "JFK", "ORD", "DEN", "DFW", "ATL", "PHX", "LAX", "HOU", "LAS", "MCO", "MEX"
    };

    private static SymbolGraph routes() {
        return GraphIO.readSymbolGraphFile(ROUTES);
    }

    private static SymbolGraph sample() {
        SymbolGraph sg = new SymbolGraph(new String[]{"甲", "乙", "丙"});
        sg.addEdge("甲", "乙");
        sg.addEdge("乙", "丙");
        sg.addEdge("甲", "乙");        // 平行边
        return sg;
    }

    @Nested
    @DisplayName("名字与编号映射")
    class MappingTest {

        @Test
        @DisplayName("名字按加入顺序编号,双向可查")
        void indexAndName() {
            SymbolGraph sg = sample();
            assertEquals(3, sg.V());
            assertEquals(3, sg.E());
            assertEquals(0, sg.indexOf("甲"));
            assertEquals(2, sg.indexOf("丙"));
            assertEquals(-1, sg.indexOf("丁"));
            assertEquals("乙", sg.nameOf(1));
            assertTrue(sg.contains("丙"));
            assertFalse(sg.contains("丁"));
            assertEquals(Arrays.asList("甲", "乙", "丙"), sg.vertices());
            assertThrows(IllegalArgumentException.class, () -> sg.nameOf(3));
            assertThrows(IllegalArgumentException.class, () -> sg.nameOf(-1));
        }

        @Test
        @DisplayName("邻接与度数:平行边按重数,自环计 2")
        void adjacencyAndDegree() {
            SymbolGraph sg = sample();
            assertEquals(Arrays.asList("乙", "乙"), sg.adjacent("甲"), "甲-乙 有两条平行边");
            assertEquals(2, sg.degree("甲"));
            assertEquals(3, sg.degree("乙"));
            assertEquals(1, sg.degree("丙"));
            assertEquals(Arrays.asList(1, 1), sg.adj(0), "甲(编号 0)的邻居是乙(编号 1),两条平行边");
            assertEquals(3, sg.maxDegree(), "乙 的度数为 3(甲、丙、甲),是最大度数");
            assertTrue(sg.hasEdge("甲", "乙"));
            assertFalse(sg.hasEdge("甲", "丙"));
            assertEquals(2, sg.countEdges("甲", "乙"));
            assertEquals(0, sg.countEdges("甲", "丙"));
            assertThrows(IllegalArgumentException.class, () -> sg.adj(3));
            assertThrows(IllegalArgumentException.class, () -> sg.degree("丁"));
            assertThrows(IllegalArgumentException.class, () -> sg.adjacent("丁"));
            assertThrows(IllegalArgumentException.class, () -> sg.hasEdge("丁", "甲"));
            assertThrows(IllegalArgumentException.class, () -> sg.countEdges("甲", "丁"));
        }

        @Test
        @DisplayName("自环:邻接表里出现两次,度数为 2")
        void selfLoop() {
            SymbolGraph sg = new SymbolGraph(new String[]{"甲"});
            sg.addEdge("甲", "甲");
            assertEquals(2, sg.degree("甲"));
            assertEquals(1, sg.E());
            assertEquals(1, sg.edges().size(), "自环只算一条边");
        }

        @Test
        @DisplayName("孤立顶点:能登记、度数为 0、内核图里占一个编号")
        void isolatedVertex() {
            SymbolGraph sg = new SymbolGraph(new String[]{"甲", "乙", "孤岛"});
            sg.addEdge("甲", "乙");
            assertEquals(3, sg.V());
            assertEquals(0, sg.degree("孤岛"));
            assertTrue(sg.adjacent("孤岛").isEmpty());
            assertEquals(3, sg.toUndirectedGraph().V());
            assertEquals(1, sg.toUndirectedGraph().E());
            assertEquals(0, sg.toUndirectedGraph().degree(2));
        }

        @Test
        @DisplayName("建模期校验:空名、重名、未登记的名字加边、null 数组")
        void validation() {
            SymbolGraph sg = sample();
            assertThrows(IllegalArgumentException.class, () -> sg.addVertex(null));
            assertThrows(IllegalArgumentException.class, () -> sg.addVertex("   "));
            assertThrows(IllegalArgumentException.class, () -> sg.addVertex("甲"));
            assertThrows(IllegalArgumentException.class, () -> sg.addEdge("甲", "丁"));
            assertThrows(IllegalArgumentException.class, () -> sg.addEdge("丁", "甲"));
            assertThrows(IllegalArgumentException.class, () -> new SymbolGraph((String[]) null));
            assertEquals(3, sg.V(), "失败的添加不应改变顶点数");
            assertEquals(3, sg.E(), "失败的添加不应改变边数");
        }
    }

    @Nested
    @DisplayName("与无向图内核一致")
    class KernelTest {

        @Test
        @DisplayName("toUndirectedGraph:V、E、邻接关系一一对应")
        void toUndirectedGraph() {
            SymbolGraph sg = sample();
            UndirectedGraph graph = sg.toUndirectedGraph();
            assertEquals(sg.V(), graph.V());
            assertEquals(sg.E(), graph.E());
            for (String name : sg.vertices()) {
                int v = sg.indexOf(name);
                assertEquals(sg.degree(name), graph.degree(v), name + " 的度数");
                Set<String> byName = new HashSet<String>(sg.adjacent(name));
                Set<Integer> byId = new HashSet<Integer>();
                for (int w : graph.adj(v)) {
                    byId.add(w);
                }
                Set<Integer> expected = new HashSet<Integer>();
                for (String neighbor : byName) {
                    expected.add(sg.indexOf(neighbor));
                }
                assertEquals(expected, byId, name + " 的邻接集合");
            }
        }

        @Test
        @DisplayName("edges():每条边只出现一次,平行边按重数,自环一次")
        void edgesOnceEach() {
            SymbolGraph sg = sample();
            List<String[]> edges = sg.edges();
            assertEquals(3, edges.size());
            int parallel = 0;
            for (String[] edge : edges) {
                Set<String> pair = new HashSet<String>(Arrays.asList(edge[0], edge[1]));
                if (pair.equals(new HashSet<String>(Arrays.asList("甲", "乙")))) {
                    parallel++;
                }
            }
            assertEquals(2, parallel, "甲-乙 的两条平行边应各出现一次");
        }
    }

    @Nested
    @DisplayName("真实数据 routes.txt")
    class RoutesTest {

        @Test
        @DisplayName("11 个机场、14 条航线,名字顺序即首次出现顺序")
        void structure() {
            SymbolGraph sg = routes();
            assertEquals(11, sg.V());
            assertEquals(14, sg.E());
            assertEquals(Arrays.asList(ROUTE_NAMES), sg.vertices());
            for (int v = 0; v < ROUTE_NAMES.length; v++) {
                assertEquals(ROUTE_NAMES[v], sg.nameOf(v));
                assertEquals(v, sg.indexOf(ROUTE_NAMES[v]));
            }
            assertEquals(5, sg.maxDegree());
        }

        @Test
        @DisplayName("邻接与度数逐项核对(含两条平行边)")
        void adjacency() {
            SymbolGraph sg = routes();
            assertEquals(new HashSet<String>(Arrays.asList("ORD")),
                    new HashSet<String>(sg.adjacent("JFK")));
            assertEquals(new HashSet<String>(Arrays.asList("JFK", "DEN", "PHX")),
                    new HashSet<String>(sg.adjacent("ORD")));
            assertEquals(new HashSet<String>(Arrays.asList("DFW", "HOU", "DEN", "MCO")),
                    new HashSet<String>(sg.adjacent("ATL")));
            assertEquals(4, sg.degree("ATL"));
            assertEquals(1, sg.degree("JFK"));
            assertEquals(1, sg.degree("MCO"));

            // PHX 的 5 条邻接里 LAX 与 LAS 各出现两次 —— routes.txt 里有平行边
            assertEquals(5, sg.degree("PHX"));
            assertEquals(2, sg.countEdges("PHX", "LAX"), "LAX-PHX 出现两次");
            assertEquals(2, sg.countEdges("PHX", "LAS"), "PHX-LAS 出现两次");
            assertTrue(sg.hasEdge("LAS", "PHX"));
            assertFalse(sg.hasEdge("JFK", "LAX"));
        }

        @Test
        @DisplayName("把图交给 BFS:JFK → MEX 最少中转 3 次,路径 JFK-ORD-PHX-LAX-MEX")
        void shortestRoute() {
            SymbolGraph sg = routes();
            UndirectedGraph graph = sg.toUndirectedGraph();
            assertEquals(11, graph.V());
            assertEquals(14, graph.E());

            BreadthFirstTraversal bfs = new BreadthFirstTraversal(graph, sg.indexOf("JFK"));
            int mex = sg.indexOf("MEX");
            assertTrue(bfs.hasPathTo(mex));
            assertEquals(4, bfs.distTo(mex), "4 条边 = 3 次中转");
            List<String> path = new ArrayList<String>();
            for (int v : bfs.pathTo(mex)) {
                path.add(sg.nameOf(v));
            }
            assertEquals(Arrays.asList("JFK", "ORD", "PHX", "LAX", "MEX"), path);
        }

        @Test
        @DisplayName("toString 含顶点数、边数与每个顶点的邻居")
        void toStringContent() {
            String text = routes().toString();
            assertTrue(text.contains("符号图:11 个顶点,14 条边"), text);
            assertTrue(text.contains("ATL"), text);
            assertTrue(text.contains("MCO"), text);
        }
    }
}
