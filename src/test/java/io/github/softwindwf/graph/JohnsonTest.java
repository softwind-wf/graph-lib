package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Johnson} 全源最短路测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>两模式(重赋权 + 每源 Dijkstra / 不重赋权 + 每源 Bellman–Ford)结果完全一致;</li>
 *   <li>与 {@link FloydWarshall} 的整张距离矩阵对拍(非负图与含负权无负环图都要测);</li>
 *   <li>重赋权本身的正确性:minReweightedWeight ≥ 0,
 *       且势函数满足 <b>可行势</b>条件 {@code h(v) ≤ h(u) + w(u,v)}(每条弧都成立)——</li>
 *   <li>路径是原图的边、权值和等于距离、与单源算法一致;</li>
 *   <li>含负环时:hasNegativeCycle 为真,dist/path 抛异常,potential 不可用;</li>
 *   <li>复杂度对比:稀疏图上 Johnson 明显快于 Floyd-Warshall(用松弛次数/耗时的粗测)。</li>
 * </ol>
 */
@DisplayName("Johnson 全源最短路测试")
class JohnsonTest {

    private static final double EPS = 1e-9;

    private static EdgeWeightedDigraph tinyEwdn() {
        return GraphIO.readWeightedDigraphFile("tinyEWDn.txt");
    }

    @Nested
    @DisplayName("教材样例 tinyEWDn.txt")
    class KnownDataTest {

        @Test
        @DisplayName("全源矩阵与逐源 Bellman-Ford 的结果逐格一致")
        void matchesPerSourceBellmanFord() {
            EdgeWeightedDigraph graph = tinyEwdn();
            Johnson johnson = new Johnson(graph);
            assertFalse(johnson.hasNegativeCycle());
            for (int s = 0; s < graph.V(); s++) {
                BellmanFordSP sp = new BellmanFordSP(graph, s);
                for (int v = 0; v < graph.V(); v++) {
                    assertEquals(sp.distTo(v), johnson.dist(s, v), EPS, s + " → " + v);
                }
            }
        }

        @Test
        @DisplayName("从 0 出发的一行与教材样例一致")
        void firstRow() {
            Johnson johnson = new Johnson(tinyEwdn());
            double[] expected = {0.0, 0.93, 0.26, 0.99, 0.26, 0.61, 1.51, 0.60};
            for (int v = 0; v < expected.length; v++) {
                assertEquals(expected[v], johnson.dist(0, v), EPS, "0 → " + v);
            }
        }

        @Test
        @DisplayName("势函数满足可行势条件 h(v) ≤ h(u) + w(u,v),重赋权后最小弧权 ≥ 0")
        void potentialIsFeasible() {
            EdgeWeightedDigraph graph = tinyEwdn();
            Johnson johnson = new Johnson(graph);
            for (DirectedEdge edge : graph.edges()) {
                double hFrom = johnson.potential(edge.from());
                double hTo = johnson.potential(edge.to());
                assertTrue(hTo <= hFrom + edge.weight() + EPS,
                        edge + " 违反可行势条件:h(" + edge.to() + ")=" + hTo
                                + " > h(" + edge.from() + ")+w=" + (hFrom + edge.weight()));
            }
            assertTrue(johnson.minReweightedWeight() >= -EPS,
                    "重赋权后最小弧权应 ≥ 0,实际 " + johnson.minReweightedWeight());
        }

        @Test
        @DisplayName("路径是原图的边,权值和等于距离")
        void pathsAreOriginalEdges() {
            EdgeWeightedDigraph graph = tinyEwdn();
            Johnson johnson = new Johnson(graph);
            List<DirectedEdge> path = johnson.path(0, 4);
            assertNotNull(path);
            double sum = 0.0;
            int current = 0;
            for (DirectedEdge edge : path) {
                assertEquals(current, edge.from(), "路径应首尾相接");
                assertTrue(graph.hasEdge(edge.from(), edge.to()), "路径的边必须存在于原图");
                current = edge.to();
                sum += edge.weight();
            }
            assertEquals(4, current);
            assertEquals(johnson.dist(0, 4), sum, EPS, "原图边权之和应等于距离");
            DirectedEdge last = path.get(path.size() - 1);
            assertEquals(-1.25, last.weight(), EPS, "0→4 的最短路最后一段是负权弧 6→4");
            assertEquals(6, last.from());
            assertTrue(johnson.path(3, 3).isEmpty(), "自己到自己:空路径");
        }

