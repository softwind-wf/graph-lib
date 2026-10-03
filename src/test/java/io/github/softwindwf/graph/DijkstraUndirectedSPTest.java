package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DijkstraUndirectedSP} 无向加权图最短路测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>教材样例 tinyEWG.txt:源点 0 的距离逐项核对(0→1 = 0.35,经 0-7-1);</li>
 *   <li><b>等价性对拍</b>:把无向边拆成两条有向边后,{@link DijkstraSP} 给出同样的距离;</li>
 *   <li>两种模式 LAZY/DENSE 结果一致;</li>
 *   <li>路径合法:相邻边首尾相接、权值之和 = distTo,顶点序列与边序列一致;</li>
 *   <li>负权被拒绝(消息里指出是哪条边、该用什么替代);</li>
 *   <li>规模:10 万顶点的路径图。</li>
 * </ol>
 */
@DisplayName("DijkstraUndirectedSP 无向最短路测试")
class DijkstraUndirectedSPTest {

    private static final double EPS = 1e-9;

    private static EdgeWeightedGraph tinyEWG() {
        return GraphIO.readWeightedFile("tinyEWG.txt");
    }

    /** 把无向加权图拆成有向加权图(每条边两个方向) */
    static EdgeWeightedDigraph toDigraph(EdgeWeightedGraph graph) {
        EdgeWeightedDigraph digraph = new EdgeWeightedDigraph(graph.V());
        for (Edge edge : graph.edges()) {
            int a = edge.either();
            int b = edge.other(a);
            digraph.addEdge(new DirectedEdge(a, b, edge.weight()));
            digraph.addEdge(new DirectedEdge(b, a, edge.weight()));
        }
        return digraph;
    }

    @Nested
    @DisplayName("教材样例 tinyEWG.txt")
    class KnownDataTest {

        @Test
        @DisplayName("源点 0:8 个顶点的距离逐项核对")
        void distances() {
            DijkstraUndirectedSP sp = new DijkstraUndirectedSP(tinyEWG(), 0);
            // 0→1 走 0-7-1(0.35)、0→3 走 0-2-3(0.43)、0→5 走 0-7-5(0.44)、0→6 直接 0-6(0.58)
            double[] expected = {0.0, 0.35, 0.26, 0.43, 0.38, 0.44, 0.58, 0.16};
            for (int v = 0; v < expected.length; v++) {
                assertEquals(expected[v], sp.distTo(v), 1e-9, "distTo(" + v + ")");
            }
            assertEquals(0, sp.source());
            assertEquals(8, sp.V());
            assertEquals(16, sp.E());
        }

        @Test
        @DisplayName("0→1 的最短路要绕 0-7-1(0.16 + 0.19 = 0.35)")
        void pathGoesThroughSeven() {
            DijkstraUndirectedSP sp = new DijkstraUndirectedSP(tinyEWG(), 0);
            assertEquals(java.util.Arrays.asList(0, 7, 1), sp.pathVertices(1));
            List<Edge> path = sp.pathTo(1);
            assertEquals(2, path.size());
            assertEquals(0.16, path.get(0).weight(), EPS);
            assertEquals(0.19, path.get(1).weight(), EPS);
        }

        @Test
        @DisplayName("每条最短路径都合法:首尾相接、权值之和 = 距离")
        void pathsAreValid() {
            EdgeWeightedGraph graph = tinyEWG();
            DijkstraUndirectedSP sp = new DijkstraUndirectedSP(graph, 0);
            for (int v = 0; v < graph.V(); v++) {
                assertTrue(sp.hasPathTo(v));
                List<Edge> path = sp.pathTo(v);
                List<Integer> vertices = sp.pathVertices(v);
                assertEquals(path.size() + 1, vertices.size());
                double sum = 0.0;
                int current = 0;
                for (Edge edge : path) {
                    assertTrue(edge.either() == current || edge.other(edge.either()) == current,
                            "路径应首尾相接");
                    current = edge.other(current);
                    sum += edge.weight();
                }
                assertEquals(v, current);
                assertEquals(sp.distTo(v), sum, 1e-9);
            }
        }

        @Test
        @DisplayName("toString 含模式、源点与规模")
        void toStringContent() {
            String text = new DijkstraUndirectedSP(tinyEWG(), 0).toString();
            assertTrue(text.contains("DijkstraUndirectedSP(LAZY, 源点 0)"), text);
            assertTrue(text.contains("8 个顶点,16 条边"), text);
        }
    }

    @Nested
    @DisplayName("与其它实现/参考实现对拍")
    class OracleTest {

