package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PrimMST} 最小生成树测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>教材样例 tinyEWG.txt:7 条边、总权值 <b>1.81</b>,边集逐条核对;</li>
 *   <li><b>结构正确性</b>(不依赖任何最短路/生成树算法):边数 = V − 分量数、
 *       所有顶点被覆盖、用并查集验证"MST 边形成的连通关系与原图完全一致"、无自环、权值求和自洽;</li>
 *   <li><b>最优性</b>用两条彼此独立的路径验证:
 *       (a) <b>环性质</b> —— 对每条非树边 f,树路径上最大边权 ≤ f 的权值,这是生成树最小性的
 *           充要条件;(b) <b>与独立实现的 Kruskal 对拍</b> —— 两种算法给出相同总权值;</li>
 *   <li>两种实现(LAZY 堆版 / DENSE 数组扫描版):权值必然相同,权值互异时边集也逐条相同;</li>
 *   <li>森林:不连通、孤立顶点、无边的图;</li>
 *   <li>边界:自环不进树、平行边只取最轻、负权值可用、等权值只保证权值相同;</li>
 *   <li>规模:10 万顶点的带权链(等于全树)、200 顶点完全图(稠密)。</li>
 * </ol>
 */
@DisplayName("PrimMST 最小生成树测试")
class PrimMSTTest {

    /** 权值比较容差(doubles 求和会有极小误差) */
    private static final double EPS = 1e-12;

    /** 教材样例:8 顶点 / 16 边,MST 权值 1.81 */
    private static EdgeWeightedGraph tinyEWG() {
        return GraphIO.readWeightedFile("tinyEWG.txt");
    }

    /** tinyEWG 的最小生成树(按权值升序,教材给出的结果) */
    private static final String[] TINY_EWG_MST = {
        "0-7 0.16", "2-3 0.17", "1-7 0.19", "0-2 0.26", "5-7 0.28", "4-5 0.35", "6-2 0.4"
    };

    private static List<String> edgeStrings(Iterable<Edge> edges) {
        List<String> list = new ArrayList<String>();
        for (Edge e : edges) {
            list.add(e.toString());
        }
        return list;
    }

    @Nested
    @DisplayName("教材样例 tinyEWG.txt")
    class TinyEWGTest {

        @Test
        @DisplayName("总权值 1.81,7 条边,边集与教材一致")
        void knownMst() {
            PrimMST mst = new PrimMST(tinyEWG());
            assertEquals(1.81, mst.weight(), EPS);
            assertEquals(7, mst.edges().size());
            assertFalse(mst.edges().isEmpty());
            assertTrue(mst.isConnected());
            assertEquals(1, mst.componentCount());
            assertEquals(8, mst.V());
            assertEquals(java.util.Arrays.asList(TINY_EWG_MST), edgeStrings(mst.edges()));
        }

        @Test
        @DisplayName("懒删除堆版与稠密扫描版结果完全相同")
        void bothModesIdentical() {
            EdgeWeightedGraph g = tinyEWG();
            PrimMST lazy = new PrimMST(g, PrimMST.Mode.LAZY);
            PrimMST dense = new PrimMST(g, PrimMST.Mode.DENSE);
            assertEquals(lazy.weight(), dense.weight(), EPS);
            assertEquals(edgeStrings(lazy.edges()), edgeStrings(dense.edges()));
            assertEquals(lazy.componentCount(), dense.componentCount());
        }

        @Test
        @DisplayName("结构:7 = V − 1 条边,覆盖全部 8 个顶点,连通关系与原图一致")
        void structure() {
            EdgeWeightedGraph g = tinyEWG();
            assertValidForest(g, new PrimMST(g));
        }

        @Test
        @DisplayName("环性质:每条非树边的树路径最大边权 ≤ 该边权值")
        void cycleProperty() {
            EdgeWeightedGraph g = tinyEWG();
            assertCycleProperty(g, new PrimMST(g));
        }

