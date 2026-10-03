package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StronglyConnectedComponents} 强连通分量测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li><b>定义级验证</b>:用一趟独立可达性搜索(BFS)算出"互相可达"关系,
 *       逐对检查"同属一个分量 ⟺ 互相可达" —— 这就是强连通分量的定义,与算法无关;</li>
 *   <li>Kosaraju 与 Tarjan 两种实现给出<b>同一个划分</b>(分量编号含义不同,只比划分);</li>
 *   <li>与 algs4 参考实现 {@code KosarajuSharirSCC} 对拍;</li>
 *   <li>缩点图必须无环:不同分量之间不可能双向可达;</li>
 *   <li>已知样例(有向无环图、单个环、两个环用单向边相连、tinyDG 样例);</li>
 *   <li>迭代实现:20 万顶点的长环不栈溢出。</li>
 * </ol>
 */
@DisplayName("StronglyConnectedComponents 强连通分量测试")
class StronglyConnectedComponentsTest {

    private static final StronglyConnectedComponents.Mode[] MODES = {
        StronglyConnectedComponents.Mode.KOSARAJU,
        StronglyConnectedComponents.Mode.TARJAN
    };

    @Nested
    @DisplayName("已知样例")
    class KnownCaseTest {

        @Test
        @DisplayName("有向无环图:每个顶点自成一个分量")
        void dag() {
            Digraph graph = DirectedCycleTest.dagSample();
            for (StronglyConnectedComponents.Mode mode : MODES) {
                StronglyConnectedComponents scc = new StronglyConnectedComponents(graph, mode);
                assertEquals(6, scc.count());
                assertEquals(1, scc.largestComponentSize());
                assertFalse(scc.isStronglyConnected());
                for (int v = 0; v < 6; v++) {
                    assertEquals(Arrays.asList(v), scc.component(scc.id(v)));
                }
            }
        }

