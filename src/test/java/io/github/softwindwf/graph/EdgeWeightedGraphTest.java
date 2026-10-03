package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EdgeWeightedGraph} 无向带权图(邻接表挂边对象)测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>邻接表对称性:一条边在两个端点的表里各存一份(同一个 {@code Edge} 对象);</li>
 *   <li>度数口径:自环贡献 2 度、平行边按重数累加、Σdeg = 2E;</li>
 *   <li>{@code edges()} 每条边只出一次,而平行边按重数逐条返回(不能用 Set 去重);</li>
 *   <li>深拷贝:结构一致、迭代顺序一致、改动互不影响,且共享不可变的 Edge 对象;</li>
 *   <li>参数校验:null 边、端点越界、非法顶点数。</li>
 * </ol>
 */
@DisplayName("EdgeWeightedGraph 无向带权图测试")
class EdgeWeightedGraphTest {

    private static EdgeWeightedGraph tinyEWG() {
        return GraphIO.readWeightedFile("tinyEWG.txt");
    }

    private static List<Edge> edgesOf(EdgeWeightedGraph g) {
        List<Edge> list = new ArrayList<Edge>();
        for (Edge e : g.edges()) {
            list.add(e);
        }
        return list;
    }

    @Nested
    @DisplayName("构造与规模")
    class ConstructionTest {

        @Test
        @DisplayName("空图:V 个顶点、0 条边、度数为 0")
        void emptyGraph() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(5);
            assertEquals(5, g.V());
            assertEquals(0, g.E());
            assertEquals(0, g.degreeSum());
            assertEquals(0, g.maxDegree());
            assertEquals(0, g.selfLoopCount());
            for (int v = 0; v < 5; v++) {
                assertEquals(0, g.degree(v));
                assertFalse(g.adj(v).iterator().hasNext());
            }
            assertFalse(g.edges().iterator().hasNext());
        }

        @Test
        @DisplayName("顶点数为负或超上限抛 IllegalArgumentException")
        void illegalVertexCount() {
            assertThrows(IllegalArgumentException.class, () -> new EdgeWeightedGraph(-1));
            assertThrows(IllegalArgumentException.class, () -> new EdgeWeightedGraph((1 << 24) + 1));
            assertEquals(0, new EdgeWeightedGraph(0).V());
        }

