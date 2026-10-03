package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FordFulkerson} 增广路法最大流测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>教材样例 tinyFN.txt:最大流 4,最小割 = {0→1, 2→3, 2→4},容量 4;</li>
 *   <li><b>合法性</b>:结果必须是一个合法流(容量约束 + 每个中间顶点流量守恒 + 流值正确);</li>
 *   <li><b>最优性</b>:与暴力枚举的最小割、与 algs4 参考实现、与另外两个算法一致;</li>
 *   <li>整数容量下流量为整数;</li>
 *   <li>参数校验与护栏。</li>
 * </ol>
 */
@DisplayName("FordFulkerson 最大流测试")
class FordFulkersonTest {

    private static final double EPS = 1e-9;

    private static FlowNetwork tinyFN() {
        return GraphIO.readFlowNetworkFile("tinyFN.txt");
    }

    @Nested
    @DisplayName("教材样例 tinyFN.txt")
    class KnownDataTest {

        @Test
        @DisplayName("最大流 4,增广 4 次,割上的三条边全部饱和")
        void knownValue() {
            FlowNetwork network = tinyFN();
            FordFulkerson maxFlow = new FordFulkerson(network, 0, 5);
            assertEquals(4.0, maxFlow.value(), EPS);
            assertEquals(4, maxFlow.augmentationCount());
            assertEquals(0, maxFlow.source());
            assertEquals(5, maxFlow.sink());

            MinCut cut = maxFlow.minCut();
            assertEquals(4.0, cut.capacity(), EPS);
            assertEquals(4.0, cut.flowAcross(), EPS);
            assertEquals(3, cut.edges().size());
            for (FlowEdge edge : cut.edges()) {
                assertEquals(edge.capacity(), edge.flow(), EPS);
            }
        }

        @Test
        @DisplayName("结果是一个合法流:容量约束 + 中间顶点守恒 + 流值")
        void flowIsFeasible() {
            FlowNetwork network = tinyFN();
            FordFulkerson maxFlow = new FordFulkerson(network, 0, 5);
            MaxFlowTestSupport.assertFeasibleFlow(network, 0, 5, maxFlow.value());
        }

        @Test
        @DisplayName("整数容量 ⟹ 整数流量")
        void integralFlow() {
            FlowNetwork network = tinyFN();
            new FordFulkerson(network, 0, 5);
            for (FlowEdge edge : network.edges()) {
                assertEquals(Math.rint(edge.flow()), edge.flow(), EPS, edge + " 的流量应为整数");
            }
        }

        @Test
        @DisplayName("s 到不了 t 时最大流为 0,割就是 {s}")
        void unreachableSink() {
            FlowNetwork network = GraphIO.parseFlowNetwork("4\n2\n0 1 3.0\n2 3 5.0\n");
            FordFulkerson maxFlow = new FordFulkerson(network, 0, 3);
            assertEquals(0.0, maxFlow.value(), EPS);
            assertEquals(0, maxFlow.augmentationCount());
            assertEquals(java.util.Arrays.asList(0, 1), maxFlow.minCut().sourceSide());
        }

        @Test
        @DisplayName("toString 含最大流值、增广次数与最小割")
        void toStringContent() {
            String text = new FordFulkerson(tinyFN(), 0, 5).toString();
            assertTrue(text.contains("FordFulkerson: 最大流 4.00"), text);
            assertTrue(text.contains("增广 4 次"), text);
            assertTrue(text.contains("MinCut"), text);
        }
    }

    @Nested
    @DisplayName("与其它实现/参考实现对拍")
    class OracleTest {

        @Test
        @DisplayName("40 张随机网络:三种算法、暴力最小割、algs4 参考实现五方一致")
        void allAgree() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 40; trial++) {
                FlowNetwork template = MaxFlowTestSupport.randomNetwork(
                        rnd, 2 + rnd.nextInt(6), rnd.nextInt(12), 6);
                int sink = template.V() - 1;

                double reference = new edu.princeton.cs.algs4.FordFulkerson(
                        MaxFlowTestSupport.toReference(template), 0, sink).value();
                double bruteForce = MaxFlowTestSupport.bruteForceMinCut(template, 0, sink);

                FlowNetwork fordFulkerson = template.copy();
                FlowNetwork edmondsKarp = template.copy();
                FlowNetwork dinic = template.copy();
                double valueA = new FordFulkerson(fordFulkerson, 0, sink).value();
                double valueB = new EdmondsKarp(edmondsKarp, 0, sink).value();
                double valueC = new Dinic(dinic, 0, sink).value();

                assertEquals(bruteForce, valueA, EPS, "第 " + trial + " 张图:暴力最小割 vs 增广路");
                assertEquals(reference, valueA, EPS, "第 " + trial + " 张图:algs4 vs 增广路");
                assertEquals(valueA, valueB, EPS, "第 " + trial + " 张图:FordFulkerson vs EdmondsKarp");
                assertEquals(valueA, valueC, EPS, "第 " + trial + " 张图:FordFulkerson vs Dinic");

                MaxFlowTestSupport.assertFeasibleFlow(fordFulkerson, 0, sink, valueA);
            }
        }

        @Test
        @DisplayName("最小割与最大流成对出现:割容量 = 流值,且割边全部饱和")
        void minCutMatchesFlow() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 20; trial++) {
                FlowNetwork network = MaxFlowTestSupport.randomNetwork(
                        rnd, 3 + rnd.nextInt(5), rnd.nextInt(12), 8);
                int sink = network.V() - 1;
                FordFulkerson maxFlow = new FordFulkerson(network, 0, sink);
                MinCut cut = maxFlow.minCut();
                assertEquals(maxFlow.value(), cut.capacity(), EPS, "割容量 = 流值");
                for (FlowEdge edge : cut.edges()) {
                    assertEquals(edge.capacity(), edge.flow(), 1e-6, "割上的边必须饱和: " + edge);
                }
            }
        }
    }

    @Nested
    @DisplayName("校验")
    class ValidationTest {

        @Test
        @DisplayName("参数校验:null、越界、源汇相同")
        void validation() {
            FlowNetwork network = tinyFN();
            assertThrows(IllegalArgumentException.class, () -> new FordFulkerson(null, 0, 5));
            assertThrows(IllegalArgumentException.class, () -> new FordFulkerson(network, 0, 6));
            assertThrows(IllegalArgumentException.class, () -> new FordFulkerson(network, -1, 5));
            assertThrows(IllegalArgumentException.class, () -> new FordFulkerson(network, 2, 2));
        }

        @Test
        @DisplayName("空网络(无边):最大流 0")
        void emptyNetwork() {
            assertEquals(0.0, new FordFulkerson(new FlowNetwork(2), 0, 1).value(), EPS);
            assertFalse(new FlowNetwork(2).E() > 0);
        }
    }
}
