package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DijkstraSP} 单源最短路径测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>教材样例 tinyEWD.txt:8 个顶点的距离与路径逐条核对(0→1 为 1.05、0→6 为 1.51 …);</li>
 *   <li><b>最优性用两条独立路径证明</b>:
 *       (a) <b>最优性条件</b> —— {@code distTo[s]=0}、任何边都不能再松弛、
 *           且每个可达顶点的距离 = "入边来源的最短距离 + 边权" 的最小值;
 *           这组条件与任何最短路算法无关,满足它就等价于"就是最短距离"(解唯一);
 *       (b) <b>独立实现的 Bellman-Ford 对拍</b> —— 另一个算法、另一种思路(V−1 轮全边松弛);</li>
 *   <li>路径合法:相邻两条边首尾相接、路径上的边都真实存在于图中、边权之和 = distTo;</li>
 *   <li>不可达顶点:{@code distTo = +∞}、{@code hasPathTo = false}、{@code pathTo = null};</li>
 *   <li>两种实现(LAZY 堆 / DENSE 线性扫描)逐点一致;</li>
 *   <li>前提校验:负权边直接拒绝并提示 Bellman-Ford;</li>
 *   <li>边界:自环、平行边只走最轻、零权边、10 万顶点深链、稠密图。</li>
 * </ol>
 */
@DisplayName("DijkstraSP 单源最短路径测试")
class DijkstraSPTest {

    /** 距离比较容差 */
    private static final double EPS = 1e-9;

    private static EdgeWeightedDigraph tinyEWD() {
        return GraphIO.readWeightedDigraphFile("tinyEWD.txt");
    }

    @Nested
    @DisplayName("教材样例 tinyEWD.txt(源点 0)")
    class TinyEWDTest {

        @Test
        @DisplayName("8 个顶点的最短距离与教材一致")
        void knownDistances() {
            DijkstraSP sp = new DijkstraSP(tinyEWD(), 0);
            assertEquals(0.00, sp.distTo(0), EPS);
            assertEquals(1.05, sp.distTo(1), EPS);
            assertEquals(0.26, sp.distTo(2), EPS);
            assertEquals(0.99, sp.distTo(3), EPS);
            assertEquals(0.38, sp.distTo(4), EPS);
            assertEquals(0.73, sp.distTo(5), EPS);
            assertEquals(1.51, sp.distTo(6), EPS);
            assertEquals(0.60, sp.distTo(7), EPS);
            assertEquals(8, sp.count(), "全部 8 个顶点都可达");
        }

        @Test
        @DisplayName("最短路径的顶点序列与教材一致")
        void knownPaths() {
            DijkstraSP sp = new DijkstraSP(tinyEWD(), 0);
            assertEquals(Arrays.asList(0), sp.pathVertices(0));
            assertEquals(Arrays.asList(0, 4, 5, 1), sp.pathVertices(1));
            assertEquals(Arrays.asList(0, 2), sp.pathVertices(2));
            assertEquals(Arrays.asList(0, 2, 7, 3), sp.pathVertices(3));
            assertEquals(Arrays.asList(0, 4), sp.pathVertices(4));
            assertEquals(Arrays.asList(0, 4, 5), sp.pathVertices(5));
            assertEquals(Arrays.asList(0, 2, 7, 3, 6), sp.pathVertices(6));
            assertEquals(Arrays.asList(0, 2, 7), sp.pathVertices(7));
        }

        @Test
        @DisplayName("路径按边返回,顺序从源点出发,边权之和等于距离")
        void knownEdgePaths() {
            DijkstraSP sp = new DijkstraSP(tinyEWD(), 0);
            List<DirectedEdge> path = sp.pathTo(1);
            assertEquals(3, path.size());
            assertEquals("0->4 0.38", path.get(0).toString());
            assertEquals("4->5 0.35", path.get(1).toString());
            assertEquals("5->1 0.32", path.get(2).toString());
            double sum = 0.0;
            for (DirectedEdge e : path) {
                sum += e.weight();
            }
            assertEquals(sp.distTo(1), sum, EPS);
            assertTrue(sp.pathTo(0).isEmpty(), "源点到自己是空路径(不是 null)");
            assertEquals("0->2 0.26", sp.edgeTo(2).toString(), "edgeTo 是进入该点的最后一条边");
            assertNull(sp.edgeTo(0), "源点没有前驱边");
        }