        @Test
        @DisplayName("边集按权值升序返回,且不可修改")
        void edgesSortedAndUnmodifiable() {
            PrimMST mst = new PrimMST(tinyEWG());
            List<Edge> edges = mst.edges();
            for (int i = 0; i + 1 < edges.size(); i++) {
                assertTrue(edges.get(i).weight() <= edges.get(i + 1).weight(), "边集应按权值升序");
            }
            assertThrows(UnsupportedOperationException.class, () -> edges.add(new Edge(0, 1, 1.0)));
        }
    }

    @Nested
    @DisplayName("随机图:与独立实现的 Kruskal 对拍 + 环性质")
    class RandomGraphTest {

        @Test
        @DisplayName("30 张随机图(权值互异):总权值等于 Kruskal,环性质成立,两实现边集一致")
        void randomGraphs() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 2 + rnd.nextInt(14);
                EdgeWeightedGraph g = MstTestSupport.randomGraphWithDistinctWeights(rnd, V, rnd.nextInt(30));

                PrimMST lazy = new PrimMST(g, PrimMST.Mode.LAZY);
                PrimMST dense = new PrimMST(g, PrimMST.Mode.DENSE);

                assertEquals(kruskalWeight(g), lazy.weight(), EPS, "第 " + trial + " 张图:Prim 与 Kruskal 总权值不一致");
                assertEquals(lazy.weight(), dense.weight(), EPS, "第 " + trial + " 张图:两种实现总权值不一致");
                assertEquals(edgeStrings(lazy.edges()), edgeStrings(dense.edges()),
                        "第 " + trial + " 张图:权值互异时边集应逐条相同");

