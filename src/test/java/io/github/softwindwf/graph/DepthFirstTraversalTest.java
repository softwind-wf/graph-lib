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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DepthFirstTraversal} 深度优先遍历测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>顺序口径:前序 = 首次访问序,后序 = 邻接点处理完的序(小图上逐点核对);</li>
 *   <li>DFS 树:父顶点链、深度、路径三者自洽,路径相邻两点必有边;</li>
 *   <li>连通性:多连通分量、孤立起点、0 顶点图;</li>
 *   <li><b>两种实现逐顶点等价</b>:递归版与显式栈迭代版在可达集、前序、后序、DFS 树上完全一致
 *       (tinyG + 30 张随机图差分);</li>
 *   <li>结构不变式:前序/后序都是已访问顶点集的排列、后序末位是起点、
 *       每个顶点的 DFS 子树在后序中占一段<b>连续区间</b>(拓扑排序与强连通分量的理论基础);</li>
 *   <li>深图:迭代版能处理 10 万层深链(递归版受系统栈限制,那一条不做断言);</li>
 *   <li>参数校验:null 图、null 方式、起点越界、查询顶点越界。</li>
 * </ol>
 */
@DisplayName("DepthFirstTraversal 深度优先遍历测试")
class DepthFirstTraversalTest {

    /** 工作区根目录下的 algs4 标准样例图:13 个顶点、15 条边、整图连通 */
    private static final String TINY_G_PATH = "tinyG.txt";

    private static UndirectedGraph tinyG() {
        return GraphIO.readFile(TINY_G_PATH);
    }

    /** 建一张图:顶点数 V,边为给定数组 */
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
    @DisplayName("小图上的前序/后序/路径(逐点核对)")
    class SmallGraphOrderTest {

        /** 边按 (0,1) (0,2) (1,3) 加入 → 邻接表(头插):adj[0]=[2,1], adj[1]=[3,0], adj[2]=[0], adj[3]=[1] */
        private UndirectedGraph small() {
            return graphOf(4, new int[][]{{0, 1}, {0, 2}, {1, 3}});
        }

        @Test
        @DisplayName("前序 = 0,2,1,3;后序 = 2,3,1,0")
        void preAndPostOrder() {
            DepthFirstTraversal t = new DepthFirstTraversal(small(), 0);
            assertArrayEquals(new int[]{0, 2, 1, 3}, t.preOrder());
            assertArrayEquals(new int[]{2, 3, 1, 0}, t.postOrder());
            assertEquals(4, t.count());
            assertEquals(0, t.preOrderIndex(0));
            assertEquals(3, t.preOrderIndex(3));
            assertEquals(0, t.postOrderIndex(2));
            assertEquals(3, t.postOrderIndex(0));
        }

        @Test
        @DisplayName("DFS 树:parent/depth 与路径一致,路径 0→3 为 0,1,3")
        void dfsTree() {
            DepthFirstTraversal t = new DepthFirstTraversal(small(), 0);
            assertEquals(-1, t.parent(0), "起点没有父顶点");
            assertEquals(0, t.parent(2));
            assertEquals(0, t.parent(1));
            assertEquals(1, t.parent(3), "3 是被 1 发现的");
            assertEquals(0, t.depth(0));
            assertEquals(1, t.depth(1));
            assertEquals(1, t.depth(2));
            assertEquals(2, t.depth(3));
            assertEquals(Arrays.asList(0, 1, 3), toList(t.pathTo(3)));
            assertEquals(Arrays.asList(0, 2), toList(t.pathTo(2)));
            assertEquals(Arrays.asList(0), toList(t.pathTo(0)), "起点到自己是长度为 1 的路径");
        }

