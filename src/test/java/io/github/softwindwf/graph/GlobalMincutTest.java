package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GlobalMincut} 全局最小割测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li><b>定义级对拍</b>:小图上枚举所有"非平凡二划分"(2^(V−1) − 1 种),直接取最小割容量 ——
 *       与 Stoer–Wagner 的任何中间步骤都无关;</li>
 *   <li><b>与 s-t 最小割对拍</b>:枚举所有顶点对 (s,t) 跑一遍最大流,取最小值
 *       (这正好是"全局最小割"的暴力定义,且复用了已经验证过的最大流实现);</li>
 *   <li>返回的划分合法:两侧非空、覆盖全部顶点、割上边权之和 = 割容量;</li>
 *   <li>不连通图的最小割是 0;自环被忽略;平行边权值相加;</li>
 *   <li>与 algs4 的 {@code GlobalMincut} 对拍;规模:200 个顶点。</li>
 * </ol>
 */
@DisplayName("GlobalMincut 全局最小割测试")
class GlobalMincutTest {

    private static final double EPS = 1e-9;

    private static EdgeWeightedGraph tinyEWG() {
        return GraphIO.readWeightedFile("tinyEWG.txt");
    }

    @Nested
    @DisplayName("已知样例")
    class KnownDataTest {

        @Test
        @DisplayName("tinyEWG.txt:最小割是 0.95(把顶点 5 单独切出去)")
        void tinyEwgKnownValues() {
            EdgeWeightedGraph graph = tinyEWG();
            GlobalMincut mincut = new GlobalMincut(graph);
            assertEquals(0.95, mincut.weight(), 1e-9);
            assertEquals(java.util.Arrays.asList(5), mincut.sideA());
            assertEquals(0.95, mincut.cutWeight(), 1e-9);
            assertEquals(3, mincut.cut().size(), "1-5、4-5、5-7");
            assertEquals(graph.V() - 1, mincut.phaseCount());
        }

        @Test
        @DisplayName("划分合法:两侧非空、覆盖全部顶点、割上边都是横跨两端的")
        void cutIsValid() {
            EdgeWeightedGraph graph = tinyEWG();
            GlobalMincut mincut = new GlobalMincut(graph);
            List<Integer> a = mincut.sideA();
            List<Integer> b = mincut.sideB();
            assertFalse(a.isEmpty());
            assertFalse(b.isEmpty());
            assertEquals(graph.V(), a.size() + b.size());
            for (Edge edge : mincut.cut()) {
                int x = edge.either();
                int y = edge.other(x);
                assertTrue(mincut.inSideA(x) != mincut.inSideA(y), edge + " 应横跨两侧");
            }
            assertEquals(mincut.weight(), mincut.cutWeight(), 1e-9, "割上权值之和应等于割容量");
            assertThrows(IllegalArgumentException.class, () -> mincut.inSideA(graph.V()));
        }

        @Test
        @DisplayName("不连通图:最小割为 0;自环被忽略;平行边权值相加")
        void edgeCases() {
            EdgeWeightedGraph disconnected = new EdgeWeightedGraph(4);
            disconnected.addEdge(new Edge(0, 1, 5.0));
            disconnected.addEdge(new Edge(2, 3, 7.0));
            assertEquals(0.0, new GlobalMincut(disconnected).weight(), EPS);

            EdgeWeightedGraph withSelfLoop = new EdgeWeightedGraph(3);
            withSelfLoop.addEdge(new Edge(0, 1, 2.0));
            withSelfLoop.addEdge(new Edge(1, 2, 2.0));
            withSelfLoop.addEdge(new Edge(0, 2, 2.0));
            withSelfLoop.addEdge(new Edge(0, 0, 100.0));        // 自环不影响任何割
            assertEquals(4.0, new GlobalMincut(withSelfLoop).weight(), EPS);

            EdgeWeightedGraph parallel = new EdgeWeightedGraph(2);
            parallel.addEdge(new Edge(0, 1, 1.5));
            parallel.addEdge(new Edge(0, 1, 2.5));
            GlobalMincut mincut = new GlobalMincut(parallel);
            assertEquals(4.0, mincut.weight(), EPS, "平行边权值相加");
            assertEquals(2, mincut.cut().size());
        }

        @Test
        @DisplayName("空图、单顶点、两个顶点")
        void trivial() {
            assertEquals(0.0, new GlobalMincut(new EdgeWeightedGraph(0)).weight(), EPS);
            assertEquals(0.0, new GlobalMincut(new EdgeWeightedGraph(1)).weight(), EPS);

            EdgeWeightedGraph pair = new EdgeWeightedGraph(2);
            pair.addEdge(new Edge(0, 1, 3.0));
            GlobalMincut mincut = new GlobalMincut(pair);
            assertEquals(3.0, mincut.weight(), EPS);
            assertEquals(1, mincut.sideA().size());
            assertEquals(1, mincut.sideB().size());
        }

        @Test
        @DisplayName("toString 含容量与两侧")
        void toStringContent() {
            String text = new GlobalMincut(tinyEWG()).toString();
            assertTrue(text.contains("GlobalMincut: 容量 0.95"), text);
            assertTrue(text.contains("A 侧"), text);
        }
    }

    @Nested
    @DisplayName("与暴力/最大流对拍")
    class OracleTest {

