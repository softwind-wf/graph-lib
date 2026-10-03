package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static io.github.softwindwf.graph.SpTestSupport.EPS;
import static io.github.softwindwf.graph.SpTestSupport.assertMatrixEquals;
import static io.github.softwindwf.graph.SpTestSupport.assertOptimalityConditions;
import static io.github.softwindwf.graph.SpTestSupport.assertPathIsValid;
import static io.github.softwindwf.graph.SpTestSupport.bellmanFordAllPairs;
import static io.github.softwindwf.graph.SpTestSupport.randomDigraph;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FloydWarshall} 全源最短路径测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>教材样例 tinyEWD.txt:完整 8×8 距离矩阵与"对每个源点各跑一次 {@link DijkstraSP}"逐格一致,
 *       同时与测试内独立实现的 Bellman–Ford 全源矩阵一致;</li>
 *   <li><b>固定点条件</b>(与算法无关的刻画):{@code dist[i][i]=0}、任何边都不能再松弛、
 *       且每个可达点对都能由某条中间点取等号 —— 三者合起来确定"就是最短距离";</li>
 *   <li>路径合法:相邻顶点间有边、边权之和等于距离、无负环时路径是简单路;</li>
 *   <li>两种更新方式(就地滚动 / 每轮副本)矩阵与路径完全一致;</li>
 *   <li><b>负权边</b>能正确处理(Dijkstra 会直接拒绝,Floyd–Warshall 不会);</li>
 *   <li><b>负环检测</b>:对角为负、返回的环真实存在且总权为负(含自环负边的情形);</li>
 *   <li>不可达、平行边、自环、零权、稠密图与规模。</li>
 * </ol>
 */
@DisplayName("FloydWarshall 全源最短路径测试")
class FloydWarshallTest {

    private static EdgeWeightedDigraph tinyEWD() {
        return GraphIO.readWeightedDigraphFile("tinyEWD.txt");
    }

    /** 把 Floyd–Warshall 的距离矩阵取出来 */
    private static double[][] matrix(FloydWarshall fw) {
        return fw.distances();
    }

    /** 对每个源点跑一次 Dijkstra,组成全源矩阵 */
    private static double[][] dijkstraAllPairs(EdgeWeightedDigraph g) {
        double[][] all = new double[g.V()][g.V()];
        for (int s = 0; s < g.V(); s++) {
            DijkstraSP sp = new DijkstraSP(g, s);
            for (int v = 0; v < g.V(); v++) {
                all[s][v] = sp.distTo(v);
            }
        }
        return all;
    }

    @Nested
    @DisplayName("教材样例 tinyEWD.txt")
    class TinyEWDTest {

        @Test
        @DisplayName("全源矩阵与'每个源点各跑一次 Dijkstra'逐格一致")
        void matchesDijkstraFromEverySource() {
            EdgeWeightedDigraph g = tinyEWD();
            FloydWarshall fw = new FloydWarshall(g);
            assertMatrixEquals(dijkstraAllPairs(g), matrix(fw), "FW 与逐源 Dijkstra");
        }

        @Test
        @DisplayName("全源矩阵与独立实现的 Bellman–Ford 一致")
        void matchesBellmanFord() {
            EdgeWeightedDigraph g = tinyEWD();
            assertMatrixEquals(bellmanFordAllPairs(g), matrix(new FloydWarshall(g)), "FW 与 Bellman-Ford");
        }

        @Test
        @DisplayName("已知点对:0→6 = 1.51、6→1 = 1.34、2→6 = 1.25、对角线为 0")
        void knownPairs() {
            FloydWarshall fw = new FloydWarshall(tinyEWD());
            assertEquals(1.51, fw.dist(0, 6), EPS);
            assertEquals(1.34, fw.dist(6, 1), EPS);
            assertEquals(1.25, fw.dist(2, 6), EPS);
            assertEquals(0.26, fw.dist(0, 2), EPS);
            assertEquals(0.38, fw.dist(0, 4), EPS);
            for (int v = 0; v < 8; v++) {
                assertEquals(0.0, fw.dist(v, v), EPS, "无负环时对角线应为 0");
                assertTrue(fw.hasPath(v, v));
            }
            assertFalse(fw.hasNegativeCycle());
            assertNull(fw.negativeCycle());
        }

