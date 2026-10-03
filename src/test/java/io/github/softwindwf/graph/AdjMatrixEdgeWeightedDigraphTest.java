package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdjMatrixEdgeWeightedDigraph} 邻接矩阵表示测试。
 *
 * <p>验证重点:</p>
 * <ul>
 *   <li><b>与邻接表等价</b>:两种表示逐弧权值一致、出入度一致、{@code edges()} 集合一致,
 *       而且互相转换不丢信息;</li>
 *   <li>矩阵语义:没边用 +∞、重复加边保留最小权值、{@code weightOf} 与
 *       {@code weightOrInfinity} 的不同行为;</li>
 *   <li>在矩阵上跑 Dijkstra,与在邻接表上跑的结果一致;</li>
 *   <li>与 algs4 的 {@code AdjMatrixEdgeWeightedDigraph} 对拍;</li>
 *   <li>校验:自环、非有限权值、越界、顶点数上限。</li>
 * </ul>
 */
@DisplayName("AdjMatrixEdgeWeightedDigraph 邻接矩阵测试")
class AdjMatrixEdgeWeightedDigraphTest {

    private static final double EPS = 1e-9;

    private static EdgeWeightedDigraph tinyEWD() {
        return GraphIO.readWeightedDigraphFile("tinyEWD.txt");
    }

    @Nested
    @DisplayName("与邻接表等价")
    class EquivalenceTest {

        @Test
        @DisplayName("从邻接表复制:V、E、每条弧的权值都一致")
        void copyFromList() {
            EdgeWeightedDigraph list = tinyEWD();
            AdjMatrixEdgeWeightedDigraph matrix = new AdjMatrixEdgeWeightedDigraph(list);
            assertEquals(list.V(), matrix.V());
            assertEquals(list.E(), matrix.E());
            for (DirectedEdge edge : list.edges()) {
                assertTrue(matrix.hasEdge(edge.from(), edge.to()), edge + " 应当存在");
                assertEquals(edge.weight(), matrix.weightOf(edge.from(), edge.to()), EPS);
            }
            for (int v = 0; v < list.V(); v++) {
                assertEquals(list.outDegree(v), matrix.outDegree(v), v + " 的出度");
                assertEquals(list.inDegree(v), matrix.inDegree(v), v + " 的入度");
            }
        }

        @Test
        @DisplayName("互相转换不丢信息:矩阵 → 邻接表 → 矩阵")
        void roundTrip() {
            AdjMatrixEdgeWeightedDigraph matrix = new AdjMatrixEdgeWeightedDigraph(tinyEWD());
            AdjMatrixEdgeWeightedDigraph again = new AdjMatrixEdgeWeightedDigraph(
                    matrix.toEdgeWeightedDigraph());
            assertEquals(matrix.V(), again.V());
            assertEquals(matrix.E(), again.E());
            for (int v = 0; v < matrix.V(); v++) {
                for (int w = 0; w < matrix.V(); w++) {
                    assertEquals(matrix.weightOrInfinity(v, w), again.weightOrInfinity(v, w), EPS);
                }
            }
        }

        @Test
        @DisplayName("在两种表示上跑 Dijkstra,距离完全一致")
        void dijkstraAgrees() {
            EdgeWeightedDigraph list = tinyEWD();
            AdjMatrixEdgeWeightedDigraph matrix = new AdjMatrixEdgeWeightedDigraph(list);
            for (int source = 0; source < list.V(); source++) {
                DijkstraSP fromList = new DijkstraSP(list, source);
                DijkstraSP fromMatrix = new DijkstraSP(matrix.toEdgeWeightedDigraph(), source);
                for (int v = 0; v < list.V(); v++) {
                    assertEquals(fromList.distTo(v), fromMatrix.distTo(v), EPS, source + "→" + v);
                }
            }
        }

        @Test
        @DisplayName("邻接查询按终点升序(与邻接表的插入顺序不同)")
        void adjacencyOrder() {
            AdjMatrixEdgeWeightedDigraph matrix = new AdjMatrixEdgeWeightedDigraph(5);
            matrix.addEdge(0, 3, 1.0);
            matrix.addEdge(0, 1, 2.0);
            matrix.addEdge(0, 4, 3.0);
            List<DirectedEdge> adjacency = matrix.adj(0);
            assertEquals(3, adjacency.size());
            assertEquals(1, adjacency.get(0).to());
            assertEquals(3, adjacency.get(1).to());
            assertEquals(4, adjacency.get(2).to());
        }
    }

    @Nested
    @DisplayName("矩阵语义与校验")
    class SemanticsTest {

