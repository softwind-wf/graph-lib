package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FlowEdge}、{@link FlowNetwork} 与 {@link MinCut} 测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>{@link FlowEdge} 的双向剩余容量语义:正向是 {@code capacity − flow},反向是 {@code flow}
 *       —— 这是"可以反悔"的实现基础;</li>
 *   <li>{@link FlowNetwork} 里同一条边在两端各出现一次(含反向可退的边),
 *       而 {@code edges()} 每条只给一次;</li>
 *   <li>{@link MinCut}:S/T 划分合法(含 s、不含 t、覆盖全部顶点)、割容量 = 跨越边的容量和、
 *       横跨割的边全部饱和;若当前流还不是最大流(残量图上 s 仍能到 t),构造直接报错;</li>
 *   <li>参数校验:负容量、自环、越界顶点、流值超容量。</li>
 * </ol>
 */
@DisplayName("FlowEdge / FlowNetwork / MinCut 测试")
class FlowNetworkTest {

    private static final double EPS = 1e-9;

    private static FlowNetwork tinyFN() {
        return GraphIO.readFlowNetworkFile("tinyFN.txt");
    }

    @Nested
    @DisplayName("FlowEdge")
    class FlowEdgeTest {

        @Test
        @DisplayName("初始状态:容量即剩余,流量为 0")
        void fresh() {
            FlowEdge edge = new FlowEdge(0, 1, 2.0);
            assertEquals(0, edge.from());
            assertEquals(1, edge.to());
            assertEquals(2.0, edge.capacity(), EPS);
            assertEquals(0.0, edge.flow(), EPS);
            assertEquals(2.0, edge.residualCapacityTo(1), EPS, "正向:还能加容量那么多");
            assertEquals(0.0, edge.residualCapacityTo(0), EPS, "反向:还没推过,退不了");
            assertEquals(0, edge.other(1));
            assertEquals(1, edge.other(0));
        }

        @Test
        @DisplayName("推流后反向有了剩余容量(这就是'反悔'的来源)")
        void afterPush() {
            FlowEdge edge = new FlowEdge(0, 1, 2.0);
            edge.addResidualFlowTo(1, 1.5);
            assertEquals(1.5, edge.flow(), EPS);
            assertEquals(0.5, edge.residualCapacityTo(1), EPS);
            assertEquals(1.5, edge.residualCapacityTo(0), EPS);
            edge.addResidualFlowTo(0, 1.5);
            assertEquals(0.0, edge.flow(), EPS);
            assertEquals(2.0, edge.residualCapacityTo(1), EPS);
        }

        @Test
        @DisplayName("校验:负容量、非有限容量、流值超容量、非法端点")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new FlowEdge(0, 1, -1.0));
            assertThrows(IllegalArgumentException.class, () -> new FlowEdge(0, 1, Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> new FlowEdge(0, 1, Double.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class, () -> new FlowEdge(-1, 1, 1.0));
            assertThrows(IllegalArgumentException.class, () -> new FlowEdge(0, 1, 1.0, 2.0));
            assertThrows(IllegalArgumentException.class, () -> new FlowEdge(0, 1, 1.0, -0.5));
            FlowEdge edge = new FlowEdge(0, 1, 1.0);
            assertThrows(IllegalArgumentException.class, () -> edge.other(9));
            assertThrows(IllegalArgumentException.class, () -> edge.residualCapacityTo(9));
            assertThrows(IllegalArgumentException.class, () -> edge.addResidualFlowTo(9, 0.5));
            assertThrows(IllegalArgumentException.class, () -> edge.addResidualFlowTo(1, -0.5));
            assertThrows(IllegalArgumentException.class, () -> edge.addResidualFlowTo(1, 2.0));
        }

        @Test
        @DisplayName("复制与相等性(容量、流量都参与)")
        void copyAndEquals() {
            FlowEdge edge = new FlowEdge(0, 1, 2.0);
            edge.addResidualFlowTo(1, 0.5);
            FlowEdge copy = new FlowEdge(edge);
            assertEquals(edge, copy);
            assertEquals(edge.hashCode(), copy.hashCode());
            assertEquals("0->1 0.50/2.00", copy.toString());
            copy.addResidualFlowTo(1, 1.5);
            assertFalse(edge.equals(copy), "流量不同就不再相等");
            assertFalse(edge.equals(null));
            assertFalse(edge.equals("0->1"));
        }
    }

