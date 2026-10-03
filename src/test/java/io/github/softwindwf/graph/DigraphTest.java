package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Digraph} 无权有向图测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>方向语义:出边只挂在起点;{@code 0-&gt;1} 存在不等于 {@code 1-&gt;0} 存在;</li>
 *   <li>正反两张邻接表:后继与前驱都能直接查到,入度/出度口径一致(Σ出度 = E);</li>
 *   <li>自环同时计入出度与入度;平行边按重数计,{@code countEdges} 给出重数;</li>
 *   <li>{@code edges()} 每条边一次(平行边按重数);</li>
 *   <li>{@code reverse()} 把边全部反向且出入度互换,反向两次回到原图;</li>
 *   <li>深拷贝与参数校验。</li>
 * </ol>
 */
@DisplayName("Digraph 无权有向图测试")
class DigraphTest {

    /** 0→1、0→2、1→3、2→3、3→4 */
    private static Digraph sample() {
        Digraph g = new Digraph(5);
        g.addEdge(0, 1);
        g.addEdge(0, 2);
        g.addEdge(1, 3);
        g.addEdge(2, 3);
        g.addEdge(3, 4);
        return g;
    }

    private static List<Integer> list(Iterable<Integer> values) {
        List<Integer> result = new ArrayList<Integer>();
        for (int v : values) {
            result.add(v);
        }
        return result;
    }

    private static List<int[]> edgesOf(Digraph g) {
        List<int[]> result = new ArrayList<int[]>();
        for (int[] e : g.edges()) {
            result.add(e);
        }
        return result;
    }

    @Nested
    @DisplayName("构造与规模")
    class ConstructionTest {

        @Test
        @DisplayName("空图与非法顶点数")
        void emptyGraph() {
            Digraph g = new Digraph(4);
            assertEquals(4, g.V());
            assertEquals(0, g.E());
            assertEquals(0, g.outDegreeSum());
            assertEquals(0, g.selfLoopCount());
            for (int v = 0; v < 4; v++) {
                assertEquals(0, g.outDegree(v));
                assertEquals(0, g.inDegree(v));
                assertFalse(g.adj(v).iterator().hasNext());
                assertFalse(g.predecessors(v).iterator().hasNext());
            }
            assertFalse(g.edges().iterator().hasNext());
            assertThrows(IllegalArgumentException.class, () -> new Digraph(-1));
            assertThrows(IllegalArgumentException.class, () -> new Digraph((1 << 24) + 1));
        }

        @Test
        @DisplayName("出度之和恒等于 E")
        void outDegreeSumEqualsE() {
            Digraph g = sample();
            int sum = 0;
            for (int v = 0; v < g.V(); v++) {
                sum += g.outDegree(v);
            }
            assertEquals(5, g.E());
            assertEquals(g.E(), sum);
            assertEquals(g.E(), g.outDegreeSum());
        }

        @Test
        @DisplayName("深拷贝:结构一致、改动互不影响")
        void deepCopy() {
            Digraph g = sample();
            Digraph copy = new Digraph(g);
            assertNotSame(g, copy);
            assertEquals(g.toString(), copy.toString());
            copy.addEdge(4, 0);
            assertEquals(6, copy.E());
            assertEquals(5, g.E(), "改动副本不应影响原图");
            assertFalse(g.hasEdge(4, 0));
            assertThrows(IllegalArgumentException.class, () -> new Digraph((Digraph) null));
        }
    }

    @Nested
    @DisplayName("有向语义与度数")
    class DirectedSemanticsTest {

        @Test
        @DisplayName("方向有意义:0→1 存在,1→0 不存在")
        void directionMatters() {
            Digraph g = new Digraph(3);
            g.addEdge(0, 1);
            assertTrue(g.hasEdge(0, 1));
            assertFalse(g.hasEdge(1, 0));
            assertEquals(1, g.outDegree(0));
            assertEquals(0, g.outDegree(1), "1 没有出边");
            assertEquals(0, g.inDegree(0));
            assertEquals(1, g.inDegree(1));
            assertEquals(list(g.adj(0)), java.util.Arrays.asList(1));
            assertFalse(g.adj(1).iterator().hasNext());
        }

