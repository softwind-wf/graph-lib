package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DirectedEulerianPath} 有向欧拉路径/回路测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>走法合法性:相邻两点之间必须有<b>该方向的弧</b>,且每条弧恰好用一次(平行弧按重数);</li>
 *   <li>存在性条件:入出度差(最多一个 +1、一个 −1,其余为 0)且所有带弧顶点弱连通 —— 测试里独立重算;</li>
 *   <li>回路时每个顶点入度 = 出度;路径时起终点恰是那两个不平衡顶点;</li>
 *   <li>有向图与无向图的差别:无向图看度数奇偶,有向图看入出度差 —— 例子对照;</li>
 *   <li>规模:20 万顶点的有向链/有向环。</li>
 * </ol>
 */
@DisplayName("DirectedEulerianPath 有向欧拉路径测试")
class DirectedEulerianPathTest {

    @Nested
    @DisplayName("基本情形")
    class BasicTest {

        @Test
        @DisplayName("有向三角形:有回路,用上全部 3 条弧")
        void directedTriangle() {
            Digraph graph = DigraphGenerator.cycle(3);
            DirectedEulerianPath euler = new DirectedEulerianPath(graph);
            assertTrue(euler.hasEulerianCycle());
            assertEquals(java.util.Arrays.asList(0, 1, 2, 0), euler.cycle());
            assertTrue(assertUsesEveryArcOnce(graph, euler.cycle()));
            assertEquals(euler.start(), euler.end());
        }

        @Test
        @DisplayName("有向链 0→1→2→3:有开走法,起点 0、终点 3")
        void directedChain() {
            Digraph graph = DigraphGenerator.path(4);
            DirectedEulerianPath euler = new DirectedEulerianPath(graph);
            assertTrue(euler.hasEulerianPath());
            assertFalse(euler.hasEulerianCycle());
            assertEquals(java.util.Arrays.asList(0, 1, 2, 3), euler.path());
            assertEquals(0, euler.start());
            assertEquals(3, euler.end());
            assertTrue(assertUsesEveryArcOnce(graph, euler.path()));
        }

        @Test
        @DisplayName("入出度差为 2 → 无解;方向不对的链也无解")
        void unbalanced() {
            Digraph graph = new Digraph(3);
            graph.addEdge(0, 1);
            graph.addEdge(0, 2);
            graph.addEdge(1, 2);
            DirectedEulerianPath euler = new DirectedEulerianPath(graph);
            assertFalse(euler.hasEulerianPath());
            assertNull(euler.trail());
            assertNull(euler.path());
            assertNull(euler.cycle());

            Digraph twoSources = new Digraph(4);
            twoSources.addEdge(0, 1);
            twoSources.addEdge(2, 3);
            assertFalse(new DirectedEulerianPath(twoSources).hasEulerianPath(), "两块互不相连");
        }

        @Test
        @DisplayName("有向图比无向图严格:0→1 与 2→1 出度差 +2 → 有向图无解")
        void directedIsStricterThanUndirected() {
            UndirectedGraph undirected = new UndirectedGraph(3);
            undirected.addEdge(0, 1);
            undirected.addEdge(2, 1);
            assertTrue(new EulerianPath(undirected).hasEulerianPath(), "无向图:0 与 2 是奇度顶点");

            Digraph directed = new Digraph(3);
            directed.addEdge(0, 1);
            directed.addEdge(2, 1);
            // 出度 0:1、2:1、1:0;入度 0:0、1:2、2:0
            // 出−入:0:+1、2:+1、1:−2 → 两个 +1、一个 −2,不满足"最多一个 +1、一个 −1"
            assertFalse(new DirectedEulerianPath(directed).hasEulerianPath());

            // 补一条 1→2 让度数平衡:出−入变成 0:+1、1:−1、2:0 → 有开走法
            directed.addEdge(1, 2);
            DirectedEulerianPath euler = new DirectedEulerianPath(directed);
            assertTrue(euler.hasEulerianPath());
            assertEquals(0, euler.start());
            assertEquals(1, euler.end(), "入度比出度多 1 的是 1 号顶点");
            assertTrue(assertUsesEveryArcOnce(directed, euler.path()));
            assertEquals(java.util.Arrays.asList(0, 1, 2, 1), euler.path());
        }