    @Nested
    @DisplayName("FlowNetwork")
    class FlowNetworkStructureTest {

        @Test
        @DisplayName("一条边在两端各出现一次,edges() 每条只给一次")
        void adjacencyAppearsTwice() {
            FlowNetwork network = new FlowNetwork(3);
            network.addEdge(0, 1, 5.0);
            network.addEdge(1, 2, 3.0);
            assertEquals(3, network.V());
            assertEquals(2, network.E());
            assertEquals(1, network.adj(0).size());
            assertEquals(2, network.adj(1).size(), "中间的顶点两侧的边都看得到");
            assertEquals(1, network.adj(2).size());
            assertEquals(2, network.edges().size(), "edges() 不重复");
            assertEquals(5.0, network.outCapacity(0), EPS);
            assertEquals(3.0, network.outCapacity(1), EPS);
            assertEquals(5.0, network.inCapacity(1), EPS);
            assertEquals(3.0, network.inCapacity(2), EPS);
        }

        @Test
        @DisplayName("平行边各自独立,容量相加;自环被拒绝")
        void parallelAndSelfLoop() {
            FlowNetwork network = new FlowNetwork(2);
            network.addEdge(0, 1, 1.5);
            network.addEdge(0, 1, 2.5);
            assertEquals(2, network.E());
            assertEquals(4.0, network.outCapacity(0), EPS);
            assertThrows(IllegalArgumentException.class, () -> network.addEdge(1, 1, 1.0));
            assertThrows(IllegalArgumentException.class, () -> network.addEdge(0, 2, 1.0));
            assertThrows(IllegalArgumentException.class, () -> network.addEdge((FlowEdge) null));
        }

        @Test
        @DisplayName("clearFlow 把流量清零;copy 是深拷贝")
        void clearAndCopy() {
            FlowNetwork network = tinyFN();
            new FordFulkerson(network, 0, network.V() - 1);
            assertTrue(network.totalFlow() > 0);
            FlowNetwork copy = network.copy();
            assertEquals(network.totalFlow(), copy.totalFlow(), EPS);

            network.clearFlow();
            assertEquals(0.0, network.totalFlow(), EPS);
            for (FlowEdge edge : network.edges()) {
                assertEquals(0.0, edge.flow(), EPS);
            }
            assertTrue(copy.totalFlow() > 0, "副本不受影响");
        }

        @Test
        @DisplayName("toString 含顶点数、边数,并逐顶点列出关联边")
        void toStringContent() {
            String text = tinyFN().toString();
            assertTrue(text.contains("6 vertices, 8 edges"), text);
            assertTrue(text.contains("0: 0->1 0.00/2.00"), text);
        }
    }

    @Nested
    @DisplayName("MinCut")
    class MinCutTest {

        @Test
        @DisplayName("tinyFN:最大流 4,最小割 S=[0,2]、T=[1,3,4,5],割容量 4")
        void tinyFNCut() {
            FlowNetwork network = tinyFN();
            FordFulkerson maxFlow = new FordFulkerson(network, 0, 5);
            assertEquals(4.0, maxFlow.value(), EPS);

            MinCut cut = maxFlow.minCut();
            assertEquals(0, cut.source());
            assertEquals(5, cut.sink());
            assertEquals(java.util.Arrays.asList(0, 2), cut.sourceSide());
            assertEquals(java.util.Arrays.asList(1, 3, 4, 5), cut.sinkSide());
            assertEquals(4.0, cut.capacity(), EPS, "割容量 = 最大流值");
            assertEquals(4.0, cut.flowAcross(), EPS, "横跨割的边全部饱和");
            assertEquals(3, cut.edges().size());
            for (FlowEdge edge : cut.edges()) {
                assertEquals(edge.capacity(), edge.flow(), EPS, "割上的边必须饱和");
            }
        }