        @Test
        @DisplayName("没边是 +∞;重复加边保留最小权值;weightOf 与 weightOrInfinity 行为不同")
        void matrixSemantics() {
            AdjMatrixEdgeWeightedDigraph matrix = new AdjMatrixEdgeWeightedDigraph(3);
            assertEquals(Double.POSITIVE_INFINITY, matrix.weightOrInfinity(0, 1), EPS);
            assertFalse(matrix.hasEdge(0, 1));
            assertThrows(NoSuchElementException.class, () -> matrix.weightOf(0, 1));

            matrix.addEdge(0, 1, 5.0);
            matrix.addEdge(0, 1, 2.0);
            matrix.addEdge(0, 1, 7.0);
            assertEquals(2.0, matrix.weightOf(0, 1), EPS, "矩阵只能存一个数,保留最小值");
            assertEquals(1, matrix.E(), "重复加边不算新边");
            assertTrue(matrix.hasEdge(0, 1));
        }

        @Test
        @DisplayName("minWeight 与 toString 的形态")
        void minWeightAndToString() {
            AdjMatrixEdgeWeightedDigraph matrix = new AdjMatrixEdgeWeightedDigraph(3);
            matrix.addEdge(0, 1, 0.5);
            matrix.addEdge(1, 2, 0.25);
            assertEquals(0.25, matrix.minWeight(), EPS);
            String text = matrix.toString();
            assertTrue(text.contains("3 vertices, 2 edges"), text);
            assertThrows(IllegalArgumentException.class,
                    () -> new AdjMatrixEdgeWeightedDigraph(0).addEdge(0, 0, 1.0));
            assertEquals(Double.POSITIVE_INFINITY,
                    new AdjMatrixEdgeWeightedDigraph(2).minWeight(), EPS, "没有弧时是 +∞");
        }

        @Test
        @DisplayName("校验:自环、非有限权值、端点越界、null 边、顶点数上限")
        void validation() {
            AdjMatrixEdgeWeightedDigraph matrix =
                    new AdjMatrixEdgeWeightedDigraph(new EdgeWeightedDigraph(4));
            assertThrows(IllegalArgumentException.class, () -> matrix.addEdge(1, 1, 1.0));
            assertThrows(IllegalArgumentException.class, () -> matrix.addEdge(0, 1, Double.NaN));
            assertThrows(IllegalArgumentException.class,
                    () -> matrix.addEdge(0, 1, Double.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class, () -> matrix.addEdge(0, 4, 1.0));
            assertThrows(IllegalArgumentException.class, () -> matrix.addEdge(-1, 1, 1.0));
            assertThrows(IllegalArgumentException.class,
                    () -> matrix.addEdge((DirectedEdge) null));
            assertThrows(IllegalArgumentException.class, () -> matrix.outDegree(4));
            assertThrows(IllegalArgumentException.class, () -> matrix.inDegree(-1));
            assertThrows(IllegalArgumentException.class, () -> matrix.adj(4));
            assertThrows(IllegalArgumentException.class, () -> matrix.weightOf(0, 4));
            assertThrows(IllegalArgumentException.class,
                    () -> new AdjMatrixEdgeWeightedDigraph((EdgeWeightedDigraph) null));
            assertThrows(IllegalArgumentException.class,
                    () -> new AdjMatrixEdgeWeightedDigraph((1 << 13) + 1));
        }
    }

    @Nested
    @DisplayName("与参考实现对拍")
    class OracleTest {

        @Test
        @DisplayName("20 张随机图:与 algs4 的 AdjMatrixEdgeWeightedDigraph 弧权一致")
        void agreesWithReference() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(6);
                EdgeWeightedDigraph graph = DigraphGenerator.edgeWeightedPositive(rnd, V, rnd.nextInt(10));
                AdjMatrixEdgeWeightedDigraph mine = new AdjMatrixEdgeWeightedDigraph(graph);
                edu.princeton.cs.algs4.AdjMatrixEdgeWeightedDigraph reference =
                        new edu.princeton.cs.algs4.AdjMatrixEdgeWeightedDigraph(V);
                for (DirectedEdge edge : graph.edges()) {
                    reference.addEdge(new edu.princeton.cs.algs4.DirectedEdge(
                            edge.from(), edge.to(), edge.weight()));
                }
                assertEquals(reference.V(), mine.V());
                assertEquals(reference.E(), mine.E(), "第 " + trial + " 张图的弧数");
                // algs4 的矩阵版只暴露 adj(v),这里把它的弧收集成"from->to = 权值"再逐条比对
                java.util.Map<String, Double> referenceEdges = new java.util.HashMap<String, Double>();
                for (int v = 0; v < V; v++) {
                    for (edu.princeton.cs.algs4.DirectedEdge edge : reference.adj(v)) {
                        referenceEdges.put(edge.from() + "->" + edge.to(), edge.weight());
                    }
                }
                for (int v = 0; v < V; v++) {
                    for (int w = 0; w < V; w++) {
                        String key = v + "->" + w;
                        boolean referenceHasEdge = referenceEdges.containsKey(key);
                        assertEquals(referenceHasEdge, mine.hasEdge(v, w),
                                "第 " + trial + " 张图:" + key);
                        if (referenceHasEdge) {
                            assertEquals(referenceEdges.get(key), mine.weightOf(v, w), EPS);
                        }
                    }
                }
            }
        }
    }
}