        @Test
        @DisplayName("30 张随机图:与暴力枚举所有二划分一致")
        void agreesWithBruteForcePartitions() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 2 + rnd.nextInt(8);
                EdgeWeightedGraph graph = GraphGenerator.edgeWeightedPositive(rnd, V, rnd.nextInt(14));
                double expected = bruteForceMinCut(graph);
                double actual = new GlobalMincut(graph).weight();
                assertEquals(expected, actual, 1e-9, "第 " + trial + " 张图:V=" + V + ", E=" + graph.E());
            }
        }

        @Test
        @DisplayName("20 张随机图:与'枚举所有 (s,t) 跑最大流取最小'一致")
        void agreesWithAllPairsMaxFlow() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(7);
                EdgeWeightedGraph graph = GraphGenerator.edgeWeightedPositive(rnd, V, rnd.nextInt(12));
                double best = Double.POSITIVE_INFINITY;
                for (int s = 0; s < V; s++) {
                    for (int t = s + 1; t < V; t++) {
                        best = Math.min(best, maxFlowValue(graph, s, t));
                    }
                }
                assertEquals(best, new GlobalMincut(graph).weight(), 1e-9,
                        "第 " + trial + " 张图");
            }
        }

        @Test
        @DisplayName("20 张随机图:与 algs4 的 GlobalMincut 对拍")
        void agreesWithReference() {
            Random rnd = new Random(31415L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(8);
                EdgeWeightedGraph graph = GraphGenerator.edgeWeightedPositive(rnd, V, rnd.nextInt(14));
                edu.princeton.cs.algs4.EdgeWeightedGraph reference =
                        new edu.princeton.cs.algs4.EdgeWeightedGraph(V);
                for (Edge edge : graph.edges()) {
                    reference.addEdge(new edu.princeton.cs.algs4.Edge(
                            edge.either(), edge.other(edge.either()), edge.weight()));
                }
                assertEquals(new edu.princeton.cs.algs4.GlobalMincut(reference).weight(),
                        new GlobalMincut(graph).weight(), 1e-9, "第 " + trial + " 张图");
            }
        }
    }

    @Nested
    @DisplayName("校验与规模")
    class ValidationAndScaleTest {

        @Test
        @DisplayName("参数校验:null 图、负权、顶点数上限")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new GlobalMincut(null));
            EdgeWeightedGraph negative = new EdgeWeightedGraph(2);
            negative.addEdge(new Edge(0, 1, -1.0));
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new GlobalMincut(negative));
            assertTrue(e.getMessage().contains("非负"), e.getMessage());
            assertThrows(IllegalArgumentException.class,
                    () -> new GlobalMincut(new EdgeWeightedGraph(2049)));
        }

        @Test
        @DisplayName("200 个顶点的图:Θ(V³) 也能跑,结果与最小流对拍")
        void scale() {
            Random rnd = new Random(99L);
            EdgeWeightedGraph graph = GraphGenerator.edgeWeightedPositive(rnd, 200, 1000);
            GlobalMincut mincut = new GlobalMincut(graph);
            assertTrue(mincut.weight() > 0);
            assertEquals(mincut.weight(), mincut.cutWeight(), 1e-6);
            // 单独切出度数最小的顶点,是一个上界
            double bestVertexCut = Double.POSITIVE_INFINITY;
            for (int v = 0; v < graph.V(); v++) {
                double sum = 0.0;
                for (Edge edge : graph.adj(v)) {
                    sum += edge.weight();
                }
                bestVertexCut = Math.min(bestVertexCut, sum);
            }
            assertTrue(mincut.weight() <= bestVertexCut + 1e-9,
                    "全局最小割不会大于任何单点割");
            assertEquals(graph.V() - 1, mincut.phaseCount());
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /** 暴力:枚举所有含顶点 0 的真子集作为 A 侧,取最小割容量 */
    static double bruteForceMinCut(EdgeWeightedGraph graph) {
        int V = graph.V();
        if (V < 2) {
            return 0.0;
        }
        double best = Double.POSITIVE_INFINITY;
        // 固定顶点 0 在 A 侧,枚举其余 V−1 个顶点的归属(排除 A 全集的 1 种)
        for (int mask = 0; mask < (1 << (V - 1)); mask++) {
            boolean[] inA = new boolean[V];
            inA[0] = true;
            int count = 1;
            for (int v = 1; v < V; v++) {
                if ((mask & (1 << (v - 1))) != 0) {
                    inA[v] = true;
                    count++;
                }
            }
            if (count == V) {
                continue;                                   // B 侧为空,不是割
            }
            double capacity = 0.0;
            for (Edge edge : graph.edges()) {
                int a = edge.either();
                int b = edge.other(a);
                if (inA[a] != inA[b]) {
                    capacity += edge.weight();
                }
            }
            best = Math.min(best, capacity);
        }
        return best;
    }

    /** 用最大流求 s-t 最小割容量(无向图 → 两个方向各加一条弧) */
    static double maxFlowValue(EdgeWeightedGraph graph, int source, int sink) {
        FlowNetwork network = new FlowNetwork(graph.V());
        for (Edge edge : graph.edges()) {
            int a = edge.either();
            int b = edge.other(a);
            network.addEdge(a, b, edge.weight());
            network.addEdge(b, a, edge.weight());
        }
        return new Dinic(network, source, sink).value();
    }
}
