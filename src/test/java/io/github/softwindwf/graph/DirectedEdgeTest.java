package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DirectedEdge} 有向带权边测试。
 *
 * <p>重点:方向是身份的一部分(与无向边 {@link Edge} 相反)、按权值可比、非法参数被拒绝。</p>
 */
@DisplayName("DirectedEdge 有向带权边测试")
class DirectedEdgeTest {

    @Nested
    @DisplayName("端点与方向")
    class DirectionTest {

        @Test
        @DisplayName("from/to 不随调用改变,也没有 other() 这种双向接口")
        void endpointsAreFixed() {
            DirectedEdge e = new DirectedEdge(0, 4, 0.38);
            assertEquals(0, e.from());
            assertEquals(4, e.to());
            assertEquals(0.38, e.weight(), 0.0);
        }

        @Test
        @DisplayName("自环:from == to")
        void selfLoop() {
            DirectedEdge loop = new DirectedEdge(2, 2, 0.5);
            assertEquals(2, loop.from());
            assertEquals(2, loop.to());
        }

        @Test
        @DisplayName("反向边是另一条边:0->4 与 4->0 不相等,散列值也不同")
        void directionMattersForEquality() {
            DirectedEdge forward = new DirectedEdge(0, 4, 0.38);
            DirectedEdge backward = new DirectedEdge(4, 0, 0.38);
            assertNotEquals(forward, backward);
            assertNotEquals(forward.hashCode(), backward.hashCode());
            assertEquals(forward, new DirectedEdge(0, 4, 0.38));
            assertEquals(forward.hashCode(), new DirectedEdge(0, 4, 0.38).hashCode());
        }

        @Test
        @DisplayName("端点相同但权值不同则不等;与 null、其他类型不等")
        void weightAndTypeMatter() {
            DirectedEdge e = new DirectedEdge(0, 4, 0.38);
            assertNotEquals(e, new DirectedEdge(0, 4, 0.39));
            assertNotEquals(e, null);
            assertNotEquals(e, "0->4 0.38");
            assertEquals(e, e);
        }

        @Test
        @DisplayName("Set 里同方向的平行边会合并,反向边不会")
        void setSemantics() {
            Set<DirectedEdge> set = new HashSet<DirectedEdge>();
            set.add(new DirectedEdge(0, 4, 0.38));
            set.add(new DirectedEdge(0, 4, 0.38));
            set.add(new DirectedEdge(4, 0, 0.38));
            assertEquals(2, set.size(), "平行同向边合并,反向边保留");
        }
    }

    @Nested
    @DisplayName("权值与比较")
    class WeightTest {

        @Test
        @DisplayName("权值可为 0 或负数;NaN/无穷被拒绝;负端点被拒绝")
        void validation() {
            assertEquals(0.0, new DirectedEdge(0, 1, 0.0).weight(), 0.0);
            assertEquals(-2.0, new DirectedEdge(0, 1, -2.0).weight(), 0.0);
            assertThrows(IllegalArgumentException.class, () -> new DirectedEdge(0, 1, Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> new DirectedEdge(0, 1, Double.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class, () -> new DirectedEdge(-1, 1, 1.0));
            assertThrows(IllegalArgumentException.class, () -> new DirectedEdge(1, -1, 1.0));
        }

        @Test
        @DisplayName("compareTo 按权值;null 抛异常")
        void compareByWeight() {
            DirectedEdge light = new DirectedEdge(0, 4, 0.26);
            DirectedEdge heavy = new DirectedEdge(6, 0, 0.58);
            assertTrue(light.compareTo(heavy) < 0);
            assertTrue(heavy.compareTo(light) > 0);
            assertEquals(0, light.compareTo(new DirectedEdge(7, 3, 0.26)));
            assertThrows(IllegalArgumentException.class, () -> light.compareTo(null));
        }

        @Test
        @DisplayName("可作为优先队列元素,按权值升序出队")
        void worksInPriorityQueue() {
            PriorityQueue<DirectedEdge> pq = new PriorityQueue<DirectedEdge>();
            pq.add(new DirectedEdge(0, 4, 0.38));
            pq.add(new DirectedEdge(0, 2, 0.26));
            pq.add(new DirectedEdge(4, 5, 0.35));
            assertEquals(0.26, pq.poll().weight(), 1e-12);
            assertEquals(0.35, pq.poll().weight(), 1e-12);
            assertEquals(0.38, pq.poll().weight(), 1e-12);
            assertTrue(pq.isEmpty());
        }

        @Test
        @DisplayName("排序:一组有向边按权值升序")
        void sortByWeight() {
            DirectedEdge[] edges = {
                new DirectedEdge(0, 4, 0.38), new DirectedEdge(0, 2, 0.26), new DirectedEdge(2, 7, 0.34)
            };
            Arrays.sort(edges);
            assertEquals("0->2 0.26", edges[0].toString());
            assertEquals("2->7 0.34", edges[1].toString());
            assertEquals("0->4 0.38", edges[2].toString());
        }

        @Test
        @DisplayName("toString 形如 0->4 0.38")
        void format() {
            assertEquals("0->4 0.38", new DirectedEdge(0, 4, 0.38).toString());
            assertEquals("2->2 0.5", new DirectedEdge(2, 2, 0.5).toString());
            assertEquals("6->0 0.58", new DirectedEdge(6, 0, 0.58).toString());
        }
    }
}
