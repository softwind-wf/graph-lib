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
 * {@link Bipartite} 二分图判定测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li><b>定义级对拍</b>:小图上暴力枚举全部 2^V 种染色方案,直接判断"有没有一种合法二着色"
 *       —— 这是完全独立的判据(SCC 式的算法一个都不用);</li>
 *   <li>是二分图时:给出的两侧集合必须满足"每条边两端异色",且两侧之和 = V;</li>
 *   <li>不是二分图时:给出的<b>奇环</b>必须是真环、且长度为奇数;</li>
 *   <li>与 algs4 的 {@code Bipartite} 对拍;</li>
 *   <li>自环 → 立即非二分图;多分量图要逐分量判定。</li>
 * </ol>
 */
@DisplayName("Bipartite 二分图判定测试")
class BipartiteTest {

    @Nested
    @DisplayName("基本情形")
    class BasicTest {

        @Test
        @DisplayName("偶环是二分图,奇环不是;树总是二分图")
        void simpleShapes() {
            assertTrue(new Bipartite(GraphGenerator.cycle(4)).isBipartite());
            assertFalse(new Bipartite(GraphGenerator.cycle(5)).isBipartite());
            assertTrue(new Bipartite(GraphGenerator.cycle(6)).isBipartite());
            assertTrue(new Bipartite(GraphGenerator.path(7)).isBipartite());
            assertTrue(new Bipartite(GraphGenerator.tree(new Random(7), 9)).isBipartite());
            assertTrue(new Bipartite(GraphGenerator.complete(2)).isBipartite());
            assertFalse(new Bipartite(GraphGenerator.complete(3)).isBipartite(), "三角形含奇环");
        }

        @Test
        @DisplayName("是二分图时两侧划分合法:每条边两端异色、两侧之和 = V")
        void sidesAreValid() {
            UndirectedGraph graph = GraphGenerator.cycle(6);
            Bipartite bipartite = new Bipartite(graph);
            assertTrue(bipartite.isBipartite());
            assertEquals(3, bipartite.sideA().size());
            assertEquals(3, bipartite.sideB().size());
            assertEquals(graph.V(), bipartite.sideA().size() + bipartite.sideB().size());
            for (int[] edge : graph.edges()) {
                assertTrue(bipartite.color(edge[0]) != bipartite.color(edge[1]),
                        "边 " + edge[0] + "-" + edge[1] + " 的两端必须异色");
            }
            assertNull(bipartite.oddCycle());
            assertEquals(0, bipartite.oddCycleLength());
        }

        @Test
        @DisplayName("不是二分图时给出合法的奇环")
        void oddCycleIsValid() {
            UndirectedGraph graph = GraphGenerator.cycle(7);
            Bipartite bipartite = new Bipartite(graph);
            assertFalse(bipartite.isBipartite());
            List<Integer> cycle = bipartite.oddCycle();
            assertEquals(7, cycle.size());
            assertEquals(1, cycle.size() % 2, "奇环长度必须是奇数");
            assertEquals(cycle.size(), bipartite.oddCycleLength());
            assertTrue(UndirectedCycleTest.assertValidCycle(graph, cycle), "奇环必须是真的环");
        }

        @Test
        @DisplayName("自环:自己和自己同色,立刻判为非二分图")
        void selfLoop() {
            UndirectedGraph graph = new UndirectedGraph(3);
            graph.addEdge(0, 1);
            graph.addEdge(1, 1);
            Bipartite bipartite = new Bipartite(graph);
            assertFalse(bipartite.isBipartite());
            assertEquals(java.util.Arrays.asList(1), bipartite.oddCycle());
        }

        @Test
        @DisplayName("多分量:每个分量都要能二着色")
        void multipleComponents() {
            UndirectedGraph ok = new UndirectedGraph(6);
            ok.addEdge(0, 1);
            ok.addEdge(1, 2);
            ok.addEdge(3, 4);
            ok.addEdge(4, 5);
            Bipartite fine = new Bipartite(ok);
            assertTrue(fine.isBipartite());
            assertEquals(6, fine.sideA().size() + fine.sideB().size());

            ok.addEdge(0, 2);                            // 第一个分量变成三角形
            Bipartite broken = new Bipartite(ok);
            assertFalse(broken.isBipartite());
            assertTrue(UndirectedCycleTest.assertValidCycle(ok, broken.oddCycle()));
        }

