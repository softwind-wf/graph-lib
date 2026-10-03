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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DirectedCycle} 有向环检测测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>无环图返回"无环",有环图返回一个<b>真正的环</b> —— 用独立检查器逐条验证
 *       "相邻两点之间确有这条弧"(含最后一条回到起点的弧)、且环上顶点互不重复;</li>
 *   <li>自环、二元环、多元环、平行边、多个环各自的情形;</li>
 *   <li>与 {@link TopologicalSort#hasCycle()} 在随机图上逐图交叉验证(两种判据必然一致);</li>
 *   <li>与 algs4 参考实现 {@code edu.princeton.cs.algs4.DirectedCycle} 对拍;</li>
 *   <li>迭代实现:20 万顶点的长链/长环不栈溢出。</li>
 * </ol>
 */
@DisplayName("DirectedCycle 有向环检测测试")
class DirectedCycleTest {

    @Nested
    @DisplayName("基本情形")
    class BasicTest {

        @Test
        @DisplayName("有向无环图:hasCycle 为 false,cycle 为 null")
        void acyclic() {
            DirectedCycle finder = new DirectedCycle(dagSample());
            assertFalse(finder.hasCycle());
            assertNull(finder.cycle());
            assertNull(finder.cycleList());
            assertEquals(0, finder.cycleLength());
            assertEquals("DirectedCycle: 无环", finder.toString());
        }

        @Test
        @DisplayName("自环:返回单元素环 [v]")
        void selfLoop() {
            Digraph graph = new Digraph(3);
            graph.addEdge(0, 1);
            graph.addEdge(1, 1);
            DirectedCycle finder = new DirectedCycle(graph);
            assertTrue(finder.hasCycle());
            assertEquals(1, finder.cycleLength());
            assertTrue(finder.cycle()[0] == 1);
            assertTrue(assertValidCycle(graph, finder.cycle()));
        }

        @Test
        @DisplayName("二元环与平行反向边:2→3、3→2 就是环")
        void twoCycle() {
            Digraph graph = new Digraph(4);
            graph.addEdge(0, 1);
            graph.addEdge(2, 3);
            graph.addEdge(3, 2);
            DirectedCycle finder = new DirectedCycle(graph);
            assertTrue(finder.hasCycle());
            assertEquals(2, finder.cycleLength());
            assertTrue(assertValidCycle(graph, finder.cycle()));
        }

        @Test
        @DisplayName("三元环与更长的环都能被找到,且环是合法的")
        void longerCycles() {
            Digraph graph = new Digraph(6);
            graph.addEdge(0, 1);
            graph.addEdge(1, 2);
            graph.addEdge(2, 0);
            graph.addEdge(3, 4);
            graph.addEdge(4, 5);
            graph.addEdge(5, 3);
            DirectedCycle finder = new DirectedCycle(graph);
            assertTrue(finder.hasCycle());
            assertEquals(3, finder.cycleLength());
            assertTrue(assertValidCycle(graph, finder.cycle()));
            assertNotNull(finder.cycleList());
        }

        @Test
        @DisplayName("平行边不构成环(2→3 出现两次仍无环)")
        void parallelEdgesAreNotACycle() {
            Digraph graph = new Digraph(4);
            graph.addEdge(0, 1);
            graph.addEdge(0, 1);
            graph.addEdge(1, 2);
            graph.addEdge(2, 3);
            assertFalse(new DirectedCycle(graph).hasCycle());
        }

        @Test
        @DisplayName("环在后面的分量里也能找到(不必从 0 出发)")
        void cycleInLaterComponent() {
            Digraph graph = new Digraph(6);
            graph.addEdge(0, 1);
            graph.addEdge(2, 3);
            graph.addEdge(3, 4);
            graph.addEdge(4, 2);
            DirectedCycle finder = new DirectedCycle(graph);
            assertTrue(finder.hasCycle());
            assertTrue(assertValidCycle(graph, finder.cycle()));
        }

        @Test
        @DisplayName("空图与单顶点图无环")
        void trivial() {
            assertFalse(new DirectedCycle(new Digraph(0)).hasCycle());
            assertFalse(new DirectedCycle(new Digraph(1)).hasCycle());
        }

        @Test
        @DisplayName("参数校验:null 图")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new DirectedCycle(null));
        }
    }

    @Nested
    @DisplayName("与其它判据/实现交叉验证")
    class CrossCheckTest {

        @Test
        @DisplayName("40 张随机有向图:本类结论与 TopologicalSort.hasCycle 完全一致")
        void agreesWithTopologicalSort() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 40; trial++) {
                Digraph graph = randomDigraph(rnd, 2 + rnd.nextInt(8), rnd.nextInt(14));
                boolean mine = new DirectedCycle(graph).hasCycle();
                boolean other = new TopologicalSort(graph).hasCycle();
                assertEquals(other, mine, "第 " + trial + " 张图:两种判据必须一致");
                if (mine) {
                    assertTrue(assertValidCycle(graph, new DirectedCycle(graph).cycle()),
                            "第 " + trial + " 张图:给出的环必须合法");
                }
            }
        }

        @Test
        @DisplayName("40 张随机有向图:与 algs4 参考实现对拍")
        void agreesWithReference() {
            Random rnd = new Random(4242L);
            for (int trial = 0; trial < 40; trial++) {
                Digraph graph = randomDigraph(rnd, 2 + rnd.nextInt(8), rnd.nextInt(14));
                edu.princeton.cs.algs4.Digraph reference = new edu.princeton.cs.algs4.Digraph(graph.V());
                for (int[] edge : graph.edges()) {
                    reference.addEdge(edge[0], edge[1]);
                }
                boolean expected = new edu.princeton.cs.algs4.DirectedCycle(reference).hasCycle();
                assertEquals(expected, new DirectedCycle(graph).hasCycle(),
                        "第 " + trial + " 张图:与 algs4 结论不一致");
            }
        }
    }

    @Nested
    @DisplayName("规模与实现")
    class ScaleTest {

        @Test
        @DisplayName("20 万顶点的长链:无环(迭代实现不栈溢出)")
        void longChain() {
            int n = 200000;
            Digraph graph = new Digraph(n);
            for (int v = 0; v + 1 < n; v++) {
                graph.addEdge(v, v + 1);
            }
            assertFalse(new DirectedCycle(graph).hasCycle());
        }

        @Test
        @DisplayName("20 万顶点的长环:有环且环长等于顶点数")
        void longCycle() {
            int n = 200000;
            Digraph graph = new Digraph(n);
            for (int v = 0; v < n; v++) {
                graph.addEdge(v, (v + 1) % n);
            }
            DirectedCycle finder = new DirectedCycle(graph);
            assertTrue(finder.hasCycle());
            assertEquals(n, finder.cycleLength());
            assertTrue(assertValidCycle(graph, finder.cycle()));
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /** 一个常见的有向无环样例:0→1/0→2,1→3,2→3,3→4,4→5 */
    static Digraph dagSample() {
        Digraph graph = new Digraph(6);
        int[][] edges = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {3, 4}, {4, 5}};
        for (int[] edge : edges) {
            graph.addEdge(edge[0], edge[1]);
        }
        return graph;
    }

    /**
     * 独立检查器:验证 {@code cycle} 确实构成一个环 ——
     * 相邻两点之间必须有这条弧(含最后一条回到起点的弧),且环上顶点互不重复。
     *
     * @return 是否合法
     */
    static boolean assertValidCycle(Digraph graph, int[] cycle) {
        if (cycle == null || cycle.length == 0) {
            return false;
        }
        Set<Integer> seen = new HashSet<Integer>();
        for (int i = 0; i < cycle.length; i++) {
            int from = cycle[i];
            int to = cycle[(i + 1) % cycle.length];
            if (!graph.hasEdge(from, to)) {
                return false;
            }
            if (!seen.add(from)) {
                return false;                       // 顶点重复出现,不是一个简单环
            }
        }
        return true;
    }

    /** 随机有向图(允许平行边与自环) */
    static Digraph randomDigraph(Random rnd, int V, int E) {
        return DigraphGenerator.anyEdges(rnd, V, E);
    }
}
