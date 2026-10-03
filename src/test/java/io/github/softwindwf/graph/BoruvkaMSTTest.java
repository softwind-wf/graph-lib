package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BoruvkaMST} 最小生成树测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>教材样例 tinyEWG.txt:7 条边、总权值 1.81,与 Prim/Kruskal 完全一致;</li>
 *   <li><b>三方对拍</b>:权值互异的随机图上,Borůvka 与 Prim、Kruskal 不仅权值相同,
 *       <b>边集合也相同</b>(此时最小生成树唯一);权值可重复时只比权值;</li>
 *   <li>结果是一个真正的生成森林:边数 = V − 连通块数、无环、连通块与原图一致;</li>
 *   <li>轮数不超过 Θ(log V) 的量级;不连通图给出森林;</li>
 *   <li>规模:1 万顶点的随机图。</li>
 * </ol>
 */
@DisplayName("BoruvkaMST 最小生成树测试")
class BoruvkaMSTTest {

    private static final double EPS = 1e-9;

    private static EdgeWeightedGraph tinyEWG() {
        return GraphIO.readWeightedFile("tinyEWG.txt");
    }

    private static Set<String> edgeKeys(List<Edge> edges) {
        Set<String> keys = new HashSet<String>();
        for (Edge edge : edges) {
            int a = edge.either();
            int b = edge.other(a);
            keys.add(Math.min(a, b) + "-" + Math.max(a, b));
        }
        return keys;
    }

    @Nested
    @DisplayName("教材样例")
    class KnownDataTest {

        @Test
        @DisplayName("tinyEWG.txt:7 条边、总权值 1.81,与 Prim/Kruskal 一致")
        void tinyEwgKnownValues() {
            EdgeWeightedGraph graph = tinyEWG();
            BoruvkaMST mst = new BoruvkaMST(graph);
            assertEquals(7, mst.edges().size());
            assertEquals(1.81, mst.weight(), 1e-9);
            assertEquals(1, mst.componentCount());
            assertTrue(mst.isConnected());
            assertEquals(new PrimMST(graph).weight(), mst.weight(), EPS);
            assertEquals(new KruskalMST(graph).weight(), mst.weight(), EPS);
            assertEquals(edgeKeys(new PrimMST(graph).edges()), edgeKeys(mst.edges()),
                    "权值互异时三种算法的边集合应当相同");
        }

        @Test
        @DisplayName("边按权值升序返回,且确实构成森林(无环、边数 = V − 连通块数)")
        void structure() {
            EdgeWeightedGraph graph = tinyEWG();
            BoruvkaMST mst = new BoruvkaMST(graph);
            List<Edge> edges = mst.edges();
            double previous = Double.NEGATIVE_INFINITY;
            for (Edge edge : edges) {
                assertTrue(edge.weight() >= previous - EPS, "边应按权值升序");
                previous = edge.weight();
            }
            UndirectedGraph forest = new UndirectedGraph(graph.V());
            for (Edge edge : edges) {
                int a = edge.either();
                forest.addEdge(a, edge.other(a));
            }
            assertFalse(new UndirectedCycle(forest).hasCycle(), "最小生成树不能有环");
            assertEquals(graph.V() - mst.componentCount(), forest.E());
        }

        @Test
        @DisplayName("不连通图:给出最小生成森林")
        void disconnected() {
            EdgeWeightedGraph graph = GraphGenerator.edgeWeightedPositive(new Random(20261004L), 9, 0);
            graph.addEdge(new Edge(0, 1, 1.0));
            graph.addEdge(new Edge(1, 2, 2.0));
            graph.addEdge(new Edge(3, 4, 1.5));
            BoruvkaMST forest = new BoruvkaMST(graph);
            assertEquals(6, forest.componentCount(), "{0,1,2}、{3,4} 与四个孤立顶点");
            assertEquals(3, forest.edges().size(), "两个非平凡分量各有 2 条与 1 条边");
            assertEquals(4.5, forest.weight(), EPS);
            assertFalse(forest.isConnected());
        }

