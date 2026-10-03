package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AcyclicLP} DAG 最长路径测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li><b>取反对拍</b>:把所有弧权取负之后跑 {@link AcyclicSP}(最短路),
 *       结果取负就是最长路 —— 等价变换,与实现无关;</li>
 *   <li><b>定义级对拍</b>:小 DAG 上暴力枚举所有源→汇路径取最大值;</li>
 *   <li>路径合法:首尾相接、权值之和 = distTo;</li>
 *   <li>有环时直接拒绝(环上最长路无界);</li>
 *   <li>不可达顶点为 −∞;与 AOE 关键路径的"最长路 = 总工期"呼应。</li>
 * </ol>
 */
@DisplayName("AcyclicLP DAG 最长路径测试")
class AcyclicLPTest {

    private static final double EPS = 1e-9;

    private static EdgeWeightedDigraph tinyEwdag() {
        return GraphIO.readWeightedDigraphFile("tinyEWDAG.txt");
    }

    @Nested
    @DisplayName("教材样例 tinyEWDAG.txt")
    class KnownDataTest {

        @Test
        @DisplayName("源点 5:最长距离逐项核对(5→2 走 5-1-3-6-0-2 = 1.97)")
        void distances() {
            AcyclicLP longest = new AcyclicLP(tinyEwdag(), 5);
            double[] expected = {1.71, 0.32, 1.97, 0.61, 0.35, 0.0, 1.13, 1.00};
            for (int v = 0; v < expected.length; v++) {
                assertEquals(expected[v], longest.distTo(v), 1e-9, "distTo(" + v + ")");
            }
            assertEquals(java.util.Arrays.asList(5, 1, 3, 6, 0, 2), longest.pathVertices(2));
            assertEquals(1.97, longest.distTo(2), EPS);
        }

        @Test
        @DisplayName("与最短路对照:最长路 ≥ 最短路,且负权弧对两者的影响不同")
        void compareWithShortest() {
            EdgeWeightedDigraph graph = tinyEwdag();
            AcyclicLP longest = new AcyclicLP(graph, 5);
            AcyclicSP shortest = new AcyclicSP(graph, 5);
            for (int v = 0; v < graph.V(); v++) {
                assertTrue(longest.distTo(v) >= shortest.distTo(v) - EPS,
                        "最长路不应短于最短路(顶点 " + v + ")");
            }
            assertEquals(-0.07, shortest.distTo(4), EPS);
            assertEquals(0.35, longest.distTo(4), EPS, "最长路不走那条负权弧");
        }

        @Test
        @DisplayName("路径合法:首尾相接、权值之和等于距离、长度等于边数")
        void pathsAreValid() {
            EdgeWeightedDigraph graph = tinyEwdag();
            AcyclicLP longest = new AcyclicLP(graph, 5);
            for (int v = 0; v < graph.V(); v++) {
                List<DirectedEdge> path = longest.pathTo(v);
                double sum = 0.0;
                int current = 5;
                for (DirectedEdge edge : path) {
                    assertEquals(current, edge.from(), "路径应首尾相接");
                    current = edge.to();
                    sum += edge.weight();
                }
                assertEquals(v, current);
                assertEquals(longest.distTo(v), sum, EPS);
                assertEquals(path.size() + 1, longest.pathVertices(v).size());
            }
        }

        @Test
        @DisplayName("toString 含源点与规模")
        void toStringContent() {
            String text = new AcyclicLP(tinyEwdag(), 5).toString();
            assertTrue(text.contains("AcyclicLP(源点 5)"), text);
            assertTrue(text.contains("8 个顶点,13 条边"), text);
        }
    }

    @Nested
    @DisplayName("取反/暴力对拍")
    class OracleTest {

