package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EdmondsKarp} 最大流测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>tinyFN.txt:最大流 4,与增广路法/参考实现一致;</li>
 *   <li><b>最短增广路</b>的可见后果:增广次数 ≤ 上界 Θ(V·E),且每条增广路的边数不超过 V−1;</li>
 *   <li>结果合法(容量约束 + 流量守恒)且与暴力最小割一致;</li>
 *   <li>随机网络上与 Ford–Fulkerson、Dinic、algs4 四方一致。</li>
 * </ol>
 */
@DisplayName("EdmondsKarp 最大流测试")
class EdmondsKarpTest {

    private static final double EPS = 1e-9;

    private static FlowNetwork tinyFN() {
        return GraphIO.readFlowNetworkFile("tinyFN.txt");
    }

    @Nested
    @DisplayName("教材样例 tinyFN.txt")
    class KnownDataTest {

        @Test
        @DisplayName("最大流 4,增广 3 次;后面的增广路会变长(用到了反向弧)")
        void knownValue() {
            FlowNetwork network = tinyFN();
            EdmondsKarp maxFlow = new EdmondsKarp(network, 0, 5);
            assertEquals(4.0, maxFlow.value(), EPS);
            assertEquals(3, maxFlow.augmentationCount());
            assertEquals(5, maxFlow.longestPathEdges(),
                    "最短增广路是相对残量图而言的:后面几轮会借反向弧绕路,所以比 3 条边长");
            assertEquals(4.0, maxFlow.minCut().capacity(), EPS);
            MaxFlowTestSupport.assertFeasibleFlow(network, 0, 5, maxFlow.value());
        }

        @Test
        @DisplayName("增广次数不超过 V·E 的上界,路径长度不超过 V−1")
        void boundedByTheory() {
            FlowNetwork network = tinyFN();
            EdmondsKarp maxFlow = new EdmondsKarp(network, 0, 5);
            assertTrue(maxFlow.augmentationCount() <= network.V() * network.E());
            assertTrue(maxFlow.longestPathEdges() <= network.V() - 1);
            assertTrue(maxFlow.minCut().inSourceSide(0));
            assertTrue(!maxFlow.inSourceSide(5));
        }

        @Test
        @DisplayName("toString 含流值、增广次数与最短路径长度")
        void toStringContent() {
            String text = new EdmondsKarp(tinyFN(), 0, 5).toString();
            assertTrue(text.contains("EdmondsKarp: 最大流 4.00"), text);
            assertTrue(text.contains("增广 3 次"), text);
        }
    }

    @Nested
    @DisplayName("与其它实现/参考实现对拍")
    class OracleTest {

        @Test
        @DisplayName("40 张随机网络:与增广路法、Dinic、暴力最小割、algs4 全部一致")
        void allAgree() {
            Random rnd = new Random(31415L);
            for (int trial = 0; trial < 40; trial++) {
                FlowNetwork template = MaxFlowTestSupport.randomNetwork(
                        rnd, 2 + rnd.nextInt(6), rnd.nextInt(12), 6);
                int sink = template.V() - 1;

                FlowNetwork network = template.copy();
                EdmondsKarp maxFlow = new EdmondsKarp(network, 0, sink);
                double value = maxFlow.value();

                assertEquals(MaxFlowTestSupport.bruteForceMinCut(template, 0, sink), value, EPS,
                        "第 " + trial + " 张图:暴力最小割");
                assertEquals(new FordFulkerson(template.copy(), 0, sink).value(), value, EPS,
                        "第 " + trial + " 张图:增广路法");
                assertEquals(new Dinic(template.copy(), 0, sink).value(), value, EPS,
                        "第 " + trial + " 张图:Dinic");
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
            assertThrows(IllegalArgumentException.class, () -> new EdmondsKarp(null, 0, 5));
            assertThrows(IllegalArgumentException.class, () -> new EdmondsKarp(network, 0, 6));
            assertThrows(IllegalArgumentException.class, () -> new EdmondsKarp(network, -1, 5));
            assertThrows(IllegalArgumentException.class, () -> new EdmondsKarp(network, 4, 4));
        }
    }
}
