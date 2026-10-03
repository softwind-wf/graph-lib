package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DirectedPaths} 有向图单源可达与路径测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>可达集合与<b>遍历模式无关</b>(BFS 与 DFS 必须给出一模一样的可达顶点集合);</li>
 *   <li>BFS 的 {@code distTo} 是<b>最少边数</b> —— 与测试里另写的 BFS 对照;</li>
 *   <li>路径合法:首尾、相邻两点之间确有弧、长度等于 {@code distTo};</li>
 *   <li>与 algs4 的 {@code BreadthFirstDirectedPaths} / {@code DepthFirstDirectedPaths} 对拍;</li>
 *   <li>有向图的不对称性:0→5 可达不代表 5→0 可达;</li>
 *   <li>迭代实现:20 万顶点的有向链不栈溢出。</li>
 * </ol>
 */
@DisplayName("DirectedPaths 有向图可达与路径测试")
class DirectedPathsTest {

    private static final String AOE_PATH = "projectAOE.txt";

    private static Digraph tinyDG() {
        return StronglyConnectedComponents.tinyDigraph();
    }

    @Nested
    @DisplayName("已知样例")
    class KnownCaseTest {

        @Test
        @DisplayName("tinyDG:从 0 可达 11 个顶点{7, 8} 不可达")
        void reachableFromZero() {
            DirectedPaths bfs = new DirectedPaths(tinyDG(), 0);
            assertEquals(11, bfs.count());
            assertFalse(bfs.hasPathTo(7));
            assertFalse(bfs.hasPathTo(8));
            assertTrue(bfs.hasPathTo(0));
            assertTrue(bfs.hasPathTo(12));

            DirectedPaths dfs = new DirectedPaths(tinyDG(), 0, DirectedPaths.Mode.DFS);
            assertEquals(bfs.count(), dfs.count(), "两种模式的可达顶点数应一致");
            for (int v = 0; v < 13; v++) {
                assertEquals(bfs.hasPathTo(v), dfs.hasPathTo(v), "顶点 " + v + " 的可达性");
            }
        }

        @Test
        @DisplayName("有向图的不对称性:0 能到 7 吗?不能;7 能到 0 吗?也不能")
        void directedAsymmetry() {
            Digraph graph = new Digraph(3);
            graph.addEdge(0, 1);
            graph.addEdge(1, 2);
            DirectedPaths fromZero = new DirectedPaths(graph, 0);
            DirectedPaths fromTwo = new DirectedPaths(graph, 2);
            assertTrue(fromZero.hasPathTo(2));
            assertFalse(fromTwo.hasPathTo(0), "有向边的反方向不通");
            assertEquals(1, fromTwo.count());
        }

        @Test
        @DisplayName("BFS 距离是最少边数,路径长度与之相等")
        void bfsDistanceIsShortest() {
            Digraph graph = tinyDG();
            DirectedPaths paths = new DirectedPaths(graph, 0);
            int[] expected = independentBfsDistance(graph, 0);
            for (int v = 0; v < graph.V(); v++) {
                assertEquals(expected[v], paths.distTo(v), "顶点 " + v + " 的最少边数");
            }
        }

        @Test
        @DisplayName("路径合法:起点是源点、终点是目标、相邻两点之间确有弧")
        void pathsAreValid() {
            Digraph graph = tinyDG();
            for (DirectedPaths.Mode mode : DirectedPaths.Mode.values()) {
                DirectedPaths paths = new DirectedPaths(graph, 0, mode);
                for (int v = 0; v < graph.V(); v++) {
                    if (!paths.hasPathTo(v)) {
                        assertNull(paths.pathTo(v));
                        assertNull(paths.pathEdgesTo(v));
                        assertEquals(-1, paths.distTo(v));
                        continue;
                    }
                    List<Integer> path = paths.pathTo(v);
                    assertEquals(0, path.get(0), "路径应从源点开始");
                    assertEquals(v, path.get(path.size() - 1), "路径应到达目标");
                    for (int i = 0; i + 1 < path.size(); i++) {
                        assertTrue(graph.hasEdge(path.get(i), path.get(i + 1)),
                                path.get(i) + "->" + path.get(i + 1) + " 应是一条弧");
                    }
                    assertEquals(paths.distTo(v), path.size() - 1, "路径边数应等于 distTo");
                    assertEquals(path.size() - 1, paths.pathEdgesTo(v).size());
                }
            }
        }

