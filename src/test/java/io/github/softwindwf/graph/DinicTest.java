package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Dinic} 最大流测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>tinyFN.txt:最大流 4,2 轮层次图、3 条增广路,与其它算法一致;</li>
 *   <li>层次图阶段数不超过 V(每轮 s→t 的最短距离严格增大);</li>
 *   <li>结果合法且与暴力最小割、algs4 参考实现一致;</li>
 *   <li>规模测试:1000 条并行路径的网络(3002 个顶点、3000 条弧)最大流 = 1000;</li>
 *   <li>与单位容量二分图匹配的等价性在 {@link BipartiteMatchingTest} 中另行验证。</li>
 * </ol>
 */
@DisplayName("Dinic 最大流测试")
class DinicTest {

    private static final double EPS = 1e-9;

    private static FlowNetwork tinyFN() {
        return GraphIO.readFlowNetworkFile("tinyFN.txt");
    }

    @Nested
    @DisplayName("教材样例 tinyFN.txt")
    class KnownDataTest {

        @Test
        @DisplayName("最大流 4,2 轮层次图,3 条增广路")
        void knownValue() {
            FlowNetwork network = tinyFN();
            Dinic maxFlow = new Dinic(network, 0, 5);
            assertEquals(4.0, maxFlow.value(), EPS);
            assertEquals(2, maxFlow.phaseCount());
            assertEquals(3, maxFlow.augmentationCount());
            assertEquals(4.0, maxFlow.minCut().capacity(), EPS);
            assertEquals(java.util.Arrays.asList(0, 2), maxFlow.minCut().sourceSide());
            MaxFlowTestSupport.assertFeasibleFlow(network, 0, 5, maxFlow.value());
        }

        @Test
        @DisplayName("阶段数不超过 V,增广路条数不超过 E·V")
        void boundedByTheory() {
            Dinic maxFlow = new Dinic(tinyFN(), 0, 5);
            assertTrue(maxFlow.phaseCount() <= 6, "阶段数 ≤ V");
            assertTrue(maxFlow.augmentationCount() <= 6 * 8, "增广路条数 ≤ V·E");
        }

        @Test
        @DisplayName("toString 含流值、阶段数与增广路条数")
        void toStringContent() {
            String text = new Dinic(tinyFN(), 0, 5).toString();
            assertTrue(text.contains("Dinic: 最大流 4.00"), text);
            assertTrue(text.contains("2 轮层次图"), text);
        }
    }

    @Nested
    @DisplayName("与其它实现/参考实现对拍")
    class OracleTest {

        @Test
        @DisplayName("50 张随机网络:与增广路法、Edmonds–Karp、暴力最小割、algs4 全部一致")
        void allAgree() {
            Random rnd = new Random(2718L);
            for (int trial = 0; trial < 50; trial++) {
                FlowNetwork template = MaxFlowTestSupport.randomNetwork(
                        rnd, 2 + rnd.nextInt(6), rnd.nextInt(12), 6);
                int sink = template.V() - 1;

                FlowNetwork network = template.copy();
                Dinic maxFlow = new Dinic(network, 0, sink);
                double value = maxFlow.value();

                assertEquals(MaxFlowTestSupport.bruteForceMinCut(template, 0, sink), value, EPS,
                        "第 " + trial + " 张图:暴力最小割");
                assertEquals(new FordFulkerson(template.copy(), 0, sink).value(), value, EPS,
                        "第 " + trial + " 张图:增广路法");
                assertEquals(new EdmondsKarp(template.copy(), 0, sink).value(), value, EPS,
                        "第 " + trial + " 张图:Edmonds–Karp");
                assertEquals(new edu.princeton.cs.algs4.FordFulkerson(
                        MaxFlowTestSupport.toReference(template), 0, sink).value(), value, EPS,
                        "第 " + trial + " 张图:algs4");
                MaxFlowTestSupport.assertFeasibleFlow(network, 0, sink, value);
                assertEquals(value, maxFlow.minCut().capacity(), EPS);
            }
        }

        @Test
        @DisplayName("参数校验:null、越界、源汇相同")
        void validation() {
            FlowNetwork network = tinyFN();
            assertThrows(IllegalArgumentException.class, () -> new Dinic(null, 0, 5));
            assertThrows(IllegalArgumentException.class, () -> new Dinic(network, 0, 6));
            assertThrows(IllegalArgumentException.class, () -> new Dinic(network, -1, 5));
            assertThrows(IllegalArgumentException.class, () -> new Dinic(network, 1, 1));
        }
    }

    @Nested
    @DisplayName("规模")
    class ScaleTest {

        @Test
        @DisplayName("1000 条并行三弧路径:3002 个顶点、3000 条弧,最大流 = 1000")
        void manyParallelPaths() {
            int paths = 1000;
            int sink = 2 + 3 * paths;
            FlowNetwork network = new FlowNetwork(sink + 1);
            for (int i = 0; i < paths; i++) {
                int a = 2 + 3 * i;
                int b = a + 1;
                int c = a + 2;
                network.addEdge(0, a, 1.0);
                network.addEdge(a, b, 1.0);
                network.addEdge(b, c, 1.0);
                network.addEdge(c, sink, 1.0);
            }
            assertEquals(4 * paths, network.E());

            Dinic maxFlow = new Dinic(network, 0, sink);
            assertEquals(paths, maxFlow.value(), EPS);
            assertEquals(paths, maxFlow.augmentationCount());
            assertTrue(maxFlow.phaseCount() <= 3, "所有路径等长,层次图只应该重建很少几轮");
            MaxFlowTestSupport.assertFeasibleFlow(network, 0, sink, paths);
        }

        @Test
        @DisplayName("稠密小图 + 整数容量:流量全为整数(强对偶的直观体现)")
        void integralFlow() {
            Random rnd = new Random(99L);
            for (int trial = 0; trial < 10; trial++) {
                FlowNetwork network = MaxFlowTestSupport.randomNetwork(
                        rnd, 4 + rnd.nextInt(4), 15, 9);
                int sink = network.V() - 1;
                Dinic maxFlow = new Dinic(network, 0, sink);
                for (FlowEdge edge : network.edges()) {
                    assertEquals(Math.rint(edge.flow()), edge.flow(), EPS, edge + " 的流量应为整数");
                }
                assertEquals(MaxFlowTestSupport.bruteForceMinCut(network, 0, sink),
                        maxFlow.value(), EPS);
            }
        }
    }
}