        @Test
        @DisplayName("弱连通但强不连通:有向环 + 一条只能进不能出的弧 → 有解/无解按度数判")
        void weaklyConnected() {
            Digraph graph = new Digraph(4);
            graph.addEdge(0, 1);
            graph.addEdge(1, 2);
            graph.addEdge(2, 0);
            graph.addEdge(0, 3);                          // 3 只有入边
            DirectedEulerianPath euler = new DirectedEulerianPath(graph);
            // 出度:0:2, 1:1, 2:1, 3:0;入度:0:1, 1:1, 2:1, 3:1
            // 出−入:0:+1(起点), 3:−1(终点), 其余 0 → 有开走法
            assertTrue(euler.hasEulerianPath());
            assertFalse(euler.hasEulerianCycle());
            assertEquals(0, euler.start());
            assertEquals(3, euler.end());
            assertTrue(assertUsesEveryArcOnce(graph, euler.path()));
        }

        @Test
        @DisplayName("平行弧:0→1 两条 → 回路 [0,1,0]?不,方向不同才算;两条同向无法闭合")
        void parallelArcs() {
            Digraph graph = new Digraph(2);
            graph.addEdge(0, 1);
            graph.addEdge(0, 1);
            DirectedEulerianPath euler = new DirectedEulerianPath(graph);
            // 出度 0:2、入度 1:2,差为 +2/−2 → 无解
            assertFalse(euler.hasEulerianPath());

            graph.addEdge(1, 0);
            graph.addEdge(1, 0);
            DirectedEulerianPath balanced = new DirectedEulerianPath(graph);
            assertTrue(balanced.hasEulerianCycle());
            assertTrue(assertUsesEveryArcOnce(graph, balanced.cycle()));
        }

        @Test
        @DisplayName("自环:计入入度与出度各一次,不影响平衡")
        void selfLoop() {
            Digraph graph = new Digraph(2);
            graph.addEdge(0, 1);
            graph.addEdge(1, 0);
            graph.addEdge(0, 0);
            DirectedEulerianPath euler = new DirectedEulerianPath(graph);
            assertTrue(euler.hasEulerianCycle());
            assertTrue(assertUsesEveryArcOnce(graph, euler.cycle()));
        }

        @Test
        @DisplayName("空图、单顶点、无边多顶点")
        void trivial() {
            assertFalse(new DirectedEulerianPath(new Digraph(0)).hasEulerianPath());
            assertFalse(new DirectedEulerianPath(new Digraph(1)).hasEulerianPath());
            assertFalse(new DirectedEulerianPath(new Digraph(5)).hasEulerianPath());
        }

