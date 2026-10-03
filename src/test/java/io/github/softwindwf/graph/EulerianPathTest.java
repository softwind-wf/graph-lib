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
 * {@link EulerianPath} 无向欧拉路径/回路测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li><b>走法合法性</b>(最重要的独立检查):返回的序列上相邻两点之间必有边,
 *       而且<b>每条边恰好被用一次</b>(平行边按重数、自环消耗两次)——
 *       用一个"边使用次数表"逐条核销;</li>
 *   <li>存在性条件:奇度顶点数 ∈ {0,2} 且非孤立顶点连通 —— 测试里独立重算一遍;</li>
 *   <li>回路时首尾相同、路径时首尾不同;起终点就是那两个奇度顶点;</li>
 *   <li>平行边、自环这些"多图"情形;</li>
 *   <li>规模:20 万顶点的链(有路径)。</li>
 * </ol>
 */
@DisplayName("EulerianPath 无向欧拉路径测试")
class EulerianPathTest {

    @Nested
    @DisplayName("基本情形")
    class BasicTest {

        @Test
        @DisplayName("三角形:有回路,走法用上全部 3 条边")
        void triangle() {
            UndirectedGraph graph = GraphGenerator.complete(3);
            EulerianPath euler = new EulerianPath(graph);
            assertTrue(euler.hasEulerianCycle());
            assertTrue(euler.hasEulerianPath());
            List<Integer> cycle = euler.cycle();
            assertEquals(4, cycle.size(), "3 条边 → 4 个顶点(首尾相同)");
            assertEquals(cycle.get(0), cycle.get(cycle.size() - 1));
            assertTrue(assertUsesEveryEdgeOnce(graph, cycle));
            assertNull(euler.path(), "有回路时没有'开走法'");
            assertEquals(euler.start(), euler.end());
        }

        @Test
        @DisplayName("链 0-1-2-3:有开路径,起终点是两个奇度顶点")
        void chain() {
            UndirectedGraph graph = GraphGenerator.path(4);
            EulerianPath euler = new EulerianPath(graph);
            assertFalse(euler.hasEulerianCycle());
            assertTrue(euler.hasEulerianPath());
            List<Integer> path = euler.path();
            assertEquals(java.util.Arrays.asList(0, 1, 2, 3), path);
            assertEquals(0, euler.start());
            assertEquals(3, euler.end());
            assertTrue(assertUsesEveryEdgeOnce(graph, path));
            assertNull(euler.cycle());
        }

        @Test
        @DisplayName("星形 K1,3:四个奇度顶点 → 无欧拉路径")
        void star() {
            UndirectedGraph graph = new UndirectedGraph(4);
            graph.addEdge(0, 1);
            graph.addEdge(0, 2);
            graph.addEdge(0, 3);
            EulerianPath euler = new EulerianPath(graph);
            assertFalse(euler.hasEulerianPath());
            assertFalse(euler.hasEulerianCycle());
            assertNull(euler.path());
            assertNull(euler.cycle());
            assertNull(euler.trail());
            assertEquals(-1, euler.start());
            assertEquals(-1, euler.end());
            assertEquals("EulerianPath: 无欧拉路径", euler.toString());
        }

        @Test
        @DisplayName("两个共享顶点的三角形:全偶度 → 有回路")
        void bowtie() {
            UndirectedGraph graph = new UndirectedGraph(5);
            graph.addEdge(0, 1);
            graph.addEdge(1, 2);
            graph.addEdge(2, 0);
            graph.addEdge(0, 3);
            graph.addEdge(3, 4);
            graph.addEdge(4, 0);
            EulerianPath euler = new EulerianPath(graph);
            assertTrue(euler.hasEulerianCycle());
            assertEquals(7, euler.cycle().size(), "6 条边 → 7 个顶点");
            assertTrue(assertUsesEveryEdgeOnce(graph, euler.cycle()));
        }

        @Test
        @DisplayName("平行边:0 与 1 之间有两条边 → 回路 [0,1,0]")
        void parallelEdges() {
            UndirectedGraph graph = new UndirectedGraph(2);
            graph.addEdge(0, 1);
            graph.addEdge(0, 1);
            EulerianPath euler = new EulerianPath(graph);
            assertTrue(euler.hasEulerianCycle());
            assertEquals(java.util.Arrays.asList(0, 1, 0), euler.cycle());
            assertTrue(assertUsesEveryEdgeOnce(graph, euler.cycle()));
        }

        @Test
        @DisplayName("自环:自环消耗两次度数,起点终点是那两个奇度顶点(0 与 1,顺序可变)")
        void selfLoop() {
            UndirectedGraph graph = new UndirectedGraph(2);
            graph.addEdge(0, 1);
            graph.addEdge(0, 0);
            EulerianPath euler = new EulerianPath(graph);
            assertTrue(euler.hasEulerianPath());
            assertEquals(3, graph.degree(0), "0 的度:0-1 一条 + 自环算 2");
            assertEquals(1, graph.degree(1));
            assertTrue((euler.start() == 0 && euler.end() == 1)
                            || (euler.start() == 1 && euler.end() == 0),
                    "两个奇度顶点就是起终点,当前 " + euler.start() + " → " + euler.end());
            assertTrue(assertUsesEveryEdgeOnce(graph, euler.path()));
            assertFalse(euler.hasEulerianCycle(), "有两个奇度顶点,没有回路");
        }

        @Test
        @DisplayName("不连通:有两块带边的子图 → 无解")
        void disconnected() {
            UndirectedGraph graph = new UndirectedGraph(6);
            graph.addEdge(0, 1);
            graph.addEdge(1, 2);
            graph.addEdge(2, 0);
            graph.addEdge(3, 4);
            graph.addEdge(4, 5);
            graph.addEdge(5, 3);
            assertFalse(new EulerianPath(graph).hasEulerianPath());
        }

