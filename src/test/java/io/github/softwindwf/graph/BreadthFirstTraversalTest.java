package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BreadthFirstTraversal} 广度优先遍历测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>顺序与层次:小图上逐点核对访问序、各层顶点、最远距离;</li>
 *   <li><b>最短性</b>(BFS 相对 DFS 的独有结论):用"距离方程"独立验证 ——
 *       {@code dist(s)=0},且对每个可达的 {@code v≠s} 有
 *       {@code dist(v) = 1 + min{dist(u) | u 与 v 相邻}},这直接等价于"不存在更短的路径",
 *       不需要再跑一遍别的算法来对照;</li>
 *   <li>路径:长度恒为 {@code distTo(v)+1}、起于起点止于目标、相邻两点必有边、顶点不重复;</li>
 *   <li>与 DFS 对照:同一张图上 BFS 路径长度恒 ≤ DFS 路径长度(tinyG 上到 12 号是 6 对 8);</li>
 *   <li>两种实现(QUEUE / LEVEL)在 30 张随机图上逐顶点等价;</li>
 *   <li>规模:10 万叶子的星形图(宽)与 10 万层深链(深)都能处理;</li>
 *   <li>非连通图、参数校验、越界层号。</li>
 * </ol>
 */
@DisplayName("BreadthFirstTraversal 广度优先遍历测试")
class BreadthFirstTraversalTest {

    /** 工作区根目录下的 algs4 标准样例图:13 个顶点、15 条边、整图连通 */
    private static final String TINY_G_PATH = "tinyG.txt";

    private static UndirectedGraph tinyG() {
        return GraphIO.readFile(TINY_G_PATH);
    }

    private static UndirectedGraph graphOf(int V, int[][] edges) {
        UndirectedGraph g = new UndirectedGraph(V);
        for (int[] e : edges) {
            g.addEdge(e[0], e[1]);
        }
        return g;
    }

    private static List<Integer> toList(Iterable<Integer> it) {
        List<Integer> list = new ArrayList<Integer>();
        for (int v : it) {
            list.add(v);
        }
        return list;
    }

    private static TreeSet<Integer> toSet(int[] values) {
        TreeSet<Integer> set = new TreeSet<Integer>();
        for (int v : values) {
            set.add(v);
        }
        return set;
    }

    @Nested
    @DisplayName("小图上的访问序与层次(逐点核对)")
    class SmallGraphTest {

        /** 边按 (0,1) (0,2) (1,3) 加入 → 邻接表(头插):adj[0]=[2,1], adj[1]=[3,0], adj[2]=[0], adj[3]=[1] */
        private UndirectedGraph small() {
            return graphOf(4, new int[][]{{0, 1}, {0, 2}, {1, 3}});
        }

        @Test
        @DisplayName("访问序 0,2,1,3;层次 [0] / [2,1] / [3]")
        void orderAndLevels() {
            BreadthFirstTraversal b = new BreadthFirstTraversal(small(), 0);
            assertArrayEquals(new int[]{0, 2, 1, 3}, b.order());
            assertEquals(4, b.count());
            assertEquals(3, b.levelCount());
            assertEquals(2, b.maxDistance());
            assertArrayEquals(new int[]{0}, b.verticesAtLevel(0));
            assertArrayEquals(new int[]{2, 1}, b.verticesAtLevel(1));
            assertArrayEquals(new int[]{3}, b.verticesAtLevel(2));
        }

        @Test
        @DisplayName("距离与最短路径:0→3 为 0,1,3(2 条边)")
        void distancesAndPaths() {
            BreadthFirstTraversal b = new BreadthFirstTraversal(small(), 0);
            assertEquals(0, b.distTo(0));
            assertEquals(1, b.distTo(1));
            assertEquals(1, b.distTo(2));
            assertEquals(2, b.distTo(3));
            assertEquals(-1, b.parent(0));
            assertEquals(0, b.parent(2));
            assertEquals(0, b.parent(1));
            assertEquals(1, b.parent(3));
            assertEquals(Arrays.asList(0, 1, 3), toList(b.pathTo(3)));
            assertEquals(Arrays.asList(0, 2), toList(b.pathTo(2)));
            assertEquals(Arrays.asList(0), toList(b.pathTo(0)));
            assertEquals(0, b.orderIndex(0));
            assertEquals(3, b.orderIndex(3));
        }