        @Test
        @DisplayName("toString 含模式、顶点数与距离表")
        void toStringContent() {
            String text = new Johnson(tinyEwdn()).toString();
            assertTrue(text.contains("Johnson(REWEIGHT)"), text);
            assertTrue(text.contains("重赋权后最小弧权"), text);
            assertTrue(text.contains("从 0 出发"), text);
        }
    }

    @Nested
    @DisplayName("与 FloydWarshall 对拍")
    class FloydWarshallComparisonTest {

        @Test
        @DisplayName("20 张随机非负图:整张距离矩阵逐格一致")
        void nonNegativeGraphs() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 20; trial++) {
                EdgeWeightedDigraph graph = BellmanFordSPTest.randomDigraph(rnd, 2 + rnd.nextInt(6), false);
                Johnson johnson = new Johnson(graph);
                FloydWarshall floyd = new FloydWarshall(graph);
                assertFalse(floyd.hasNegativeCycle());
                for (int u = 0; u < graph.V(); u++) {
                    for (int v = 0; v < graph.V(); v++) {
                        assertEquals(floyd.dist(u, v), johnson.dist(u, v), EPS,
                                "第 " + trial + " 张图," + u + " → " + v);
                    }
                }
            }
        }

        @Test
        @DisplayName("20 张随机含负权图(无负环):整张距离矩阵逐格一致")
        void graphsWithNegativeEdges() {
            Random rnd = new Random(31415L);
            int checked = 0;
            for (int trial = 0; trial < 200 && checked < 20; trial++) {
                EdgeWeightedDigraph graph = BellmanFordSPTest.randomDigraph(rnd, 2 + rnd.nextInt(6), true);
                FloydWarshall floyd = new FloydWarshall(graph);
                if (floyd.hasNegativeCycle()) {
                    continue;                              // 有负环的图在另一个测试里验证
                }
                checked++;
                Johnson johnson = new Johnson(graph);
                assertFalse(johnson.hasNegativeCycle());
                for (int u = 0; u < graph.V(); u++) {
                    for (int v = 0; v < graph.V(); v++) {
                        assertEquals(floyd.dist(u, v), johnson.dist(u, v), EPS,
                                "第 " + trial + " 张图," + u + " → " + v);
                    }
                }
            }
            assertEquals(20, checked, "应当至少检查到 20 张无负环的图");
        }

        @Test
        @DisplayName("与 FloydWarshall 的负环判定一致")
        void negativeCycleAgreement() {
            Random rnd = new Random(1618L);
            int withCycle = 0;
            for (int trial = 0; trial < 40; trial++) {
                EdgeWeightedDigraph graph = BellmanFordSPTest.randomDigraph(rnd, 2 + rnd.nextInt(6), true);
                boolean expected = new FloydWarshall(graph).hasNegativeCycle();
                Johnson johnson = new Johnson(graph);
                assertEquals(expected, johnson.hasNegativeCycle(), "第 " + trial + " 张图的负环判定");
                if (expected) {
                    withCycle++;
                    assertNotNull(johnson.negativeCycle());
                    assertTrue(BellmanFordSPTest.assertValidNegativeCycle(graph, johnson.negativeCycle()));
                }
            }
            assertTrue(withCycle > 0, "随机图里应当出现过负环,否则这个测试没验证到东西");
        }
    }

    @Nested
    @DisplayName("两种模式、负环行为与校验")
    class ModesAndValidationTest {

        @Test
        @DisplayName("两种模式给出同样的距离矩阵")
        void modesAgree() {
            Random rnd = new Random(2718L);
            for (int trial = 0; trial < 10; trial++) {
                EdgeWeightedDigraph graph = BellmanFordSPTest.randomDigraph(rnd, 2 + rnd.nextInt(4), true);
                FloydWarshall floyd = new FloydWarshall(graph);
                Johnson reweight = new Johnson(graph, Johnson.Mode.REWEIGHT);
                Johnson perSource = new Johnson(graph, Johnson.Mode.BELLMAN_FORD_PER_SOURCE);
                assertEquals(floyd.hasNegativeCycle(), reweight.hasNegativeCycle());
                assertEquals(reweight.hasNegativeCycle(), perSource.hasNegativeCycle());
                if (reweight.hasNegativeCycle()) {
                    continue;
                }
                for (int u = 0; u < graph.V(); u++) {
                    for (int v = 0; v < graph.V(); v++) {
                        assertEquals(reweight.dist(u, v), perSource.dist(u, v), EPS,
                                "第 " + trial + " 张图," + u + " → " + v);
                    }
                }
            }
        }

        @Test
        @DisplayName("默认用 REWEIGHT(真 Johnson)")
        void defaultMode() {
            assertEquals(Johnson.Mode.REWEIGHT, new Johnson(tinyEwdn()).mode());
        }

        @Test
        @DisplayName("含负环:hasNegativeCycle 为真,dist/path 抛异常,势函数不可用")
        void negativeCycleBehaviour() {
            EdgeWeightedDigraph graph = GraphIO.readWeightedDigraphFile("tinyEWDnc.txt");
            Johnson johnson = new Johnson(graph);
            assertTrue(johnson.hasNegativeCycle());
            assertEquals(2, johnson.negativeCycle().size());
            assertThrows(IllegalStateException.class, () -> johnson.dist(0, 6));
            assertThrows(IllegalStateException.class, () -> johnson.hasPath(0, 6));
            assertThrows(IllegalStateException.class, () -> johnson.path(0, 6));
            assertThrows(IllegalStateException.class, () -> johnson.distances());
            assertThrows(IllegalStateException.class, () -> johnson.potential(0));
            assertTrue(Double.isNaN(johnson.minReweightedWeight()));
        }

        @Test
        @DisplayName("非重赋权模式没有势函数")
        void potentialUnavailableInOtherMode() {
            Johnson johnson = new Johnson(tinyEwdn(), Johnson.Mode.BELLMAN_FORD_PER_SOURCE);
            assertThrows(IllegalStateException.class, () -> johnson.potential(0));
        }

        @Test
        @DisplayName("参数校验:null 图、null 模式、顶点越界")
        void validation() {
            EdgeWeightedDigraph graph = tinyEwdn();
            assertThrows(IllegalArgumentException.class, () -> new Johnson(null));
            assertThrows(IllegalArgumentException.class, () -> new Johnson(graph, (Johnson.Mode) null));
            Johnson johnson = new Johnson(graph);
            assertThrows(IllegalArgumentException.class, () -> johnson.dist(0, 8));
            assertThrows(IllegalArgumentException.class, () -> johnson.dist(-1, 0));
            assertThrows(IllegalArgumentException.class, () -> johnson.hasPath(0, 8));
            assertThrows(IllegalArgumentException.class, () -> johnson.path(0, 8));
            assertThrows(IllegalArgumentException.class, () -> johnson.potential(8));
        }

        @Test
        @DisplayName("不可达:距离为 +∞,path 为 null")
        void unreachable() {
            EdgeWeightedDigraph graph = GraphIO.parseWeightedDigraph("3\n1\n0 1 -1\n");
            Johnson johnson = new Johnson(graph);
            assertEquals(-1.0, johnson.dist(0, 1), EPS);
            assertEquals(Double.POSITIVE_INFINITY, johnson.dist(1, 0), EPS);
            assertFalse(johnson.hasPath(1, 0));
            org.junit.jupiter.api.Assertions.assertNull(johnson.path(1, 0));
            assertEquals(Double.POSITIVE_INFINITY, johnson.distances()[1][0], EPS);
        }

        @Test
        @DisplayName("稀疏图交叉点观察:n 越大 Johnson 越划算(打印实测耗时,不硬性断言快慢)")
        void sparseGraphCrossover() {
            for (int n : new int[]{200, 600}) {
                EdgeWeightedDigraph graph = new EdgeWeightedDigraph(n);
                for (int v = 0; v + 1 < n; v++) {
                    graph.addEdge(v, v + 1, 1.0);
                    graph.addEdge(v, (v + 7) % n, 2.0);
                }
                long floydStart = System.nanoTime();
                FloydWarshall floyd = new FloydWarshall(graph);
                long floydNanos = System.nanoTime() - floydStart;

                long johnsonStart = System.nanoTime();
                Johnson johnson = new Johnson(graph);
                long johnsonNanos = System.nanoTime() - johnsonStart;

                assertFalse(floyd.hasNegativeCycle());
                assertFalse(johnson.hasNegativeCycle());
                for (int u = 0; u < n; u += 17) {
                    for (int v = 0; v < n; v += 13) {
                        assertEquals(floyd.dist(u, v), johnson.dist(u, v), EPS, u + " → " + v);
                    }
                }
                System.out.println("n=" + n + "(E=" + graph.E() + "): FloydWarshall Θ(n³) "
                        + floydNanos / 1000000.0 + " ms,Johnson Θ(nE log n) "
                        + johnsonNanos / 1000000.0 + " ms");
            }
        }
    }
}