        @Test
        @DisplayName("深拷贝:结构一致、顺序一致、改动互不影响")
        void deepCopy() {
            EdgeWeightedGraph g = tinyEWG();
            EdgeWeightedGraph copy = new EdgeWeightedGraph(g);
            assertNotSame(g, copy);
            assertEquals(g.V(), copy.V());
            assertEquals(g.E(), copy.E());
            assertEquals(g.toString(), copy.toString(), "邻接表的迭代顺序也应一致");

            copy.addEdge(0, 1, 9.99);
            assertEquals(17, copy.E());
            assertEquals(16, g.E(), "改动副本不应影响原图");
            assertFalse(g.toString().contains("9.99"));

            assertThrows(IllegalArgumentException.class, () -> new EdgeWeightedGraph((EdgeWeightedGraph) null));
        }
    }

    @Nested
    @DisplayName("加边与邻接表")
    class AddEdgeTest {

        @Test
        @DisplayName("一条边同时出现在两个端点的邻接表里(同一个对象)")
        void adjacencyIsSymmetric() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(4);
            Edge e = new Edge(0, 3, 2.5);
            g.addEdge(e);
            assertEquals(1, g.E());
            assertEquals(1, g.degree(0));
            assertEquals(1, g.degree(3));
            assertEquals(2, g.degreeSum());
            List<Edge> at0 = list(g.adj(0));
            List<Edge> at3 = list(g.adj(3));
            assertEquals(1, at0.size());
            assertEquals(1, at3.size());
            assertSame(e, at0.get(0), "两端存的是同一个边对象(Edge 不可变,共享安全)");
            assertSame(e, at3.get(0));
            assertEquals(3, at0.get(0).other(0));
            assertEquals(0, at3.get(0).other(3));
        }

        @Test
        @DisplayName("便捷重载 addEdge(v, w, weight) 等价于 addEdge(new Edge(...))")
        void convenienceOverload() {
            EdgeWeightedGraph a = new EdgeWeightedGraph(2);
            a.addEdge(0, 1, 1.25);
            EdgeWeightedGraph b = new EdgeWeightedGraph(2);
            b.addEdge(new Edge(0, 1, 1.25));
            assertEquals(a.toString(), b.toString());
        }

        @Test
        @DisplayName("自环:邻接表里出现两次,度贡献 2,selfLoopCount 记 1")
        void selfLoop() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(1, 1, 0.5);
            assertEquals(1, g.E());
            assertEquals(2, g.degree(1));
            assertEquals(1, g.selfLoopCount());
            assertEquals(2, list(g.adj(1)).size(), "自环在邻接表里存两份");
        }

        @Test
        @DisplayName("平行边:按重数计,edges() 逐条返回")
        void parallelEdges() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(2);
            g.addEdge(0, 1, 0.3);
            g.addEdge(1, 0, 0.7);
            g.addEdge(0, 1, 0.5);
            assertEquals(3, g.E());
            assertEquals(3, g.degree(0));
            assertEquals(3, g.degree(1));
            assertEquals(3, edgesOf(g).size(), "平行边逐条返回,不能去重");
            assertEquals(6, g.degreeSum());
        }

        @Test
        @DisplayName("端点越界、null 边抛 IllegalArgumentException")
        void invalidEdges() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 1, 1.0);
            assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 3, 1.0));
            assertThrows(IllegalArgumentException.class, () -> g.addEdge(new Edge(0, 5, 1.0)));
            assertThrows(IllegalArgumentException.class, () -> g.addEdge((Edge) null));
            assertThrows(IllegalArgumentException.class, () -> g.adj(3));
            assertThrows(IllegalArgumentException.class, () -> g.degree(-1));
            assertEquals(1, g.E(), "非法调用不应改变边数");
        }
    }

    @Nested
    @DisplayName("遍历")
    class TraversalTest {

        @Test
        @DisplayName("edges() 每条边只出一次,条数等于 E")
        void edgesOnceEach() {
            EdgeWeightedGraph g = tinyEWG();
            List<Edge> edges = edgesOf(g);
            assertEquals(8, g.V());
            assertEquals(16, g.E());
            assertEquals(16, edges.size());
            TreeSet<String> unique = new TreeSet<String>();
            for (Edge e : edges) {
                unique.add(e.toString());
            }
            assertEquals(16, unique.size(), "tinyEWG 没有平行边,16 条边互不相同");
        }

        @Test
        @DisplayName("自环只出一次、平行边按重数出")
        void edgesWithSelfLoopAndParallel() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 1, 0.1);
            g.addEdge(0, 1, 0.2);
            g.addEdge(2, 2, 0.3);
            g.addEdge(2, 2, 0.4);
            List<Edge> edges = edgesOf(g);
            assertEquals(4, edges.size());
            assertEquals(4, g.E());
            assertEquals(2, g.selfLoopCount());
            assertEquals(4, g.degree(2));
        }

        @Test
        @DisplayName("迭代器不支持 remove")
        void iteratorsAreReadOnly() {
            EdgeWeightedGraph g = tinyEWG();
            java.util.Iterator<Edge> it = g.edges().iterator();
            assertTrue(it.hasNext());
            it.next();
            assertThrows(UnsupportedOperationException.class, it::remove);
        }

        @Test
        @DisplayName("tinyEWG:逐点度数与样例一致")
        void tinyEWGDegrees() {
            EdgeWeightedGraph g = tinyEWG();
            // 顶点 0:0-7(.16) 0-4(.38) 0-2(.26) 6-0(.58) → 4 条
            assertEquals(4, g.degree(0));
            // 顶点 4:4-5 4-7 0-4 6-4 → 4 条
            assertEquals(4, g.degree(4));
            // 顶点 6:6-2 3-6 6-0 6-4 → 4 条
            assertEquals(4, g.degree(6));
            // 顶点 2:2-3 0-2 1-2 2-7 6-2 → 5 条(它和 7 是度数最大的)
            assertEquals(5, g.degree(2));
            assertEquals(5, g.degree(7));
            assertEquals(3, g.degree(3));
            assertEquals(16 * 2, g.degreeSum());
            assertEquals(5, g.maxDegree());
        }
    }

    @Nested
    @DisplayName("显示")
    class ToStringTest {

        @Test
        @DisplayName("首行为规模,随后每行列出该顶点的关联边与权值")
        void format() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 1, 0.5);
            String s = g.toString();
            String[] lines = s.split("\\r?\\n");
            assertEquals("3 vertices, 1 edges", lines[0]);
            assertEquals("0: 1(0.5) ", lines[1]);
            assertEquals("1: 0(0.5) ", lines[2]);
            assertEquals("2: ", lines[3]);
        }
    }

    private static List<Edge> list(Iterable<Edge> edges) {
        List<Edge> result = new ArrayList<Edge>();
        for (Edge e : edges) {
            result.add(e);
        }
        return result;
    }
}
