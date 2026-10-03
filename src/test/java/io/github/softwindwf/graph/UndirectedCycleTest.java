package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UndirectedCycle} 无向图环检测测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li><b>独立判据</b>:无向图有环 ⟺ {@code E ≥ V − 分量数}(森林恰好 {@code E = V − c})——
 *       与分量数交叉验证,完全不依赖找环算法;</li>
 *   <li>给出的环必须<b>真的存在</b>:相邻两点之间有边、闭合,自环与平行边两个特例;</li>
 *   <li>树无环;加一条边就有环;30 张随机图与判据一致;</li>
 *   <li>20 万顶点规模:链(无环)与环(有环),迭代实现不栈溢出。</li>
 * </ol>
 */
@DisplayName("UndirectedCycle 无向图环检测测试")
class UndirectedCycleTest {

    @Nested
    @DisplayName("基本情形")
    class BasicTest {

        @Test
        @DisplayName("树无环(6 个顶点、5 条边)")
        void tree() {
            UndirectedGraph tree = GraphGenerator.tree(new Random(1), 6);
            UndirectedCycle finder = new UndirectedCycle(tree);
            assertFalse(finder.hasCycle());
            assertNull(finder.cycle());
            assertNull(finder.cycleEdges());
            assertEquals(0, finder.cycleLength());
            assertEquals("UndirectedCycle: 无环", finder.toString());
        }

        @Test
        @DisplayName("三角形:环长 3,环是合法的")
        void triangle() {
            UndirectedGraph graph = new UndirectedGraph(3);
            graph.addEdge(0, 1);
            graph.addEdge(1, 2);
            graph.addEdge(2, 0);
            UndirectedCycle finder = new UndirectedCycle(graph);
            assertTrue(finder.hasCycle());
            assertEquals(3, finder.cycleLength());
            assertTrue(assertValidCycle(graph, finder.cycle()));
        }

        @Test
        @DisplayName("自环:返回单元素环 [v]")
        void selfLoop() {
            UndirectedGraph graph = new UndirectedGraph(3);
            graph.addEdge(0, 1);
            graph.addEdge(1, 1);
            UndirectedCycle finder = new UndirectedCycle(graph);
            assertTrue(finder.hasCycle());
            assertEquals(java.util.Arrays.asList(1), finder.cycle());
            assertTrue(assertValidCycle(graph, finder.cycle()));
        }

        @Test
        @DisplayName("平行边构成 2-环 —— 只按'是不是父顶点'判会漏掉这个特例")
        void parallelEdges() {
            UndirectedGraph graph = new UndirectedGraph(3);
            graph.addEdge(0, 1);
            graph.addEdge(0, 1);
            graph.addEdge(1, 2);
            UndirectedCycle finder = new UndirectedCycle(graph);
            assertTrue(finder.hasCycle());
            assertEquals(2, finder.cycleLength());
            assertEquals(java.util.Arrays.asList(0, 1), finder.cycle());
            assertTrue(assertValidCycle(graph, finder.cycle()));
        }

        @Test
        @DisplayName("树加一条边就有环;环可能很长")
        void treePlusOneEdge() {
            UndirectedGraph graph = GraphGenerator.path(10);
            assertFalse(new UndirectedCycle(graph).hasCycle());
            graph.addEdge(0, 9);
            UndirectedCycle finder = new UndirectedCycle(graph);
            assertTrue(finder.hasCycle());
            assertEquals(10, finder.cycleLength(), "整条路径 + 首尾相连 = 长度 10 的环");
            assertTrue(assertValidCycle(graph, finder.cycle()));
        }

        @Test
        @DisplayName("空图、单顶点、无边多顶点都无环")
        void trivial() {
            assertFalse(new UndirectedCycle(new UndirectedGraph(0)).hasCycle());
            assertFalse(new UndirectedCycle(new UndirectedGraph(1)).hasCycle());
            assertFalse(new UndirectedCycle(new UndirectedGraph(5)).hasCycle());
        }