        @Test
        @DisplayName("第 0 行等于 Dijkstra 从 0 出发的距离,路径与 Dijkstra 一致")
        void rowZeroMatchesDijkstra() {
            EdgeWeightedDigraph g = tinyEWD();
            FloydWarshall fw = new FloydWarshall(g);
            DijkstraSP sp = new DijkstraSP(g, 0);
            for (int v = 0; v < g.V(); v++) {
                assertEquals(sp.distTo(v), fw.dist(0, v), EPS, "0→" + v);
                assertEquals(sp.pathVertices(v), fw.path(0, v), "0→" + v + " 的路径");
            }
            assertEquals(Arrays.asList(0, 2, 7, 3, 6), fw.path(0, 6));
        }

        @Test
        @DisplayName("固定点条件:没有边能再松弛,且每个可达点对都由最优中间点取等号")
        void optimalityConditions() {
            EdgeWeightedDigraph g = tinyEWD();
            FloydWarshall fw = new FloydWarshall(g);
            for (int i = 0; i < g.V(); i++) {
                double[] row = new double[g.V()];
                for (int j = 0; j < g.V(); j++) {
                    row[j] = fw.dist(i, j);
                }
                assertOptimalityConditions(g, i, row);
            }
            // 三角不等式:任何中间点 k 都不会让 i→j 更短
            for (int i = 0; i < g.V(); i++) {
                for (int j = 0; j < g.V(); j++) {
                    for (int k = 0; k < g.V(); k++) {
                        double viaK = fw.dist(i, k) + fw.dist(k, j);
                        assertTrue(fw.dist(i, j) <= viaK + EPS,
                                "经 " + k + " 中转更短:" + i + "→" + j + " 应 ≤ " + viaK);
                    }
                }
            }
        }

        @Test
        @DisplayName("路径合法:边长之和等于距离,且路径是简单路(无重复顶点)")
        void pathsAreValidAndSimple() {
            EdgeWeightedDigraph g = tinyEWD();
            FloydWarshall fw = new FloydWarshall(g);
            for (int i = 0; i < g.V(); i++) {
                for (int j = 0; j < g.V(); j++) {
                    List<Integer> vertices = fw.path(i, j);
                    assertPathIsValid(g, i, j, vertices, fw.hasPath(i, j));
                    if (vertices == null) {
                        continue;
                    }
                    double sum = 0.0;
                    for (DirectedEdge e : fw.pathEdges(i, j)) {
                        sum += e.weight();
                    }
                    assertEquals(fw.dist(i, j), sum, EPS, i + "→" + j + " 的路径权值之和");
                    if (i != j) {
                        assertEquals(vertices.size() - 1, fw.pathEdges(i, j).size(), "边数应比顶点数少 1");
                        assertFalse(hasDuplicates(vertices), i + "→" + j + " 的路径不应有重复顶点");
                    }
                }
            }
            assertEquals(1, fw.path(3, 3).size(), "自己到自己应是单元素空路径(不是 null)");
        }

        @Test
        @DisplayName("两种更新方式(就地 / 副本)的矩阵与路径完全一致")
        void bothModesIdentical() {
            EdgeWeightedDigraph g = tinyEWD();
            FloydWarshall inPlace = new FloydWarshall(g, FloydWarshall.Mode.IN_PLACE);
            FloydWarshall copy = new FloydWarshall(g, FloydWarshall.Mode.COPY);
            assertMatrixEquals(matrix(inPlace), matrix(copy), "两种方式的矩阵");
            for (int i = 0; i < g.V(); i++) {
                for (int j = 0; j < g.V(); j++) {
                    assertEquals(inPlace.path(i, j), copy.path(i, j), i + "→" + j + " 的路径");
                    assertEquals(inPlace.hasNegativeCycle(), copy.hasNegativeCycle());
                }
            }
        }
    }

    @Nested
    @DisplayName("随机图:与独立参照物对拍")
    class RandomGraphTest {