        @Test
        @DisplayName("访问顺序:长度等于可达数,且恰好覆盖全部可达顶点")
        void orderCoversReachable() {
            Digraph graph = tinyDG();
            for (DirectedPaths.Mode mode : DirectedPaths.Mode.values()) {
                DirectedPaths paths = new DirectedPaths(graph, 0, mode);
                int[] order = paths.order();
                assertEquals(paths.count(), order.length);
                Set<Integer> seen = new HashSet<Integer>();
                for (int v : order) {
                    assertTrue(seen.add(v), "访问顺序里不应重复出现顶点");
                    assertTrue(paths.hasPathTo(v), v + " 应可达");
                }
                assertEquals(0, order[0], "第一个访问的应是源点");
            }
        }

        @Test
        @DisplayName("单顶点、无出边、空图")
        void trivial() {
            Digraph single = new Digraph(1);
            DirectedPaths paths = new DirectedPaths(single, 0);
            assertEquals(1, paths.count());
            assertEquals(0, paths.distTo(0));
            assertEquals(java.util.Arrays.asList(0), paths.pathTo(0));
            assertEquals(0, paths.pathEdgesTo(0).size());

            Digraph sink = new Digraph(2);
            sink.addEdge(1, 0);
            assertEquals(1, new DirectedPaths(sink, 0).count(), "0 没有出边");
            assertEquals(2, new DirectedPaths(sink, 1).count());
        }
    }

    @Nested
    @DisplayName("与参考实现对拍")
    class OracleTest {

