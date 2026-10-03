package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TransitiveClosure} 传递闭包测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>两种实现(逐点 DFS / Warshall 动态规划)结果<b>逐格一致</b>;</li>
 *   <li>与 {@link DirectedPaths} 逐点对照:闭包说"能到" ⟺ 单源搜索说"能到";</li>
 *   <li>与 algs4 的 {@code TransitiveClosure} 对拍;</li>
 *   <li>自反性(自己到自己恒为 true)、可达对计数与强连通分量/SCC 的关系
 *       (同一 SCC 内两两可达);</li>
 *   <li>顶点数上限保护(V² 空间)。</li>
 * </ol>
 */
@DisplayName("TransitiveClosure 传递闭包测试")
class TransitiveClosureTest {

    private static Digraph tinyDG() {
        return StronglyConnectedComponents.tinyDigraph();
    }

    @Nested
    @DisplayName("基本性质")
    class BasicTest {

        @Test
        @DisplayName("自反性:自己到自己恒为 true;有向链只有单向可达")
        void reflexivityAndDirection() {
            Digraph chain = DigraphGenerator.path(4);
            TransitiveClosure closure = new TransitiveClosure(chain);
            for (int v = 0; v < 4; v++) {
                assertTrue(closure.reachable(v, v), "自己到自己必须可达");
            }
            assertTrue(closure.reachable(0, 3));
            assertFalse(closure.reachable(3, 0), "有向边的反方向不通");
            assertEquals(java.util.Arrays.asList(0, 1, 2, 3), closure.reachableFrom(0));
            assertEquals(java.util.Arrays.asList(3), closure.reachableFrom(3));
        }

        @Test
        @DisplayName("可达对计数:链上有 10 对(4 个自反 + 6 个不同)")
        void counts() {
            TransitiveClosure closure = new TransitiveClosure(DigraphGenerator.path(4));
            assertEquals(10, closure.count());
            assertEquals(6, closure.countDistinctPairs());

            TransitiveClosure complete = new TransitiveClosure(DigraphGenerator.complete(4));
            assertEquals(16, complete.count(), "完全图里所有对都可达");
            assertEquals(12, complete.countDistinctPairs());
        }

        @Test
        @DisplayName("空图与单顶点")
        void trivial() {
            assertEquals(0, new TransitiveClosure(new Digraph(0)).count());
            TransitiveClosure single = new TransitiveClosure(new Digraph(1));
            assertTrue(single.reachable(0, 0));
            assertEquals(1, single.count());
        }

        @Test
        @DisplayName("同一个强连通分量内的顶点两两可达(与 SCC 对照)")
        void consistentWithStronglyConnectedComponents() {
            Digraph graph = tinyDG();
            TransitiveClosure closure = new TransitiveClosure(graph);
            StronglyConnectedComponents scc = new StronglyConnectedComponents(graph);
            for (int v = 0; v < graph.V(); v++) {
                for (int w = 0; w < graph.V(); w++) {
                    if (scc.stronglyConnected(v, w)) {
                        assertTrue(closure.reachable(v, w), "同 SCC 必互相可达");
                        assertTrue(closure.reachable(w, v));
                    }
                }
            }
        }

        @Test
        @DisplayName("toString 含规模与可达对数")
        void toStringContent() {
            String text = new TransitiveClosure(tinyDG()).toString();
            assertTrue(text.contains("TransitiveClosure(DFS): 13 个顶点"), text);
            assertTrue(text.contains("可达对"), text);
        }
    }

    @Nested
    @DisplayName("与其它实现对拍")
    class OracleTest {

        @Test
        @DisplayName("30 张随机图:DFS 与 Warshall 两种模式逐格一致")
        void modesAgree() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 30; trial++) {
                Digraph graph = DigraphGenerator.anyEdges(rnd, 1 + rnd.nextInt(9), rnd.nextInt(16));
                TransitiveClosure dfs = new TransitiveClosure(graph, TransitiveClosure.Mode.DFS);
                TransitiveClosure warshall = new TransitiveClosure(graph, TransitiveClosure.Mode.WARSHALL);
                assertEquals(dfs.count(), warshall.count(), "第 " + trial + " 张图的可达对数");
                for (int v = 0; v < graph.V(); v++) {
                    for (int w = 0; w < graph.V(); w++) {
                        assertEquals(dfs.reachable(v, w), warshall.reachable(v, w),
                                "第 " + trial + " 张图:" + v + "→" + w);
                    }
                }
            }
        }

        @Test
        @DisplayName("30 张随机图:与 DirectedPaths 逐点对照")
        void agreesWithDirectedPaths() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 30; trial++) {
                Digraph graph = DigraphGenerator.anyEdges(rnd, 1 + rnd.nextInt(8), rnd.nextInt(14));
                TransitiveClosure closure = new TransitiveClosure(graph);
                for (int v = 0; v < graph.V(); v++) {
                    DirectedPaths paths = new DirectedPaths(graph, v);
                    for (int w = 0; w < graph.V(); w++) {
                        assertEquals(paths.hasPathTo(w), closure.reachable(v, w),
                                "第 " + trial + " 张图:" + v + "→" + w);
                    }
                }
            }
        }

        @Test
        @DisplayName("20 张随机图:与 algs4 的 TransitiveClosure 对拍")
        void agreesWithReference() {
            Random rnd = new Random(31415L);
            for (int trial = 0; trial < 20; trial++) {
                Digraph graph = DigraphGenerator.anyEdges(rnd, 1 + rnd.nextInt(9), rnd.nextInt(14));
                edu.princeton.cs.algs4.Digraph reference = new edu.princeton.cs.algs4.Digraph(graph.V());
                for (int[] edge : graph.edges()) {
                    reference.addEdge(edge[0], edge[1]);
                }
                edu.princeton.cs.algs4.TransitiveClosure expected =
                        new edu.princeton.cs.algs4.TransitiveClosure(reference);
                TransitiveClosure mine = new TransitiveClosure(graph);
                for (int v = 0; v < graph.V(); v++) {
                    for (int w = 0; w < graph.V(); w++) {
                        assertEquals(expected.reachable(v, w), mine.reachable(v, w),
                                "第 " + trial + " 张图:" + v + "→" + w);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("校验")
    class ValidationTest {

        @Test
        @DisplayName("参数校验:null 图、null 模式、越界顶点")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new TransitiveClosure(null));
            assertThrows(IllegalArgumentException.class,
                    () -> new TransitiveClosure(tinyDG(), (TransitiveClosure.Mode) null));
            TransitiveClosure closure = new TransitiveClosure(tinyDG());
            assertThrows(IllegalArgumentException.class, () -> closure.reachable(13, 0));
            assertThrows(IllegalArgumentException.class, () -> closure.reachable(0, -1));
            assertThrows(IllegalArgumentException.class, () -> closure.reachableFrom(13));
        }

        @Test
        @DisplayName("顶点数超过上限(结果矩阵是 V²)直接报错并指出替代方案")
        void sizeGuard() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new TransitiveClosure(new Digraph((1 << 14) + 1)));
            assertTrue(e.getMessage().contains("DirectedPaths"), e.getMessage());
            assertTrue(e.getMessage().contains("StronglyConnectedComponents"), e.getMessage());
        }
    }
}