        @Test
        @DisplayName("自环与平行边不影响 BFS(每个顶点只入队一次)")
        void selfLoopAndParallelEdges() {
            UndirectedGraph g = graphOf(3, new int[][]{{0, 0}, {0, 1}, {0, 1}, {1, 2}, {2, 2}});
            BreadthFirstTraversal b = new BreadthFirstTraversal(g, 0);
            assertArrayEquals(new int[]{0, 1, 2}, b.order());
            assertEquals(3, b.count());
            assertEquals(1, b.distTo(1));
            assertEquals(2, b.distTo(2));
            assertEquals(3, b.order().length, "访问序里每个顶点只出现一次");
            assertEquals(3, toSet(b.order()).size());
        }

        @Test
        @DisplayName("order() 返回副本,外部改动不影响内部状态")
        void orderIsDefensiveCopy() {
            BreadthFirstTraversal b = new BreadthFirstTraversal(small(), 0);
            int[] first = b.order();
            first[0] = 99;
            assertArrayEquals(new int[]{0, 2, 1, 3}, b.order());
            int[] level = b.verticesAtLevel(1);
            level[0] = 99;
            assertArrayEquals(new int[]{2, 1}, b.verticesAtLevel(1));
        }
    }

    @Nested
    @DisplayName("tinyG.txt:访问序、层次与最短路径")
    class TinyGTest {

        @Test
        @DisplayName("访问序 0,5,6,2,1,4,3,7,8,10,9,12,11;最远距离 6、共 7 层")
        void orderAndLevels() {
            BreadthFirstTraversal b = new BreadthFirstTraversal(tinyG(), 0);
            assertArrayEquals(new int[]{0, 5, 6, 2, 1, 4, 3, 7, 8, 10, 9, 12, 11}, b.order());
            assertEquals(13, b.count());
            assertEquals(7, b.levelCount());
            assertEquals(6, b.maxDistance());
            assertArrayEquals(new int[]{0}, b.verticesAtLevel(0));
            assertArrayEquals(new int[]{5, 6, 2, 1}, b.verticesAtLevel(1));
            assertArrayEquals(new int[]{4, 3, 7}, b.verticesAtLevel(2));
            assertArrayEquals(new int[]{8}, b.verticesAtLevel(3));
            assertArrayEquals(new int[]{10}, b.verticesAtLevel(4));
            assertArrayEquals(new int[]{9}, b.verticesAtLevel(5));
            assertArrayEquals(new int[]{12, 11}, b.verticesAtLevel(6));
        }

        @Test
        @DisplayName("0→12 最短路径为 0,6,7,8,10,9,12(6 条边)")
        void shortestPathTo12() {
            BreadthFirstTraversal b = new BreadthFirstTraversal(tinyG(), 0);
            assertEquals(6, b.distTo(12));
            assertEquals(Arrays.asList(0, 6, 7, 8, 10, 9, 12), toList(b.pathTo(12)));
            assertEquals(5, b.distTo(9));
            assertEquals(4, b.distTo(10));
        }

        @Test
        @DisplayName("层次自洽:每个顶点的距离等于它所在层号,各层顶点互不重复且并集是可达集")
        void levelsAreConsistent() {
            UndirectedGraph g = tinyG();
            BreadthFirstTraversal b = new BreadthFirstTraversal(g, 0);
            TreeSet<Integer> union = new TreeSet<Integer>();
            int total = 0;
            for (int d = 0; d < b.levelCount(); d++) {
                for (int v : b.verticesAtLevel(d)) {
                    assertEquals(d, b.distTo(v), "顶点 " + v + " 的距离与层号不符");
                    assertTrue(union.add(v), "顶点 " + v + " 出现在多个层里");
                    total++;
                }
            }
            assertEquals(13, total);
            assertEquals(13, union.size());
            for (int v = 0; v < g.V(); v++) {
                assertTrue(contains(b.verticesAtLevel(b.distTo(v)), v),
                        "顶点 " + v + " 应出现在第 " + b.distTo(v) + " 层");
            }
        }

        @Test
        @DisplayName("访问序是可达点集的排列,且距离非递减")
        void orderIsPermutationWithNondecreasingDistance() {
            BreadthFirstTraversal b = new BreadthFirstTraversal(tinyG(), 0);
            TreeSet<Integer> reachable = new TreeSet<Integer>();
            for (int v = 0; v < 13; v++) {
                reachable.add(v);
            }
            assertEquals(reachable, toSet(b.order()));
            int previous = -1;
            for (int v : b.order()) {
                assertTrue(b.distTo(v) >= previous, "访问序应按距离非递减");
                previous = b.distTo(v);
            }
        }
    }

