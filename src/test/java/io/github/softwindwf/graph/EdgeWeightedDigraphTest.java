package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EdgeWeightedDigraph} 有向带权图测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li><b>出边语义</b>:边只挂在起点(与无向图"两端各存一份"形成对照),因此出度 = 邻接表长度、
 *       入度要单独统计;</li>
 *   <li>{@code edges()} 无需去重(每条边本来只有一份),条数恒等于 E;</li>
 *   <li>自环同时计入出度与入度;平行边按重数计;</li>
 *   <li>深拷贝、参数校验。</li>
 * </ol>
 */
@DisplayName("EdgeWeightedDigraph 有向带权图测试")
class EdgeWeightedDigraphTest {

    private static EdgeWeightedDigraph tinyEWD() {
        return GraphIO.readWeightedDigraphFile("tinyEWD.txt");
    }

    private static List<DirectedEdge> edgesOf(EdgeWeightedDigraph g) {
        List<DirectedEdge> list = new ArrayList<DirectedEdge>();
        for (DirectedEdge e : g.edges()) {
            list.add(e);
        }
        return list;
    }

    @Nested
    @DisplayName("构造与规模")
    class ConstructionTest {

        @Test
        @DisplayName("空图与非法顶点数")
        void emptyGraph() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(4);
            assertEquals(4, g.V());
            assertEquals(0, g.E());
            assertEquals(0, g.outDegreeSum());
            assertEquals(0, g.maxOutDegree());
            assertEquals(0, g.selfLoopCount());
            assertFalse(g.edges().iterator().hasNext());
            assertThrows(IllegalArgumentException.class, () -> new EdgeWeightedDigraph(-1));
            assertThrows(IllegalArgumentException.class, () -> new EdgeWeightedDigraph((1 << 24) + 1));
        }

        @Test
        @DisplayName("全部出度之和恒等于 E")
        void outDegreeSumEqualsE() {
            EdgeWeightedDigraph g = tinyEWD();
            int sum = 0;
            for (int v = 0; v < g.V(); v++) {
                sum += g.outDegree(v);
            }
            assertEquals(g.E(), sum);
            assertEquals(g.E(), g.outDegreeSum());
        }