        @Test
        @DisplayName("空图与孤立顶点:按定义是二分图")
        void trivial() {
            assertTrue(new Bipartite(new UndirectedGraph(0)).isBipartite());
            assertTrue(new Bipartite(new UndirectedGraph(4)).isBipartite());
            assertEquals(4, new Bipartite(new UndirectedGraph(4)).sideA().size());
        }

        @Test
        @DisplayName("toString 两种形态")
        void toStringContent() {
            assertTrue(new Bipartite(GraphGenerator.cycle(4)).toString().contains("是二分图"));
            assertTrue(new Bipartite(GraphGenerator.cycle(5)).toString().contains("不是二分图"));
        }
    }

    @Nested
    @DisplayName("与暴力枚举/参考实现对拍")
    class OracleTest {

        @Test
        @DisplayName("40 张随机图:与暴力枚举 2^V 种染色一致")
        void agreesWithBruteForce() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 40; trial++) {
                int V = 1 + rnd.nextInt(8);
                UndirectedGraph graph = GraphGenerator.anyEdges(rnd, V, rnd.nextInt(14));
                boolean expected = bruteForceBipartite(graph);
                Bipartite bipartite = new Bipartite(graph);
                assertEquals(expected, bipartite.isBipartite(),
                        "第 " + trial + " 张图:V=" + V + ", E=" + graph.E());
                if (!expected) {
                    assertTrue(UndirectedCycleTest.assertValidCycle(graph, bipartite.oddCycle()),
                            "第 " + trial + " 张图的奇环必须合法");
                    assertEquals(1, bipartite.oddCycleLength() % 2, "必须是奇环");
                }
            }
        }

        @Test
        @DisplayName("30 张随机图:与 algs4 的 Bipartite 对拍")
        void agreesWithReference() {
            Random rnd = new Random(2718L);
            for (int trial = 0; trial < 30; trial++) {
                UndirectedGraph graph = GraphGenerator.anyEdges(rnd, 1 + rnd.nextInt(9), rnd.nextInt(14));
                edu.princeton.cs.algs4.Graph reference = new edu.princeton.cs.algs4.Graph(graph.V());
                for (int[] edge : graph.edges()) {
                    reference.addEdge(edge[0], edge[1]);
                }
                assertEquals(new edu.princeton.cs.algs4.Bipartite(reference).isBipartite(),
                        new Bipartite(graph).isBipartite(), "第 " + trial + " 张图");
            }
        }

        @Test
        @DisplayName("二分图的两侧集合确实满足'边两端异色'(30 张随机二分图)")
        void sidesValidOnRandomBipartite() {
            Random rnd = new Random(555L);
            for (int trial = 0; trial < 30; trial++) {
                UndirectedGraph graph = GraphGenerator.bipartite(rnd, 1 + rnd.nextInt(6),
                        1 + rnd.nextInt(6), rnd.nextInt(15));
                Bipartite bipartite = new Bipartite(graph);
                assertTrue(bipartite.isBipartite(), "构造出来的就是二分图");
                for (int[] edge : graph.edges()) {
                    assertTrue(bipartite.color(edge[0]) != bipartite.color(edge[1]));
                }
            }
        }
    }

    @Nested
    @DisplayName("校验")
    class ValidationTest {

        @Test
        @DisplayName("null 图、越界顶点")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new Bipartite(null));
            Bipartite bipartite = new Bipartite(GraphGenerator.cycle(4));
            assertThrows(IllegalArgumentException.class, () -> bipartite.color(4));
            assertThrows(IllegalArgumentException.class, () -> bipartite.color(-1));
            assertThrows(IllegalArgumentException.class, () -> bipartite.inSideA(4));
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /** 暴力:枚举全部 2^V 种染色,看是否存在合法二着色 */
    static boolean bruteForceBipartite(UndirectedGraph graph) {
        if (graph.V() > 20) {
            throw new IllegalArgumentException("暴力枚举只适合小图");
        }
        for (int mask = 0; mask < (1 << graph.V()); mask++) {
            boolean ok = true;
            for (int[] edge : graph.edges()) {
                int colorA = (mask >> edge[0]) & 1;
                int colorB = (mask >> edge[1]) & 1;
                if (colorA == colorB) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }
}
