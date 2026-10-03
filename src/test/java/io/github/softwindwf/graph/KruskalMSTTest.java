package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static io.github.softwindwf.graph.MstTestSupport.EPS;
import static io.github.softwindwf.graph.MstTestSupport.assertCycleProperty;
import static io.github.softwindwf.graph.MstTestSupport.assertValidForest;
import static io.github.softwindwf.graph.MstTestSupport.edgeStrings;
import static io.github.softwindwf.graph.MstTestSupport.randomGraphWithDistinctWeights;
import static io.github.softwindwf.graph.MstTestSupport.sortedEdgeStrings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KruskalMST} 最小生成树测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>教材样例 tinyEWG.txt:7 条边、总权值 <b>1.81</b>,边集逐条核对;</li>
 *   <li><b>提前结束</b>:并查集集合数降到 1 即停 —— tinyEWG 只看 13 条就够(E = 16),
 *       构造用例可精确断言"恰好检查了 V−1 条";</li>
 *   <li><b>最优性</b>由两条独立路径保证:环性质(充要条件)+ 与 {@link PrimMST} 对拍
 *       (两者算法结构完全不同:一个按边排序 + 并查集,一个按顶点集合 + 堆/数组);</li>
 *   <li>结构正确性:边数 = V − 分量数、连通关系与原图逐顶点对一致(用的是测试自带的并查集,
 *       不是被测的 {@link UF});</li>
 *   <li>两种实现(SORT 排序扫描 / HEAP 最小堆)结果一致;</li>
 *   <li>森林、孤立顶点、0 顶点、自环、平行边、负权、等权、稠密图、10 万顶点链。</li>
 * </ol>
 */
@DisplayName("KruskalMST 最小生成树测试")
class KruskalMSTTest {

    /** 教材样例:8 顶点 / 16 边,MST 权值 1.81 */
    private static EdgeWeightedGraph tinyEWG() {
        return GraphIO.readWeightedFile("tinyEWG.txt");
    }

    /** tinyEWG 的最小生成树(按权值升序;端点已归一为小-大,便于跨实现比较) */
    private static final String[] TINY_EWG_MST = {
        "0-7 0.16", "2-3 0.17", "1-7 0.19", "0-2 0.26", "5-7 0.28", "4-5 0.35", "2-6 0.4"
    };

    @Nested
    @DisplayName("教材样例 tinyEWG.txt")
    class TinyEWGTest {

        @Test
        @DisplayName("总权值 1.81,7 条边,边集与教材一致")
        void knownMst() {
            KruskalMST mst = new KruskalMST(tinyEWG());
            assertEquals(1.81, mst.weight(), EPS);
            assertEquals(7, mst.edges().size());
            assertEquals(Arrays.asList(TINY_EWG_MST), edgeStrings(mst.edges()));
            assertEquals(8, mst.V());
            assertEquals(1, mst.componentCount());
            assertTrue(mst.isConnected());
        }

        @Test
        @DisplayName("提前结束:16 条边里只检查了 13 条(第 13 条 6-2 0.40 加入后已连通)")
        void stopsEarly() {
            KruskalMST mst = new KruskalMST(tinyEWG());
            assertEquals(16, tinyEWG().E());
            assertEquals(13, mst.edgesExamined());
            assertEquals("2-6 0.4", edgeStrings(mst.edges()).get(6), "最后加进来的是最重的那条");
        }

        @Test
        @DisplayName("排序扫描版与最小堆版结果完全相同")
        void bothModesIdentical() {
            EdgeWeightedGraph g = tinyEWG();
            KruskalMST sort = new KruskalMST(g, KruskalMST.Mode.SORT);
            KruskalMST heap = new KruskalMST(g, KruskalMST.Mode.HEAP);
            assertEquals(sort.weight(), heap.weight(), EPS);
            assertEquals(edgeStrings(sort.edges()), edgeStrings(heap.edges()));
            assertEquals(sort.edgesExamined(), heap.edgesExamined());
            assertEquals(sort.componentCount(), heap.componentCount());
        }

        @Test
        @DisplayName("结构:7 = V − 1 条边,连通关系与原图逐顶点对一致")
        void structure() {
            EdgeWeightedGraph g = tinyEWG();
            KruskalMST mst = new KruskalMST(g);
            assertValidForest(g, mst.edges(), mst.componentCount(), mst.weight());
        }