        @Test
        @DisplayName("S/T 划分合法:含源点、不含汇点、覆盖全部顶点,且都是枚举出来的最小割")
        void cutIsValidAndMinimal() {
            FlowNetwork network = tinyFN();
            MinCut cut = new FordFulkerson(network, 0, 5).minCut();
            int inS = 0;
            int inT = 0;
            for (int v = 0; v < network.V(); v++) {
                if (cut.inSourceSide(v)) {
                    inS++;
                    assertTrue(cut.sourceSide().contains(v));
                }
                else {
                    inT++;
                    assertEquals("T", cut.sideOf(v));
                }
            }
            assertEquals(network.V(), inS + inT);
            assertTrue(cut.inSourceSide(0));
            assertFalse(cut.inSourceSide(5));
            // 与暴力枚举的最小割容量对照
            assertEquals(MaxFlowTestSupport.bruteForceMinCut(network, 0, 5), cut.capacity(), EPS);
        }

        @Test
        @DisplayName("还没跑最大流时(残量图上 s 仍能到 t)构造集合割会报错")
        void rejectsNonMaximalFlow() {
            FlowNetwork network = tinyFN();
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new MinCut(network, 0, 5));
            assertTrue(e.getMessage().contains("最大流"), e.getMessage());
        }

        @Test
        @DisplayName("参数校验:null、越界、源汇相同;inSourceSide 越界")
        void validation() {
            FlowNetwork network = tinyFN();
            assertThrows(IllegalArgumentException.class, () -> new MinCut(null, 0, 5));
            assertThrows(IllegalArgumentException.class, () -> new MinCut(network, 0, 6));
            assertThrows(IllegalArgumentException.class, () -> new MinCut(network, -1, 5));
            assertThrows(IllegalArgumentException.class, () -> new MinCut(network, 3, 3));
            MinCut cut = new FordFulkerson(network, 0, 5).minCut();
            assertThrows(IllegalArgumentException.class, () -> cut.inSourceSide(6));
            assertThrows(IllegalArgumentException.class, () -> cut.sideOf(-1));
        }

        @Test
        @DisplayName("toString 含 S、T、容量与组成边")
        void toStringContent() {
            String text = new FordFulkerson(tinyFN(), 0, 5).minCut().toString();
            assertTrue(text.contains("MinCut: S=[0, 2]"), text);
            assertTrue(text.contains("容量 4.00"), text);
            assertTrue(text.contains("0->1"), text);
        }
    }

    @Nested
    @DisplayName("与 algs4 参考实现对拍")
    class ReferenceTest {

        @Test
        @DisplayName("tinyFN:流值与 algs4 的 FordFulkerson 一致")
        void tinyFNValue() {
            FlowNetwork network = tinyFN();
            edu.princeton.cs.algs4.FordFulkerson reference = new edu.princeton.cs.algs4.FordFulkerson(
                    MaxFlowTestSupport.toReference(network), 0, 5);
            assertEquals(reference.value(), new FordFulkerson(network, 0, 5).value(), EPS);
        }

        @Test
        @DisplayName("20 张随机小网络:最大流值与 algs4 一致,且都等于暴力枚举的最小割")
        void randomNetworks() {
            java.util.Random rnd = new java.util.Random(20261004L);
            for (int trial = 0; trial < 20; trial++) {
                FlowNetwork network = MaxFlowTestSupport.randomNetwork(
                        rnd, 2 + rnd.nextInt(6), rnd.nextInt(10), 5);
                int sink = network.V() - 1;
                double expected = new edu.princeton.cs.algs4.FordFulkerson(
                        MaxFlowTestSupport.toReference(network), 0, sink).value();
                double bruteForce = MaxFlowTestSupport.bruteForceMinCut(network, 0, sink);
                assertEquals(expected, bruteForce, EPS, "第 " + trial + " 张图:参考实现 vs 暴力最小割");

                FlowNetwork mine = network.copy();
                assertEquals(expected, new FordFulkerson(mine, 0, sink).value(), EPS,
                        "第 " + trial + " 张图:本实现 vs 参考实现");
                MaxFlowTestSupport.assertFeasibleFlow(mine, 0, sink, expected);
                assertEquals(expected, new FordFulkerson(network.copy(), 0, sink).minCut().capacity(),
                        EPS, "第 " + trial + " 张图:最小割容量应等于最大流值");
            }
        }
    }
}