        @Test
        @DisplayName("换起点(从 3 出发)顺序随之改变,仍只是已访问集的排列")
        void fromAnotherSource() {
            UndirectedGraph g = small();
            DepthFirstTraversal t = new DepthFirstTraversal(g, 3);
            assertArrayEquals(new int[]{3, 1, 0, 2}, t.preOrder());
            assertArrayEquals(new int[]{2, 0, 1, 3}, t.postOrder());
            assertEquals(Arrays.asList(3, 1, 0, 2), toList(t.pathTo(2)));
        }

        @Test
        @DisplayName("自环与平行边不影响遍历(每个顶点仍只访问一次)")
        void selfLoopAndParallelEdges() {
            UndirectedGraph g = graphOf(3, new int[][]{{0, 0}, {0, 1}, {0, 1}, {1, 2}, {2, 2}});
            DepthFirstTraversal t = new DepthFirstTraversal(g, 0);
            assertEquals(3, t.count());
            assertArrayEquals(new int[]{0, 1, 2}, t.preOrder());
            assertArrayEquals(new int[]{2, 1, 0}, t.postOrder());
            assertEquals(1, t.preOrderIndex(1), "平行边不会让顶点被访问两次");
        }

        @Test
        @DisplayName("preOrder()/postOrder() 返回副本,外部改动不影响内部状态")
        void ordersAreDefensiveCopies() {
            DepthFirstTraversal t = new DepthFirstTraversal(small(), 0);
            int[] first = t.preOrder();
            first[0] = 99;
            assertArrayEquals(new int[]{0, 2, 1, 3}, t.preOrder(), "数组应每次重新拷贝");
        }
    }

    @Nested
    @DisplayName("tinyG.txt 上的结构与不变式")
    class TinyGTest {

        @Test
        @DisplayName("整图连通:可达 13/13,所有顶点 marked")
        void wholeGraphReachable() {
            UndirectedGraph g = tinyG();
            DepthFirstTraversal t = new DepthFirstTraversal(g, 0);
            assertEquals(13, t.count());
            for (int v = 0; v < g.V(); v++) {
                assertTrue(t.marked(v), "顶点 " + v + " 应可达");
                assertTrue(t.hasPathTo(v));
            }
        }

        @Test
        @DisplayName("前序/后序都是已访问顶点集的排列,后序末位是起点")
        void ordersArePermutations() {
            UndirectedGraph g = tinyG();
            DepthFirstTraversal t = new DepthFirstTraversal(g, 0);
            TreeSet<Integer> all = new TreeSet<Integer>();
            for (int v = 0; v < g.V(); v++) {
                all.add(v);
            }
            assertEquals(all, toSet(t.preOrder()));
            assertEquals(all, toSet(t.postOrder()));
            assertEquals(13, t.preOrder().length);
            assertEquals(13, t.postOrder().length);
            assertEquals(0, t.preOrder()[0], "前序第一个是起点");
            assertEquals(0, t.postOrder()[12], "后序最后一个是起点");
        }

        @Test
        @DisplayName("DFS 树自洽:每个非起点顶点的深度 = 父顶点深度 + 1")
        void treeDepthConsistent() {
            DepthFirstTraversal t = new DepthFirstTraversal(tinyG(), 0);
            for (int v = 0; v < 13; v++) {
                if (v == 0) {
                    continue;
                }
                int p = t.parent(v);
                assertTrue(p >= 0, "顶点 " + v + " 应有父顶点");
                assertEquals(t.depth(p) + 1, t.depth(v), "顶点 " + v + " 的深度与父顶点不符");
            }
        }

        @Test
        @DisplayName("每个可达顶点的路径合法:起于起点、止于该顶点、相邻两点之间有边")
        void everyPathIsValid() {
            UndirectedGraph g = tinyG();
            DepthFirstTraversal t = new DepthFirstTraversal(g, 0);
            for (int v = 0; v < g.V(); v++) {
                List<Integer> path = toList(t.pathTo(v));
                assertNotNull(path, "顶点 " + v + " 应可达");
                assertEquals(0, path.get(0).intValue(), "路径应从起点开始");
                assertEquals(v, path.get(path.size() - 1).intValue(), "路径应止于目标顶点");
                assertEquals(t.depth(v) + 1, path.size(), "路径长度应为深度 + 1");
                for (int i = 0; i + 1 < path.size(); i++) {
                    assertTrue(g.hasEdge(path.get(i), path.get(i + 1)),
                            "路径上 " + path.get(i) + "-" + path.get(i + 1) + " 之间没有边");
                }
            }
        }