        @Test
        @DisplayName("20 张随机图:两种模式一致,且与拆分后的有向 Dijkstra 一致")
        void agreesWithDirectedVersion() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(10);
                EdgeWeightedGraph graph = GraphGenerator.edgeWeightedPositive(rnd, V, rnd.nextInt(20));
                int source = rnd.nextInt(V);
                DijkstraUndirectedSP lazy = new DijkstraUndirectedSP(
                        graph, source, DijkstraUndirectedSP.Mode.LAZY);
                DijkstraUndirectedSP dense = new DijkstraUndirectedSP(
                        graph, source, DijkstraUndirectedSP.Mode.DENSE);
                DijkstraSP directed = new DijkstraSP(toDigraph(graph), source);
                for (int v = 0; v < V; v++) {
                    assertEquals(lazy.distTo(v), dense.distTo(v), EPS,
                            "第 " + trial + " 张图两种模式 " + source + "→" + v);
                    assertEquals(directed.distTo(v), lazy.distTo(v), EPS,
                            "第 " + trial + " 张图与有向版 " + source + "→" + v);
                }
            }
        }

        @Test
        @DisplayName("与 algs4 的 DijkstraUndirectedSP 对拍")
        void agreesWithReference() {
            Random rnd = new Random(2718L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(10);
                EdgeWeightedGraph graph = GraphGenerator.edgeWeightedPositive(rnd, V, rnd.nextInt(20));
                edu.princeton.cs.algs4.EdgeWeightedGraph reference =
                        new edu.princeton.cs.algs4.EdgeWeightedGraph(V);
                for (Edge edge : graph.edges()) {
                    reference.addEdge(new edu.princeton.cs.algs4.Edge(
                            edge.either(), edge.other(edge.either()), edge.weight()));
                }
                int source = rnd.nextInt(V);
                edu.princeton.cs.algs4.DijkstraUndirectedSP expected =
                        new edu.princeton.cs.algs4.DijkstraUndirectedSP(reference, source);
                DijkstraUndirectedSP mine = new DijkstraUndirectedSP(graph, source);
                for (int v = 0; v < V; v++) {
                    assertEquals(expected.distTo(v), mine.distTo(v), EPS,
                            "第 " + trial + " 张图 " + source + "→" + v);
                }
            }
        }

        @Test
        @DisplayName("不可达:距离为 +∞,路径为 null")
        void unreachable() {
            EdgeWeightedGraph graph = new EdgeWeightedGraph(4);
            graph.addEdge(new Edge(0, 1, 1.0));
            graph.addEdge(new Edge(2, 3, 1.0));
            DijkstraUndirectedSP sp = new DijkstraUndirectedSP(graph, 0);
            assertEquals(1.0, sp.distTo(1), EPS);
            assertFalse(sp.hasPathTo(2));
            assertEquals(Double.POSITIVE_INFINITY, sp.distTo(2), EPS);
            assertNull(sp.pathTo(2));
            assertNull(sp.pathVertices(2));
            assertNull(sp.edgeTo(2));
        }

        @Test
        @DisplayName("负权被拒绝,消息指出是哪条边")
        void negativeWeightRejected() {
            EdgeWeightedGraph graph = new EdgeWeightedGraph(3);
            graph.addEdge(new Edge(0, 1, 1.0));
            graph.addEdge(new Edge(1, 2, -0.5));
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new DijkstraUndirectedSP(graph, 0));
            assertTrue(e.getMessage().contains("负权"), e.getMessage());
            assertTrue(e.getMessage().contains("1-2"), e.getMessage());
            assertTrue(e.getMessage().contains("BellmanFordSP"), e.getMessage());
        }
    }

    @Nested
    @DisplayName("校验与规模")
    class ValidationAndScaleTest {

        @Test
        @DisplayName("参数校验:null 图、null 模式、源点越界、查询越界")
        void validation() {
            EdgeWeightedGraph graph = tinyEWG();
            assertThrows(IllegalArgumentException.class, () -> new DijkstraUndirectedSP(null, 0));
            assertThrows(IllegalArgumentException.class, () -> new DijkstraUndirectedSP(
                    graph, 0, (DijkstraUndirectedSP.Mode) null));
            assertThrows(IllegalArgumentException.class, () -> new DijkstraUndirectedSP(graph, 8));
            assertThrows(IllegalArgumentException.class, () -> new DijkstraUndirectedSP(graph, -1));
            DijkstraUndirectedSP sp = new DijkstraUndirectedSP(graph, 0);
            assertThrows(IllegalArgumentException.class, () -> sp.distTo(8));
            assertThrows(IllegalArgumentException.class, () -> sp.hasPathTo(-1));
            assertThrows(IllegalArgumentException.class, () -> sp.pathTo(8));
            assertThrows(IllegalArgumentException.class, () -> sp.pathVertices(8));
            assertThrows(IllegalArgumentException.class, () -> sp.edgeTo(8));
        }

        @Test
        @DisplayName("稠密模式适合小图(5000 顶点);大图用懒式(10 万顶点)")
        void scale() {
            int denseN = 5000;
            EdgeWeightedGraph dense = new EdgeWeightedGraph(denseN);
            for (int v = 0; v + 1 < denseN; v++) {
                dense.addEdge(new Edge(v, v + 1, 1.0));
            }
            DijkstraUndirectedSP denseSp = new DijkstraUndirectedSP(dense, 0,
                    DijkstraUndirectedSP.Mode.DENSE);
            assertEquals(denseN - 1, denseSp.distTo(denseN - 1), EPS);

            int lazyN = 100000;
            EdgeWeightedGraph lazy = new EdgeWeightedGraph(lazyN);
            for (int v = 0; v + 1 < lazyN; v++) {
                lazy.addEdge(new Edge(v, v + 1, 1.0));
            }
            DijkstraUndirectedSP lazySp = new DijkstraUndirectedSP(lazy, 0,
                    DijkstraUndirectedSP.Mode.LAZY);
            assertEquals(lazyN - 1, lazySp.distTo(lazyN - 1), EPS);
            assertEquals(lazyN - 1, lazySp.pathTo(lazyN - 1).size());
        }
    }
}
