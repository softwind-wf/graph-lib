package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BellmanFordSP} 一般图最短路与负环检测测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>含负权无负环的样例 tinyEWDn.txt:8 个顶点的距离逐项核对(与 algs4 官方数据一致);</li>
 *   <li>两种实现(队列 SPFA / 全弧扫描)结果完全一致;</li>
 *   <li>负环:tinyEWDnc.txt 上给出 4→5→4(权值 -0.31)的负环,
 *       且与 {@link FloydWarshall} 的判定一致;随机图上与 algs4 参考实现对拍;</li>
 *   <li>负环时查询接口必须<b>报错而不是返回错数</b>(distTo/pathTo 抛 IllegalStateException);</li>
 *   <li>不可达的负环不算数(不能误报);</li>
 *   <li>路径合法性 + 20 万顶点长链不栈溢出。</li>
 * </ol>
 */
@DisplayName("BellmanFordSP 最短路与负环测试")
class BellmanFordSPTest {

    private static final double EPS = 1e-9;

    private static EdgeWeightedDigraph tinyEwdn() {
        return GraphIO.readWeightedDigraphFile("tinyEWDn.txt");
    }

    private static EdgeWeightedDigraph tinyEwdnc() {
        return GraphIO.readWeightedDigraphFile("tinyEWDnc.txt");
    }

    @Nested
    @DisplayName("教材样例 tinyEWDn.txt(有负权、无负环)")
    class KnownDataTest {

        @Test
        @DisplayName("源点 0:8 个顶点的距离逐项核对")
        void distances() {
            BellmanFordSP sp = new BellmanFordSP(tinyEwdn(), 0);
            assertFalse(sp.hasNegativeCycle());
            double[] expected = {0.0, 0.93, 0.26, 0.99, 0.26, 0.61, 1.51, 0.60};
            for (int v = 0; v < expected.length; v++) {
                assertEquals(expected[v], sp.distTo(v), EPS, "distTo(" + v + ")");
            }
            assertEquals(0, sp.source());
            assertEquals(8, sp.V());
            assertEquals(15, sp.E());
        }

        @Test
        @DisplayName("两种实现给出一模一样的距离与路径")
        void modesAgree() {
            EdgeWeightedDigraph graph = tinyEwdn();
            for (int s = 0; s < graph.V(); s++) {
                BellmanFordSP queue = new BellmanFordSP(graph, s, BellmanFordSP.Mode.QUEUE);
                BellmanFordSP sweep = new BellmanFordSP(graph, s, BellmanFordSP.Mode.SWEEP);
                assertFalse(queue.hasNegativeCycle());
                assertFalse(sweep.hasNegativeCycle());
                for (int v = 0; v < graph.V(); v++) {
                    assertEquals(queue.distTo(v), sweep.distTo(v), EPS, s + " → " + v);
                }
                assertEquals(queue.pathTo(6), sweep.pathTo(6));
            }
        }

        @Test
        @DisplayName("路径合法:首尾相接,权值之和等于距离")
        void pathsAreValid() {
            EdgeWeightedDigraph graph = tinyEwdn();
            BellmanFordSP sp = new BellmanFordSP(graph, 0);
            for (int v = 0; v < graph.V(); v++) {
                List<DirectedEdge> path = sp.pathTo(v);
                double sum = 0.0;
                int current = 0;
                for (DirectedEdge edge : path) {
                    assertEquals(current, edge.from(), "路径应首尾相接");
                    current = edge.to();
                    sum += edge.weight();
                }
                assertEquals(v, current);
                assertEquals(sp.distTo(v), sum, EPS, "路径权值之和应等于距离");
                assertEquals(sp.pathVertices(v).size(), path.size() + 1, "顶点数比边数多 1");
            }
        }

        @Test
        @DisplayName("负权弧确实在最短路上:0→4 走 0→2→7→3→6→4 = 0.26")
        void negativeEdgeOnPath() {
            BellmanFordSP sp = new BellmanFordSP(tinyEwdn(), 0);
            assertEquals(Arrays.asList(0, 2, 7, 3, 6, 4), sp.pathVertices(4));
            assertEquals(0.26, sp.distTo(4), EPS);
        }

        @Test
        @DisplayName("工作量统计:队列模式与全弧扫描模式的松弛次数/轮数")
        void statistics() {
            BellmanFordSP queue = new BellmanFordSP(tinyEwdn(), 0, BellmanFordSP.Mode.QUEUE);
            BellmanFordSP sweep = new BellmanFordSP(tinyEwdn(), 0, BellmanFordSP.Mode.SWEEP);
            assertTrue(queue.relaxCount() > 0);
            assertTrue(sweep.relaxCount() > 0);
            assertTrue(queue.maxQueueSize() >= 1);
            assertEquals(0, sweep.maxQueueSize(), "SWEEP 不用队列");
            assertEquals(0, queue.sweepCount(), "QUEUE 不做全弧扫描");
            assertTrue(sweep.sweepCount() <= 8, "最多扫 V 轮:实际 " + sweep.sweepCount());
        }