        @Test
        @DisplayName("每个顶点的 DFS 子树在后序中占一段连续区间,且顶点本身在该区间末尾")
        void postOrderSubtreeIsContiguous() {
            UndirectedGraph g = tinyG();
            DepthFirstTraversal t = new DepthFirstTraversal(g, 0);
            assertSubtreeContiguity(g, t);
        }

        @Test
        @DisplayName("可复现:同一张图两次遍历结果完全相同")
        void deterministic() {
            UndirectedGraph g = tinyG();
            DepthFirstTraversal a = new DepthFirstTraversal(g, 0);
            DepthFirstTraversal b = new DepthFirstTraversal(g, 0);
            assertArrayEquals(a.preOrder(), b.preOrder());
            assertArrayEquals(a.postOrder(), b.postOrder());
            assertEquals(a.count(), b.count());
        }
    }

    @Nested
    @DisplayName("非连通图")
    class DisconnectedTest {

        @Test
        @DisplayName("两个连通分量:只能到达所在分量,另一分量 pathTo 为 null")
        void twoComponents() {
            UndirectedGraph g = graphOf(4, new int[][]{{0, 1}, {2, 3}});
            DepthFirstTraversal t = new DepthFirstTraversal(g, 0);
            assertEquals(2, t.count());
            assertTrue(t.marked(0));
            assertTrue(t.marked(1));
            assertFalse(t.marked(2));
            assertFalse(t.marked(3));
            assertTrue(t.hasPathTo(1));
            assertFalse(t.hasPathTo(2));
            assertNull(t.pathTo(2), "不连通的顶点没有路径");
            assertNull(t.pathTo(3));
            assertEquals(-1, t.preOrderIndex(2), "未访问顶点没有前序下标");
            assertEquals(-1, t.postOrderIndex(2));
            assertEquals(-1, t.parent(2));
            assertArrayEquals(new int[]{0, 1}, t.preOrder());
        }

        @Test
        @DisplayName("孤立起点:只能到达自己")
        void isolatedSource() {
            UndirectedGraph g = new UndirectedGraph(3);
            DepthFirstTraversal t = new DepthFirstTraversal(g, 1);
            assertEquals(1, t.count());
            assertArrayEquals(new int[]{1}, t.preOrder());
            assertArrayEquals(new int[]{1}, t.postOrder());
            assertEquals(Arrays.asList(1), toList(t.pathTo(1)));
            assertEquals(0, t.depth(1));
        }

        @Test
        @DisplayName("0 顶点图没有合法起点")
        void zeroVertexGraph() {
            assertThrows(IllegalArgumentException.class, () -> new DepthFirstTraversal(new UndirectedGraph(0), 0));
        }
    }

    @Nested
    @DisplayName("递归实现与迭代实现逐顶点等价")
    class ModeConsistencyTest {

        @Test
        @DisplayName("默认构造是递归实现")
        void defaultMode() {
            DepthFirstTraversal t = new DepthFirstTraversal(tinyG(), 0);
            assertEquals(DepthFirstTraversal.Mode.RECURSIVE, t.mode());
            assertEquals(0, t.source());
        }

        @Test
        @DisplayName("tinyG 上两种实现的可达集、前序、后序、DFS 树、路径完全一致")
        void bothModesAgreeOnTinyG() {
            assertModesAgree(tinyG(), 0);
        }