        @Test
        @DisplayName("空图与无边图")
        void trivial() {
            BoruvkaMST empty = new BoruvkaMST(new EdgeWeightedGraph(0));
            assertEquals(0, empty.edges().size());
            assertEquals(0.0, empty.weight(), EPS);
            assertEquals(0, empty.componentCount());

            BoruvkaMST isolated = new BoruvkaMST(new EdgeWeightedGraph(4));
            assertEquals(0, isolated.edges().size());
            assertEquals(4, isolated.componentCount());
        }

        @Test
        @DisplayName("参数校验:null 图")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new BoruvkaMST(null));
        }
    }

    @Nested
    @DisplayName("与 Prim/Kruskal 对拍")
    class OracleTest {

        @Test
        @DisplayName("30 张权值互异的随机连通图:三者权值与边集合都相同")
        void agreesWithPrimAndKruskal() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 2 + rnd.nextInt(14);
                EdgeWeightedGraph graph = GraphGenerator.edgeWeightedDistinctWeights(
                        rnd, V, V - 1 + rnd.nextInt(V * 2));
                BoruvkaMST boruvka = new BoruvkaMST(graph);
                PrimMST prim = new PrimMST(graph);
                KruskalMST kruskal = new KruskalMST(graph);

                assertEquals(prim.weight(), boruvka.weight(), EPS, "第 " + trial + " 张图:Prim vs Borůvka");
                assertEquals(kruskal.weight(), boruvka.weight(), EPS,
                        "第 " + trial + " 张图:Kruskal vs Borůvka");
                assertEquals(edgeKeys(prim.edges()), edgeKeys(boruvka.edges()),
                        "第 " + trial + " 张图:权值互异时边集合也应相同");
                assertEquals(edgeKeys(kruskal.edges()), edgeKeys(boruvka.edges()));
                assertEquals(prim.componentCount(), boruvka.componentCount());
            }
        }

        @Test
        @DisplayName("20 张带平行边/自环的随机图:只比总权值(树可能不唯一)")
        void agreesOnMultigraphs() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(10);
                EdgeWeightedGraph graph = GraphGenerator.edgeWeighted(rnd, V, rnd.nextInt(20), false);
                assertEquals(new PrimMST(graph).weight(), new BoruvkaMST(graph).weight(), EPS,
                        "第 " + trial + " 张图");
                assertEquals(new KruskalMST(graph).weight(), new BoruvkaMST(graph).weight(), EPS);
            }
        }

        @Test
        @DisplayName("与 algs4 的 BoruvkaMST 对拍")
        void agreesWithReference() {
            Random rnd = new Random(31415L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(10);
                EdgeWeightedGraph graph = GraphGenerator.edgeWeightedPositive(rnd, V, V + rnd.nextInt(15));
                edu.princeton.cs.algs4.EdgeWeightedGraph reference =
                        new edu.princeton.cs.algs4.EdgeWeightedGraph(V);
                for (Edge edge : graph.edges()) {
                    reference.addEdge(new edu.princeton.cs.algs4.Edge(
                            edge.either(), edge.other(edge.either()), edge.weight()));
                }
                assertEquals(new edu.princeton.cs.algs4.BoruvkaMST(reference).weight(),
                        new BoruvkaMST(graph).weight(), EPS, "第 " + trial + " 张图");
            }
        }
    }

    @Nested
    @DisplayName("规模")
    class ScaleTest {

        @Test
        @DisplayName("1 万顶点的随机连通图:轮数在 log 量级,总权值与 Kruskal 一致")
        void large() {
            Random rnd = new Random(99L);
            EdgeWeightedGraph graph = GraphGenerator.edgeWeightedDistinctWeights(rnd, 10000, 30000);
            BoruvkaMST mst = new BoruvkaMST(graph);
            assertEquals(new KruskalMST(graph).weight(), mst.weight(), 1e-6);
            assertEquals(graph.V() - mst.componentCount(), mst.edges().size());
            assertTrue(mst.roundCount() <= 20, "轮数应与 log V 同量级,实际 " + mst.roundCount());
        }
    }
}