        @Test
        @DisplayName("30 张随机 DAG:把弧权取负跑最短路,再取负 = 最长路")
        void agreesWithNegatedShortestPath() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 30; trial++) {
                EdgeWeightedDigraph graph = DigraphGenerator.edgeWeightedDag(
                        rnd, 2 + rnd.nextInt(8), rnd.nextInt(16), true);
                int source = rnd.nextInt(graph.V());
                AcyclicLP longest = new AcyclicLP(graph, source);

                EdgeWeightedDigraph negated = new EdgeWeightedDigraph(graph.V());
                for (DirectedEdge edge : graph.edges()) {
                    negated.addEdge(new DirectedEdge(edge.from(), edge.to(), -edge.weight()));
                }
                AcyclicSP shortestOfNegated = new AcyclicSP(negated, source);

                for (int v = 0; v < graph.V(); v++) {
                    if (!longest.hasPathTo(v)) {
                        assertFalse(shortestOfNegated.hasPathTo(v));
                        continue;
                    }
                    assertEquals(-shortestOfNegated.distTo(v), longest.distTo(v), EPS,
                            "第 " + trial + " 张图 " + source + "→" + v);
                }
            }
        }

        @Test
        @DisplayName("30 张小 DAG:与暴力枚举所有路径的最大值一致")
        void agreesWithBruteForce() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 30; trial++) {
                EdgeWeightedDigraph graph = DigraphGenerator.edgeWeightedDag(
                        rnd, 2 + rnd.nextInt(6), rnd.nextInt(10), true);
                AcyclicLP longest = new AcyclicLP(graph, 0);
                for (int v = 0; v < graph.V(); v++) {
                    double best = bruteForceLongestTo(graph, 0, v);
                    if (best == Double.NEGATIVE_INFINITY) {
                        assertFalse(longest.hasPathTo(v), "第 " + trial + " 张图:" + v + " 应不可达");
                    }
                    else {
                        assertEquals(best, longest.distTo(v), EPS,
                                "第 " + trial + " 张图 0→" + v);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("校验")
    class ValidationTest {

        @Test
        @DisplayName("有环图抛 IllegalArgumentException,并说明原因")
        void cycleRejected() {
            EdgeWeightedDigraph graph = GraphIO.parseWeightedDigraph("3\n3\n0 1 1\n1 2 1\n2 0 1\n");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new AcyclicLP(graph, 0));
            assertTrue(e.getMessage().contains("有环"), e.getMessage());
            assertTrue(e.getMessage().contains("无限变长"), e.getMessage());
        }

        @Test
        @DisplayName("参数校验:null 图、源点越界、查询越界")
        void validation() {
            EdgeWeightedDigraph graph = tinyEwdag();
            assertThrows(IllegalArgumentException.class, () -> new AcyclicLP(null, 0));
            assertThrows(IllegalArgumentException.class, () -> new AcyclicLP(graph, 8));
            assertThrows(IllegalArgumentException.class, () -> new AcyclicLP(graph, -1));
            AcyclicLP longest = new AcyclicLP(graph, 5);
            assertThrows(IllegalArgumentException.class, () -> longest.distTo(8));
            assertThrows(IllegalArgumentException.class, () -> longest.hasPathTo(-1));
            assertThrows(IllegalArgumentException.class, () -> longest.pathTo(8));
            assertThrows(IllegalArgumentException.class, () -> longest.edgeTo(8));
            assertThrows(IllegalArgumentException.class, () -> longest.pathVertices(8));
        }

        @Test
        @DisplayName("不可达顶点:−∞ 且路径为 null")
        void unreachable() {
            EdgeWeightedDigraph graph = GraphIO.parseWeightedDigraph("4\n2\n0 1 1\n2 3 1\n");
            AcyclicLP longest = new AcyclicLP(graph, 0);
            assertEquals(1.0, longest.distTo(1), EPS);
            assertEquals(Double.NEGATIVE_INFINITY, longest.distTo(2), EPS);
            assertFalse(longest.hasPathTo(2));
            assertNull(longest.pathTo(2));
            assertNull(longest.pathVertices(2));
            assertNull(longest.edgeTo(2));
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /** 暴力:source → target 的最大路径长度 */
    private static double bruteForceLongestTo(EdgeWeightedDigraph graph, int source, int target) {
        return walkTo(graph, source, target, 0.0);
    }

    private static double walkTo(EdgeWeightedDigraph graph, int v, int target, double soFar) {
        if (v == target) {
            return soFar;
        }
        double best = Double.NEGATIVE_INFINITY;
        for (DirectedEdge edge : graph.adj(v)) {
            best = Math.max(best, walkTo(graph, edge.to(), target, soFar + edge.weight()));
        }
        return best;
    }
}
