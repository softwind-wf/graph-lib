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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AcyclicSP} 有向无环图最短路测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>教材样例 tinyEWDAG.txt(含负权弧 -1.20)逐点核对距离与路径 ——
 *       特别注意"绕远路反而更短":5→4 的最短路要经过 6→4 这条负权弧;</li>
 *   <li><b>与 Bellman–Ford 对拍</b>(一般图算法在小 DAG 上的结果必须一致),
 *       非负图再与 Dijkstra 对拍,并与 algs4 参考实现对拍;</li>
 *   <li>路径合法性:首尾相接、权值之和等于报告的距离;</li>
 *   <li>有环图必须被拒绝(消息里提示改用 BellmanFordSP / DijkstraSP);</li>
 *   <li>迭代实现:20 万顶点的链式 DAG 不栈溢出。</li>
 * </ol>
 */
@DisplayName("AcyclicSP 有向无环图最短路测试")
class AcyclicSPTest {

    private static final double EPS = 1e-9;

    private static EdgeWeightedDigraph tinyEwdag() {
        return GraphIO.readWeightedDigraphFile("tinyEWDAG.txt");
    }

    @Nested
    @DisplayName("教材样例 tinyEWDAG.txt")
    class KnownDataTest {

        @Test
        @DisplayName("源点 5:8 个顶点的距离逐项核对(含负权弧带来的更短路径)")
        void distances() {
            AcyclicSP sp = new AcyclicSP(tinyEwdag(), 5);
            double[] expected = {0.31, 0.32, 0.57, 0.61, -0.07, 0.0, 1.13, 0.28};
            for (int v = 0; v < expected.length; v++) {
                assertEquals(expected[v], sp.distTo(v), EPS, "distTo(" + v + ")");
            }
            assertEquals(5, sp.source());
            assertEquals(8, sp.V());
            assertEquals(13, sp.E());
        }

        @Test
        @DisplayName("负权弧被用上了:5→4 的最短路是 5→1→3→6→4,距离 -0.07")
        void negativeEdgeIsUsed() {
            AcyclicSP sp = new AcyclicSP(tinyEwdag(), 5);
            assertEquals(Arrays.asList(5, 1, 3, 6, 4), sp.pathVertices(4));
            assertEquals(-0.07, sp.distTo(4), EPS);
            assertEquals(Arrays.asList(5, 1, 3, 6, 4, 0, 2), sp.pathVertices(2),
                    "到 2 的最短路也要先经过负权弧");
            assertEquals(Arrays.asList(5, 7), sp.pathVertices(7), "到 7 直接走 5->7 更短");
        }

        @Test
        @DisplayName("每条最短路径都合法:首尾相接,权值之和等于距离")
        void pathsAreValid() {
            EdgeWeightedDigraph graph = tinyEwdag();
            AcyclicSP sp = new AcyclicSP(graph, 5);
            for (int v = 0; v < graph.V(); v++) {
                assertTrue(sp.hasPathTo(v), "源点 5 应能到达 " + v);
                List<DirectedEdge> path = sp.pathTo(v);
                double sum = 0.0;
                int current = 5;
                for (DirectedEdge edge : path) {
                    assertEquals(current, edge.from(), "路径应首尾相接");
                    current = edge.to();
                    sum += edge.weight();
                }
                assertEquals(v, current, "路径终点应是 " + v);
                assertEquals(sp.distTo(v), sum, EPS, "路径权值之和应等于距离");
            }
        }

        @Test
        @DisplayName("拓扑序合法:每条弧的起点都排在终点之前")
        void topologicalOrderIsValid() {
            EdgeWeightedDigraph graph = tinyEwdag();
            int[] order = new AcyclicSP(graph, 5).topologicalOrder();
            int[] position = new int[graph.V()];
            for (int i = 0; i < order.length; i++) {
                position[order[i]] = i;
            }
            for (DirectedEdge edge : graph.edges()) {
                assertTrue(position[edge.from()] < position[edge.to()], edge + " 的起点应排在终点之前");
            }
            assertEquals(11, new AcyclicSP(graph, 5).relaxCount(),
                    "13 条弧里只有 11 条松弛成功 —— 其余几条被更短的路\"挡住\"了");
        }