        @Test
        @DisplayName("参数校验:null 图")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new UndirectedCycle(null));
        }
    }

    @Nested
    @DisplayName("与独立判据对拍")
    class OracleTest {

        @Test
        @DisplayName("40 张随机图:有环 ⟺ E > V − 分量数(森林恰好 E = V − 分量数)")
        void agreesWithForestFormula() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 40; trial++) {
                UndirectedGraph graph = GraphGenerator.anyEdges(rnd, 1 + rnd.nextInt(10),
                        rnd.nextInt(16));
                boolean expected = graph.E() > graph.V() - ConnectedComponentsTest.componentCount(graph);
                UndirectedCycle finder = new UndirectedCycle(graph);
                assertEquals(expected, finder.hasCycle(), "第 " + trial + " 张图:E=" + graph.E()
                        + ", V=" + graph.V() + ", 分量数=" + ConnectedComponentsTest.componentCount(graph));
                if (finder.hasCycle()) {
                    assertTrue(assertValidCycle(graph, finder.cycle()),
                            "第 " + trial + " 张图给出的环必须合法");
                }
            }
        }

        @Test
        @DisplayName("30 张随机简单图 + 随机树:树一定无环,简单图按公式判定")
        void agreesOnStructuredGraphs() {
            Random rnd = new Random(31415L);
            for (int trial = 0; trial < 30; trial++) {
                UndirectedGraph tree = GraphGenerator.tree(rnd, 2 + rnd.nextInt(12));
                assertFalse(new UndirectedCycle(tree).hasCycle(), "树不可能有环");

                UndirectedGraph simple = GraphGenerator.simple(rnd, 2 + rnd.nextInt(10), rnd.nextInt(20));
                boolean expected = simple.E() > simple.V()
                        - ConnectedComponentsTest.componentCount(simple);
                assertEquals(expected, new UndirectedCycle(simple).hasCycle());
            }
        }
    }

    @Nested
    @DisplayName("规模与显示")
    class ScaleTest {

        @Test
        @DisplayName("20 万顶点的链(无环)与链加一条闭合边(有环)")
        void large() {
            int n = 200000;
            UndirectedGraph chain = new UndirectedGraph(n);
            for (int v = 0; v + 1 < n; v++) {
                chain.addEdge(v, v + 1);
            }
            assertFalse(new UndirectedCycle(chain).hasCycle());

            chain.addEdge(n - 1, 0);
            UndirectedCycle finder = new UndirectedCycle(chain);
            assertTrue(finder.hasCycle());
            assertEquals(n, finder.cycleLength());
        }

        @Test
        @DisplayName("toString 有环/无环两种形态")
        void toStringContent() {
            UndirectedGraph graph = GraphGenerator.complete(3);
            assertTrue(new UndirectedCycle(graph).toString().startsWith("UndirectedCycle: 有环"));
            assertTrue(new UndirectedCycle(new UndirectedGraph(2)).toString().contains("无环"));
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /**
     * 独立检查器:环上的相邻两点之间必须有边,且每条边消耗的"条数"不超过实际存在的条数
     * (这样自环与平行边都能正确验证)。
     */
    static boolean assertValidCycle(UndirectedGraph graph, List<Integer> cycle) {
        if (cycle == null || cycle.isEmpty()) {
            return false;
        }
        if (cycle.size() == 1) {
            return graph.hasEdge(cycle.get(0), cycle.get(0));
        }
        for (int i = 0; i < cycle.size(); i++) {
            int from = cycle.get(i);
            int to = cycle.get((i + 1) % cycle.size());
            if (!graph.hasEdge(from, to)) {
                return false;
            }
        }
        if (cycle.size() == 2) {
            // 2-环必须由两条不同的平行边构成
            int count = 0;
            for (int w : graph.adj(cycle.get(0))) {
                if (w == cycle.get(1)) {
                    count++;
                }
            }
            return count >= 2;
        }
        return true;
    }
}
