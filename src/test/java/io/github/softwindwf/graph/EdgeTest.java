package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Edge} 无向带权边测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>无向语义:两个端点都能"看出去"拿到另一端,方向不影响相等与散列;</li>
 *   <li>排序:按权值可比,可直接进优先队列;</li>
 *   <li>校验:负端点、NaN、无穷大权值一律拒绝;</li>
 *   <li>集合语义的副作用:同端点同权值 ⇒ equals 相等(测试显式固定这一行为,避免以后被误改)。</li>
 * </ol>
 */
@DisplayName("Edge 无向带权边测试")
class EdgeTest {

    @Nested
    @DisplayName("端点与无向语义")
    class EndpointsTest {

        @Test
        @DisplayName("either/other:两个方向都能取到另一端")
        void otherFromBothEnds() {
            Edge e = new Edge(4, 5, 0.35);
            assertEquals(4, e.either());
            assertEquals(5, e.other(4));
            assertEquals(4, e.other(5));
        }

        @Test
        @DisplayName("自环:other(v) 仍返回 v 本身")
        void selfLoop() {
            Edge loop = new Edge(2, 2, 1.5);
            assertEquals(2, loop.either());
            assertEquals(2, loop.other(2));
            assertEquals(1.5, loop.weight(), 0.0);
        }

        @Test
        @DisplayName("other 传入非端点抛 IllegalArgumentException")
        void otherInvalidVertex() {
            Edge e = new Edge(4, 5, 0.35);
            assertThrows(IllegalArgumentException.class, () -> e.other(6));
            assertThrows(IllegalArgumentException.class, () -> e.other(-1));
        }

        @Test
        @DisplayName("负端点被拒绝")
        void negativeVertexRejected() {
            assertThrows(IllegalArgumentException.class, () -> new Edge(-1, 0, 1.0));
            assertThrows(IllegalArgumentException.class, () -> new Edge(0, -2, 1.0));
        }
    }

    @Nested
    @DisplayName("权值校验与比较")
    class WeightTest {

        @Test
        @DisplayName("权值可以为 0 或负数(Prim 只要求可比较,不要求非负)")
        void zeroAndNegativeWeightsAllowed() {
            assertEquals(0.0, new Edge(0, 1, 0.0).weight(), 0.0);
            assertEquals(-2.5, new Edge(0, 1, -2.5).weight(), 0.0);
        }

        @Test
        @DisplayName("NaN 与正负无穷被拒绝")
        void nonFiniteWeightsRejected() {
            assertThrows(IllegalArgumentException.class, () -> new Edge(0, 1, Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> new Edge(0, 1, Double.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class, () -> new Edge(0, 1, Double.NEGATIVE_INFINITY));
        }

        @Test
        @DisplayName("compareTo 按权值升序;null 抛 IllegalArgumentException")
        void compareByWeight() {
            Edge light = new Edge(0, 1, 0.16);
            Edge heavy = new Edge(0, 1, 0.93);
            assertTrue(light.compareTo(heavy) < 0);
            assertTrue(heavy.compareTo(light) > 0);
            assertEquals(0, light.compareTo(new Edge(7, 3, 0.16)), "权值相同即相等,与端点无关");
            assertThrows(IllegalArgumentException.class, () -> light.compareTo(null));
        }

        @Test
        @DisplayName("排序:一组边按权值升序")
        void sortByWeight() {
            Edge[] edges = {new Edge(1, 2, 0.36), new Edge(0, 7, 0.16), new Edge(2, 3, 0.17)};
            Arrays.sort(edges);
            assertEquals("0-7 0.16", edges[0].toString());
            assertEquals("2-3 0.17", edges[1].toString());
            assertEquals("1-2 0.36", edges[2].toString());
        }
    }

    @Nested
    @DisplayName("相等与散列(无向)")
    class EqualityTest {

        @Test
        @DisplayName("方向无关:Edge(4,5) 等于 Edge(5,4),散列值也相同")
        void directionAgnostic() {
            Edge a = new Edge(4, 5, 0.35);
            Edge b = new Edge(5, 4, 0.35);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
            assertEquals(a, a);
        }

        @Test
        @DisplayName("权值不同则不等;端点不同则不等;与 null、其他类型不等")
        void differentEdges() {
            Edge a = new Edge(4, 5, 0.35);
            assertNotEquals(a, new Edge(4, 5, 0.36));
            assertNotEquals(a, new Edge(4, 6, 0.35));
            assertNotEquals(a, null);
            assertNotEquals(a, "4-5 0.35");
        }

        @Test
        @DisplayName("同端点同权值的平行边在 Set 里会合并成一条(显式固定的副作用)")
        void parallelEdgesCollapseInSet() {
            Set<Edge> set = new HashSet<Edge>();
            set.add(new Edge(4, 5, 0.35));
            set.add(new Edge(5, 4, 0.35));
            assertEquals(1, set.size(), "equals 只看端点集合与权值,同权平行边会被去重");
        }

        @Test
        @DisplayName("Set 里方向不同的边只留一条,但不同权值的平行边都保留")
        void setKeepsDistinctWeights() {
            Set<Edge> set = new TreeSet<Edge>();
            set.add(new Edge(4, 5, 0.35));
            set.add(new Edge(5, 4, 0.35));
            set.add(new Edge(4, 5, 0.37));
            assertEquals(2, set.size());
        }
    }

    @Nested
    @DisplayName("显示")
    class ToStringTest {

        @Test
        @DisplayName("toString 形如 4-5 0.35")
        void format() {
            assertEquals("4-5 0.35", new Edge(4, 5, 0.35).toString());
            assertEquals("2-2 1.5", new Edge(2, 2, 1.5).toString());
            assertEquals("0-1 3.0", new Edge(0, 1, 3).toString(), "整数权值以 double 形式打印");
        }
    }

    @Test
    @DisplayName("边可作为优先队列元素,出队顺序按权值升序")
    void worksInPriorityQueue() {
        java.util.PriorityQueue<Edge> pq = new java.util.PriorityQueue<Edge>();
        pq.add(new Edge(1, 2, 0.36));
        pq.add(new Edge(0, 7, 0.16));
        pq.add(new Edge(2, 3, 0.17));
        assertEquals(0.16, pq.poll().weight(), 1e-12);
        assertEquals(0.17, pq.poll().weight(), 1e-12);
        assertEquals(0.36, pq.poll().weight(), 1e-12);
        assertTrue(pq.isEmpty());
    }
}