        @Test
        @DisplayName("确定顺序是距离升序,最后一个确定的是最远的顶点")
        void settleOrderIsByDistance() {
            DijkstraSP sp = new DijkstraSP(tinyEWD(), 0);
            int[] order = sp.settleOrder();
            assertEquals(8, order.length);
            double previous = -1.0;
            for (int v : order) {
                assertTrue(sp.distTo(v) >= previous - EPS, "确定顺序应按距离非递减");
                previous = sp.distTo(v);
            }
            assertEquals(6, order[order.length - 1], "最远的 6 号最后确定");
            assertArrayEquals(new int[]{0, 2, 4, 7, 5, 3, 1, 6}, order);
        }

        @Test
        @DisplayName("最优性条件:任何边都不能再松弛,且距离等于入边来源的最小值")
        void optimalityConditions() {
            EdgeWeightedDigraph g = tinyEWD();
            assertOptimalityConditions(g, new DijkstraSP(g, 0));
        }

        @Test
        @DisplayName("与 Bellman-Ford 对拍(独立算法)")
        void agreesWithBellmanFord() {
            EdgeWeightedDigraph g = tinyEWD();
            assertMatchesBellmanFord(g, 0);
        }

        @Test
        @DisplayName("两种实现逐点一致")
        void bothModesIdentical() {
            EdgeWeightedDigraph g = tinyEWD();
            DijkstraSP lazy = new DijkstraSP(g, 0, DijkstraSP.Mode.LAZY);
            DijkstraSP dense = new DijkstraSP(g, 0, DijkstraSP.Mode.DENSE);
            for (int v = 0; v < g.V(); v++) {
                assertEquals(lazy.distTo(v), dense.distTo(v), EPS, "距离(" + v + ")");
                assertEquals(lazy.pathVertices(v), dense.pathVertices(v), "路径(" + v + ")");
                assertEquals(lazy.edgeTo(v) == null, dense.edgeTo(v) == null, "前驱边有无(" + v + ")");
            }
            assertArrayEquals(lazy.settleOrder(), dense.settleOrder());
        }

        @Test
        @DisplayName("换源点(从 6 出发)结果随之改变,仍满足最优性")
        void anotherSource() {
            EdgeWeightedDigraph g = tinyEWD();
            DijkstraSP sp = new DijkstraSP(g, 6);
            assertEquals(0.0, sp.distTo(6), EPS);
            assertEquals(0.40, sp.distTo(2), EPS);   // 6->2
            assertEquals(0.58, sp.distTo(0), EPS);   // 6->0
            assertEquals(0.93, sp.distTo(4), EPS);   // 6->4(比 6->0->4 = 0.96 更短)
            assertEquals(0.74, sp.distTo(7), EPS);   // 6->2->7
            assertEquals(1.02, sp.distTo(5), EPS);   // 6->2->7->5(比 6->4->5 = 1.28 更短)
            assertEquals(1.13, sp.distTo(3), EPS);   // 6->2->7->3
            assertEquals(1.34, sp.distTo(1), EPS);   // 6->2->7->5->1
            assertEquals(8, sp.count(), "从 6 出发其实能到达全部顶点");
            assertOptimalityConditions(g, sp);
            assertMatchesBellmanFord(g, 6);
        }
    }

    @Nested
    @DisplayName("随机图:最优性条件 + Bellman-Ford 对拍")
    class RandomGraphTest {

        @Test
        @DisplayName("30 张随机有向图(非负权):距离等于 Bellman-Ford,最优性条件成立,两实现一致")
        void randomGraphs() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 2 + rnd.nextInt(12);
                EdgeWeightedDigraph g = randomDigraph(rnd, V, rnd.nextInt(30));
                int source = rnd.nextInt(V);