    @Nested
    @DisplayName("最短性(与 DFS 的关键差别)")
    class ShortestPathTest {

        @Test
        @DisplayName("tinyG:距离满足 dist(v) = 1 + min{dist(u) | u 与 v 相邻},即不存在更短路径")
        void distancesAreOptimalOnTinyG() {
            assertDistancesAreOptimal(tinyG(), 0);
        }

        @Test
        @DisplayName("30 张随机图:距离方程、路径合法性与可达性闭包都成立")
        void distancesAreOptimalOnRandomGraphs() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 1 + rnd.nextInt(12);
                UndirectedGraph g = new UndirectedGraph(V);
                int E = rnd.nextInt(25);
                for (int i = 0; i < E; i++) {
                    g.addEdge(rnd.nextInt(V), rnd.nextInt(V));
                }
                int source = rnd.nextInt(V);
                assertDistancesAreOptimal(g, source);
            }
        }

        @Test
        @DisplayName("BFS 路径长度恒 ≤ DFS 路径长度(tinyG 上到 12 号:6 边 vs 8 边)")
        void bfsIsNeverLongerThanDfs() {
            UndirectedGraph g = tinyG();
            BreadthFirstTraversal bfs = new BreadthFirstTraversal(g, 0);
            DepthFirstTraversal dfs = new DepthFirstTraversal(g, 0);

            assertEquals(6, edgesOf(bfs.pathTo(12)));
            assertEquals(8, edgesOf(dfs.pathTo(12)), "DFS 走了一条更长的路");
            assertTrue(edgesOf(bfs.pathTo(12)) < edgesOf(dfs.pathTo(12)), "BFS 应严格更短");

            for (int v = 0; v < g.V(); v++) {
                assertTrue(edgesOf(bfs.pathTo(v)) <= edgesOf(dfs.pathTo(v)),
                        "顶点 " + v + ":BFS 路径不应比 DFS 更长");
            }
        }
    }

    @Nested
    @DisplayName("非连通图")
    class DisconnectedTest {

        @Test
        @DisplayName("两个连通分量:只能到达所在分量,另一分量距离为 -1、路径为 null")
        void twoComponents() {
            UndirectedGraph g = graphOf(4, new int[][]{{0, 1}, {2, 3}});
            BreadthFirstTraversal b = new BreadthFirstTraversal(g, 0);
            assertEquals(2, b.count());
            assertEquals(2, b.levelCount());
            assertEquals(1, b.maxDistance());
            assertArrayEquals(new int[]{0, 1}, b.order());
            assertArrayEquals(new int[]{0}, b.verticesAtLevel(0));
            assertArrayEquals(new int[]{1}, b.verticesAtLevel(1));
            assertEquals(1, b.distTo(1));
            assertEquals(-1, b.distTo(2));
            assertFalse(b.marked(2));
            assertFalse(b.hasPathTo(2));
            assertNull(b.pathTo(2));
            assertEquals(-1, b.orderIndex(2));
            assertEquals(-1, b.parent(2));
        }

        @Test
        @DisplayName("孤立起点:只有自己一层")
        void isolatedSource() {
            BreadthFirstTraversal b = new BreadthFirstTraversal(new UndirectedGraph(3), 1);
            assertEquals(1, b.count());
            assertEquals(1, b.levelCount());
            assertEquals(0, b.maxDistance());
            assertArrayEquals(new int[]{1}, b.order());
            assertEquals(Arrays.asList(1), toList(b.pathTo(1)));
        }

        @Test
        @DisplayName("0 顶点图没有合法起点")
        void zeroVertexGraph() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BreadthFirstTraversal(new UndirectedGraph(0), 0));
        }
    }

    @Nested
    @DisplayName("两种实现等价")
    class ModeConsistencyTest {

        @Test
        @DisplayName("默认构造是单队列实现")
        void defaultMode() {
            BreadthFirstTraversal b = new BreadthFirstTraversal(tinyG(), 0);
            assertEquals(BreadthFirstTraversal.Mode.QUEUE, b.mode());
            assertEquals(0, b.source());
        }

        @Test
        @DisplayName("tinyG 上 QUEUE 与 LEVEL 的访问序、距离、层次、路径完全一致")
        void bothModesAgreeOnTinyG() {
            assertModesAgree(tinyG(), 0);
        }

        @Test
        @DisplayName("30 张随机图(含自环、平行边、多分量)两种实现完全一致")
        void bothModesAgreeOnRandomGraphs() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 1 + rnd.nextInt(12);
                UndirectedGraph g = new UndirectedGraph(V);
                int E = rnd.nextInt(25);
                for (int i = 0; i < E; i++) {
                    g.addEdge(rnd.nextInt(V), rnd.nextInt(V));
                }
                assertModesAgree(g, rnd.nextInt(V));
            }
        }
    }

    @Nested
    @DisplayName("规模:宽图与深图")
    class ScaleTest {

        @Test
        @DisplayName("10 万叶子的星形图:全部距离为 1、第 1 层 10 万个顶点")
        void wideStarGraph() {
            final int leaves = 100_000;
            UndirectedGraph star = new UndirectedGraph(leaves + 1);
            for (int v = 1; v <= leaves; v++) {
                star.addEdge(0, v);
            }
            BreadthFirstTraversal b = new BreadthFirstTraversal(star, 0);
            assertEquals(leaves + 1, b.count());
            assertEquals(2, b.levelCount());
            assertEquals(1, b.maxDistance());
            assertEquals(leaves, b.verticesAtLevel(1).length);
            assertEquals(1, b.distTo(leaves));
        }

        @Test
        @DisplayName("10 万层的深链:距离等于下标(迭代式算法,不存在爆栈问题)")
        void deepChain() {
            final int n = 100_000;
            UndirectedGraph chain = new UndirectedGraph(n);
            for (int v = 0; v + 1 < n; v++) {
                chain.addEdge(v, v + 1);
            }
            BreadthFirstTraversal b = new BreadthFirstTraversal(chain, 0);
            assertEquals(n, b.count());
            assertEquals(n, b.levelCount(), "链上每一层只有一个顶点");
            assertEquals(n - 1, b.distTo(n - 1));
            assertEquals(n - 1, b.maxDistance());
            assertEquals(n, toList(b.pathTo(n - 1)).size());
            assertArrayEquals(new int[]{0, 1, 2, 3}, Arrays.copyOf(b.order(), 4));
        }
    }

    @Nested
    @DisplayName("参数校验")
    class ErrorTest {

        @Test
        @DisplayName("null 图 / null 方式 / 起点越界")
        void constructorErrors() {
            UndirectedGraph g = tinyG();
            assertThrows(IllegalArgumentException.class, () -> new BreadthFirstTraversal(null, 0));
            assertThrows(IllegalArgumentException.class, () -> new BreadthFirstTraversal(g, 0, null));
            assertThrows(IllegalArgumentException.class, () -> new BreadthFirstTraversal(g, -1));
            assertThrows(IllegalArgumentException.class, () -> new BreadthFirstTraversal(g, 13));
        }

        @Test
        @DisplayName("查询越界顶点、越界层号")
        void queryErrors() {
            BreadthFirstTraversal b = new BreadthFirstTraversal(tinyG(), 0);
            assertThrows(IllegalArgumentException.class, () -> b.marked(-1));
            assertThrows(IllegalArgumentException.class, () -> b.marked(13));
            assertThrows(IllegalArgumentException.class, () -> b.distTo(13));
            assertThrows(IllegalArgumentException.class, () -> b.orderIndex(-1));
            assertThrows(IllegalArgumentException.class, () -> b.parent(13));
            assertThrows(IllegalArgumentException.class, () -> b.hasPathTo(13));
            assertThrows(IllegalArgumentException.class, () -> b.pathTo(13));
            assertThrows(IllegalArgumentException.class, () -> b.verticesAtLevel(-1));
            assertThrows(IllegalArgumentException.class, () -> b.verticesAtLevel(7), "层号上界是 levelCount-1");
        }

        @Test
        @DisplayName("toString 含实现方式、可达数、最远距离与层数")
        void toStringContent() {
            String s = new BreadthFirstTraversal(tinyG(), 0).toString();
            assertTrue(s.contains("QUEUE"), s);
            assertTrue(s.contains("13/13"), s);
            assertTrue(s.contains("最远距离 6"), s);
            assertTrue(s.contains("层数 7"), s);
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /**
     * 独立验证"距离就是最短距离":不重跑任何最短路算法,只用距离方程
     * <pre>
     *   dist(s) = 0
     *   dist(v) = 1 + min{ dist(u) | u 与 v 相邻 }        (v ≠ s 且可达)
     * </pre>
     * 该方程的解唯一,因此满足它就等于"不存在更短的路径"。同时核对路径合法性与可达闭包。
     */
    private static void assertDistancesAreOptimal(UndirectedGraph g, int source) {
        BreadthFirstTraversal b = new BreadthFirstTraversal(g, source);
        assertEquals(0, b.distTo(source), "起点距离应为 0");

        for (int v = 0; v < g.V(); v++) {
            if (!b.marked(v)) {
                assertEquals(-1, b.distTo(v), "不可达顶点距离应为 -1");
                assertFalse(b.hasPathTo(v));
                assertNull(b.pathTo(v));
                continue;
            }
            // 可达闭包:可达顶点的邻居必然可达
            for (int u : g.adj(v)) {
                assertTrue(b.marked(u), "顶点 " + u + " 与可达顶点 " + v + " 相邻,应可达");
            }
            if (v == source) {
                continue;
            }
            int best = Integer.MAX_VALUE;
            for (int u : g.adj(v)) {
                best = Math.min(best, b.distTo(u));
            }
            assertEquals(best + 1, b.distTo(v),
                    "顶点 " + v + " 的距离不是最短(邻居最小距离 + 1 才是)");
        }

        // 边两端的距离差不超过 1(三角形不等式方向之一)
        for (int v = 0; v < g.V(); v++) {
            if (!b.marked(v)) {
                continue;
            }
            for (int u : g.adj(v)) {
                assertTrue(b.distTo(u) <= b.distTo(v) + 1, "边 " + v + "-" + u + " 两端距离差不合法");
            }
        }

        // 路径合法性:长度 = 距离 + 1,起于起点、止于目标、相邻两点有边、无重复顶点
        for (int v = 0; v < g.V(); v++) {
            if (!b.marked(v)) {
                continue;
            }
            List<Integer> path = toList(b.pathTo(v));
            assertEquals(b.distTo(v) + 1, path.size(), "顶点 " + v + " 的路径长度应等于距离 + 1");
            assertEquals(source, path.get(0).intValue(), "路径应从起点开始");
            assertEquals(v, path.get(path.size() - 1).intValue(), "路径应止于目标顶点");
            assertEquals(path.size(), new TreeSet<Integer>(path).size(), "路径不应有重复顶点");
            for (int i = 0; i + 1 < path.size(); i++) {
                assertTrue(g.hasEdge(path.get(i), path.get(i + 1)),
                        "路径上 " + path.get(i) + "-" + path.get(i + 1) + " 之间没有边");
            }
        }
    }

    /** 同一个图、同一个起点,两种实现的所有可观测量必须一致 */
    private static void assertModesAgree(UndirectedGraph g, int source) {
        BreadthFirstTraversal queue = new BreadthFirstTraversal(g, source, BreadthFirstTraversal.Mode.QUEUE);
        BreadthFirstTraversal level = new BreadthFirstTraversal(g, source, BreadthFirstTraversal.Mode.LEVEL);

        assertEquals(queue.count(), level.count(), "可达顶点数");
        assertEquals(queue.levelCount(), level.levelCount(), "层数");
        assertEquals(queue.maxDistance(), level.maxDistance(), "最远距离");
        assertArrayEquals(queue.order(), level.order(), "访问序");
        for (int d = 0; d < queue.levelCount(); d++) {
            assertArrayEquals(queue.verticesAtLevel(d), level.verticesAtLevel(d), "第 " + d + " 层");
        }
        for (int v = 0; v < g.V(); v++) {
            assertEquals(queue.marked(v), level.marked(v), "marked(" + v + ")");
            assertEquals(queue.distTo(v), level.distTo(v), "distTo(" + v + ")");
            assertEquals(queue.parent(v), level.parent(v), "parent(" + v + ")");
            assertEquals(queue.orderIndex(v), level.orderIndex(v), "orderIndex(" + v + ")");
            Iterable<Integer> a = queue.pathTo(v);
            Iterable<Integer> b = level.pathTo(v);
            if (a == null || b == null) {
                assertNull(a, "单队列版路径为 null 时按层版也应如此:顶点 " + v);
                assertNull(b, "按层版路径为 null 时单队列版也应如此:顶点 " + v);
            }
            else {
                assertEquals(toList(a), toList(b), "pathTo(" + v + ")");
            }
        }
    }

    /** 路径的边数 */
    private static int edgesOf(Iterable<Integer> path) {
        int n = 0;
        for (int ignored : path) {
            n++;
        }
        return n - 1;
    }

    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return -1;
    }

    /** 数组中是否含目标值 */
    private static boolean contains(int[] values, int target) {
        return indexOf(values, target) >= 0;
    }
}