        @Test
        @DisplayName("30 张随机图(含自环、平行边、多分量)两种实现完全一致")
        void bothModesAgreeOnRandomGraphs() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 1 + rnd.nextInt(12);
                int E = rnd.nextInt(25);
                UndirectedGraph g = new UndirectedGraph(V);
                for (int i = 0; i < E; i++) {
                    g.addEdge(rnd.nextInt(V), rnd.nextInt(V));
                }
                int source = rnd.nextInt(V);
                assertModesAgree(g, source);
            }
        }
    }

    @Nested
    @DisplayName("深图")
    class DeepGraphTest {

        @Test
        @DisplayName("迭代实现能处理 10 万层的链(递归实现受系统栈限制,不做断言)")
        void iterativeHandlesDeepChain() {
            final int n = 100_000;
            UndirectedGraph chain = new UndirectedGraph(n);
            for (int v = 0; v + 1 < n; v++) {
                chain.addEdge(v, v + 1);
            }
            DepthFirstTraversal t = new DepthFirstTraversal(chain, 0, DepthFirstTraversal.Mode.ITERATIVE);
            assertEquals(n, t.count());
            assertEquals(n - 1, t.depth(n - 1));
            assertEquals(n - 2, t.parent(n - 1), "链上父顶点是前一个顶点");
            List<Integer> path = toList(t.pathTo(n - 1));
            assertEquals(n, path.size());
            assertEquals(0, path.get(0).intValue());
            assertEquals(n - 1, path.get(n - 1).intValue());
            assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6, 7}, headOf(t.preOrder(), 8), "链上前序就是从 0 递增");
        }

        @Test
        @DisplayName("递归实现在数千层深度下正常(2000 层)")
        void recursiveHandlesModestDepth() {
            final int n = 2_000;
            UndirectedGraph chain = new UndirectedGraph(n);
            for (int v = 0; v + 1 < n; v++) {
                chain.addEdge(v, v + 1);
            }
            DepthFirstTraversal t = new DepthFirstTraversal(chain, 0, DepthFirstTraversal.Mode.RECURSIVE);
            assertEquals(n, t.count());
            assertEquals(n - 1, t.depth(n - 1));
        }
    }

    @Nested
    @DisplayName("参数校验")
    class ErrorTest {

        @Test
        @DisplayName("null 图 / null 方式 / 起点越界 抛 IllegalArgumentException")
        void constructorErrors() {
            UndirectedGraph g = tinyG();
            assertThrows(IllegalArgumentException.class, () -> new DepthFirstTraversal(null, 0));
            assertThrows(IllegalArgumentException.class, () -> new DepthFirstTraversal(g, 0, null));
            assertThrows(IllegalArgumentException.class, () -> new DepthFirstTraversal(g, -1));
            assertThrows(IllegalArgumentException.class, () -> new DepthFirstTraversal(g, 13));
        }

        @Test
        @DisplayName("查询越界顶点一律抛 IllegalArgumentException")
        void queryErrors() {
            DepthFirstTraversal t = new DepthFirstTraversal(tinyG(), 0);
            assertThrows(IllegalArgumentException.class, () -> t.marked(-1));
            assertThrows(IllegalArgumentException.class, () -> t.marked(13));
            assertThrows(IllegalArgumentException.class, () -> t.preOrderIndex(13));
            assertThrows(IllegalArgumentException.class, () -> t.postOrderIndex(-1));
            assertThrows(IllegalArgumentException.class, () -> t.parent(13));
            assertThrows(IllegalArgumentException.class, () -> t.depth(-1));
            assertThrows(IllegalArgumentException.class, () -> t.hasPathTo(13));
            assertThrows(IllegalArgumentException.class, () -> t.pathTo(13));
        }

        @Test
        @DisplayName("toString 含实现方式、起点与可达数")
        void toStringContent() {
            String s = new DepthFirstTraversal(tinyG(), 0).toString();
            assertTrue(s.contains("RECURSIVE"), s);
            assertTrue(s.contains("13/13"), s);
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /** 同一个图、同一个起点,两种实现的所有可观测量必须一致 */
    private static void assertModesAgree(UndirectedGraph g, int source) {
        DepthFirstTraversal recursive = new DepthFirstTraversal(g, source, DepthFirstTraversal.Mode.RECURSIVE);
        DepthFirstTraversal iterative = new DepthFirstTraversal(g, source, DepthFirstTraversal.Mode.ITERATIVE);

        assertEquals(recursive.count(), iterative.count(), "可达顶点数");
        assertArrayEquals(recursive.preOrder(), iterative.preOrder(), "前序");
        assertArrayEquals(recursive.postOrder(), iterative.postOrder(), "后序");
        for (int v = 0; v < g.V(); v++) {
            assertEquals(recursive.marked(v), iterative.marked(v), "marked(" + v + ")");
            assertEquals(recursive.parent(v), iterative.parent(v), "parent(" + v + ")");
            assertEquals(recursive.depth(v), iterative.depth(v), "depth(" + v + ")");
            assertEquals(recursive.preOrderIndex(v), iterative.preOrderIndex(v), "前序下标(" + v + ")");
            assertEquals(recursive.postOrderIndex(v), iterative.postOrderIndex(v), "后序下标(" + v + ")");
            Iterable<Integer> a = recursive.pathTo(v);
            Iterable<Integer> b = iterative.pathTo(v);
            if (a == null || b == null) {
                assertNull(a, "递归版路径为 null 时迭代版也应如此:顶点 " + v);
                assertNull(b, "迭代版路径为 null 时递归版也应如此:顶点 " + v);
            }
            else {
                assertEquals(toList(a), toList(b), "pathTo(" + v + ")");
            }
        }

        // 顺带核对不变式,让随机图也覆盖结构性质
        assertPostOrderIsPermutationOfReachable(g, recursive);
        assertSubtreeContiguity(g, recursive);
    }

    /** 后序是已访问顶点集的排列 */
    private static void assertPostOrderIsPermutationOfReachable(UndirectedGraph g, DepthFirstTraversal t) {
        TreeSet<Integer> reachable = new TreeSet<Integer>();
        for (int v = 0; v < g.V(); v++) {
            if (t.marked(v)) {
                reachable.add(v);
            }
        }
        assertEquals(reachable, toSet(t.postOrder()), "后序应是可达点集的排列");
        assertEquals(reachable, toSet(t.preOrder()), "前序应是可达点集的排列");
        assertEquals(t.count(), t.postOrder().length);
    }

    /**
     * DFS 子树在后序中连续:对每个可达顶点 v,其子树(所有 parent 链经过 v 的顶点)
     * 在后序序列里占一段连续区间,且 v 位于该区间末尾。
     */
    private static void assertSubtreeContiguity(UndirectedGraph g, DepthFirstTraversal t) {
        int[] post = t.postOrder();
        int[] position = new int[g.V()];
        Arrays.fill(position, -1);
        for (int i = 0; i < post.length; i++) {
            position[post[i]] = i;
        }
        for (int v = 0; v < g.V(); v++) {
            if (!t.marked(v)) {
                continue;
            }
            int min = Integer.MAX_VALUE;
            int max = -1;
            int size = 0;
            for (int u = 0; u < g.V(); u++) {
                if (!t.marked(u)) {
                    continue;
                }
                int x = u;
                while (x != -1 && x != v) {
                    x = t.parent(x);
                }
                if (x == v) {
                    min = Math.min(min, position[u]);
                    max = Math.max(max, position[u]);
                    size++;
                }
            }
            assertEquals(size, max - min + 1, "顶点 " + v + " 的子树在后序中应连续");
            assertEquals(position[v], max, "顶点 " + v + " 应是其子树在后序中的最后一个");
        }
    }

    /** 取数组前 n 个元素 */
    private static int[] headOf(int[] values, int n) {
        return Arrays.copyOf(values, n);
    }
}