                DijkstraSP lazy = new DijkstraSP(g, source, DijkstraSP.Mode.LAZY);
                DijkstraSP dense = new DijkstraSP(g, source, DijkstraSP.Mode.DENSE);

                double[] expected = SpTestSupport.bellmanFord(g, source);
                for (int v = 0; v < V; v++) {
                    assertEquals(expected[v], lazy.distTo(v), EPS,
                            "第 " + trial + " 张图:顶点 " + v + " 的距离与 Bellman-Ford 不符");
                    assertEquals(lazy.distTo(v), dense.distTo(v), EPS, "两种实现距离不一致");
                }
                assertOptimalityConditions(g, lazy);
                assertPathsAreValid(g, lazy);
            }
        }

        @Test
        @DisplayName("稠密图(300 顶点完全有向图):与 Bellman-Ford 一致")
        void denseGraph() {
            Random rnd = new Random(9L);
            int V = 300;
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(V);
            for (int v = 0; v < V; v++) {
                for (int w = 0; w < V; w++) {
                    if (v != w) {
                        g.addEdge(v, w, 0.001 + rnd.nextInt(1000));
                    }
                }
            }
            assertEquals(V * (V - 1), g.E());
            DijkstraSP lazy = new DijkstraSP(g, 0);
            DijkstraSP dense = new DijkstraSP(g, 0, DijkstraSP.Mode.DENSE);
            double[] expected = SpTestSupport.bellmanFord(g, 0);
            for (int v = 0; v < V; v++) {
                assertEquals(expected[v], lazy.distTo(v), EPS);
                assertEquals(expected[v], dense.distTo(v), EPS);
            }
        }

        @Test
        @DisplayName("10 万顶点的有向链:距离等于下标")
        void largeChain() {
            int n = 100_000;
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(n);
            for (int v = 0; v + 1 < n; v++) {
                g.addEdge(v, v + 1, 1.0);
            }
            DijkstraSP sp = new DijkstraSP(g, 0);
            assertEquals(n, sp.count());
            assertEquals(n - 1, sp.distTo(n - 1), EPS);
            assertEquals(n, sp.pathVertices(n - 1).size());
            assertEquals(n - 1, sp.pathTo(n - 1).size());
        }
    }

    @Nested
    @DisplayName("森林式边界:不可达与退化图")
    class EdgeCaseTest {

        @Test
        @DisplayName("不可达顶点:distTo = +∞,pathTo = null")
        void unreachableVertices() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(4);
            g.addEdge(0, 1, 1.0);
            g.addEdge(2, 3, 1.0);       // 与 0 无关
            DijkstraSP sp = new DijkstraSP(g, 0);
            assertEquals(2, sp.count());
            assertEquals(Double.POSITIVE_INFINITY, sp.distTo(2), 0.0);
            assertFalse(sp.hasPathTo(2));
            assertNull(sp.pathTo(2));
            assertNull(sp.pathVertices(2));
            assertNull(sp.edgeTo(2));
            assertOptimalityConditions(g, sp);
            assertMatchesBellmanFord(g, 0);
        }

        @Test
        @DisplayName("孤立源点:只有自己可达")
        void isolatedSource() {
            DijkstraSP sp = new DijkstraSP(new EdgeWeightedDigraph(3), 1);
            assertEquals(1, sp.count());
            assertEquals(0.0, sp.distTo(1), EPS);
            assertEquals(Arrays.asList(1), sp.pathVertices(1));
            assertArrayEquals(new int[]{1}, sp.settleOrder());
        }

        @Test
        @DisplayName("0 顶点图与单顶点图")
        void trivialGraphs() {
            DijkstraSP single = new DijkstraSP(new EdgeWeightedDigraph(1), 0);
            assertEquals(1, single.count());
            assertEquals(0.0, single.distTo(0), EPS);
            assertThrows(IllegalArgumentException.class,
                    () -> new DijkstraSP(new EdgeWeightedDigraph(0), 0));
        }

        @Test
        @DisplayName("自环不影响结果,平行边只走最轻的那条")
        void selfLoopsAndParallelEdges() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            g.addEdge(0, 0, 0.1);          // 自环
            g.addEdge(0, 1, 5.0);
            g.addEdge(0, 1, 1.0);          // 更轻的平行边
            g.addEdge(1, 2, 1.0);
            DijkstraSP sp = new DijkstraSP(g, 0);
            assertEquals(1.0, sp.distTo(1), EPS);
            assertEquals(2.0, sp.distTo(2), EPS);
            assertEquals("0->1 1.0", sp.edgeTo(1).toString(), "应选更轻的平行边");
            assertMatchesBellmanFord(g, 0);
        }

        @Test
        @DisplayName("零权边正常参与")
        void zeroWeights() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            g.addEdge(0, 1, 0.0);
            g.addEdge(1, 2, 0.0);
            g.addEdge(0, 2, 5.0);
            DijkstraSP sp = new DijkstraSP(g, 0);
            assertEquals(0.0, sp.distTo(2), EPS);
            assertEquals(Arrays.asList(0, 1, 2), sp.pathVertices(2));
        }

        @Test
        @DisplayName("边权相同且等距时,路径取舍规则固定(顶点编号小的先确定)")
        void deterministicTieBreaking() {
            // 0->1 与 0->2 等长;再从 1、2 各有一条到 3 的等长边
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(4);
            g.addEdge(0, 1, 1.0);
            g.addEdge(0, 2, 1.0);
            g.addEdge(1, 3, 1.0);
            g.addEdge(2, 3, 1.0);
            DijkstraSP sp = new DijkstraSP(g, 0);
            assertEquals(2.0, sp.distTo(3), EPS);
            assertEquals(Arrays.asList(0, 1, 3), sp.pathVertices(3), "等长时取编号小的前驱");
            assertOptimalityConditions(g, sp);
        }
    }

    @Nested
    @DisplayName("前提校验与显示")
    class ValidationTest {

        @Test
        @DisplayName("存在负权边时抛 IllegalArgumentException,并提示 Bellman-Ford")
        void negativeWeightRejected() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            g.addEdge(0, 1, 1.0);
            g.addEdge(1, 2, -0.5);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new DijkstraSP(g, 0));
            assertTrue(e.getMessage().contains("非负"), e.getMessage());
            assertTrue(e.getMessage().contains("Bellman-Ford"), e.getMessage());
            assertTrue(e.getMessage().contains("1->2"), "报错应定位到那条边:" + e.getMessage());
        }

        @Test
        @DisplayName("null 图 / null 方式 / 源点越界")
        void constructorErrors() {
            EdgeWeightedDigraph g = tinyEWD();
            assertThrows(IllegalArgumentException.class, () -> new DijkstraSP(null, 0));
            assertThrows(IllegalArgumentException.class, () -> new DijkstraSP(g, 0, null));
            assertThrows(IllegalArgumentException.class, () -> new DijkstraSP(g, -1));
            assertThrows(IllegalArgumentException.class, () -> new DijkstraSP(g, 8));
        }

        @Test
        @DisplayName("查询越界顶点")
        void queryErrors() {
            DijkstraSP sp = new DijkstraSP(tinyEWD(), 0);
            assertThrows(IllegalArgumentException.class, () -> sp.distTo(8));
            assertThrows(IllegalArgumentException.class, () -> sp.distTo(-1));
            assertThrows(IllegalArgumentException.class, () -> sp.hasPathTo(8));
            assertThrows(IllegalArgumentException.class, () -> sp.edgeTo(8));
            assertThrows(IllegalArgumentException.class, () -> sp.pathTo(8));
            assertThrows(IllegalArgumentException.class, () -> sp.pathVertices(-1));
        }

        @Test
        @DisplayName("默认使用 LAZY;toString 含实现方式、源点与可达数")
        void defaultModeAndToString() {
            DijkstraSP sp = new DijkstraSP(tinyEWD(), 0);
            assertEquals(DijkstraSP.Mode.LAZY, sp.mode());
            assertEquals(0, sp.source());
            assertEquals(8, sp.V());
            String s = sp.toString();
            assertTrue(s.contains("LAZY"), s);
            assertTrue(s.contains("8/8"), s);
        }
    }

    // ------------------------------------------------------------------
    // 独立验证工具(不复用 Dijkstra 的任何中间结果)
    // ------------------------------------------------------------------

    /**
     * 最短路的<b>最优性条件</b>(不用任何最短路算法即可判定):
     * <ol>
     *   <li>{@code dist[s] = 0};</li>
     *   <li>对每条边 v-&gt;w(且 v 可达):{@code dist[w] ≤ dist[v] + weight},即没有任何边还能被松弛 ——
     *       这保证 dist 不小于真实最短距离(对任意路径归纳);</li>
     *   <li>对每个可达的 {@code w ≠ s}:{@code dist[w] = min{dist[v] + weight(v,w)}},即恰好由某条入边达到 ——
     *       这保证 dist 不大于真实最短距离。</li>
     * </ol>
     * 三条合起来说明 dist 就是最短距离(非负权下解唯一)。实现放在
     * {@link SpTestSupport#assertOptimalityConditions},与 {@link FloydWarshall} 的测试共用。
     */
    private static void assertOptimalityConditions(EdgeWeightedDigraph g, DijkstraSP sp) {
        double[] dist = new double[g.V()];
        for (int v = 0; v < g.V(); v++) {
            dist[v] = sp.distTo(v);
        }
        SpTestSupport.assertOptimalityConditions(g, sp.source(), dist);
    }

    /** 与独立实现的 Bellman-Ford 对拍(不依赖 Dijkstra 的任何结论) */
    private static void assertMatchesBellmanFord(EdgeWeightedDigraph g, int source) {
        double[] expected = SpTestSupport.bellmanFord(g, source);
        DijkstraSP sp = new DijkstraSP(g, source);
        for (int v = 0; v < g.V(); v++) {
            if (Double.isInfinite(expected[v])) {
                assertFalse(sp.hasPathTo(v), "顶点 " + v + " 应不可达");
                assertEquals(Double.POSITIVE_INFINITY, sp.distTo(v), 0.0);
            }
            else {
                assertEquals(expected[v], sp.distTo(v), EPS, "顶点 " + v + " 的距离不符");
            }
        }
    }

    /** 路径合法:首尾相接、边都真实存在、边权之和等于距离 */
    private static void assertPathsAreValid(EdgeWeightedDigraph g, DijkstraSP sp) {
        int source = sp.source();
        for (int v = 0; v < g.V(); v++) {
            List<Integer> vertices = sp.pathVertices(v);
            if (!sp.hasPathTo(v)) {
                assertNull(vertices, "不可达顶点不应有路径");
                assertNull(sp.pathTo(v));
                continue;
            }
            assertEquals(source, vertices.get(0).intValue(), "路径应从源点开始");
            assertEquals(v, vertices.get(vertices.size() - 1).intValue(), "路径应止于目标");
            double sum = 0.0;
            for (DirectedEdge e : sp.pathTo(v)) {
                assertTrue(g.hasEdge(e.from(), e.to()),
                        "路径上 " + e + " 这条边不在图中");
                sum += e.weight();
            }
            assertEquals(sp.distTo(v), sum, EPS, "顶点 " + v + " 的路径边权之和应等于距离");
            assertEquals(vertices.size(), sp.pathTo(v).size() + 1, "边数应比顶点数少 1");
        }
    }

    /** 随机有向图:委托给公共工具(非负权,含自环与平行边) */
    private static EdgeWeightedDigraph randomDigraph(Random rnd, int V, int edgeCount) {
        return SpTestSupport.randomDigraph(rnd, V, edgeCount, false);
    }
}