        @Test
        @DisplayName("30 张随机图(非负权):全源矩阵等于 Bellman–Ford,固定点与路径合法")
        void randomNonNegativeGraphs() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 2 + rnd.nextInt(10);
                EdgeWeightedDigraph g = randomDigraph(rnd, V, rnd.nextInt(25), false);
                FloydWarshall fw = new FloydWarshall(g);
                assertFalse(fw.hasNegativeCycle());
                assertMatrixEquals(bellmanFordAllPairs(g), matrix(fw), "第 " + trial + " 张图");
                for (int i = 0; i < V; i++) {
                    double[] row = new double[V];
                    for (int j = 0; j < V; j++) {
                        row[j] = fw.dist(i, j);
                    }
                    assertOptimalityConditions(g, i, row);
                }
                assertPathsForAllPairs(g, fw);
            }
        }

        @Test
        @DisplayName("负权但无负环:Floyd–Warshall 正常求解(Dijkstra 会拒绝)")
        void negativeEdgesWithoutNegativeCycle() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(4);
            g.addEdge(0, 1, 1.0);
            g.addEdge(0, 2, 4.0);
            g.addEdge(1, 2, -2.0);     // 负边:0→1→2 = -1 比直达 4 更好
            g.addEdge(2, 3, 1.0);
            g.addEdge(1, 3, 5.0);

            FloydWarshall fw = new FloydWarshall(g);
            assertFalse(fw.hasNegativeCycle(), "有负边但没有负环");
            assertEquals(-1.0, fw.dist(0, 2), EPS);
            assertEquals(0.0, fw.dist(0, 3), EPS);      // -1 + 1
            assertEquals(Arrays.asList(0, 1, 2), fw.path(0, 2));
            assertMatrixEquals(bellmanFordAllPairs(g), matrix(fw), "负权图与 Bellman-Ford");

            // 对照:Dijkstra 会直接拒绝这张图
            assertThrows(IllegalArgumentException.class, () -> new DijkstraSP(g, 0));
        }

        @Test
        @DisplayName("20 张随机 DAG(带负权、必然无环):矩阵与 Bellman–Ford 一致")
        void randomGraphsWithNegativeEdges() {
            Random rnd = new Random(4242L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(9);
                EdgeWeightedDigraph g = SpTestSupport.randomDag(rnd, V, 1 + rnd.nextInt(20), true);
                FloydWarshall fw = new FloydWarshall(g);
                assertFalse(fw.hasNegativeCycle(), "DAG 不可能有环,更不会有负环");
                assertMatrixEquals(bellmanFordAllPairs(g), matrix(fw), "第 " + trial + " 张负权 DAG");
                assertPathsForAllPairs(g, fw);
            }
        }
    }

    @Nested
    @DisplayName("负环检测")
    class NegativeCycleTest {

        @Test
        @DisplayName("三元负环:hasNegativeCycle 为真,返回的环真实存在且总权为负")
        void threeVertexNegativeCycle() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(4);
            g.addEdge(0, 1, 1.0);
            g.addEdge(1, 2, 1.0);
            g.addEdge(2, 3, 1.0);
            g.addEdge(3, 1, -2.5);          // 1→2→3→1 = -0.5

            FloydWarshall fw = new FloydWarshall(g);
            assertTrue(fw.hasNegativeCycle());
            assertTrue(fw.dist(1, 1) < 0, "环上顶点的对角线应为负");
            assertTrue(fw.dist(2, 2) < 0);
            assertTrue(fw.dist(3, 3) < 0);
            assertEquals(0.0, fw.dist(0, 0), EPS, "不在环上的顶点对角线仍为 0");

            List<Integer> cycle = fw.negativeCycle();
            assertNotNull(cycle);
            assertTrue(cycle.size() >= 2, "至少两个顶点:" + cycle);
            assertTrue(cycleWeight(g, cycle) < 0, "返回的环总权应为负:" + cycle);
        }

        @Test
        @DisplayName("自环负边:单顶点的负环")
        void selfLoopNegativeEdge() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            g.addEdge(0, 1, 1.0);
            g.addEdge(1, 1, -1.0);          // 自环负边
            FloydWarshall fw = new FloydWarshall(g);
            assertTrue(fw.hasNegativeCycle());
            assertTrue(fw.dist(1, 1) < 0);
            assertEquals(Arrays.asList(1), fw.negativeCycle());
            assertEquals(-1.0, cycleWeight(g, fw.negativeCycle()), EPS);
        }

        @Test
        @DisplayName("正环与零环都不算负环")
        void positiveAndZeroCyclesAreFine() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            g.addEdge(0, 1, 1.0);
            g.addEdge(1, 2, 1.0);
            g.addEdge(2, 0, 1.0);           // 总权 3 的正环
            g.addEdge(0, 2, 2.0);           // 0→1→2 = 2,与直达相等
            FloydWarshall fw = new FloydWarshall(g);
            assertFalse(fw.hasNegativeCycle());
            assertNull(fw.negativeCycle());
            for (int v = 0; v < 3; v++) {
                assertEquals(0.0, fw.dist(v, v), EPS);
            }

            EdgeWeightedDigraph zero = new EdgeWeightedDigraph(2);
            zero.addEdge(0, 1, 1.0);
            zero.addEdge(1, 0, -1.0);       // 总权 0 的环:不是负环
            FloydWarshall zeroFw = new FloydWarshall(zero);
            assertFalse(zeroFw.hasNegativeCycle());
            assertEquals(0.0, zeroFw.dist(0, 0), EPS);
        }

        @Test
        @DisplayName("负环只影响能到达它的点:不可达顶点的行不受污染")
        void unreachableRowsAreClean() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(4);
            g.addEdge(0, 1, 1.0);
            g.addEdge(1, 2, 1.0);
            g.addEdge(2, 1, -3.0);          // 1↔2 之间的负环
            // 顶点 3 与谁都无关
            FloydWarshall fw = new FloydWarshall(g);
            assertTrue(fw.hasNegativeCycle());
            assertEquals(0.0, fw.dist(3, 3), EPS);
            assertEquals(Double.POSITIVE_INFINITY, fw.dist(3, 0), 0.0);
            assertFalse(fw.hasPath(3, 0));
            assertNull(fw.path(3, 0));
        }
    }

    @Nested
    @DisplayName("边界与规模")
    class EdgeCaseTest {

        @Test
        @DisplayName("不可达点对:距离 +∞、路径 null")
        void unreachablePairs() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(4);
            g.addEdge(0, 1, 1.0);
            g.addEdge(2, 3, 1.0);
            FloydWarshall fw = new FloydWarshall(g);
            assertEquals(Double.POSITIVE_INFINITY, fw.dist(1, 0), 0.0);
            assertFalse(fw.hasPath(1, 0));
            assertNull(fw.path(1, 0));
            assertNull(fw.pathEdges(1, 0));
            assertEquals(1.0, fw.dist(2, 3), EPS);
            assertEquals(Arrays.asList(2, 3), fw.path(2, 3));
        }

        @Test
        @DisplayName("平行边取最轻,非负自环不影响对角线")
        void parallelEdgesAndSelfLoops() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(2);
            g.addEdge(0, 1, 5.0);
            g.addEdge(0, 1, 1.0);
            g.addEdge(1, 1, 2.0);           // 正自环:忽略
            g.addEdge(0, 0, 3.0);
            FloydWarshall fw = new FloydWarshall(g);
            assertEquals(1.0, fw.dist(0, 1), EPS);
            assertEquals(0.0, fw.dist(0, 0), EPS);
            assertEquals(0.0, fw.dist(1, 1), EPS);
            assertFalse(fw.hasNegativeCycle());
        }

        @Test
        @DisplayName("零顶点、单顶点、无边的图")
        void trivialGraphs() {
            FloydWarshall empty = new FloydWarshall(new EdgeWeightedDigraph(0));
            assertEquals(0, empty.V());
            assertFalse(empty.hasNegativeCycle());
            assertTrue(empty.distances().length == 0);

            FloydWarshall single = new FloydWarshall(new EdgeWeightedDigraph(1));
            assertEquals(0.0, single.dist(0, 0), EPS);
            assertEquals(Arrays.asList(0), single.path(0, 0));

            FloydWarshall noEdges = new FloydWarshall(new EdgeWeightedDigraph(3));
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (i != j) {
                        assertEquals(Double.POSITIVE_INFINITY, noEdges.dist(i, j), 0.0);
                    }
                }
            }
        }

        @Test
        @DisplayName("200 顶点稠密图:抽查若干行与逐源 Dijkstra 一致")
        void denseGraph() {
            Random rnd = new Random(31L);
            int V = 200;
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(V);
            for (int v = 0; v < V; v++) {
                for (int w = 0; w < V; w++) {
                    if (v != w) {
                        g.addEdge(v, w, 0.001 + rnd.nextInt(1000));
                    }
                }
            }
            FloydWarshall fw = new FloydWarshall(g);
            assertFalse(fw.hasNegativeCycle());
            for (int row : new int[]{0, 77, 199}) {
                DijkstraSP sp = new DijkstraSP(g, row);
                for (int v = 0; v < V; v++) {
                    assertEquals(sp.distTo(v), fw.dist(row, v), EPS, row + "→" + v);
                }
            }
        }

        @Test
        @DisplayName("distances() 返回副本,外部修改不影响内部")
        void distancesAreCopied() {
            FloydWarshall fw = new FloydWarshall(tinyEWD());
            double[][] copy = fw.distances();
            copy[0][6] = 999.0;
            assertEquals(1.51, fw.dist(0, 6), EPS);
        }

        @Test
        @DisplayName("参数校验:null 图 / null 方式 / 越界顶点")
        void validation() {
            EdgeWeightedDigraph g = tinyEWD();
            assertThrows(IllegalArgumentException.class, () -> new FloydWarshall(null));
            assertThrows(IllegalArgumentException.class, () -> new FloydWarshall(g, null));
            FloydWarshall fw = new FloydWarshall(g);
            assertThrows(IllegalArgumentException.class, () -> fw.dist(-1, 0));
            assertThrows(IllegalArgumentException.class, () -> fw.dist(0, 8));
            assertThrows(IllegalArgumentException.class, () -> fw.hasPath(8, 0));
            assertThrows(IllegalArgumentException.class, () -> fw.path(0, 8));
            assertThrows(IllegalArgumentException.class, () -> fw.pathEdges(8, 0));
        }

        @Test
        @DisplayName("默认使用就地更新;toString 含实现方式与负环结论")
        void defaultModeAndToString() {
            FloydWarshall fw = new FloydWarshall(tinyEWD());
            assertEquals(FloydWarshall.Mode.IN_PLACE, fw.mode());
            assertTrue(fw.toString().contains("IN_PLACE"), fw.toString());
            assertTrue(fw.toString().contains("存在负环=false"), fw.toString());
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private static void assertPathsForAllPairs(EdgeWeightedDigraph g, FloydWarshall fw) {
        for (int i = 0; i < g.V(); i++) {
            for (int j = 0; j < g.V(); j++) {
                List<Integer> vertices = fw.path(i, j);
                assertPathIsValid(g, i, j, vertices, fw.hasPath(i, j));
                if (vertices == null) {
                    continue;
                }
                double sum = 0.0;
                for (DirectedEdge e : fw.pathEdges(i, j)) {
                    sum += e.weight();
                }
                assertEquals(fw.dist(i, j), sum, EPS, i + "→" + j + " 的路径权值之和");
            }
        }
    }

    /** 环的真实总权(末尾回到起点) */
    private static double cycleWeight(EdgeWeightedDigraph g, List<Integer> cycle) {
        double sum = 0.0;
        for (int index = 0; index < cycle.size(); index++) {
            int from = cycle.get(index);
            int to = cycle.get((index + 1) % cycle.size());
            assertTrue(g.hasEdge(from, to), "环上应存在边 " + from + "->" + to);
            sum += g.weightOf(from, to);
        }
        return sum;
    }

    private static boolean hasDuplicates(List<Integer> vertices) {
        return new java.util.HashSet<Integer>(vertices).size() != vertices.size();
    }
}
