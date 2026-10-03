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
 * {@link DigraphGenerator} 有向图生成器测试。
 *
 * <p>验证重点:生成器承诺的结构性质必须成立 —— {@code dag} 真的无环、
 * {@code simple} 真的没有自环/平行弧、{@code cycle} 真的每个顶点出入度都是 1、
 * {@code edgeWeightedDag} 真的无环(这样才允许负权)。</p>
 */
@DisplayName("DigraphGenerator 有向图生成器测试")
class DigraphGeneratorTest {

    @Nested
    @DisplayName("形状承诺")
    class ShapeTest {

        @Test
        @DisplayName("anyEdges:弧数严格等于要求,允许自环与平行弧")
        void anyEdges() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 1 + rnd.nextInt(7);
                int E = rnd.nextInt(20);
                Digraph graph = DigraphGenerator.anyEdges(rnd, V, E);
                assertEquals(V, graph.V());
                assertEquals(E, graph.E());
            }
        }

        @Test
        @DisplayName("simple:无自环、无平行弧,弧数贴近期望")
        void simple() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 3 + rnd.nextInt(8);
                int E = rnd.nextInt(20);
                Digraph graph = DigraphGenerator.simple(rnd, V, E);
                assertEquals(Math.min(E, V * (V - 1)), graph.E());
                assertEquals(0, graph.selfLoopCount(), "简单图不能有自环");
                for (int v = 0; v < V; v++) {
                    for (int w = 0; w < V; w++) {
                        assertTrue(graph.countEdges(v, w) <= 1, "同一方向最多一条弧");
                    }
                }
            }
        }

        @Test
        @DisplayName("dag:一定无环(拓扑排序必然成功)")
        void dag() {
            Random rnd = new Random(555L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 2 + rnd.nextInt(10);
                Digraph graph = DigraphGenerator.dag(rnd, V, rnd.nextInt(20));
                assertFalse(new DirectedCycle(graph).hasCycle(), "生成的 DAG 不能有环");
                assertFalse(new TopologicalSort(graph).hasCycle());
                for (int[] edge : edges(graph)) {
                    assertTrue(edge[0] < edge[1], "只应从小号指向大号");
                }
            }
        }

        @Test
        @DisplayName("complete / path / cycle:形状确定")
        void deterministicShapes() {
            assertEquals(12, DigraphGenerator.complete(4).E());
            assertEquals(4, DigraphGenerator.path(5).E());
            assertEquals(5, DigraphGenerator.cycle(5).E());
            Digraph cycle = DigraphGenerator.cycle(6);
            for (int v = 0; v < 6; v++) {
                assertEquals(1, cycle.inDegree(v));
                assertEquals(1, cycle.outDegree(v));
            }
            assertTrue(new DirectedEulerianPath(cycle).hasEulerianCycle());
            assertFalse(new DirectedEulerianPath(DigraphGenerator.path(5)).hasEulerianCycle());
            assertFalse(new DirectedCycle(DigraphGenerator.dag(new Random(1), 5, 3)).hasCycle());
        }
    }

    @Nested
    @DisplayName("带权与概率生成")
    class WeightedTest {

        @Test
        @DisplayName("edgeWeighted:权值范围符合 allowNegative")
        void edgeWeighted() {
            Random rnd = new Random(31415L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(8);
                int E = rnd.nextInt(14);
                EdgeWeightedDigraph positive = DigraphGenerator.edgeWeighted(rnd, V, E, false);
                assertEquals(E, positive.E());
                for (DirectedEdge edge : positive.edges()) {
                    assertTrue(edge.weight() > 0);
                }
                EdgeWeightedDigraph negative = DigraphGenerator.edgeWeighted(rnd, V, E, true);
                assertEquals(E, negative.E());
            }
        }

        @Test
        @DisplayName("edgeWeightedPositive:简单图且权值全正")
        void edgeWeightedPositive() {
            Random rnd = new Random(1618L);
            for (int trial = 0; trial < 20; trial++) {
                int V = 2 + rnd.nextInt(8);
                EdgeWeightedDigraph graph = DigraphGenerator.edgeWeightedPositive(rnd, V, rnd.nextInt(15));
                for (DirectedEdge edge : graph.edges()) {
                    assertTrue(edge.weight() > 0);
                    assertTrue(edge.from() != edge.to(), "不含自环");
                }
                assertFalse(new TopologicalSort(graph.toDigraph()).hasCycle() && graph.E() == 0);
                for (int v = 0; v < V; v++) {
                    for (int w = 0; w < V; w++) {
                        if (v != w) {
                            assertTrue(countIn(graph, v, w) <= 1);
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("edgeWeightedDag:无环(所以可以含负权);概率版按概率生成")
        void edgeWeightedDag() {
            Random rnd = new Random(4321L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 2 + rnd.nextInt(10);
                EdgeWeightedDigraph graph = DigraphGenerator.edgeWeightedDag(rnd, V, rnd.nextInt(20), true);
                assertFalse(new TopologicalSort(graph.toDigraph()).hasCycle(), "DAG 不能有环");
                for (DirectedEdge edge : graph.edges()) {
                    assertTrue(edge.from() < edge.to());
                }
            }
            Random byProbability = new Random(99L);
            EdgeWeightedDigraph sparse = DigraphGenerator.edgeWeightedDagByProbability(byProbability, 20, 0.05, false);
            EdgeWeightedDigraph dense = DigraphGenerator.edgeWeightedDagByProbability(byProbability, 20, 0.9, false);
            assertFalse(new TopologicalSort(sparse.toDigraph()).hasCycle());
            assertFalse(new TopologicalSort(dense.toDigraph()).hasCycle());
            assertTrue(dense.E() > sparse.E(), "概率越大弧越多");
        }
    }

    @Nested
    @DisplayName("参数校验与可复现")
    class ValidationTest {

        @Test
        @DisplayName("null 随机源、负顶点数、非法概率")
        void validation() {
            Random rnd = new Random(1);
            assertThrows(IllegalArgumentException.class, () -> DigraphGenerator.anyEdges(null, 3, 3));
            assertThrows(IllegalArgumentException.class, () -> DigraphGenerator.anyEdges(rnd, -1, 3));
            assertThrows(IllegalArgumentException.class, () -> DigraphGenerator.simple(null, 3, 3));
            assertThrows(IllegalArgumentException.class, () -> DigraphGenerator.dag(rnd, -1, 3));
            assertThrows(IllegalArgumentException.class, () -> DigraphGenerator.complete(-1));
            assertThrows(IllegalArgumentException.class, () -> DigraphGenerator.path(-1));
            assertThrows(IllegalArgumentException.class, () -> DigraphGenerator.cycle(-1));
            assertThrows(IllegalArgumentException.class,
                    () -> DigraphGenerator.edgeWeighted(null, 3, 3, true));
            assertThrows(IllegalArgumentException.class,
                    () -> DigraphGenerator.edgeWeightedPositive(rnd, -1, 3));
            assertThrows(IllegalArgumentException.class,
                    () -> DigraphGenerator.edgeWeightedDag(rnd, -1, 3, true));
            assertThrows(IllegalArgumentException.class,
                    () -> DigraphGenerator.edgeWeightedDagByProbability(rnd, 3, -0.1, true));
            assertThrows(IllegalArgumentException.class,
                    () -> DigraphGenerator.edgeWeightedDagByProbability(rnd, 3, 1.5, true));
        }

        @Test
        @DisplayName("固定种子可复现")
        void reproducible() {
            assertEquals(DigraphGenerator.anyEdges(new Random(7), 8, 12).toString(),
                    DigraphGenerator.anyEdges(new Random(7), 8, 12).toString());
        }
    }

    private static java.util.List<int[]> edges(Digraph graph) {
        java.util.List<int[]> list = new java.util.ArrayList<int[]>();
        for (int[] edge : graph.edges()) {
            list.add(edge);
        }
        return list;
    }

    private static int countIn(EdgeWeightedDigraph graph, int from, int to) {
        int count = 0;
        for (DirectedEdge edge : graph.adj(from)) {
            if (edge.to() == to) {
                count++;
            }
        }
        return count;
    }
}