        @Test
        @DisplayName("前驱表:入边能直接查出来,顺序为加入顺序")
        void predecessors() {
            Digraph g = new Digraph(4);
            g.addEdge(0, 3);
            g.addEdge(1, 3);
            g.addEdge(2, 3);
            assertEquals(java.util.Arrays.asList(0, 1, 2), list(g.predecessors(3)));
            assertEquals(3, g.inDegree(3));
            assertEquals(0, g.inDegree(0));
            assertFalse(g.predecessors(0).iterator().hasNext());
        }

        @Test
        @DisplayName("自环:出度与入度各记 1,selfLoopCount 记 1")
        void selfLoop() {
            Digraph g = new Digraph(3);
            g.addEdge(1, 1);
            assertEquals(1, g.E());
            assertEquals(1, g.outDegree(1));
            assertEquals(1, g.inDegree(1));
            assertEquals(1, g.selfLoopCount());
            assertTrue(g.hasEdge(1, 1));
            assertEquals(1, g.outDegreeSum(), "自环也算一条出边");
        }

        @Test
        @DisplayName("平行边:出度/入度按重数,countEdges 给出条数")
        void parallelEdges() {
            Digraph g = new Digraph(2);
            g.addEdge(0, 1);
            g.addEdge(0, 1);
            g.addEdge(0, 1);
            assertEquals(3, g.E());
            assertEquals(3, g.outDegree(0));
            assertEquals(3, g.inDegree(1));
            assertEquals(3, g.countEdges(0, 1));
            assertEquals(0, g.countEdges(1, 0));
            assertEquals(3, edgesOf(g).size(), "平行边按重数逐条返回");
        }

        @Test
        @DisplayName("端点越界抛 IllegalArgumentException")
        void validation() {
            Digraph g = new Digraph(3);
            assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 3));
            assertThrows(IllegalArgumentException.class, () -> g.addEdge(-1, 0));
            assertThrows(IllegalArgumentException.class, () -> g.adj(3));
            assertThrows(IllegalArgumentException.class, () -> g.predecessors(-1));
            assertThrows(IllegalArgumentException.class, () -> g.inDegree(3));
            assertThrows(IllegalArgumentException.class, () -> g.countEdges(0, 3));
        }
    }

    @Nested
    @DisplayName("遍历与反向图")
    class TraversalAndReverseTest {

        @Test
        @DisplayName("edges() 每条边恰好一次,端点是 (from, to)")
        void edgesOnceEach() {
            Digraph g = sample();
            List<int[]> edges = edgesOf(g);
            assertEquals(5, edges.size());
            assertEquals(0, edges.get(0)[0]);
            assertEquals(1, edges.get(0)[1]);
            assertThrows(UnsupportedOperationException.class, () -> g.edges().iterator().remove());
        }

        @Test
        @DisplayName("reverse():边全部反向,出入度互换,V/E 不变")
        void reverse() {
            Digraph g = sample();
            Digraph r = g.reverse();
            assertEquals(g.V(), r.V());
            assertEquals(g.E(), r.E());
            for (int[] e : g.edges()) {
                assertTrue(r.hasEdge(e[1], e[0]), "反向图里应有 " + e[1] + "->" + e[0]);
                assertFalse(r.hasEdge(e[0], e[1]));
            }
            for (int v = 0; v < g.V(); v++) {
                assertEquals(g.outDegree(v), r.inDegree(v), "反向图的入度 = 原图出度");
                assertEquals(g.inDegree(v), r.outDegree(v));
            }
            assertEquals(g.toString(), r.reverse().toString(), "反向两次回到原图");
        }

        @Test
        @DisplayName("toString:首行为规模,随后每行列出该顶点的后继")
        void format() {
            Digraph g = new Digraph(3);
            g.addEdge(0, 2);
            String[] lines = g.toString().split("\\r?\\n");
            assertEquals("3 vertices, 1 edges", lines[0]);
            assertEquals("0: 2 ", lines[1]);
            assertEquals("1: ", lines[2]);
            assertEquals("2: ", lines[3]);
        }

        @Test
        @DisplayName("10 万顶点的链:邻接与前驱都正确")
        void largeChain() {
            int n = 100_000;
            Digraph g = new Digraph(n);
            for (int v = 0; v + 1 < n; v++) {
                g.addEdge(v, v + 1);
            }
            assertEquals(n - 1, g.E());
            assertEquals(1, g.outDegree(0));
            assertEquals(1, g.inDegree(n - 1));
            assertEquals(0, g.inDegree(0));
            assertEquals(0, g.outDegree(n - 1));
            assertEquals(list(g.predecessors(n - 1)), java.util.Arrays.asList(n - 2));
        }
    }
}