                assertValidForest(g, lazy);
                assertCycleProperty(g, lazy);
            }
        }

        @Test
        @DisplayName("完全图 K200(稠密):两种实现同权值,且等于 Kruskal")
        void denseCompleteGraph() {
            Random rnd = new Random(7L);
            int V = 200;
            EdgeWeightedGraph g = new EdgeWeightedGraph(V);
            Set<Integer> usedKeys = new HashSet<Integer>();
            for (int v = 0; v < V; v++) {
                for (int w = v + 1; w < V; w++) {
                    g.addEdge(v, w, MstTestSupport.distinctWeight(rnd, usedKeys));
                }
            }
            assertEquals(V * (V - 1) / 2, g.E());
            PrimMST lazy = new PrimMST(g, PrimMST.Mode.LAZY);
            PrimMST dense = new PrimMST(g, PrimMST.Mode.DENSE);
            assertEquals(kruskalWeight(g), lazy.weight(), EPS);
            assertEquals(lazy.weight(), dense.weight(), EPS);
            assertEquals(V - 1, lazy.edges().size());
            assertCycleProperty(g, dense);
        }

        @Test
        @DisplayName("10 万顶点的带权链:生成树就是全部 99999 条边")
        void largeChain() {
            int n = 100_000;
            EdgeWeightedGraph g = new EdgeWeightedGraph(n);
            for (int v = 0; v + 1 < n; v++) {
                g.addEdge(v, v + 1, 1.0);
            }
            PrimMST mst = new PrimMST(g, PrimMST.Mode.LAZY);
            assertEquals(n - 1, mst.edges().size());
            assertEquals(n - 1, mst.weight(), EPS);
            assertTrue(mst.isConnected());
        }
    }

    @Nested
    @DisplayName("森林:不连通、孤立顶点、无边")
    class ForestTest {

        @Test
        @DisplayName("两个分量:各取最小生成树,总权值为两者之和,分量数为 2")
        void twoComponents() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(6);
            // 分量一:0-1(1.0)、1-2(3.0)、0-2(2.0) → MST 取 1.0 + 2.0
            g.addEdge(0, 1, 1.0);
            g.addEdge(1, 2, 3.0);
            g.addEdge(0, 2, 2.0);
            // 分量二:3-4(5.0)、4-5(4.0)、3-5(9.0) → MST 取 5.0 + 4.0
            g.addEdge(3, 4, 5.0);
            g.addEdge(4, 5, 4.0);
            g.addEdge(3, 5, 9.0);

            PrimMST lazy = new PrimMST(g, PrimMST.Mode.LAZY);
            PrimMST dense = new PrimMST(g, PrimMST.Mode.DENSE);
            assertEquals(2, lazy.componentCount());
            assertFalse(lazy.isConnected());
            assertEquals(4, lazy.edges().size());
            assertEquals(12.0, lazy.weight(), EPS);
            assertEquals(lazy.weight(), kruskalWeight(g), EPS);
            assertEquals(lazy.weight(), dense.weight(), EPS);
            assertValidForest(g, lazy);
            assertCycleProperty(g, lazy);
        }

        @Test
        @DisplayName("孤立顶点自成一个分量,贡献 0 条边、0 权值")
        void isolatedVertices() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(5);
            g.addEdge(0, 1, 2.0);
            PrimMST mst = new PrimMST(g);
            assertEquals(4, mst.componentCount(), "0-1 一个分量,2、3、4 各一个");
            assertEquals(1, mst.edges().size());
            assertEquals(2.0, mst.weight(), EPS);
            assertFalse(mst.isConnected());
        }

        @Test
        @DisplayName("没有任何边的图:全是孤立顶点")
        void noEdges() {
            PrimMST mst = new PrimMST(new EdgeWeightedGraph(3));
            assertEquals(0, mst.edges().size());
            assertEquals(0.0, mst.weight(), EPS);
            assertEquals(3, mst.componentCount());
            assertFalse(mst.isConnected());
        }

        @Test
        @DisplayName("单个顶点与 0 个顶点")
        void trivialGraphs() {
            PrimMST single = new PrimMST(new EdgeWeightedGraph(1));
            assertEquals(0, single.edges().size());
            assertEquals(0.0, single.weight(), EPS);
            assertEquals(1, single.componentCount());
            assertTrue(single.isConnected());

            PrimMST empty = new PrimMST(new EdgeWeightedGraph(0));
            assertEquals(0, empty.edges().size());
            assertEquals(0.0, empty.weight(), EPS);
            assertEquals(0, empty.componentCount());
            assertFalse(empty.isConnected());
        }
    }

    @Nested
    @DisplayName("边界情形")
    class EdgeCaseTest {

        @Test
        @DisplayName("自环不进入生成树")
        void selfLoopsIgnored() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 0, -5.0);          // 再轻的自环也没有用
            g.addEdge(0, 1, 1.0);
            g.addEdge(1, 2, 2.0);
            g.addEdge(2, 2, -9.0);
            PrimMST mst = new PrimMST(g);
            assertEquals(2, mst.edges().size());
            assertEquals(3.0, mst.weight(), EPS);
            for (Edge e : mst.edges()) {
                assertTrue(e.either() != e.other(e.either()), "生成树里不应有自环");
            }
        }

        @Test
        @DisplayName("平行边只取最轻的那条")
        void parallelEdgesPickLightest() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 1, 0.9);
            g.addEdge(0, 1, 0.1);
            g.addEdge(1, 0, 0.5);
            g.addEdge(1, 2, 0.2);
            PrimMST lazy = new PrimMST(g, PrimMST.Mode.LAZY);
            PrimMST dense = new PrimMST(g, PrimMST.Mode.DENSE);
            assertEquals(0.3, lazy.weight(), EPS);
            assertEquals(0.3, dense.weight(), EPS);
            assertEquals(2, lazy.edges().size());
        }

        @Test
        @DisplayName("负权值可用:三角形取两条负边")
        void negativeWeights() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 1, -5.0);
            g.addEdge(1, 2, -3.0);
            g.addEdge(0, 2, 1.0);
            PrimMST mst = new PrimMST(g);
            assertEquals(-8.0, mst.weight(), EPS);
            assertEquals(-8.0, kruskalWeight(g), EPS);
            assertValidForest(g, mst);
            assertCycleProperty(g, mst);
        }

        @Test
        @DisplayName("零权值与等权值:只保证总权值相同,不保证边集相同")
        void equalWeights() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(4);
            for (int v = 0; v < 4; v++) {
                for (int w = v + 1; w < 4; w++) {
                    g.addEdge(v, w, 1.0);      // K4 全等权
                }
            }
            PrimMST lazy = new PrimMST(g, PrimMST.Mode.LAZY);
            PrimMST dense = new PrimMST(g, PrimMST.Mode.DENSE);
            assertEquals(3.0, lazy.weight(), EPS);
            assertEquals(3.0, dense.weight(), EPS);
            assertEquals(3.0, kruskalWeight(g), EPS);
            assertEquals(3, lazy.edges().size());
            assertEquals(3, dense.edges().size());
            assertValidForest(g, lazy);
            assertValidForest(g, dense);
            assertCycleProperty(g, lazy);
            assertCycleProperty(g, dense);
        }

        @Test
        @DisplayName("零权值边正常参与")
        void zeroWeights() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 1, 0.0);
            g.addEdge(1, 2, 0.0);
            g.addEdge(0, 2, 5.0);
            PrimMST mst = new PrimMST(g);
            assertEquals(0.0, mst.weight(), EPS);
            assertEquals(2, mst.edges().size());
        }
    }

    @Nested
    @DisplayName("参数与显示")
    class MiscTest {

        @Test
        @DisplayName("默认使用懒删除堆版")
        void defaultMode() {
            PrimMST mst = new PrimMST(tinyEWG());
            assertEquals(PrimMST.Mode.LAZY, mst.mode());
        }

        @Test
        @DisplayName("null 图 / null 方式抛 IllegalArgumentException")
        void constructorErrors() {
            assertThrows(IllegalArgumentException.class, () -> new PrimMST(null));
            assertThrows(IllegalArgumentException.class, () -> new PrimMST(tinyEWG(), null));
        }

        @Test
        @DisplayName("toString 含实现方式、边数、总权值与分量数")
        void toStringContent() {
            String s = new PrimMST(tinyEWG()).toString();
            assertTrue(s.contains("LAZY"), s);
            assertTrue(s.contains("7 条边"), s);
            assertTrue(s.contains("1.81"), s);
            assertTrue(s.contains("连通分量 1"), s);
        }
    }

    // ------------------------------------------------------------------
    // 独立验证工具(刻意不复用 Prim 的任何中间结果)
    // ------------------------------------------------------------------

    /**
     * 结构验证:边数 = V − 分量数;每条树边确实是原图的边;
     * 树边形成的连通关系与原图逐顶点对一致;权值求和自洽;无自环。
     *
     * <p>复用 {@link MstTestSupport} 里的公共实现 —— 它自带一份<b>独立编写</b>的并查集,
     * 不使用被测代码的 {@link UF}。</p>
     */
    private static void assertValidForest(EdgeWeightedGraph g, PrimMST mst) {
        MstTestSupport.assertValidForest(g, mst.edges(), mst.componentCount(), mst.weight());
    }

    /**
     * 环性质(生成树最小性的充要条件):对每条非树边 f,
     * f 与"树中两端点之间的路径"构成一个环,f 必须是该环上最重的边之一 ——
     * 即树路径上的最大边权 ≤ f 的权值。若存在反例,把那条更重的树边换成 f 会得到更小的生成树。
     */
    private static void assertCycleProperty(EdgeWeightedGraph g, PrimMST mst) {
        MstTestSupport.assertCycleProperty(g, mst.edges());
    }

    /** 独立实现的 Kruskal:按权值升序加边,用并查集判环;返回最小生成森林总权值 */
    private static double kruskalWeight(EdgeWeightedGraph g) {
        List<Edge> all = new ArrayList<Edge>();
        for (Edge e : g.edges()) {
            all.add(e);
        }
        Collections.sort(all);
        MstTestSupport.UnionFind uf = new MstTestSupport.UnionFind(g.V());
        double sum = 0.0;
        for (Edge e : all) {
            int v = e.either();
            int w = e.other(v);
            if (uf.union(v, w)) {
                sum += e.weight();
            }
        }
        return sum;
    }
}
