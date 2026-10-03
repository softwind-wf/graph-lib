package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConnectedComponents} 连通分量测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>与 {@link UF}(并查集)在<b>所有顶点对</b>上交叉验证"是否连通"—— 两条独立技术路线;</li>
 *   <li>分量的结构:编号、大小、顶点列表,尺寸之和 = V,分量之间没有边;</li>
 *   <li>教材样例:tinyG.txt 是连通的;人为拆开后的分量数;</li>
 *   <li>自环、平行边不影响分量;孤立顶点各自成量;</li>
 *   <li>迭代实现:20 万顶点不栈溢出。</li>
 * </ol>
 */
@DisplayName("ConnectedComponents 连通分量测试")
class ConnectedComponentsTest {

    @Nested
    @DisplayName("已知样例")
    class KnownCaseTest {

        @Test
        @DisplayName("tinyG.txt 是连通的(1 个分量)")
        void tinyG() {
            UndirectedGraph graph = GraphIO.readFile("tinyG.txt");
            ConnectedComponents cc = new ConnectedComponents(graph);
            assertEquals(1, cc.count());
            assertTrue(cc.isConnected());
            assertEquals(graph.V(), cc.largestComponentSize());
            assertEquals(1, cc.components().size());
        }

        @Test
        @DisplayName("三个互不相连的成分,分量数 3")
        void disconnected() {
            UndirectedGraph graph = new UndirectedGraph(7);
            graph.addEdge(0, 1);
            graph.addEdge(1, 2);                     // 分量 A = {0,1,2}
            graph.addEdge(3, 4);
            graph.addEdge(4, 5);                     // 分量 B = {3,4,5}
            // 6 是孤立顶点                          // 分量 C = {6}
            ConnectedComponents cc = new ConnectedComponents(graph);
            assertEquals(3, cc.count());
            assertFalse(cc.isConnected());
            assertEquals(3, cc.largestComponentSize());
            assertTrue(cc.connected(0, 2));
            assertFalse(cc.connected(0, 3));
            assertTrue(cc.connected(6, 6));
            assertEquals(Arrays.asList(
                    Arrays.asList(0, 1, 2), Arrays.asList(3, 4, 5), Arrays.asList(6)),
                    cc.components());
        }

        @Test
        @DisplayName("自环与平行边不影响分量")
        void selfLoopAndParallelEdges() {
            UndirectedGraph graph = new UndirectedGraph(4);
            graph.addEdge(0, 1);
            graph.addEdge(0, 1);                     // 平行边
            graph.addEdge(1, 1);                     // 自环
            graph.addEdge(2, 3);
            ConnectedComponents cc = new ConnectedComponents(graph);
            assertEquals(2, cc.count());
            assertTrue(cc.connected(0, 1));
            assertFalse(cc.connected(1, 2));
        }

        @Test
        @DisplayName("无边的图:每个顶点各成一个分量")
        void noEdges() {
            ConnectedComponents cc = new ConnectedComponents(new UndirectedGraph(5));
            assertEquals(5, cc.count());
            assertEquals(1, cc.largestComponentSize());
            for (int v = 0; v < 5; v++) {
                assertEquals(Arrays.asList(v), cc.component(cc.id(v)));
            }
        }

        @Test
        @DisplayName("空图:0 个分量,isConnected 为 false")
        void empty() {
            ConnectedComponents cc = new ConnectedComponents(new UndirectedGraph(0));
            assertEquals(0, cc.count());
            assertFalse(cc.isConnected());
            assertEquals(0, cc.largestComponentSize());
            assertTrue(cc.components().isEmpty());
        }
    }

    @Nested
    @DisplayName("与并查集对拍")
    class UnionFindComparisonTest {

