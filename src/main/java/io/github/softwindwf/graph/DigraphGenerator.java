package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 随机图生成器(有向图 / 有向带权图):与 {@link GraphGenerator} 一一对应。
 *
 * <p>有向图比无向图多了一个"结构性质"的关键维度 —— <b>有没有环</b>,
 * 而很多算法(DAG 最短路、拓扑排序、关键路径)只在无环时成立,所以生成器专门提供:</p>
 * <ul>
 *   <li>{@link #dag} / {@link #edgeWeightedDag} —— 只从小编号指向大编号,<b>必然无环</b>;</li>
 *   <li>{@link #anyEdges} / {@link #edgeWeighted} —— 任意弧(自环与平行边允许,可能成环);</li>
 *   <li>{@link #simple} —— 不含自环与平行边;</li>
 *   <li>{@link #complete} / {@link #path} / {@link #cycle} —— 形状确定的有向图(便于手算核对)。</li>
 * </ul>
 *
 * <pre>
 * Random rnd = new Random(42);
 * Digraph dag = DigraphGenerator.dag(rnd, 12, 20);                    // 保证无环
 * EdgeWeightedDigraph w = DigraphGenerator.edgeWeightedDag(rnd, 12, 20, true);   // 带负权的最长/最短路实验
 * </pre>
 *
 * @see Digraph
 * @see EdgeWeightedDigraph
 * @see GraphGenerator
 */
public final class DigraphGenerator {

    private DigraphGenerator() {
    }

    /**
     * 随机有向图:<b>允许自环与平行边</b>,弧数严格等于 {@code E}。
     *
     * @param rnd 随机源
     * @param V   顶点数
     * @param E   弧数
     * @return 有向图
     * @throws IllegalArgumentException 参数非法
     */
    public static Digraph anyEdges(Random rnd, int V, int E) {
        checkArguments(rnd, V);
        Digraph graph = new Digraph(V);
        for (int i = 0; i < Math.max(E, 0); i++) {
            graph.addEdge(rnd.nextInt(V), rnd.nextInt(V));
        }
        return graph;
    }

    /**
     * 随机简单有向图:不含自环、不含平行边(同一对顶点最多一条弧,方向不同算两条)。
     *
     * @param rnd 随机源
     * @param V   顶点数
     * @param E   期望弧数(超过 V(V−1) 时截断)
     * @return 简单有向图
     * @throws IllegalArgumentException 参数非法
     */
    public static Digraph simple(Random rnd, int V, int E) {
        checkArguments(rnd, V);
        int maxEdges = V * (V - 1);
        int target = Math.min(Math.max(E, 0), maxEdges);
        Digraph graph = new Digraph(V);
        long guard = 0;
        while (graph.E() < target && guard++ < 100L * (target + 1) * (target + 1)) {
            int v = rnd.nextInt(V);
            int w = rnd.nextInt(V);
            if (v != w && !graph.hasEdge(v, w)) {
                graph.addEdge(v, w);
            }
        }
        return graph;
    }

    /**
     * 随机<b>有向无环图</b>:只从小号顶点指向大号顶点(必然无环,但弧的分布是有偏的)。
     *
     * @param rnd 随机源
     * @param V   顶点数
     * @param E   弧数(可以超过 C(V,2),因为允许平行边)
     * @return 有向无环图
     * @throws IllegalArgumentException 参数非法
     */
    public static Digraph dag(Random rnd, int V, int E) {
        checkArguments(rnd, V);
        Digraph graph = new Digraph(V);
        if (V < 2) {
            return graph;
        }
        for (int i = 0; i < Math.max(E, 0); i++) {
            int from = rnd.nextInt(V - 1);
            int to = from + 1 + rnd.nextInt(V - from - 1);
            graph.addEdge(from, to);
        }
        return graph;
    }

    /**
     * 完全有向图:每一对不同顶点之间两个方向各一条弧。
     *
     * @param V 顶点数
     * @return 完全有向图
     * @throws IllegalArgumentException 顶点数为负
     */
    public static Digraph complete(int V) {
        if (V < 0) {
            throw new IllegalArgumentException("顶点数不能为负: " + V);
        }
        Digraph graph = new Digraph(V);
        for (int v = 0; v < V; v++) {
            for (int w = 0; w < V; w++) {
                if (v != w) {
                    graph.addEdge(v, w);
                }
            }
        }
        return graph;
    }

    /**
     * 有向路径 0→1→…→(V−1)(无环)。
     *
     * @param V 顶点数
     * @return 有向路径
     * @throws IllegalArgumentException 顶点数为负
     */
    public static Digraph path(int V) {
        if (V < 0) {
            throw new IllegalArgumentException("顶点数不能为负: " + V);
        }
        Digraph graph = new Digraph(V);
        for (int v = 0; v + 1 < V; v++) {
            graph.addEdge(v, v + 1);
        }
        return graph;
    }

    /**
     * 有向环 0→1→…→(V−1)→0(每个顶点入度 = 出度 = 1)。
     *
     * @param V 顶点数(≥ 3 才是真的环)
     * @return 有向环
     * @throws IllegalArgumentException 顶点数为负
     */
    public static Digraph cycle(int V) {
        if (V < 0) {
            throw new IllegalArgumentException("顶点数不能为负: " + V);
        }
        Digraph graph = new Digraph(V);
        for (int v = 0; v + 1 < V; v++) {
            graph.addEdge(v, v + 1);
        }
        if (V >= 3) {
            graph.addEdge(V - 1, 0);
        }
        return graph;
    }

    /**
     * 随机有向带权图(允许自环与平行边;权值可负)。
     *
     * @param rnd           随机源
     * @param V             顶点数
     * @param E             弧数
     * @param allowNegative 是否允许负权
     * @return 有向带权图
     * @throws IllegalArgumentException 参数非法
     */
    public static EdgeWeightedDigraph edgeWeighted(Random rnd, int V, int E, boolean allowNegative) {
        checkArguments(rnd, V);
        EdgeWeightedDigraph graph = new EdgeWeightedDigraph(V);
        for (int i = 0; i < Math.max(E, 0); i++) {
            graph.addEdge(rnd.nextInt(V), rnd.nextInt(V), randomWeight(rnd, allowNegative));
        }
        return graph;
    }

    /**
     * 随机有向带权图,且<b>权值全为正</b>(适合 Dijkstra 的正常输入)。
     *
     * @param rnd 随机源
     * @param V   顶点数
     * @param E   期望弧数(超过 V(V−1) 时截断)
     * @return 正权有向图
     * @throws IllegalArgumentException 参数非法
     */
    public static EdgeWeightedDigraph edgeWeightedPositive(Random rnd, int V, int E) {
        checkArguments(rnd, V);
        int maxEdges = V * (V - 1);
        int target = Math.min(Math.max(E, 0), maxEdges);
        EdgeWeightedDigraph graph = new EdgeWeightedDigraph(V);
        long guard = 0;
        while (graph.E() < target && guard++ < 100L * (target + 1) * (target + 1)) {
            int v = rnd.nextInt(V);
            int w = rnd.nextInt(V);
            if (v != w && !graph.hasEdge(v, w)) {
                graph.addEdge(v, w, 1 + rnd.nextDouble() * 9);
            }
        }
        return graph;
    }

    /**
     * 随机<b>带权有向无环图</b>:只从小号指向大号,必然无环(可含负权,便于实验 DAG 最短路/最长路)。
     *
     * @param rnd           随机源
     * @param V             顶点数
     * @param E             弧数
     * @param allowNegative 是否允许负权
     * @return 带权有向无环图
     * @throws IllegalArgumentException 参数非法
     */
    public static EdgeWeightedDigraph edgeWeightedDag(Random rnd, int V, int E, boolean allowNegative) {
        checkArguments(rnd, V);
        EdgeWeightedDigraph graph = new EdgeWeightedDigraph(V);
        if (V < 2) {
            return graph;
        }
        for (int i = 0; i < Math.max(E, 0); i++) {
            int from = rnd.nextInt(V - 1);
            int to = from + 1 + rnd.nextInt(V - from - 1);
            graph.addEdge(from, to, randomWeight(rnd, allowNegative));
        }
        return graph;
    }

    /**
     * 按概率生成带权有向无环图:对每一对 {@code from < to} 的顶点,以概率 {@code p} 加一条弧。
     * 用于"弧数不固定但必然无环"的实验。
     *
     * @param rnd           随机源
     * @param V             顶点数
     * @param p             每对顶点之间加弧的概率,取 {@code [0, 1]}
     * @param allowNegative 是否允许负权
     * @return 带权有向无环图
     * @throws IllegalArgumentException 参数非法
     */
    public static EdgeWeightedDigraph edgeWeightedDagByProbability(Random rnd, int V, double p,
                                                                   boolean allowNegative) {
        checkArguments(rnd, V);
        if (p < 0 || p > 1) {
            throw new IllegalArgumentException("概率应在 [0, 1] 内,当前为 " + p);
        }
        EdgeWeightedDigraph graph = new EdgeWeightedDigraph(V);
        for (int from = 0; from < V; from++) {
            for (int to = from + 1; to < V; to++) {
                if (rnd.nextDouble() < p) {
                    graph.addEdge(from, to, randomWeight(rnd, allowNegative));
                }
            }
        }
        return graph;
    }

    /** 带权图的随机权值:[−2, 4) 或 (1, 10) */
    private static double randomWeight(Random rnd, boolean allowNegative) {
        return allowNegative ? -2 + rnd.nextDouble() * 6 : 1 + rnd.nextDouble() * 9;
    }

    private static void checkArguments(Random rnd, int V) {
        if (rnd == null) {
            throw new IllegalArgumentException("随机源不能为 null");
        }
        if (V < 0) {
            throw new IllegalArgumentException("顶点数不能为负: " + V);
        }
    }

    /**
     * 演示:生成几种形状并打印它们的结构特征(是否有环、是否有正/负权)。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        Random rnd = new Random(20261004L);
        print("anyEdges(8, 12)", anyEdges(rnd, 8, 12));
        print("simple(8, 12)", simple(rnd, 8, 12));
        print("dag(8, 12)", dag(rnd, 8, 12));
        print("cycle(8)", cycle(8));
        print("complete(5)", complete(5));

        EdgeWeightedDigraph weightedDag = edgeWeightedDag(rnd, 8, 12, true);
        System.out.println("edgeWeightedDag(8, 12, 允许负权): V=" + weightedDag.V()
                + ", E=" + weightedDag.E() + ",有环? "
                + new TopologicalSort(weightedDag.toDigraph()).hasCycle());
        System.out.println("  最小弧权 " + minWeight(weightedDag));

        EdgeWeightedDigraph byProbability = edgeWeightedDagByProbability(rnd, 10, 0.3, false);
        System.out.println("edgeWeightedDagByProbability(10, 0.3, 全正): V=" + byProbability.V()
                + ", E=" + byProbability.E());
        System.out.println("  所有顶点可达性检查(从 0 出发可达 "
                + new DirectedPaths(byProbability.toDigraph(), 0).count() + " 个顶点)");
    }

    private static void print(String label, Digraph graph) {
        boolean cyclic = new DirectedCycle(graph).hasCycle();
        System.out.println(label + ": V=" + graph.V() + ", E=" + graph.E()
                + ",最大出度 " + graph.maxOutDegree() + ",有环? " + cyclic
                + ",SCC 数 " + new StronglyConnectedComponents(graph).count());
    }

    private static double minWeight(EdgeWeightedDigraph graph) {
        double min = Double.POSITIVE_INFINITY;
        List<DirectedEdge> edges = new ArrayList<DirectedEdge>();
        for (DirectedEdge edge : graph.edges()) {
            edges.add(edge);
            min = Math.min(min, edge.weight());
        }
        return edges.isEmpty() ? Double.NaN : min;
    }
}
