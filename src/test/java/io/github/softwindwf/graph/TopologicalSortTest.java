package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TopologicalSort} 拓扑排序测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li><b>拓扑序的定义性质</b>(与算法无关的判据):序列是全部顶点的排列,且每条边
 *       {@code u -> v} 都满足 "u 的位置 &lt; v 的位置";</li>
 *   <li>三种实现(Kahn 队列 / Kahn 最小堆 / DFS 逆后序)给出的序列都合法;</li>
 *   <li>{@link TopologicalSort.Mode#KAHN_LEX} 的"字典序最小"用<b>暴力枚举</b>独立验证
 *       (小图上枚举全部排列取第一个合法者);</li>
 *   <li>回路检测:三种实现结论一致({@code isDag} 相同),给出的回路真实存在
 *       (相邻有边、首尾相接、顶点不重复);</li>
 *   <li>随机 DAG(按隐藏顺序只加前向边)必然有解;随机有向图则两种情形都会出现;</li>
 *   <li>边界:自环、平行边、多分量、10 万顶点深链(迭代实现不爆栈)、0/1 顶点。</li>
 * </ol>
 */
@DisplayName("TopologicalSort 拓扑排序测试")
class TopologicalSortTest {

    /** 教材/演示用的小 DAG:0→5、0→2、0→1、3→6、3→5、3→4、5→2、6→4、6→0、3→2、1→4 */
    private static Digraph smallDag() {
        Digraph g = new Digraph(7);
        int[][] edges = {{0, 5}, {0, 2}, {0, 1}, {3, 6}, {3, 5}, {3, 4}, {5, 2}, {6, 4}, {6, 0}, {3, 2}, {1, 4}};
        for (int[] e : edges) {
            g.addEdge(e[0], e[1]);
        }
        return g;
    }

    @Nested
    @DisplayName("拓扑序的定义性质")
    class ValidityTest {

        @Test
        @DisplayName("三种实现的序列都是合法拓扑序")
        void allModesProduceValidOrders() {
            Digraph g = smallDag();
            for (TopologicalSort.Mode mode : TopologicalSort.Mode.values()) {
                TopologicalSort ts = new TopologicalSort(g, mode);
                assertTrue(ts.isDag(), mode + " 应判定为无回路");
                assertFalse(ts.hasCycle());
                assertNull(ts.cycle());
                assertValidOrder(g, ts.order(), mode.toString());
            }
        }

        @Test
        @DisplayName("3 号是唯一入度为 0 的顶点,必然排在最前")
        void firstVertex() {
            Digraph g = smallDag();
            assertEquals(0, g.inDegree(3));
            for (int v = 0; v < g.V(); v++) {
                if (v != 3) {
                    assertTrue(g.inDegree(v) > 0, "除 3 之外都应有先修");
                }
            }
            TopologicalSort ts = new TopologicalSort(g);
            assertValidOrder(g, ts.order(), "KAHN");
            assertEquals(0, ts.positionOf(3), "唯一的入度 0 顶点必然排在最前");
        }

        @Test
        @DisplayName("positionOf 与 order 自洽,越界被拒绝")
        void positions() {
            Digraph g = smallDag();
            TopologicalSort ts = new TopologicalSort(g);
            int[] order = ts.order();
            for (int index = 0; index < order.length; index++) {
                assertEquals(index, ts.positionOf(order[index]));
            }
            assertThrows(IllegalArgumentException.class, () -> ts.positionOf(-1));
            assertThrows(IllegalArgumentException.class, () -> ts.positionOf(7));
        }

        @Test
        @DisplayName("order() 返回副本,外部改动不影响内部")
        void orderIsCopied() {
            TopologicalSort ts = new TopologicalSort(smallDag());
            int[] first = ts.order();
            first[0] = 99;
            assertFalse(ts.order()[0] == 99, "order() 每次都应是新副本");
            assertValidOrder(smallDag(), ts.order(), "KAHN");
        }

        @Test
        @DisplayName("课程 AOV 网:先修关系全部满足")
        void courseOrder() {
            AOVNetwork net = new AOVNetwork(new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9"});
            net.addPrecedence("C1", "C3");
            net.addPrecedence("C2", "C3");
            net.addPrecedence("C3", "C4");
            net.addPrecedence("C2", "C4");
            net.addPrecedence("C2", "C5");
            net.addPrecedence("C4", "C6");
            net.addPrecedence("C5", "C6");
            net.addPrecedence("C4", "C7");
            net.addPrecedence("C9", "C7");
            net.addPrecedence("C1", "C8");
            net.addPrecedence("C8", "C9");

            Digraph graph = net.toDigraph();
            for (TopologicalSort.Mode mode : TopologicalSort.Mode.values()) {
                TopologicalSort ts = new TopologicalSort(graph, mode);
                assertTrue(ts.isDag());
                assertValidOrder(graph, ts.order(), mode.toString());
                List<String> names = net.namesOf(ts.order());
                assertEquals(9, names.size());
                // 用名称再核对一遍先修关系,顺便验证 namesOf 的映射
                assertTrue(names.indexOf("C1") < names.indexOf("C3"));
                assertTrue(names.indexOf("C2") < names.indexOf("C4"));
                assertTrue(names.indexOf("C3") < names.indexOf("C4"));
                assertTrue(names.indexOf("C4") < names.indexOf("C6"));
                assertTrue(names.indexOf("C5") < names.indexOf("C6"));
                assertTrue(names.indexOf("C8") < names.indexOf("C9"));
                assertTrue(names.indexOf("C9") < names.indexOf("C7"));
            }
        }
    }

    @Nested
    @DisplayName("字典序最小(与暴力枚举对拍)")
    class LexicographicTest {

        @Test
        @DisplayName("小 DAG:最小堆版等于暴力枚举出的字典序最小拓扑序")
        void lexicographicMatchesBruteForce() {
            Digraph g = smallDag();
            TopologicalSort lex = new TopologicalSort(g, TopologicalSort.Mode.KAHN_LEX);
            int[] brute = bruteForceLexicographicSmallest(g);
            assertNotNull(brute);
            assertArrayEquals(brute, lex.order(), "字典序最小拓扑序");
        }

        @Test
        @DisplayName("20 张随机 DAG:最小堆版都等于暴力枚举结果")
        void lexicographicOnRandomDags() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 3 + rnd.nextInt(4);              // 3..6,排列数最多 720
                Digraph g = randomDag(rnd, V, rnd.nextInt(8));
                int[] brute = bruteForceLexicographicSmallest(g);
                assertNotNull(brute, "随机 DAG 必有拓扑序");
                int[] actual = new TopologicalSort(g, TopologicalSort.Mode.KAHN_LEX).order();
                assertArrayEquals(brute, actual, "第 " + trial + " 张图的字典序最小拓扑序");
                // 另外两种实现只要合法即可(不要求最小)
                assertValidOrder(g, new TopologicalSort(g, TopologicalSort.Mode.KAHN).order(), "KAHN");
                assertValidOrder(g, new TopologicalSort(g, TopologicalSort.Mode.DFS).order(), "DFS");
            }
        }

        @Test
        @DisplayName("全无关的图:字典序最小就是 0,1,2,…")
        void independentVertices() {
            Digraph g = new Digraph(5);
            assertArrayEquals(new int[]{0, 1, 2, 3, 4},
                    new TopologicalSort(g, TopologicalSort.Mode.KAHN_LEX).order());
            assertArrayEquals(new int[]{0, 1, 2, 3, 4},
                    new TopologicalSort(g, TopologicalSort.Mode.KAHN).order());
        }
    }

    @Nested
    @DisplayName("回路检测")
    class CycleTest {

        @Test
        @DisplayName("自环:最短的回路")
        void selfLoop() {
            Digraph g = new Digraph(3);
            g.addEdge(0, 1);
            g.addEdge(1, 1);
            for (TopologicalSort.Mode mode : TopologicalSort.Mode.values()) {
                TopologicalSort ts = new TopologicalSort(g, mode);
                assertFalse(ts.isDag(), mode + " 应判定有回路");
                assertTrue(ts.hasCycle());
                assertNull(ts.order());
                assertEquals(-1, ts.positionOf(0));
                assertArrayEquals(new int[]{1}, ts.cycle());
            }
        }

        @Test
        @DisplayName("二元环与三元环:回路真实存在")
        void simpleCycles() {
            Digraph two = new Digraph(3);
            two.addEdge(0, 1);
            two.addEdge(1, 0);
            two.addEdge(1, 2);
            for (TopologicalSort.Mode mode : TopologicalSort.Mode.values()) {
                TopologicalSort ts = new TopologicalSort(two, mode);
                assertFalse(ts.isDag());
                assertCycleIsValid(two, ts.cycle(), mode.toString());
            }

            Digraph three = new Digraph(4);
            three.addEdge(0, 1);
            three.addEdge(1, 2);
            three.addEdge(2, 0);
            three.addEdge(2, 3);
            for (TopologicalSort.Mode mode : TopologicalSort.Mode.values()) {
                TopologicalSort ts = new TopologicalSort(three, mode);
                assertFalse(ts.isDag());
                assertCycleIsValid(three, ts.cycle(), mode.toString());
            }
        }

        @Test
        @DisplayName("回路之外还有可排序的顶点时,仍然报告不可行")
        void partialDagWithCycle() {
            Digraph g = new Digraph(5);
            g.addEdge(0, 1);            // 0→1 正常
            g.addEdge(2, 3);            // 2→3→4→2 成环
            g.addEdge(3, 4);
            g.addEdge(4, 2);
            for (TopologicalSort.Mode mode : TopologicalSort.Mode.values()) {
                TopologicalSort ts = new TopologicalSort(g, mode);
                assertFalse(ts.isDag(), mode + ":只要有一处回路,整体就无拓扑序");
                assertNull(ts.order());
                assertCycleIsValid(g, ts.cycle(), mode.toString());
            }
        }

        @Test
        @DisplayName("平行边不改变结论:平行边 + 回路仍被识别")
        void parallelEdgesWithCycle() {
            Digraph g = new Digraph(3);
            g.addEdge(0, 1);
            g.addEdge(0, 1);
            g.addEdge(1, 2);
            g.addEdge(2, 0);
            TopologicalSort ts = new TopologicalSort(g);
            assertFalse(ts.isDag());
            assertCycleIsValid(g, ts.cycle(), "KAHN");

            Digraph acyclic = new Digraph(3);
            acyclic.addEdge(0, 1);
            acyclic.addEdge(0, 1);
            acyclic.addEdge(1, 2);
            assertArrayEquals(new int[]{0, 1, 2}, new TopologicalSort(acyclic).order());
        }

        @Test
        @DisplayName("随机有向图:三种实现结论一致;有解时序列合法,无解时回路合法")
        void randomGraphs() {
            Random rnd = new Random(4242L);
            int acyclic = 0;
            int cyclic = 0;
            for (int trial = 0; trial < 60; trial++) {
                int V = 2 + rnd.nextInt(8);
                Digraph g = new Digraph(V);
                int edges = rnd.nextInt(15);
                for (int i = 0; i < edges; i++) {
                    g.addEdge(rnd.nextInt(V), rnd.nextInt(V));
                }
                boolean expected = new TopologicalSort(g, TopologicalSort.Mode.KAHN).isDag();
                for (TopologicalSort.Mode mode : TopologicalSort.Mode.values()) {
                    TopologicalSort ts = new TopologicalSort(g, mode);
                    assertEquals(expected, ts.isDag(), "第 " + trial + " 张图 " + mode + " 的结论不一致");
                    if (expected) {
                        assertValidOrder(g, ts.order(), mode.toString());
                    }
                    else {
                        assertNull(ts.order());
                        assertCycleIsValid(g, ts.cycle(), mode.toString());
                    }
                }
                if (expected) {
                    acyclic++;
                }
                else {
                    cyclic++;
                }
            }
            assertTrue(acyclic > 0 && cyclic > 0, "随机图应两种情形都出现:无环 " + acyclic + ", 有环 " + cyclic);
        }
    }

    @Nested
    @DisplayName("边界与规模")
    class EdgeCaseTest {

        @Test
        @DisplayName("0 顶点、1 顶点、无边图")
        void trivialGraphs() {
            TopologicalSort empty = new TopologicalSort(new Digraph(0));
            assertTrue(empty.isDag());
            assertArrayEquals(new int[0], empty.order());
            assertNull(empty.cycle());

            TopologicalSort single = new TopologicalSort(new Digraph(1));
            assertArrayEquals(new int[]{0}, single.order());

            TopologicalSort noEdges = new TopologicalSort(new Digraph(4));
            assertValidOrder(new Digraph(4), noEdges.order(), "无边图");
            assertEquals(4, noEdges.order().length);
        }

        @Test
        @DisplayName("10 万顶点的深链:Kahn 与 DFS(迭代)都能处理,顺序就是 0..n-1")
        void largeChain() {
            int n = 100_000;
            Digraph g = new Digraph(n);
            for (int v = 0; v + 1 < n; v++) {
                g.addEdge(v, v + 1);
            }
            TopologicalSort kahn = new TopologicalSort(g, TopologicalSort.Mode.KAHN);
            assertTrue(kahn.isDag());
            assertEquals(0, kahn.order()[0]);
            assertEquals(n - 1, kahn.order()[n - 1]);

            TopologicalSort dfs = new TopologicalSort(g, TopologicalSort.Mode.DFS);
            assertTrue(dfs.isDag());
            assertEquals(0, dfs.order()[0], "逆后序在链上就是从 0 递增");
            assertEquals(n - 1, dfs.order()[n - 1]);
        }

        @Test
        @DisplayName("多连通分量:每片各自排序,整体仍是合法拓扑序")
        void multipleComponents() {
            Digraph g = new Digraph(6);
            g.addEdge(0, 1);
            g.addEdge(2, 3);
            g.addEdge(4, 5);
            for (TopologicalSort.Mode mode : TopologicalSort.Mode.values()) {
                TopologicalSort ts = new TopologicalSort(g, mode);
                assertTrue(ts.isDag());
                assertValidOrder(g, ts.order(), mode.toString());
            }
        }

        @Test
        @DisplayName("参数校验与 toString")
        void validationAndToString() {
            Digraph g = smallDag();
            assertThrows(IllegalArgumentException.class, () -> new TopologicalSort(null));
            assertThrows(IllegalArgumentException.class, () -> new TopologicalSort(g, null));
            assertEquals(TopologicalSort.Mode.KAHN, new TopologicalSort(g).mode());
            assertEquals(7, new TopologicalSort(g).V());

            String dagText = new TopologicalSort(g).toString();
            assertTrue(dagText.contains("有拓扑序=true"), dagText);
            assertTrue(dagText.contains("KAHN"), dagText);

            Digraph cyclic = new Digraph(2);
            cyclic.addEdge(0, 1);
            cyclic.addEdge(1, 0);
            String cycleText = new TopologicalSort(cyclic).toString();
            assertTrue(cycleText.contains("有拓扑序=false"), cycleText);
            assertTrue(cycleText.contains("回路"), cycleText);
        }
    }

    // ------------------------------------------------------------------
    // 独立验证工具
    // ------------------------------------------------------------------

    /**
     * 拓扑序的<b>定义性质</b>(不依赖任何排序算法):序列是 0..V-1 的排列,
     * 且对每条边 {@code u -> v} 都有 u 排在 v 之前。
     */
    private static void assertValidOrder(Digraph g, int[] order, String what) {
        assertNotNull(order, what + " 应有拓扑序");
        assertEquals(g.V(), order.length, what + " 序列长度应等于顶点数");

        Set<Integer> seen = new TreeSet<Integer>();
        int[] position = new int[g.V()];
        Arrays.fill(position, -1);
        for (int index = 0; index < order.length; index++) {
            assertTrue(order[index] >= 0 && order[index] < g.V(), what + " 序列含非法顶点");
            assertTrue(seen.add(order[index]), what + " 序列含重复顶点");
            position[order[index]] = index;
        }
        assertEquals(g.V(), seen.size(), what + " 序列应是全部顶点的排列");

        for (int[] e : g.edges()) {
            assertTrue(position[e[0]] < position[e[1]],
                    what + ":边 " + e[0] + "->" + e[1] + " 的起点没有排在终点之前");
        }
    }

    /** 回路合法性:顶点互不重复、相邻顶点之间(含末尾接回首顶点)都有边 */
    private static void assertCycleIsValid(Digraph g, int[] cycle, String what) {
        assertNotNull(cycle, what + " 应给出一个回路");
        assertTrue(cycle.length >= 1, what + " 回路至少一个顶点(自环)");
        Set<Integer> distinct = new HashSet<Integer>();
        for (int v : cycle) {
            assertTrue(distinct.add(v), what + " 回路顶点不应重复");
        }
        for (int index = 0; index < cycle.length; index++) {
            int from = cycle[index];
            int to = cycle[(index + 1) % cycle.length];
            assertTrue(g.hasEdge(from, to), what + ":回路里缺少边 " + from + "->" + to);
        }
    }

    /**
     * 暴力枚举字典序最小的拓扑序:生成 0..V-1 的全部排列(按字典序),
     * 返回第一个满足"所有边都从前往后"的排列。只用于小图(V ≤ 7)当独立参照物。
     */
    private static int[] bruteForceLexicographicSmallest(Digraph g) {
        int V = g.V();
        int[] permutation = new int[V];
        for (int i = 0; i < V; i++) {
            permutation[i] = i;
        }
        List<int[]> all = new ArrayList<int[]>();
        permute(permutation, 0, all);
        all.sort((a, b) -> {
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) {
                    return Integer.compare(a[i], b[i]);
                }
            }
            return 0;
        });
        for (int[] candidate : all) {
            if (isValidOrder(g, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static void permute(int[] values, int start, List<int[]> out) {
        if (start == values.length) {
            out.add(Arrays.copyOf(values, values.length));
            return;
        }
        for (int i = start; i < values.length; i++) {
            swap(values, start, i);
            permute(values, start + 1, out);
            swap(values, start, i);
        }
    }

    private static void swap(int[] values, int i, int j) {
        int tmp = values[i];
        values[i] = values[j];
        values[j] = tmp;
    }

    private static boolean isValidOrder(Digraph g, int[] order) {
        int[] position = new int[g.V()];
        for (int index = 0; index < order.length; index++) {
            position[order[index]] = index;
        }
        for (int[] e : g.edges()) {
            if (position[e[0]] >= position[e[1]]) {
                return false;
            }
        }
        return true;
    }

    /** 随机 DAG:交给 {@link DigraphGenerator#dag}(只从小号指向大号,必然无环) */
    private static Digraph randomDag(Random rnd, int V, int edgeCount) {
        return DigraphGenerator.dag(rnd, V, edgeCount);
    }

    private static int position(int[] order, int value) {
        for (int i = 0; i < order.length; i++) {
            if (order[i] == value) {
                return i;
            }
        }
        return -1;
    }
}