        @Test
        @DisplayName("30 张随机图:逐对顶点比较'是否连通',两条路线必须一致")
        void agreesWithUnionFind() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 30; trial++) {
                UndirectedGraph graph = GraphGenerator.simple(rnd, 2 + rnd.nextInt(12),
                        1 + rnd.nextInt(20));
                ConnectedComponents cc = new ConnectedComponents(graph);
                UF uf = new UF(graph.V());
                for (int[] edge : graph.edges()) {
                    uf.union(edge[0], edge[1]);
                }
                assertEquals(uf.count(), cc.count(), "第 " + trial + " 张图的分量数");
                for (int v = 0; v < graph.V(); v++) {
                    for (int w = 0; w < graph.V(); w++) {
                        assertEquals(uf.connected(v, w), cc.connected(v, w),
                                "第 " + trial + " 张图:" + v + " 与 " + w);
                    }
                }
            }
        }

        @Test
        @DisplayName("分量内部任意两点连通、分量之间没有边、大小之和等于 V")
        void structureIsConsistent() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 20; trial++) {
                UndirectedGraph graph = GraphGenerator.simple(rnd, 3 + rnd.nextInt(10), rnd.nextInt(15));
                ConnectedComponents cc = new ConnectedComponents(graph);
                int total = 0;
                for (int c = 0; c < cc.count(); c++) {
                    List<Integer> vertices = cc.component(c);
                    assertEquals(cc.size(c), vertices.size());
                    total += vertices.size();
                    for (int v : vertices) {
                        assertEquals(c, cc.id(v));
                        for (int w : vertices) {
                            assertTrue(cc.connected(v, w), "同分量内部应连通");
                        }
                    }
                }
                assertEquals(graph.V(), total, "各分量大小之和应等于顶点数");
                // 分量之间不应有边
                for (int[] edge : graph.edges()) {
                    assertEquals(cc.id(edge[0]), cc.id(edge[1]),
                            "边 " + edge[0] + "-" + edge[1] + " 的两端应在同一个分量里");
                }
            }
        }
    }

    @Nested
    @DisplayName("校验与规模")
    class ValidationAndScaleTest {

        @Test
        @DisplayName("参数校验:null 图、越界顶点、越界分量号")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new ConnectedComponents(null));
            ConnectedComponents cc = new ConnectedComponents(GraphIO.readFile("tinyG.txt"));
            assertThrows(IllegalArgumentException.class, () -> cc.id(13));
            assertThrows(IllegalArgumentException.class, () -> cc.id(-1));
            assertThrows(IllegalArgumentException.class, () -> cc.size(1));
            assertThrows(IllegalArgumentException.class, () -> cc.component(-1));
            assertThrows(IllegalArgumentException.class, () -> cc.connected(0, 13));
        }

        @Test
        @DisplayName("20 万顶点的链:1 个分量;20 万顶点无边:20 万个分量")
        void large() {
            int n = 200000;
            UndirectedGraph chain = new UndirectedGraph(n);
            for (int v = 0; v + 1 < n; v++) {
                chain.addEdge(v, v + 1);
            }
            ConnectedComponents cc = new ConnectedComponents(chain);
            assertEquals(1, cc.count());
            assertEquals(n, cc.largestComponentSize());

            assertEquals(n, new ConnectedComponents(new UndirectedGraph(n)).count());
        }

        @Test
        @DisplayName("toString 含 V、分量数与最大分量,并逐行列出分量")
        void toStringContent() {
            UndirectedGraph graph = new UndirectedGraph(3);
            graph.addEdge(0, 1);
            String text = new ConnectedComponents(graph).toString();
            assertTrue(text.contains("ConnectedComponents: V=3"), text);
            assertTrue(text.contains("分量数=2"), text);
            assertTrue(text.contains("[0, 1]"), text);
        }
    }

    /** 供其它测试复用:把分量收集成规范列表 */
    static List<List<Integer>> componentsOf(UndirectedGraph graph) {
        return new ConnectedComponents(graph).components();
    }

    /** 供其它测试复用:分量个数 */
    static int componentCount(UndirectedGraph graph) {
        return new ConnectedComponents(graph).count();
    }

    /** 供其它测试复用:把某个分量的顶点收集成列表 */
    static List<Integer> componentOf(UndirectedGraph graph, int vertex) {
        ConnectedComponents cc = new ConnectedComponents(graph);
        return new ArrayList<Integer>(cc.component(cc.id(vertex)));
    }
}