        @Test
        @DisplayName("环性质:每条非树边的树路径最大边权 ≤ 该边权值")
        void cycleProperty() {
            EdgeWeightedGraph g = tinyEWG();
            assertCycleProperty(g, new KruskalMST(g).edges());
        }

        @Test
        @DisplayName("与 Prim 的结果一致(总权值与边集)")
        void agreesWithPrim() {
            EdgeWeightedGraph g = tinyEWG();
            KruskalMST kruskal = new KruskalMST(g);
            PrimMST prim = new PrimMST(g);
            assertEquals(prim.weight(), kruskal.weight(), EPS);
            assertEquals(sortedEdgeStrings(prim.edges()), sortedEdgeStrings(kruskal.edges()));
        }

        @Test
        @DisplayName("边集按权值升序返回,且不可修改")
        void edgesSortedAndUnmodifiable() {
            List<Edge> edges = new KruskalMST(tinyEWG()).edges();
            for (int i = 0; i + 1 < edges.size(); i++) {
                assertTrue(edges.get(i).weight() <= edges.get(i + 1).weight(), "边集应按权值升序");
            }
            assertThrows(UnsupportedOperationException.class, () -> edges.add(new Edge(0, 1, 1.0)));
        }
    }

    @Nested
    @DisplayName("与独立实现(Prim)对拍 + 结构/环性质")
    class CrossCheckTest {

        @Test
        @DisplayName("30 张随机图(权值互异):与 Prim 总权值、边集完全一致,环性质成立")
        void randomGraphsAgainstPrim() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 2 + rnd.nextInt(14);
                EdgeWeightedGraph g = randomGraphWithDistinctWeights(rnd, V, rnd.nextInt(30));

                KruskalMST kruskal = new KruskalMST(g);
                PrimMST prim = new PrimMST(g);

                assertEquals(prim.weight(), kruskal.weight(), EPS, "第 " + trial + " 张图:总权值不一致");
                assertEquals(sortedEdgeStrings(prim.edges()), sortedEdgeStrings(kruskal.edges()),
                        "第 " + trial + " 张图:权值互异时边集应逐条相同");
                assertEquals(prim.componentCount(), kruskal.componentCount(), "第 " + trial + " 张图:分量数不一致");