        @Test
        @DisplayName("无边(全孤立)与单顶点:没有走法")
        void noEdges() {
            assertFalse(new EulerianPath(new UndirectedGraph(0)).hasEulerianPath());
            assertFalse(new EulerianPath(new UndirectedGraph(1)).hasEulerianPath());
            assertFalse(new EulerianPath(new UndirectedGraph(5)).hasEulerianPath());
        }

        @Test
        @DisplayName("参数校验:null 图")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new EulerianPath(null));
        }
    }

    @Nested
    @DisplayName("与独立条件对拍")
    class OracleTest {

        @Test
        @DisplayName("40 张随机图:存在性与'奇度条件 + 非孤立连通'一致;有解时走法必须合法")
        void agreesWithConditions() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 40; trial++) {
                UndirectedGraph graph = GraphGenerator.anyEdges(rnd, 1 + rnd.nextInt(8),
                        rnd.nextInt(14));
                EulerianPath euler = new EulerianPath(graph);
                boolean expected = independentCondition(graph);
                assertEquals(expected, euler.hasEulerianPath(),
                        "第 " + trial + " 张图:V=" + graph.V() + ", E=" + graph.E());
                assertEquals(expected && oddCount(graph) == 0, euler.hasEulerianCycle());
                if (expected) {
                    assertTrue(assertUsesEveryEdgeOnce(graph, euler.trail()),
                            "第 " + trial + " 张图的走法必须用上每条边恰好一次");
                }
            }
        }

        @Test
        @DisplayName("即使存在性成立,给出的走法也一定从合法的起点开始")
        void startVertexIsLegal() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 20; trial++) {
                UndirectedGraph graph = GraphGenerator.connected(rnd, 3 + rnd.nextInt(6), 3 + rnd.nextInt(8));
                EulerianPath euler = new EulerianPath(graph);
                if (!euler.hasEulerianPath()) {
                    continue;
                }
                int odd = oddCount(graph);
                if (odd == 2) {
                    assertEquals(1, graph.degree(euler.start()) % 2, "起点应是奇度顶点");
                    assertEquals(1, graph.degree(euler.end()) % 2, "终点应是奇度顶点");
                    assertTrue(euler.start() != euler.end());
                }
                else {
                    assertEquals(0, odd);
                    assertEquals(euler.start(), euler.end(), "全偶度时是回路,首尾相同");
                }
            }
        }
    }

    @Nested
    @DisplayName("规模与显示")
    class ScaleTest {

        @Test
        @DisplayName("20 万顶点的链:有欧拉路径,长度 = 边数 + 1")
        void largeChain() {
            int n = 200000;
            UndirectedGraph graph = GraphGenerator.path(n);
            EulerianPath euler = new EulerianPath(graph);
            assertTrue(euler.hasEulerianPath());
            assertFalse(euler.hasEulerianCycle());
            assertEquals(n, euler.path().size(), "边数 n−1,顶点序列长 n");
        }

        @Test
        @DisplayName("20 万顶点的环:有欧拉回路,长度 = 边数 + 1")
        void largeCycle() {
            int n = 200000;
            UndirectedGraph graph = GraphGenerator.cycle(n);
            EulerianPath euler = new EulerianPath(graph);
            assertTrue(euler.hasEulerianCycle());
            assertEquals(n + 1, euler.cycle().size());
        }

        @Test
        @DisplayName("toString 三种形态")
        void toStringContent() {
            assertTrue(new EulerianPath(GraphGenerator.complete(3)).toString().contains("有欧拉回路"));
            assertTrue(new EulerianPath(GraphGenerator.path(3)).toString().contains("有欧拉路径"));
            assertTrue(new EulerianPath(new UndirectedGraph(3)).toString().contains("无欧拉路径"));
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /** 奇度顶点数 */
    static int oddCount(UndirectedGraph graph) {
        int odd = 0;
        for (int v = 0; v < graph.V(); v++) {
            if (graph.degree(v) % 2 != 0) {
                odd++;
            }
        }
        return odd;
    }

    /** 独立重算存在性:奇度顶点数 ∈ {0,2} 且所有带边顶点连通 */
    static boolean independentCondition(UndirectedGraph graph) {
        int odd = oddCount(graph);
        if (odd != 0 && odd != 2) {
            return false;
        }
        int start = -1;
        for (int v = 0; v < graph.V(); v++) {
            if (graph.degree(v) > 0) {
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
        }
        for (int v = 0; v < graph.V(); v++) {
            if (graph.degree(v) > 0 && !visited[v]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 独立检查走法:相邻两点之间必须有边,并且<b>每条边的使用次数恰好等于它的重数</b>
     * (自环重数为 2)。用一张"已消耗的表"逐段核销。
     */
    static boolean assertUsesEveryEdgeOnce(UndirectedGraph graph, List<Integer> trail) {
        if (trail == null || trail.isEmpty()) {
            return false;
        }
        // 按"边"计数(自环也只算一条),与"走一次消耗一条边"对应
        Map<String, Integer> remaining = new HashMap<String, Integer>();
        for (int[] edge : graph.edges()) {
            String key = Math.min(edge[0], edge[1]) + "-" + Math.max(edge[0], edge[1]);
            Integer count = remaining.get(key);
            remaining.put(key, count == null ? 1 : count + 1);
        }
        if (trail.size() != graph.E() + 1) {
            return false;
        }
        for (int i = 0; i + 1 < trail.size(); i++) {
            int a = trail.get(i);
            int b = trail.get(i + 1);
            String key = Math.min(a, b) + "-" + Math.max(a, b);
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