        @Test
        @DisplayName("参数校验:null 图")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new DirectedEulerianPath(null));
        }
    }

    @Nested
    @DisplayName("与独立条件对拍")
    class OracleTest {

        @Test
        @DisplayName("50 张随机有向图:存在性与度数条件一致;有解时走法合法")
        void agreesWithConditions() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 50; trial++) {
                Digraph graph = DigraphGenerator.anyEdges(rnd, 1 + rnd.nextInt(8), rnd.nextInt(14));
                DirectedEulerianPath euler = new DirectedEulerianPath(graph);
                boolean expected = independentCondition(graph);
                assertEquals(expected, euler.hasEulerianPath(),
                        "第 " + trial + " 张图:V=" + graph.V() + ", E=" + graph.E());
                assertEquals(expected && isBalanced(graph), euler.hasEulerianCycle());
                if (expected) {
                    assertTrue(assertUsesEveryArcOnce(graph, euler.trail()),
                            "第 " + trial + " 张图给出的走法必须用上每条弧恰一次");
                }
            }
        }

        @Test
        @DisplayName("30 张随机有向环(V ≥ 3):一定能走成回路")
        void cyclesAlwaysWork() {
            Random rnd = new Random(4321L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 3 + rnd.nextInt(8);
                Digraph graph = DigraphGenerator.cycle(V);
                DirectedEulerianPath euler = new DirectedEulerianPath(graph);
                assertTrue(euler.hasEulerianCycle(), "有向环必然有欧拉回路");
                assertEquals(V + 1, euler.cycle().size());
                assertTrue(assertUsesEveryArcOnce(graph, euler.cycle()));
            }
        }
    }

    @Nested
    @DisplayName("规模与显示")
    class ScaleTest {

        @Test
        @DisplayName("20 万顶点的有向链与有向环")
        void large() {
            int n = 200000;
            Digraph chain = DigraphGenerator.path(n);
            DirectedEulerianPath euler = new DirectedEulerianPath(chain);
            assertTrue(euler.hasEulerianPath());
            assertFalse(euler.hasEulerianCycle());
            assertEquals(n, euler.path().size());

            Digraph cycle = DigraphGenerator.cycle(n);
            DirectedEulerianPath cyclic = new DirectedEulerianPath(cycle);
            assertTrue(cyclic.hasEulerianCycle());
            assertEquals(n + 1, cyclic.cycle().size());
        }

        @Test
        @DisplayName("toString 三种形态")
        void toStringContent() {
            assertTrue(new DirectedEulerianPath(DigraphGenerator.cycle(3)).toString()
                    .contains("有欧拉回路"));
            assertTrue(new DirectedEulerianPath(DigraphGenerator.path(3)).toString()
                    .contains("有欧拉路径"));
            assertTrue(new DirectedEulerianPath(new Digraph(3)).toString().contains("无欧拉路径"));
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /** 所有带弧顶点的入度 = 出度 */
    static boolean isBalanced(Digraph graph) {
        for (int v = 0; v < graph.V(); v++) {
            if (graph.inDegree(v) != graph.outDegree(v)) {
                return false;
            }
        }
        return true;
    }

    /** 独立重算存在性:度数条件 + 带弧顶点弱连通 */
    static boolean independentCondition(Digraph graph) {
        int surplus = 0;
        int deficit = 0;
        for (int v = 0; v < graph.V(); v++) {
            int difference = graph.outDegree(v) - graph.inDegree(v);
            if (difference == 1) {
                surplus++;
            }
            else if (difference == -1) {
                deficit++;
            }
            else if (difference != 0) {
                return false;
            }
        }
        if (!(surplus == 0 && deficit == 0) && !(surplus == 1 && deficit == 1)) {
            return false;
        }
        int start = -1;
        for (int v = 0; v < graph.V(); v++) {
            if (graph.outDegree(v) + graph.inDegree(v) > 0) {
                start = v;
                break;
            }
        }
        if (start < 0) {
            return false;
        }
        boolean[] visited = new boolean[graph.V()];
        java.util.Deque<Integer> stack = new java.util.ArrayDeque<Integer>();
        visited[start] = true;
        stack.push(start);
        while (!stack.isEmpty()) {
            int v = stack.pop();
            for (int w : graph.adj(v)) {
                if (!visited[w]) {
                    visited[w] = true;
                    stack.push(w);
                }
            }
            for (int w : graph.predecessors(v)) {
                if (!visited[w]) {
                    visited[w] = true;
                    stack.push(w);
                }
            }
        }
        for (int v = 0; v < graph.V(); v++) {
            if ((graph.outDegree(v) + graph.inDegree(v)) > 0 && !visited[v]) {
                return false;
            }
        }
        return true;
    }

    /** 独立检查走法:每段都必须是该方向的弧,且每条弧恰好被用一次 */
    static boolean assertUsesEveryArcOnce(Digraph graph, List<Integer> trail) {
        if (trail == null || trail.isEmpty()) {
            return false;
        }
        Map<String, Integer> remaining = new HashMap<String, Integer>();
        for (int v = 0; v < graph.V(); v++) {
            for (int w : graph.adj(v)) {
                String key = v + "->" + w;
                Integer count = remaining.get(key);
                remaining.put(key, count == null ? 1 : count + 1);
            }
        }
        if (trail.size() != graph.E() + 1) {
            return false;
        }
        for (int i = 0; i + 1 < trail.size(); i++) {
            String key = trail.get(i) + "->" + trail.get(i + 1);
            Integer count = remaining.get(key);
            if (count == null || count <= 0) {
                return false;
            }
            remaining.put(key, count - 1);
        }
        for (Integer count : remaining.values()) {
            if (count != 0) {
                return false;
            }
        }
        return true;
    }
}