        @Test
        @DisplayName("深拷贝:结构一致、改动互不影响")
        void deepCopy() {
            EdgeWeightedDigraph g = tinyEWD();
            EdgeWeightedDigraph copy = new EdgeWeightedDigraph(g);
            assertNotSame(g, copy);
            assertEquals(g.toString(), copy.toString());
            assertEquals(g.E(), copy.E());
            for (int v = 0; v < g.V(); v++) {
                assertEquals(g.inDegree(v), copy.inDegree(v), "入度也要拷过来");
            }
            copy.addEdge(0, 1, 9.99);
            assertEquals(16, copy.E());
            assertEquals(15, g.E(), "改动副本不应影响原图");
            assertThrows(IllegalArgumentException.class, () -> new EdgeWeightedDigraph((EdgeWeightedDigraph) null));
        }
    }

    @Nested
    @DisplayName("有向边的存储语义")
    class DirectedStorageTest {

        @Test
        @DisplayName("边只出现在起点的邻接表里,终点看不到它")
        void edgeOnlyAtFromVertex() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            DirectedEdge e = new DirectedEdge(0, 2, 1.5);
            g.addEdge(e);
            assertEquals(1, g.E());
            assertEquals(1, g.outDegree(0));
            assertEquals(0, g.outDegree(2), "2 是终点,它的出边表里不该有这条边");
            assertEquals(1, g.inDegree(2));
            assertEquals(0, g.inDegree(0));
            assertSame(e, list(g.adj(0)).get(0), "存的是同一个对象");
            assertFalse(g.adj(2).iterator().hasNext());
        }

        @Test
        @DisplayName("反向边互不影响:0->2 存在不等于 2->0 存在")
        void reverseEdgeIsIndependent() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            g.addEdge(0, 2, 1.5);
            assertTrue(g.hasEdge(0, 2));
            assertFalse(g.hasEdge(2, 0));
            g.addEdge(2, 0, 9.0);
            assertTrue(g.hasEdge(2, 0));
            assertEquals(2, g.E());
            assertEquals(1, g.outDegree(0));
            assertEquals(1, g.outDegree(2));
            assertEquals(1, g.inDegree(0));
            assertEquals(1, g.inDegree(2));
            assertEquals(1.5, g.weightOf(0, 2), 1e-12);
            assertEquals(9.0, g.weightOf(2, 0), 1e-12);
        }

        @Test
        @DisplayName("便捷重载 addEdge(from, to, weight)")
        void convenienceOverload() {
            EdgeWeightedDigraph a = new EdgeWeightedDigraph(2);
            a.addEdge(0, 1, 1.25);
            EdgeWeightedDigraph b = new EdgeWeightedDigraph(2);
            b.addEdge(new DirectedEdge(0, 1, 1.25));
            assertEquals(a.toString(), b.toString());
        }

        @Test
        @DisplayName("自环:出度与入度各记 1,selfLoopCount 记 1")
        void selfLoop() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            g.addEdge(1, 1, 0.5);
            assertEquals(1, g.E());
            assertEquals(1, g.outDegree(1));
            assertEquals(1, g.inDegree(1));
            assertEquals(1, g.selfLoopCount());
            assertEquals(1, g.outDegreeSum(), "出度之和仍是 E(自环只算一条边)");
        }

        @Test
        @DisplayName("平行有向边按重数计,weightOf 取最轻")
        void parallelEdges() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(2);
            g.addEdge(0, 1, 0.9);
            g.addEdge(0, 1, 0.1);
            g.addEdge(0, 1, 0.5);
            assertEquals(3, g.E());
            assertEquals(3, g.outDegree(0));
            assertEquals(3, g.inDegree(1));
            assertEquals(3, edgesOf(g).size(), "三条平行边各自返回一次");
            assertEquals(0.1, g.weightOf(0, 1), 1e-12);
        }

        @Test
        @DisplayName("端点越界、null 边抛 IllegalArgumentException")
        void invalidEdges() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            g.addEdge(0, 1, 1.0);
            assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 3, 1.0));
            assertThrows(IllegalArgumentException.class, () -> g.addEdge(new DirectedEdge(0, 5, 1.0)));
            assertThrows(IllegalArgumentException.class, () -> g.addEdge((DirectedEdge) null));
            assertThrows(IllegalArgumentException.class, () -> g.adj(3));
            assertThrows(IllegalArgumentException.class, () -> g.outDegree(-1));
            assertThrows(IllegalArgumentException.class, () -> g.inDegree(3));
            assertThrows(java.util.NoSuchElementException.class, () -> g.weightOf(1, 2));
            assertEquals(1, g.E(), "非法调用不应改变边数");
        }
    }

    @Nested
    @DisplayName("遍历与 tinyEWD.txt")
    class TraversalTest {

        @Test
        @DisplayName("edges() 每条边恰好一次,条数等于 E(无需去重)")
        void edgesOnceEach() {
            EdgeWeightedDigraph g = tinyEWD();
            List<DirectedEdge> edges = edgesOf(g);
            assertEquals(8, g.V());
            assertEquals(15, g.E());
            assertEquals(15, edges.size());
            // 反向边是不同对象,15 条边互不相同
            assertEquals(15, new java.util.HashSet<DirectedEdge>(edges).size());
        }

        @Test
        @DisplayName("tinyEWD:出度/入度与样例一致")
        void degrees() {
            EdgeWeightedDigraph g = tinyEWD();
            // 出边:0->4, 0->2        → 出度 2
            assertEquals(2, g.outDegree(0));
            // 入边:6->0              → 入度 1
            assertEquals(1, g.inDegree(0));
            // 5 的出边:5->4, 5->7, 5->1 → 3;入边 4->5, 7->5 → 2
            assertEquals(3, g.outDegree(5));
            assertEquals(2, g.inDegree(5));
            // 6 的出边:6->2, 6->0, 6->4 → 3;入边 3->6 → 1
            assertEquals(3, g.outDegree(6));
            assertEquals(1, g.inDegree(6));
            assertEquals(15, g.outDegreeSum());
            assertEquals(15, totalInDegree(g));
            assertEquals(3, g.maxOutDegree());
            assertEquals(0, g.selfLoopCount());
        }

        @Test
        @DisplayName("迭代器不支持 remove")
        void iteratorsAreReadOnly() {
            java.util.Iterator<DirectedEdge> it = tinyEWD().edges().iterator();
            assertTrue(it.hasNext());
            it.next();
            assertThrows(UnsupportedOperationException.class, it::remove);
        }

        @Test
        @DisplayName("toString:首行为规模,随后每行列出该顶点的出边(终点与权值)")
        void format() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            g.addEdge(0, 2, 1.5);
            String[] lines = g.toString().split("\\r?\\n");
            assertEquals("3 vertices, 1 edges", lines[0]);
            assertEquals("0: 2(1.5) ", lines[1]);
            assertEquals("1: ", lines[2]);
            assertEquals("2: ", lines[3]);
        }

        private int totalInDegree(EdgeWeightedDigraph g) {
            int sum = 0;
            for (int v = 0; v < g.V(); v++) {
                sum += g.inDegree(v);
            }
            return sum;
        }
    }

    @Nested
    @DisplayName("与无权有向图互转")
    class ToDigraphTest {

        @Test
        @DisplayName("toDigraph:顶点数、边数、拓扑结构一致(权值丢弃,平行边保留)")
        void toDigraph() {
            EdgeWeightedDigraph weighted = tinyEWD();
            Digraph plain = weighted.toDigraph();
            assertEquals(weighted.V(), plain.V());
            assertEquals(weighted.E(), plain.E());
            for (int v = 0; v < weighted.V(); v++) {
                assertEquals(weighted.outDegree(v), plain.outDegree(v), v + " 的出度");
                assertEquals(weighted.inDegree(v), plain.inDegree(v), v + " 的入度");
                for (int w = 0; w < weighted.V(); w++) {
                    assertEquals(countInWeighted(weighted, v, w), plain.countEdges(v, w),
                            "从 " + v + " 到 " + w + " 的边数(平行边按重数)");
                }
            }
        }

        @Test
        @DisplayName("有环的加权图转过去后,TopologicalSort 与 DirectedCycle 都能认出环")
        void cyclesAreVisible() {
            EdgeWeightedDigraph weighted = GraphIO.parseWeightedDigraph("3\n3\n0 1 1\n1 2 1\n2 0 1\n");
            assertTrue(new TopologicalSort(weighted.toDigraph()).hasCycle());
            assertTrue(new DirectedCycle(weighted.toDigraph()).hasCycle());
            assertTrue(new TopologicalSort(tinyEWD().toDigraph()).hasCycle(),
                    "tinyEWD 里有 4->5 与 5->4,拓扑结构本身就有环");
        }

        private int countInWeighted(EdgeWeightedDigraph graph, int from, int to) {
            int count = 0;
            for (DirectedEdge edge : graph.adj(from)) {
                if (edge.to() == to) {
                    count++;
                }
            }
            return count;
        }
    }

    private static List<DirectedEdge> list(Iterable<DirectedEdge> edges) {
        List<DirectedEdge> result = new ArrayList<DirectedEdge>();
        for (DirectedEdge e : edges) {
            result.add(e);
        }
        return result;
    }
}