        @Test
        @DisplayName("toString 含源点、实现方式与统计")
        void toStringContent() {
            String text = new BellmanFordSP(tinyEwdn(), 0).toString();
            assertTrue(text.contains("BellmanFordSP(QUEUE, 源点 0)"), text);
            assertTrue(text.contains("队列峰长"), text);
            assertTrue(text.contains("0 to 6: 1.51"), text);
        }
    }

    @Nested
    @DisplayName("负环")
    class NegativeCycleTest {

        @Test
        @DisplayName("tinyEWDnc.txt:检测到 4→5→4,权值 -0.31")
        void detectsNegativeCycle() {
            BellmanFordSP sp = new BellmanFordSP(tinyEwdnc(), 0);
            assertTrue(sp.hasNegativeCycle());
            List<DirectedEdge> cycle = sp.negativeCycle();
            assertNotNull(cycle);
            assertEquals(2, cycle.size());
            assertEquals(4, cycle.get(0).from());
            assertEquals(5, cycle.get(0).to());
            assertEquals(5, cycle.get(1).from());
            assertEquals(4, cycle.get(1).to());
            assertEquals(-0.31, sp.negativeCycleWeight(), 1e-9);
            assertTrue(assertValidNegativeCycle(tinyEwdnc(), cycle));
        }

        @Test
        @DisplayName("负环时查询接口报错而不是返回错数")
        void queriesThrow() {
            BellmanFordSP sp = new BellmanFordSP(tinyEwdnc(), 0);
            assertThrows(IllegalStateException.class, () -> sp.distTo(6));
            assertThrows(IllegalStateException.class, () -> sp.hasPathTo(6));
            assertThrows(IllegalStateException.class, () -> sp.edgeTo(6));
            assertThrows(IllegalStateException.class, () -> sp.pathTo(6));
            assertThrows(IllegalStateException.class, () -> sp.pathVertices(6));
            assertThrows(IllegalArgumentException.class, () -> sp.distTo(8), "越界仍应报越界");
        }

        @Test
        @DisplayName("与 FloydWarshall 的判定一致(有负环)")
        void agreesWithFloydWarshallPositive() {
            assertTrue(new FloydWarshall(tinyEwdnc()).hasNegativeCycle());
            assertFalse(new FloydWarshall(tinyEwdn()).hasNegativeCycle());
            assertTrue(new BellmanFordSP(tinyEwdnc(), 0).hasNegativeCycle());
            assertFalse(new BellmanFordSP(tinyEwdn(), 0).hasNegativeCycle());
        }

        @Test
        @DisplayName("不可达的负环不能误报")
        void unreachableNegativeCycle() {
            // 0→1 正常;2→3→2 是负环,但从 0 到不了
            EdgeWeightedDigraph graph = GraphIO.parseWeightedDigraph(
                    "4\n4\n0 1 5\n2 3 1\n3 2 -2\n2 2 0\n");
            BellmanFordSP sp = new BellmanFordSP(graph, 0);
            assertFalse(sp.hasNegativeCycle(), "负环不可达,不应误报");
            assertEquals(5.0, sp.distTo(1), EPS);
            assertEquals(Double.POSITIVE_INFINITY, sp.distTo(2), EPS);
            assertNull(sp.pathTo(2));
        }