        @Test
        @DisplayName("对照实验:Dijkstra 会拒绝这张含负权的图,提示改用 Bellman-Ford")
        void dijkstraRejectsNegativeWeights() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new DijkstraSP(tinyEwdag(), 5));
            assertTrue(e.getMessage().contains("负权"), e.getMessage());
        }

        @Test
        @DisplayName("toString 含源点、顶点/边数与每个顶点的结果")
        void toStringContent() {
            String text = new AcyclicSP(tinyEwdag(), 5).toString();
            assertTrue(text.contains("AcyclicSP(源点 5)"), text);
            assertTrue(text.contains("松弛 11 次"), text);
            assertTrue(text.contains("-0.07"), text);
        }
    }

    @Nested
    @DisplayName("与其它实现/参考实现对拍")
    class OracleTest {

        @Test
        @DisplayName("30 张随机 DAG(含负权):与 BellmanFordSP 两种模式完全一致")
        void agreesWithBellmanFord() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 30; trial++) {
                EdgeWeightedDigraph graph = randomDag(rnd, 2 + rnd.nextInt(8), true);
                for (int s = 0; s < graph.V(); s++) {
                    AcyclicSP mine = new AcyclicSP(graph, s);
                    for (BellmanFordSP.Mode mode : BellmanFordSP.Mode.values()) {
                        BellmanFordSP other = new BellmanFordSP(graph, s, mode);
                        assertFalse(other.hasNegativeCycle(), "DAG 不可能有负环");
                        for (int v = 0; v < graph.V(); v++) {
                            assertEquals(other.distTo(v), mine.distTo(v), EPS,
                                    "第 " + trial + " 张图,源点 " + s + ",终点 " + v);
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("30 张随机非负 DAG:与 DijkstraSP 一致")
        void agreesWithDijkstra() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 30; trial++) {
                EdgeWeightedDigraph graph = randomDag(rnd, 2 + rnd.nextInt(8), false);
                int s = rnd.nextInt(graph.V());
                AcyclicSP mine = new AcyclicSP(graph, s);
                DijkstraSP other = new DijkstraSP(graph, s);
                for (int v = 0; v < graph.V(); v++) {
                    assertEquals(other.distTo(v), mine.distTo(v), EPS,
                            "第 " + trial + " 张图," + s + " → " + v);
                }
            }
        }

        @Test
        @DisplayName("30 张随机 DAG:与 algs4 参考实现对拍")
        void agreesWithReference() {
            Random rnd = new Random(31337L);
            for (int trial = 0; trial < 30; trial++) {
                EdgeWeightedDigraph graph = randomDag(rnd, 2 + rnd.nextInt(8), true);
                edu.princeton.cs.algs4.EdgeWeightedDigraph reference =
                        new edu.princeton.cs.algs4.EdgeWeightedDigraph(graph.V());
                for (DirectedEdge edge : graph.edges()) {
                    reference.addEdge(new edu.princeton.cs.algs4.DirectedEdge(
                            edge.from(), edge.to(), edge.weight()));
                }
                int s = rnd.nextInt(graph.V());
                edu.princeton.cs.algs4.AcyclicSP expected = new edu.princeton.cs.algs4.AcyclicSP(reference, s);
                AcyclicSP mine = new AcyclicSP(graph, s);
                for (int v = 0; v < graph.V(); v++) {
                    assertEquals(expected.distTo(v), mine.distTo(v), EPS,
                            "第 " + trial + " 张图," + s + " → " + v);
                }
            }
        }

        @Test
        @DisplayName("不可达顶点:距离为 +∞,pathTo 为 null")
        void unreachable() {
            EdgeWeightedDigraph graph = GraphIO.parseWeightedDigraph("4\n2\n0 1 1\n2 3 -1\n");
            AcyclicSP sp = new AcyclicSP(graph, 0);
            assertEquals(1.0, sp.distTo(1), EPS);
            assertFalse(sp.hasPathTo(2));
            assertEquals(Double.POSITIVE_INFINITY, sp.distTo(2), EPS);
            assertNull(sp.pathTo(2));
            assertNull(sp.pathVertices(2));
            assertNull(sp.edgeTo(2));
        }
    }

    @Nested
    @DisplayName("校验与规模")
    class ValidationAndScaleTest {

        @Test
        @DisplayName("有环图:抛 IllegalArgumentException 并在消息里给出环与替代算法")
        void cycleRejected() {
            EdgeWeightedDigraph graph = GraphIO.parseWeightedDigraph("3\n3\n0 1 1\n1 2 1\n2 0 1\n");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new AcyclicSP(graph, 0));
            assertTrue(e.getMessage().contains("有环"), e.getMessage());
            assertTrue(e.getMessage().contains("BellmanFordSP"), e.getMessage());
        }

        @Test
        @DisplayName("参数校验:null 图、源点越界、查询越界")
        void validation() {
            EdgeWeightedDigraph graph = tinyEwdag();
            assertThrows(IllegalArgumentException.class, () -> new AcyclicSP(null, 0));
            assertThrows(IllegalArgumentException.class, () -> new AcyclicSP(graph, 8));
            assertThrows(IllegalArgumentException.class, () -> new AcyclicSP(graph, -1));
            AcyclicSP sp = new AcyclicSP(graph, 5);
            assertThrows(IllegalArgumentException.class, () -> sp.distTo(8));
            assertThrows(IllegalArgumentException.class, () -> sp.hasPathTo(-1));
            assertThrows(IllegalArgumentException.class, () -> sp.pathTo(8));
            assertThrows(IllegalArgumentException.class, () -> sp.edgeTo(8));
            assertThrows(IllegalArgumentException.class, () -> sp.pathVertices(8));
        }

        @Test
        @DisplayName("20 万顶点的链式 DAG(迭代实现不栈溢出)")
        void largeChain() {
            int n = 200000;
            EdgeWeightedDigraph graph = new EdgeWeightedDigraph(n);
            for (int v = 0; v + 1 < n; v++) {
                graph.addEdge(v, v + 1, 1.0 + (v % 3) * 0.25);
            }
            AcyclicSP sp = new AcyclicSP(graph, 0);
            assertTrue(sp.hasPathTo(n - 1));
            assertTrue(sp.distTo(n - 1) > n / 2.0);
            assertEquals(n - 1, sp.pathTo(n - 1).size());
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /**
     * 随机 DAG:顶点编号小的只能指向编号大的(必然无环);
     * {@code allowNegative} 为 true 时权值取 [-2, 2.5],否则取 [0, 2.5]。
     */
    static EdgeWeightedDigraph randomDag(Random rnd, int V, boolean allowNegative) {
        return DigraphGenerator.edgeWeightedDagByProbability(rnd, V, 0.35, allowNegative);
    }

    /** 供其它测试复用:把图的所有边收集成"from->to"字符串 */
    static List<String> edgeStrings(EdgeWeightedDigraph graph) {
        List<String> list = new ArrayList<String>();
        for (DirectedEdge edge : graph.edges()) {
            list.add(edge.from() + "->" + edge.to());
        }
        java.util.Collections.sort(list);
        return list;
    }
}