        @Test
        @DisplayName("一个长环:整张图就是一个分量")
        void singleCycle() {
            Digraph graph = new Digraph(7);
            for (int v = 0; v < 7; v++) {
                graph.addEdge(v, (v + 1) % 7);
            }
            for (StronglyConnectedComponents.Mode mode : MODES) {
                StronglyConnectedComponents scc = new StronglyConnectedComponents(graph, mode);
                assertEquals(1, scc.count());
                assertEquals(7, scc.largestComponentSize());
                assertTrue(scc.isStronglyConnected());
                assertTrue(scc.stronglyConnected(0, 5));
                assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6), scc.component(scc.id(3)));
            }
        }

        @Test
        @DisplayName("两个环 + 一条单向边:两个分量(单向可达不等于强连通)")
        void twoCyclesJoined() {
            Digraph graph = new Digraph(4);
            graph.addEdge(0, 1);
            graph.addEdge(1, 0);        // 分量 A = {0,1}
            graph.addEdge(2, 3);
            graph.addEdge(3, 2);        // 分量 B = {2,3}
            graph.addEdge(1, 2);        // A → B 单向
            for (StronglyConnectedComponents.Mode mode : MODES) {
                StronglyConnectedComponents scc = new StronglyConnectedComponents(graph, mode);
                assertEquals(2, scc.count());
                assertTrue(scc.stronglyConnected(0, 1));
                assertTrue(scc.stronglyConnected(2, 3));
                assertFalse(scc.stronglyConnected(0, 2), "0 能到 2,但 2 到不了 0");
                assertEquals(2, scc.largestComponentSize());
            }
        }

        @Test
        @DisplayName("自环不合并分量(单顶点仍是独立分量,只是有环)")
        void selfLoop() {
            Digraph graph = new Digraph(3);
            graph.addEdge(0, 0);
            graph.addEdge(1, 2);
            StronglyConnectedComponents scc = new StronglyConnectedComponents(graph);
            assertEquals(3, scc.count());
            assertEquals(Arrays.asList(0), scc.component(scc.id(0)));
        }

        @Test
        @DisplayName("tinyDG 样例(13 顶点 22 弧):3 个分量,最大的含 10 个顶点")
        void tinyDigraph() {
            Digraph graph = StronglyConnectedComponents.tinyDigraph();
            assertEquals(13, graph.V());
            assertEquals(22, graph.E());
            for (StronglyConnectedComponents.Mode mode : MODES) {
                StronglyConnectedComponents scc = new StronglyConnectedComponents(graph, mode);
                assertEquals(3, scc.count());
                assertEquals(10, scc.largestComponentSize());
                assertEquals(Arrays.asList(
                        Arrays.asList(0, 2, 3, 4, 5, 6, 9, 10, 11, 12),
                        Arrays.asList(1),
                        Arrays.asList(7, 8)), scc.components());
            }
        }
    }

    @Nested
    @DisplayName("定义级验证:同分量 ⟺ 互相可达")
    class DefinitionTest {

        @Test
        @DisplayName("30 张随机有向图:逐对检查'同分量 ⟺ 互相可达'")
        void matchesMutualReachability() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 30; trial++) {
                Digraph graph = DirectedCycleTest.randomDigraph(rnd, 2 + rnd.nextInt(7), rnd.nextInt(12));
                StronglyConnectedComponents scc = new StronglyConnectedComponents(graph);

                boolean[][] reach = reachability(graph);
                for (int v = 0; v < graph.V(); v++) {
                    assertTrue(reach[v][v], "顶点总应能到达自己");
                    for (int w = 0; w < graph.V(); w++) {
                        boolean mutual = reach[v][w] && reach[w][v];
                        assertEquals(mutual, scc.stronglyConnected(v, w),
                                "第 " + trial + " 张图:" + v + " 与 " + w + " 的判定不一致");
                    }
                }
                // 分量大小与顶点数一致
                int total = 0;
                for (int c = 0; c < scc.count(); c++) {
                    total += scc.size(c);
                    assertEquals(scc.size(c), scc.component(c).size());
                }
                assertEquals(graph.V(), total);
            }
        }

        @Test
        @DisplayName("两种实现给出同一个划分;缩点图无环")
        void modesAgreeAndCondensationIsAcyclic() {
            Random rnd = new Random(99991L);
            for (int trial = 0; trial < 30; trial++) {
                Digraph graph = DirectedCycleTest.randomDigraph(rnd, 2 + rnd.nextInt(7), rnd.nextInt(12));
                StronglyConnectedComponents kosaraju =
                        new StronglyConnectedComponents(graph, StronglyConnectedComponents.Mode.KOSARAJU);
                StronglyConnectedComponents tarjan =
                        new StronglyConnectedComponents(graph, StronglyConnectedComponents.Mode.TARJAN);

                assertEquals(kosaraju.count(), tarjan.count(), "分量个数");
                assertEquals(kosaraju.components(), tarjan.components(), "划分(规范化后)");
                for (int v = 0; v < graph.V(); v++) {
                    for (int w = 0; w < graph.V(); w++) {
                        assertEquals(kosaraju.stronglyConnected(v, w), tarjan.stronglyConnected(v, w));
                    }
                }

                // 缩点图:任意两个不同分量之间不能双向可达
                int count = tarjan.count();
                boolean[][] componentReach = new boolean[count][count];
                for (int[] edge : graph.edges()) {
                    int from = tarjan.id(edge[0]);
                    int to = tarjan.id(edge[1]);
                    componentReach[from][to] = true;
                }
                for (int k = 0; k < count; k++) {
                    for (int i = 0; i < count; i++) {
                        for (int j = 0; j < count; j++) {
                            if (componentReach[i][k] && componentReach[k][j]) {
                                componentReach[i][j] = true;
                            }
                        }
                    }
                }
                for (int a = 0; a < count; a++) {
                    for (int b = 0; b < count; b++) {
                        if (a != b) {
                            assertFalse(componentReach[a][b] && componentReach[b][a],
                                    "缩点图出现了双向可达,说明分量没有取到最大");
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("30 张随机有向图:与 algs4 参考实现对拍")
        void matchesReference() {
            Random rnd = new Random(555L);
            for (int trial = 0; trial < 30; trial++) {
                Digraph graph = DirectedCycleTest.randomDigraph(rnd, 2 + rnd.nextInt(7), rnd.nextInt(12));
                edu.princeton.cs.algs4.Digraph reference = new edu.princeton.cs.algs4.Digraph(graph.V());
                for (int[] edge : graph.edges()) {
                    reference.addEdge(edge[0], edge[1]);
                }
                edu.princeton.cs.algs4.KosarajuSharirSCC expected =
                        new edu.princeton.cs.algs4.KosarajuSharirSCC(reference);
                StronglyConnectedComponents mine = new StronglyConnectedComponents(
                        graph, StronglyConnectedComponents.Mode.KOSARAJU);

                assertEquals(canonical(expected, graph.V()), mine.components(),
                        "第 " + trial + " 张图的划分不一致");
            }
        }
    }

    @Nested
    @DisplayName("查询、规模与校验")
    class QueryAndScaleTest {

        @Test
        @DisplayName("默认用 Tarjan;components() 是规范形式(内部升序、分量按最小顶点排序)")
        void queryBasics() {
            Digraph graph = new Digraph(5);
            graph.addEdge(3, 4);
            graph.addEdge(4, 3);
            graph.addEdge(0, 1);
            graph.addEdge(1, 0);
            graph.addEdge(1, 3);
            StronglyConnectedComponents scc = new StronglyConnectedComponents(graph);
            assertEquals(StronglyConnectedComponents.Mode.TARJAN, scc.mode());
            assertEquals(5, scc.V());
            assertEquals(3, scc.count(), "{0,1}、{2}、{3,4}");
            assertEquals(Arrays.asList(
                    Arrays.asList(0, 1), Arrays.asList(2), Arrays.asList(3, 4)), scc.components());
            assertEquals(2, scc.largestComponentSize());
            assertEquals(2, scc.size(scc.id(0)));
            assertTrue(scc.toString().contains("分量数=3"));
            assertTrue(scc.toString().contains("TARJAN"));
        }

        @Test
        @DisplayName("空图:0 个分量,不是强连通")
        void emptyGraph() {
            StronglyConnectedComponents scc = new StronglyConnectedComponents(new Digraph(0));
            assertEquals(0, scc.count());
            assertEquals(0, scc.largestComponentSize());
            assertFalse(scc.isStronglyConnected());
            assertTrue(scc.components().isEmpty());
        }

        @Test
        @DisplayName("20 万顶点的长环:1 个分量(迭代实现不栈溢出)")
        void hugeCycle() {
            int n = 200000;
            Digraph graph = new Digraph(n);
            for (int v = 0; v < n; v++) {
                graph.addEdge(v, (v + 1) % n);
            }
            StronglyConnectedComponents scc = new StronglyConnectedComponents(graph);
            assertEquals(1, scc.count());
            assertEquals(n, scc.size(0));
        }

        @Test
        @DisplayName("20 万顶点的长链:20 万个分量")
        void hugeChain() {
            int n = 200000;
            Digraph graph = new Digraph(n);
            for (int v = 0; v + 1 < n; v++) {
                graph.addEdge(v, v + 1);
            }
            assertEquals(n, new StronglyConnectedComponents(graph).count());
        }

        @Test
        @DisplayName("参数校验:null 图、null 算法、越界编号、非法分量号")
        void validation() {
            Digraph graph = DirectedCycleTest.dagSample();
            assertThrows(IllegalArgumentException.class, () -> new StronglyConnectedComponents(null));
            assertThrows(IllegalArgumentException.class,
                    () -> new StronglyConnectedComponents(graph, (StronglyConnectedComponents.Mode) null));
            StronglyConnectedComponents scc = new StronglyConnectedComponents(graph);
            assertThrows(IllegalArgumentException.class, () -> scc.id(6));
            assertThrows(IllegalArgumentException.class, () -> scc.id(-1));
            assertThrows(IllegalArgumentException.class, () -> scc.size(6));
            assertThrows(IllegalArgumentException.class, () -> scc.component(-1));
            assertThrows(IllegalArgumentException.class, () -> scc.stronglyConnected(0, 6));
            assertThrows(IllegalArgumentException.class, () -> scc.stronglyConnected(-1, 0));
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /** 一轮独立的可达性搜索:reach[v][w] 表示 v 能否到达 w(不依赖 SCC 实现) */
    private static boolean[][] reachability(Digraph graph) {
        int V = graph.V();
        boolean[][] reach = new boolean[V][V];
        for (int start = 0; start < V; start++) {
            Deque<Integer> stack = new ArrayDeque<Integer>();
            stack.push(start);
            reach[start][start] = true;
            while (!stack.isEmpty()) {
                int v = stack.pop();
                for (int w : graph.adj(v)) {
                    if (!reach[start][w]) {
                        reach[start][w] = true;
                        stack.push(w);
                    }
                }
            }
        }
        return reach;
    }

    /** 把 algs4 参考实现的分量整理成本包的规范形式(内部升序、分量按最小顶点排序) */
    private static List<List<Integer>> canonical(edu.princeton.cs.algs4.KosarajuSharirSCC scc, int vertexCount) {
        List<List<Integer>> result = new ArrayList<List<Integer>>();
        for (int c = 0; c < scc.count(); c++) {
            result.add(new ArrayList<Integer>());
        }
        for (int v = 0; v < vertexCount; v++) {
            (result.get(scc.id(v))).add(v);
        }
        result.sort(new java.util.Comparator<List<Integer>>() {
            public int compare(List<Integer> a, List<Integer> b) {
                return Integer.compare(a.get(0), b.get(0));
            }
        });
        return result;
    }
}
