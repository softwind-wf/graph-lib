package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 最短路径测试的公共校验工具({@link DijkstraSP} 与 {@link FloydWarshall} 两个测试类共用)。
 *
 * <p><b>独立性声明</b>:这里实现的 Bellman–Ford 与被测的两个类都无关 ——
 * 它是"每轮把所有边松弛一遍、共 V−1 轮"的朴素动态规划,思路不同于 Dijkstra 的贪心,
 * 也不同于 Floyd–Warshall 的三重循环。用它当参照物可以避免"用同一个思路验证自己"。</p>
 */
final class SpTestSupport {

    /** 距离比较容差 */
    static final double EPS = 1e-9;

    private SpTestSupport() {
        throw new AssertionError("测试工具类不应该被实例化");
    }

    /**
     * 独立实现的 Bellman–Ford:做 V−1 轮"对每条边松弛一遍",返回单源最短距离。
     * 只在<b>无负环</b>的图上使用(有负环时结果无意义)。
     *
     * @param g      有向带权图
     * @param source 源点
     * @return 各顶点最短距离;不可达为 +∞
     */
    static double[] bellmanFord(EdgeWeightedDigraph g, int source) {
        int V = g.V();
        double[] dist = new double[V];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[source] = 0.0;
        List<DirectedEdge> edges = new ArrayList<DirectedEdge>();
        for (DirectedEdge e : g.edges()) {
            edges.add(e);
        }
        for (int round = 0; round < V - 1; round++) {
            boolean changed = false;
            for (DirectedEdge e : edges) {
                if (dist[e.from()] < Double.POSITIVE_INFINITY) {
                    double candidate = dist[e.from()] + e.weight();
                    if (candidate < dist[e.to()] - EPS) {
                        dist[e.to()] = candidate;
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        return dist;
    }

    /**
     * 全源距离矩阵的独立参照物:对每个源点各跑一次 Bellman–Ford。
     *
     * @param g 有向带权图(不得含负环)
     * @return allPairs[i][j] = i 到 j 的最短距离
     */
    static double[][] bellmanFordAllPairs(EdgeWeightedDigraph g) {
        double[][] allPairs = new double[g.V()][];
        for (int source = 0; source < g.V(); source++) {
            allPairs[source] = bellmanFord(g, source);
        }
        return allPairs;
    }

    /**
     * 单源最短路的固定点条件(与具体算法无关的刻画):
     * <ul>
     *   <li>{@code dist[s] = 0};</li>
     *   <li>对每条边 v-&gt;w(v 可达):{@code dist[w] ≤ dist[v] + weight} —— 没有边能再松弛;</li>
     *   <li>每个可达的 {@code w ≠ s} 都有某条入边取到等号:{@code dist[w] = min{dist[v] + weight}}。</li>
     * </ul>
     */
    static void assertOptimalityConditions(EdgeWeightedDigraph g, int source, double[] dist) {
        assertEquals(0.0, dist[source], EPS, "源点距离应为 0");
        for (DirectedEdge e : g.edges()) {
            if (dist[e.from()] < Double.POSITIVE_INFINITY) {
                assertTrue(dist[e.to()] <= dist[e.from()] + e.weight() + EPS,
                        "边 " + e + " 仍可松弛:dist[" + e.to() + "]=" + dist[e.to()]
                                + " > dist[" + e.from() + "]+w=" + (dist[e.from()] + e.weight()));
            }
        }
        for (int w = 0; w < g.V(); w++) {
            if (w == source || dist[w] == Double.POSITIVE_INFINITY) {
                continue;
            }
            double best = Double.POSITIVE_INFINITY;
            for (DirectedEdge e : g.edges()) {
                if (e.to() == w && dist[e.from()] < Double.POSITIVE_INFINITY) {
                    best = Math.min(best, dist[e.from()] + e.weight());
                }
            }
            assertEquals(best, dist[w], EPS, "顶点 " + w + " 的距离不等于其入边来源的最小值");
        }
    }

    /** 随机有向图;{@code allowNegative} 为 true 时权值落在 [-1, 1) */
    static EdgeWeightedDigraph randomDigraph(Random rnd, int V, int edgeCount, boolean allowNegative) {
        return DigraphGenerator.edgeWeighted(rnd, V, edgeCount, allowNegative);
    }

    /**
     * 随机<b>有向无环图</b>:边只从编号小的顶点指向编号大的顶点(权值可为负),
     * 因此<b>必然没有环</b>,是测试"带负权边的正确性"最省事的造图方式。
     */
    static EdgeWeightedDigraph randomDag(Random rnd, int V, int edgeCount, boolean allowNegative) {
        return DigraphGenerator.edgeWeightedDag(rnd, V, edgeCount, allowNegative);
    }

    /** 断言两张 V×V 距离矩阵逐格一致 */
    static void assertMatrixEquals(double[][] expected, double[][] actual, String message) {
        assertEquals(expected.length, actual.length, message + ":行数");
        for (int i = 0; i < expected.length; i++) {
            for (int j = 0; j < expected[i].length; j++) {
                if (Double.isInfinite(expected[i][j]) && Double.isInfinite(actual[i][j])) {
                    continue;
                }
                assertEquals(expected[i][j], actual[i][j], EPS, message + ":(" + i + "," + j + ")");
            }
        }
    }

    /** 图上是否真的存在这条边 */
    static void assertEdgeExists(EdgeWeightedDigraph g, int from, int to) {
        assertTrue(g.hasEdge(from, to), "路径上的边 " + from + "->" + to + " 不在图中");
    }

    /** 顶点序列是否是图中的一条合法路径(相邻都有边),并断言不可达时返回 null */
    static void assertPathIsValid(EdgeWeightedDigraph g, int from, int to,
                                  List<Integer> vertices, boolean reachable) {
        if (!reachable) {
            assertEquals(null, vertices, "不可达时路径应为 null");
            return;
        }
        assertFalse(vertices == null, "可达却没有路径");
        assertEquals(from, vertices.get(0).intValue(), "路径应从起点开始");
        assertEquals(to, vertices.get(vertices.size() - 1).intValue(), "路径应止于终点");
        for (int i = 0; i + 1 < vertices.size(); i++) {
            assertEdgeExists(g, vertices.get(i), vertices.get(i + 1));
        }
    }
}