        @Test
        @DisplayName("30 张随机有向图:可达性与 algs4 两种实现一致;BFS 距离也一致")
        void agreesWithReference() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 30; trial++) {
                Digraph graph = DirectedCycleTest.randomDigraph(rnd, 2 + rnd.nextInt(8), rnd.nextInt(14));
                edu.princeton.cs.algs4.Digraph reference = new edu.princeton.cs.algs4.Digraph(graph.V());
                for (int[] edge : graph.edges()) {
                    reference.addEdge(edge[0], edge[1]);
                }
                int source = rnd.nextInt(graph.V());
                edu.princeton.cs.algs4.BreadthFirstDirectedPaths refBfs =
                        new edu.princeton.cs.algs4.BreadthFirstDirectedPaths(reference, source);
                edu.princeton.cs.algs4.DepthFirstDirectedPaths refDfs =
                        new edu.princeton.cs.algs4.DepthFirstDirectedPaths(reference, source);

                DirectedPaths myBfs = new DirectedPaths(graph, source, DirectedPaths.Mode.BFS);
                DirectedPaths myDfs = new DirectedPaths(graph, source, DirectedPaths.Mode.DFS);
                for (int v = 0; v < graph.V(); v++) {
                    assertEquals(refBfs.hasPathTo(v), myBfs.hasPathTo(v),
                            "第 " + trial + " 张图 BFS 可达性 " + source + "->" + v);
                    assertEquals(refDfs.hasPathTo(v), myDfs.hasPathTo(v),
                            "第 " + trial + " 张图 DFS 可达性 " + source + "->" + v);
                    assertEquals(refBfs.hasPathTo(v), myDfs.hasPathTo(v),
                            "第 " + trial + " 张图两种模式的可达性应互相一致");
                    if (refBfs.hasPathTo(v)) {
                        assertEquals(refBfs.distTo(v), myBfs.distTo(v),
                                "第 " + trial + " 张图 " + source + "->" + v + " 的最少边数");
                    }
                }
            }
        }

        @Test
        @DisplayName("AOE 网的拓扑结构上也能用(前向可达)")
        void worksOnAoeStructure() {
            AOENetwork net = GraphIO.readAoeFile(AOE_PATH);
            DirectedPaths paths = new DirectedPaths(net.toDigraph(), 0);
            assertEquals(13, paths.count(), "E0 应当能到达所有事件");
            assertEquals(0, paths.distTo(0));
            // 0→12 有两条等长的最短路径(0,1,3,6,10,12 与 0,1,3,6,9,12),只校验"等长且合法"
            List<Integer> path = paths.pathTo(12);
            assertEquals(6, path.size(), "5 条弧");
            assertEquals(0, path.get(0));
            assertEquals(12, path.get(path.size() - 1));
            assertEquals(5, paths.distTo(12));
            for (int i = 0; i + 1 < path.size(); i++) {
                assertTrue(net.toDigraph().hasEdge(path.get(i), path.get(i + 1)),
                        path.get(i) + "->" + path.get(i + 1) + " 应是一条弧");
            }
        }
    }

    @Nested
    @DisplayName("校验与规模")
    class ValidationAndScaleTest {

        @Test
        @DisplayName("参数校验:null 图、null 模式、源点越界、查询越界")
        void validation() {
            Digraph graph = tinyDG();
            assertThrows(IllegalArgumentException.class, () -> new DirectedPaths(null, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new DirectedPaths(graph, 0, (DirectedPaths.Mode) null));
            assertThrows(IllegalArgumentException.class, () -> new DirectedPaths(graph, 13));
            assertThrows(IllegalArgumentException.class, () -> new DirectedPaths(graph, -1));
            DirectedPaths paths = new DirectedPaths(graph, 0);
            assertThrows(IllegalArgumentException.class, () -> paths.hasPathTo(13));
            assertThrows(IllegalArgumentException.class, () -> paths.distTo(-1));
            assertThrows(IllegalArgumentException.class, () -> paths.pathTo(13));
            assertThrows(IllegalArgumentException.class, () -> paths.pathEdgesTo(13));
        }

        @Test
        @DisplayName("20 万顶点的有向链:两种模式都不栈溢出")
        void largeChain() {
            int n = 200000;
            Digraph graph = new Digraph(n);
            for (int v = 0; v + 1 < n; v++) {
                graph.addEdge(v, v + 1);
            }
            for (DirectedPaths.Mode mode : DirectedPaths.Mode.values()) {
                DirectedPaths paths = new DirectedPaths(graph, 0, mode);
                assertEquals(n, paths.count());
                assertEquals(n - 1, paths.distTo(n - 1));
                assertEquals(n - 1, paths.pathTo(n - 1).size() - 1);
            }
        }

        @Test
        @DisplayName("toString 含模式、源点与可达数")
        void toStringContent() {
            String text = new DirectedPaths(tinyDG(), 0).toString();
            assertTrue(text.contains("DirectedPaths(BFS, 源点 0)"), text);
            assertTrue(text.contains("可达 11 个"), text);
            assertTrue(text.contains("不可达"), text);
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /** 独立的 BFS:返回从 source 到每个顶点的最少边数(−1 表示不可达) */
    private static int[] independentBfsDistance(Digraph graph, int source) {
        int[] distance = new int[graph.V()];
        java.util.Arrays.fill(distance, -1);
        Deque<Integer> queue = new ArrayDeque<Integer>();
        distance[source] = 0;
        queue.add(source);
        while (!queue.isEmpty()) {
            int v = queue.poll();
            for (int w : graph.adj(v)) {
                if (distance[w] < 0) {
                    distance[w] = distance[v] + 1;
                    queue.add(w);
                }
            }
        }
        return distance;
    }

    /** 供其它测试复用:把可达集合收集成列表 */
    static List<Integer> reachableVertices(Digraph graph, int source) {
        DirectedPaths paths = new DirectedPaths(graph, source);
        List<Integer> result = new ArrayList<Integer>();
        for (int v = 0; v < graph.V(); v++) {
            if (paths.hasPathTo(v)) {
                result.add(v);
            }
        }
        return result;
    }
}