                assertValidForest(g, kruskal.edges(), kruskal.componentCount(), kruskal.weight());
                assertCycleProperty(g, kruskal.edges());
            }
        }

        @Test
        @DisplayName("200 顶点完全图(稠密):与 Prim 同权值,且两种实现同结果")
        void denseCompleteGraph() {
            Random rnd = new Random(7L);
            int V = 200;
            EdgeWeightedGraph g = new EdgeWeightedGraph(V);
            java.util.Set<Integer> usedKeys = new java.util.HashSet<Integer>();
            for (int v = 0; v < V; v++) {
                for (int w = v + 1; w < V; w++) {
                    g.addEdge(v, w, MstTestSupport.distinctWeight(rnd, usedKeys));
                }
            }
            assertEquals(V * (V - 1) / 2, g.E());

            KruskalMST sort = new KruskalMST(g, KruskalMST.Mode.SORT);
            KruskalMST heap = new KruskalMST(g, KruskalMST.Mode.HEAP);
            PrimMST prim = new PrimMST(g);

            assertEquals(V - 1, sort.edges().size());
            assertEquals(prim.weight(), sort.weight(), EPS);
            assertEquals(sort.weight(), heap.weight(), EPS);
            assertEquals(edgeStrings(sort.edges()), edgeStrings(heap.edges()));
            assertCycleProperty(g, sort.edges());
        }

        @Test
        @DisplayName("10 万顶点的带权链:99999 条边全要,总权值即为边数")
        void largeChain() {
            int n = 100_000;
            EdgeWeightedGraph g = new EdgeWeightedGraph(n);
            for (int v = 0; v + 1 < n; v++) {
                g.addEdge(v, v + 1, 1.0);
            }
            KruskalMST mst = new KruskalMST(g);
            assertEquals(n - 1, mst.edges().size());
            assertEquals(n - 1, mst.weight(), EPS);
            assertEquals(n - 1, mst.edgesExamined(), "链必须用完全部边才连通");
            assertTrue(mst.isConnected());
        }
    }

    @Nested
    @DisplayName("提前结束与边数统计")
    class EarlyStopTest {

        @Test
        @DisplayName("连通图:恰好检查 V−1 条最小边后停止")
        void stopsAtVMinusOneEdges() {
            // 6 个顶点的链(权值 1..5)+ 三条重边(权值 100):最小 5 条边一加完就连通
            EdgeWeightedGraph g = new EdgeWeightedGraph(6);
            for (int v = 0; v + 1 < 6; v++) {
                g.addEdge(v, v + 1, v + 1);
            }
            g.addEdge(0, 3, 100.0);
            g.addEdge(1, 4, 100.0);
            g.addEdge(2, 5, 100.0);

            KruskalMST mst = new KruskalMST(g);
            assertEquals(8, g.E());
            assertEquals(5, mst.edges().size());
            assertEquals(5, mst.edgesExamined(), "前 5 条边加完即连通,后面 3 条不必再看");
            assertEquals(15.0, mst.weight(), EPS);
            assertValidForest(g, mst.edges(), mst.componentCount(), mst.weight());
        }

        @Test
        @DisplayName("不连通图:无法提前结束,必须检查完全部边")
        void disconnectedGraphExaminesAll() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(6);
            g.addEdge(0, 1, 1.0);
            g.addEdge(1, 2, 2.0);
            g.addEdge(0, 2, 3.0);
            g.addEdge(3, 4, 1.0);
            g.addEdge(5, 5, 0.5);       // 自环

            KruskalMST mst = new KruskalMST(g);
            assertEquals(5, g.E());
            assertEquals(5, mst.edgesExamined(), "集合数降不到 1,边要全部看完");
            assertEquals(3, mst.componentCount(), "{0,1,2}、{3,4}、{5}(自环不改变分量)");
            assertEquals(4.0, mst.weight(), EPS);
            assertValidForest(g, mst.edges(), mst.componentCount(), mst.weight());
            assertEquals(new PrimMST(g).weight(), mst.weight(), EPS);
        }

        @Test
        @DisplayName("检查过的边数总在 [生成树边数, E] 之间")
        void examinedBounds() {
            Random rnd = new Random(11L);
            for (int trial = 0; trial < 20; trial++) {
                EdgeWeightedGraph g = randomGraphWithDistinctWeights(rnd, 2 + rnd.nextInt(10), rnd.nextInt(20));
                KruskalMST mst = new KruskalMST(g);
                assertTrue(mst.edgesExamined() >= mst.edges().size(),
                        "检查数不应少于采纳数");
                assertTrue(mst.edgesExamined() <= g.E(), "检查数不应超过总边数");
                assertTrue(mst.edgesExamined() >= 1 || g.E() == 0);
            }
        }
    }

    @Nested
    @DisplayName("森林与边界")
    class ForestAndEdgeCaseTest {

        @Test
        @DisplayName("两个分量:各取最小生成树")
        void twoComponents() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(6);
            g.addEdge(0, 1, 1.0);
            g.addEdge(1, 2, 3.0);
            g.addEdge(0, 2, 2.0);
            g.addEdge(3, 4, 5.0);
            g.addEdge(4, 5, 4.0);
            g.addEdge(3, 5, 9.0);

            KruskalMST mst = new KruskalMST(g);
            assertEquals(2, mst.componentCount());
            assertFalse(mst.isConnected());
            assertEquals(4, mst.edges().size());
            assertEquals(12.0, mst.weight(), EPS);
            assertEquals(new PrimMST(g).weight(), mst.weight(), EPS);
            assertValidForest(g, mst.edges(), mst.componentCount(), mst.weight());
        }

        @Test
        @DisplayName("孤立顶点、无边图、单顶点、0 顶点")
        void trivialGraphs() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(5);
            g.addEdge(0, 1, 2.0);
            KruskalMST mst = new KruskalMST(g);
            assertEquals(4, mst.componentCount());
            assertEquals(1, mst.edges().size());
            assertEquals(2.0, mst.weight(), EPS);

            KruskalMST noEdges = new KruskalMST(new EdgeWeightedGraph(3));
            assertEquals(0, noEdges.edges().size());
            assertEquals(0.0, noEdges.weight(), EPS);
            assertEquals(3, noEdges.componentCount());
            assertEquals(0, noEdges.edgesExamined());

            KruskalMST single = new KruskalMST(new EdgeWeightedGraph(1));
            assertEquals(1, single.componentCount());
            assertTrue(single.isConnected());
            assertEquals(0.0, single.weight(), EPS);

            KruskalMST empty = new KruskalMST(new EdgeWeightedGraph(0));
            assertEquals(0, empty.componentCount());
            assertFalse(empty.isConnected());
            assertEquals(0.0, empty.weight(), EPS);
        }

        @Test
        @DisplayName("自环不入选;再轻的自环也没用")
        void selfLoopsIgnored() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 0, -5.0);
            g.addEdge(0, 1, 1.0);
            g.addEdge(1, 2, 2.0);
            g.addEdge(2, 2, -9.0);
            KruskalMST mst = new KruskalMST(g);
            assertEquals(2, mst.edges().size());
            assertEquals(3.0, mst.weight(), EPS);
            for (Edge e : mst.edges()) {
                assertTrue(e.either() != e.other(e.either()));
            }
        }

        @Test
        @DisplayName("平行边只取最轻的一条")
        void parallelEdgesPickLightest() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 1, 0.9);
            g.addEdge(0, 1, 0.1);
            g.addEdge(1, 0, 0.5);
            g.addEdge(1, 2, 0.2);
            KruskalMST mst = new KruskalMST(g);
            assertEquals(0.3, mst.weight(), EPS);
            assertEquals(2, mst.edges().size());
        }

        @Test
        @DisplayName("负权值可用")
        void negativeWeights() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 1, -5.0);
            g.addEdge(1, 2, -3.0);
            g.addEdge(0, 2, 1.0);
            KruskalMST mst = new KruskalMST(g);
            assertEquals(-8.0, mst.weight(), EPS);
            assertValidForest(g, mst.edges(), mst.componentCount(), mst.weight());
            assertCycleProperty(g, mst.edges());
        }

        @Test
        @DisplayName("等权边:只保证总权值相同,不保证边集相同")
        void equalWeights() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(4);
            for (int v = 0; v < 4; v++) {
                for (int w = v + 1; w < 4; w++) {
                    g.addEdge(v, w, 1.0);
                }
            }
            KruskalMST mst = new KruskalMST(g);
            assertEquals(3.0, mst.weight(), EPS);
            assertEquals(3, mst.edges().size());
            assertEquals(new PrimMST(g).weight(), mst.weight(), EPS);
            assertValidForest(g, mst.edges(), mst.componentCount(), mst.weight());
            assertCycleProperty(g, mst.edges());
        }

        @Test
        @DisplayName("零权值边正常参与")
        void zeroWeights() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 1, 0.0);
            g.addEdge(1, 2, 0.0);
            g.addEdge(0, 2, 5.0);
            KruskalMST mst = new KruskalMST(g);
            assertEquals(0.0, mst.weight(), EPS);
            assertEquals(2, mst.edges().size());
        }
    }

    @Nested
    @DisplayName("参数与显示")
    class MiscTest {

        @Test
        @DisplayName("默认使用排序扫描版")
        void defaultMode() {
            assertEquals(KruskalMST.Mode.SORT, new KruskalMST(tinyEWG()).mode());
        }

        @Test
        @DisplayName("null 图 / null 方式抛 IllegalArgumentException")
        void constructorErrors() {
            assertThrows(IllegalArgumentException.class, () -> new KruskalMST(null));
            assertThrows(IllegalArgumentException.class, () -> new KruskalMST(tinyEWG(), null));
        }

        @Test
        @DisplayName("toString 含实现方式、边数、总权值、分量数与检查边数")
        void toStringContent() {
            String s = new KruskalMST(tinyEWG()).toString();
            assertTrue(s.contains("SORT"), s);
            assertTrue(s.contains("7 条边"), s);
            assertTrue(s.contains("1.81"), s);
            assertTrue(s.contains("检查 13/16 条边"), s);
        }
    }
}
