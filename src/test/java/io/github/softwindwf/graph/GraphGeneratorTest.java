package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GraphGenerator} 无向图生成器测试。
 *
 * <p>生成器本身也要测:它被所有随机化测试当作"输入源",如果它生成的图不是它名字所说的那种,
 * 后面所有测试的结论都会跟着错。所以这里逐条验证每个方法的<b>语义承诺</b>:
 * {@code simple} 真的没有自环与平行边、{@code connected} 真的连通、{@code tree} 真的是树
 * (E = V−1 且无环)、{@code bipartite} 真的二分、{@code edgeWeightedDistinctWeights} 的权值真的互异。</p>
 */
@DisplayName("GraphGenerator 无向图生成器测试")
class GraphGeneratorTest {

    @Nested
    @DisplayName("形状承诺")
    class ShapeTest {

        @Test
        @DisplayName("simple:无自环、无平行边、边数尽量等于要求")
        void simple() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 3 + rnd.nextInt(12);
                int E = rnd.nextInt(20);
                UndirectedGraph graph = GraphGenerator.simple(rnd, V, E);
                assertEquals(V, graph.V());
                assertEquals(0, graph.selfLoopCount(), "简单图不能有自环");
                assertEquals(Math.min(E, V * (V - 1) / 2), graph.E(), "边数应贴近期望(受上限约束)");
                // 无平行边:邻接表里每个邻居只出现一次
                for (int v = 0; v < V; v++) {
                    Set<Integer> seen = new HashSet<Integer>();
                    for (int w : graph.adj(v)) {
                        assertTrue(seen.add(w), "顶点 " + v + " 的邻居 " + w + " 重复出现 = 平行边");
                    }
                }
            }
        }

        @Test
        @DisplayName("anyEdges:边数严格等于要求,允许自环与平行边")
        void anyEdges() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 1 + rnd.nextInt(6);
                int E = rnd.nextInt(20);
                UndirectedGraph graph = GraphGenerator.anyEdges(rnd, V, E);
                assertEquals(V, graph.V());
                assertEquals(E, graph.E(), "边数必须严格等于要求");
            }
        }

        @Test
        @DisplayName("connected:一定连通,且边数不少于 V−1")
        void connected() {
            Random rnd = new Random(555L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(10);
                UndirectedGraph graph = GraphGenerator.connected(rnd, V, rnd.nextInt(15));
                assertTrue(new ConnectedComponents(graph).isConnected(),
                        "第 " + trial + " 张图应当连通");
                assertTrue(graph.E() >= V - 1);
            }
        }

        @Test
        @DisplayName("tree:连通且无环,恰好 V−1 条边")
        void tree() {
            Random rnd = new Random(2718L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 1 + rnd.nextInt(12);
                UndirectedGraph tree = GraphGenerator.tree(rnd, V);
                assertEquals(Math.max(V - 1, 0), tree.E());
                assertFalse(new UndirectedCycle(tree).hasCycle(), "树不能有环");
                if (V > 0) {
                    assertTrue(new ConnectedComponents(tree).isConnected());
                }
            }
        }

        @Test
        @DisplayName("complete / path / cycle:形状确定")
        void deterministicShapes() {
            assertEquals(6, GraphGenerator.complete(4).E());
            assertEquals(3, GraphGenerator.complete(4).maxDegree());
            assertEquals(4, GraphGenerator.path(5).E());
            assertEquals(2, GraphGenerator.path(5).maxDegree(), "链的中间顶点度为 2");
            assertEquals(1, GraphGenerator.path(5).minDegree(), "链的两端度为 1");
            assertEquals(5, GraphGenerator.cycle(5).E());
            assertEquals(2, GraphGenerator.cycle(5).maxDegree());
            assertTrue(new UndirectedCycle(GraphGenerator.cycle(5)).hasCycle());
        }

        @Test
        @DisplayName("bipartite:只连接左右两部分,因此一定是二分图")
        void bipartite() {
            Random rnd = new Random(31415L);
            for (int trial = 0; trial < 20; trial++) {
                int left = 1 + rnd.nextInt(6);
                int right = 1 + rnd.nextInt(6);
                UndirectedGraph graph = GraphGenerator.bipartite(rnd, left, right, rnd.nextInt(15));
                assertEquals(left + right, graph.V());
                for (int[] edge : graph.edges()) {
                    boolean leftSide = edge[0] < left;
                    boolean rightSide = edge[1] < left;
                    assertTrue(leftSide != rightSide, "边只能连接左右两侧");
                }
                assertTrue(new Bipartite(graph).isBipartite());
            }
        }
    }

    @Nested
    @DisplayName("带权与流网络")
    class WeightedTest {

        @Test
        @DisplayName("edgeWeighted:权值范围符合 allowNegative;edgeWeightedDistinctWeights 权值互异")
        void weights() {
            Random rnd = new Random(4321L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(8);
                int E = rnd.nextInt(12);
                EdgeWeightedGraph nonNegative = GraphGenerator.edgeWeighted(rnd, V, E, false);
                for (Edge edge : nonNegative.edges()) {
                    assertTrue(edge.weight() > 0, "不允许负权时权值应为正");
                }
                EdgeWeightedGraph withNegative = GraphGenerator.edgeWeighted(rnd, V, E, true);
                assertEquals(E, withNegative.E());

                EdgeWeightedGraph distinct = GraphGenerator.edgeWeightedDistinctWeights(rnd, V, E);
                Set<String> weights = new HashSet<String>();
                for (Edge edge : distinct.edges()) {
                    assertTrue(weights.add(String.valueOf(edge.weight())), "权值应当互不相同");
                }
            }
        }

        @Test
        @DisplayName("edgeWeightedPositive / flowNetwork:正权、源点只出不进、汇点只进不出")
        void positiveAndFlow() {
            Random rnd = new Random(1618L);
            for (int trial = 0; trial < 20; trial++) {
                EdgeWeightedGraph positive = GraphGenerator.edgeWeightedPositive(rnd, 4 + rnd.nextInt(8),
                        rnd.nextInt(12));
                for (Edge edge : positive.edges()) {
                    assertTrue(edge.weight() > 0);
                }

                int V = 3 + rnd.nextInt(6);
                FlowNetwork network = GraphGenerator.flowNetwork(rnd, V, rnd.nextInt(10), 6);
                assertEquals(V, network.V());
                assertEquals(0, network.inDegree(0), "源点不应有入边");
                assertEquals(0, network.outDegree(V - 1), "汇点不应有出边");
                assertTrue(network.outCapacity(0) > 0);
                assertTrue(network.inCapacity(V - 1) > 0);
            }
        }
    }

    @Nested
    @DisplayName("参数校验")
    class ValidationTest {

        @Test
        @DisplayName("null 随机源与负顶点数都要报错")
        void validation() {
            Random rnd = new Random(1);
            assertThrows(IllegalArgumentException.class, () -> GraphGenerator.simple(null, 5, 5));
            assertThrows(IllegalArgumentException.class, () -> GraphGenerator.simple(rnd, -1, 5));
            assertThrows(IllegalArgumentException.class, () -> GraphGenerator.anyEdges(null, 5, 5));
            assertThrows(IllegalArgumentException.class, () -> GraphGenerator.connected(rnd, -2, 1));
            assertThrows(IllegalArgumentException.class, () -> GraphGenerator.tree(null, 3));
            assertThrows(IllegalArgumentException.class, () -> GraphGenerator.complete(-1));
            assertThrows(IllegalArgumentException.class, () -> GraphGenerator.path(-1));
            assertThrows(IllegalArgumentException.class, () -> GraphGenerator.cycle(-1));
            assertThrows(IllegalArgumentException.class,
                    () -> GraphGenerator.bipartite(null, 2, 2, 3));
            assertThrows(IllegalArgumentException.class,
                    () -> GraphGenerator.bipartite(rnd, -1, 2, 3));
            assertThrows(IllegalArgumentException.class, () -> GraphGenerator.flowNetwork(rnd, 1, 3, 5));
            assertThrows(IllegalArgumentException.class, () -> GraphGenerator.flowNetwork(rnd, 4, 3, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> GraphGenerator.edgeWeighted(rnd, -1, 3, true));
            assertThrows(IllegalArgumentException.class,
                    () -> GraphGenerator.edgeWeightedDistinctWeights(null, 3, 3));
        }

        @Test
        @DisplayName("固定种子可复现")
        void reproducible() {
            UndirectedGraph first = GraphGenerator.simple(new Random(42), 10, 15);
            UndirectedGraph second = GraphGenerator.simple(new Random(42), 10, 15);
            assertEquals(first.toString(), second.toString(), "同种子必须给出同一张图");
        }
    }
}