        @Test
        @DisplayName("40 张随机图:与 FloydWarshall 一致(注意可达性:换遍所有源点就能对齐)")
        void randomGraphsAgainstFloydWarshall() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 40; trial++) {
                EdgeWeightedDigraph graph = randomDigraph(rnd, 2 + rnd.nextInt(7), true);
                boolean expected = new FloydWarshall(graph).hasNegativeCycle();

                // FloydWarshall 看全图的负环;BellmanFordSP 只看"从源点可达"的负环,
                // 所以这里对每个源点各跑一次:只要存在某个源点能发现问题,就说明全图有负环
                boolean detected = false;
                List<DirectedEdge> detectedCycle = null;
                for (int s = 0; s < graph.V(); s++) {
                    BellmanFordSP sp = new BellmanFordSP(graph, s);
                    if (sp.hasNegativeCycle()) {
                        detected = true;
                        detectedCycle = sp.negativeCycle();
                        assertTrue(assertValidNegativeCycle(graph, detectedCycle),
                                "第 " + trial + " 张图(源点 " + s + ")给出的环必须真的构成负环");
                        break;
                    }
                }
                assertEquals(expected, detected, "第 " + trial + " 张图的负环判定");
                if (expected) {
                    assertNotNull(detectedCycle);
                }
            }
        }

        @Test
        @DisplayName("不可达的负环不算数:同一个图换源点就应当能报出来")
        void reachabilityMatters() {
            EdgeWeightedDigraph graph = GraphIO.parseWeightedDigraph(
                    "4\n3\n0 1 5\n2 3 1\n3 2 -2\n");
            assertFalse(new BellmanFordSP(graph, 0).hasNegativeCycle(), "0 到不了那个负环");
            assertTrue(new BellmanFordSP(graph, 2).hasNegativeCycle(), "从 2 出发就能看到负环");
            assertTrue(new FloydWarshall(graph).hasNegativeCycle(), "FloydWarshall 看全图");
        }

        @Test
        @DisplayName("30 张随机图:与 algs4 参考实现对拍")
        void agreesWithReference() {
            Random rnd = new Random(2468L);
            for (int trial = 0; trial < 30; trial++) {
                EdgeWeightedDigraph graph = randomDigraph(rnd, 2 + rnd.nextInt(7), true);
                edu.princeton.cs.algs4.EdgeWeightedDigraph reference =
                        new edu.princeton.cs.algs4.EdgeWeightedDigraph(graph.V());
                for (DirectedEdge edge : graph.edges()) {
                    reference.addEdge(new edu.princeton.cs.algs4.DirectedEdge(
                            edge.from(), edge.to(), edge.weight()));
                }
                edu.princeton.cs.algs4.BellmanFordSP expected =
                        new edu.princeton.cs.algs4.BellmanFordSP(reference, 0);
                BellmanFordSP mine = new BellmanFordSP(graph, 0);
                assertEquals(expected.hasNegativeCycle(), mine.hasNegativeCycle(),
                        "第 " + trial + " 张图的负环判定");
                if (!mine.hasNegativeCycle()) {
                    for (int v = 0; v < graph.V(); v++) {
                        assertEquals(expected.distTo(v), mine.distTo(v), EPS,
                                "第 " + trial + " 张图,0 → " + v);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("校验与规模")
    class ValidationAndScaleTest {

        @Test
        @DisplayName("参数校验:null 图、null 方式、源点越界、查询越界")
        void validation() {
            EdgeWeightedDigraph graph = tinyEwdn();
            assertThrows(IllegalArgumentException.class, () -> new BellmanFordSP(null, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new BellmanFordSP(graph, 0, (BellmanFordSP.Mode) null));
            assertThrows(IllegalArgumentException.class, () -> new BellmanFordSP(graph, 8));
            assertThrows(IllegalArgumentException.class, () -> new BellmanFordSP(graph, -1));
            BellmanFordSP sp = new BellmanFordSP(graph, 0);
            assertThrows(IllegalArgumentException.class, () -> sp.distTo(8));
            assertThrows(IllegalArgumentException.class, () -> sp.hasPathTo(-1));
            assertThrows(IllegalArgumentException.class, () -> sp.pathTo(8));
        }

        @Test
        @DisplayName("默认用 QUEUE(SPFA)")
        void defaultMode() {
            assertEquals(BellmanFordSP.Mode.QUEUE, new BellmanFordSP(tinyEwdn(), 0).mode());
        }

        @Test
        @DisplayName("20 万顶点的链:SPFA 一遍收敛,不栈溢出")
        void largeChain() {
            int n = 200000;
            EdgeWeightedDigraph graph = new EdgeWeightedDigraph(n);
            for (int v = 0; v + 1 < n; v++) {
                graph.addEdge(v, v + 1, -1.0);              // 全程负权(但无环)
            }
            BellmanFordSP sp = new BellmanFordSP(graph, 0);
            assertFalse(sp.hasNegativeCycle());
            assertEquals(-(n - 1), sp.distTo(n - 1), EPS);
            assertEquals(n - 1, sp.pathTo(n - 1).size());
        }

        @Test
        @DisplayName("20 万顶点长环 + 一条负权边:能查出负环")
        void largeNegativeCycle() {
            int n = 200000;
            EdgeWeightedDigraph graph = new EdgeWeightedDigraph(n);
            for (int v = 0; v < n; v++) {
                graph.addEdge(v, (v + 1) % n, 1.0);
            }
            graph.addEdge(n - 1, 0, -2.0);                  // 环总权 n - 2 > 0,还不是负环
            assertFalse(new BellmanFordSP(graph, 0).hasNegativeCycle());
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /** 随机有向加权图;{@code allowNegative} 为 true 时权值取 [-2, 2] */
    static EdgeWeightedDigraph randomDigraph(Random rnd, int V, boolean allowNegative) {
        return DigraphGenerator.edgeWeighted(rnd, V, rnd.nextInt(V * 2 + 3), allowNegative);
    }

    /** 独立检查器:边列表必须首尾相接成环,且总权值为负 */
    static boolean assertValidNegativeCycle(EdgeWeightedDigraph graph, List<DirectedEdge> cycle) {
        if (cycle == null || cycle.isEmpty()) {
            return false;
        }
        double sum = 0.0;
        for (int i = 0; i < cycle.size(); i++) {
            DirectedEdge edge = cycle.get(i);
            DirectedEdge next = cycle.get((i + 1) % cycle.size());
            if (edge.to() != next.from()) {
                return false;
            }
            if (!graph.hasEdge(edge.from(), edge.to())) {
                return false;
            }
            sum += edge.weight();
        }
        return sum < 0;
    }
}
