package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 随机图生成器(无向图):给测试与实验提供"各种形状"的图。
 *
 * <p><b>为什么要单独做</b>:以前每个测试文件都各自写一遍"随机图"的代码,重复且语义不一致
 * (有的允许自环与平行边、有的不允许;有的图权值互异、有的可能重复)。
 * 集中到本类之后,每个方法的名字就把语义说清楚了,测试里直接调用:</p>
 * <ul>
 *   <li>{@link #simple} —— <b>简单图</b>:没有自环、没有平行边,边数尽量贴近要求(受组合数上限约束);</li>
 *   <li>{@link #anyEdges} —— <b>任意图</b>:自环与平行边都允许(边数严格等于要求);</li>
 *   <li>{@link #connected} —— 先随机生成一棵生成树保证连通,再加额外的边;</li>
 *   <li>{@link #complete}/{@link #path}/{@link #cycle}/{@link #tree}/{@link #bipartite} —— 形状确定的图;</li>
 *   <li>{@link #edgeWeighted} —— 带权(权值可负、可重复);{@link #edgeWeightedDistinctWeights} —— 权值<b>互异</b>
 *       (于是最小生成树唯一,便于两两对照);</li>
 *   <li>{@link #flowNetwork} —— 流网络(0 是源点、V−1 是汇点,源点只出不进、汇点只进不出)。</li>
 * </ul>
 *
 * <p>所有方法都要求传入 {@link Random},这样才能用固定种子复现实验(测试里全部显式给种子)。</p>
 *
 * <pre>
 * Random rnd = new Random(42);
 * UndirectedGraph g = GraphGenerator.connected(rnd, 20, 40);
 * EdgeWeightedGraph w = GraphGenerator.edgeWeightedDistinctWeights(rnd, 20, 40);
 * </pre>
 *
 * @see UndirectedGraph
 * @see EdgeWeightedGraph
 * @see DigraphGenerator
 * @see FlowNetwork
 */
public final class GraphGenerator {

    private GraphGenerator() {
    }

    /**
     * 随机<b>简单</b>无向图:没有自环、没有平行边。
     *
     * @param rnd 随机源
     * @param V   顶点数
     * @param E   期望边数(超过 C(V,2) 时会被截断到上限)
     * @return 简单图
     * @throws IllegalArgumentException 顶点数为负或随机源为 null
     */
    public static UndirectedGraph simple(Random rnd, int V, int E) {
        checkArguments(rnd, V);
        int maxEdges = V * (V - 1) / 2;
        int target = Math.min(Math.max(E, 0), maxEdges);
        UndirectedGraph graph = new UndirectedGraph(V);
        long guard = 0;
        long guardLimit = 100L * (target + 1) * (target + 1);
        while (graph.E() < target && guard++ < guardLimit) {
            int v = rnd.nextInt(V);
            int w = rnd.nextInt(V);
            if (v != w && !graph.hasEdge(v, w)) {
                graph.addEdge(v, w);
            }
        }
        return graph;
    }

    /**
     * 随机无向图:<b>允许自环与平行边</b>,边数严格等于 {@code E}。
     *
     * @param rnd 随机源
     * @param V   顶点数
     * @param E   边数(可以大于 C(V,2),因为允许平行边)
     * @return 任意图
     * @throws IllegalArgumentException 参数非法
     */
    public static UndirectedGraph anyEdges(Random rnd, int V, int E) {
        checkArguments(rnd, V);
        UndirectedGraph graph = new UndirectedGraph(V);
        for (int i = 0; i < Math.max(E, 0); i++) {
            graph.addEdge(rnd.nextInt(V), rnd.nextInt(V));
        }
        return graph;
    }

    /**
     * 随机<b>连通</b>无向图:先随机生成一棵生成树,再补 {@code E-(V-1)} 条边。
     *
     * @param rnd 随机源
     * @param V   顶点数(≥ 1)
     * @param E   边数(小于 V−1 时按 V−1 处理,保证连通)
     * @return 连通图
     * @throws IllegalArgumentException 参数非法
     */
    public static UndirectedGraph connected(Random rnd, int V, int E) {
        checkArguments(rnd, V);
        UndirectedGraph graph = tree(rnd, V);
        int target = Math.max(E, Math.max(V - 1, 0));
        long guard = 0;
        while (graph.E() < target && guard++ < 100L * (target + 1)) {
            int v = rnd.nextInt(V);
            int w = rnd.nextInt(V);
            if (v != w && !graph.hasEdge(v, w)) {
                graph.addEdge(v, w);
            }
        }
        return graph;
    }

    /**
     * 随机生成树(连通且无环)。
     *
     * @param rnd 随机源
     * @param V   顶点数
     * @return 一棵随机生成树
     * @throws IllegalArgumentException 参数非法
     */
    public static UndirectedGraph tree(Random rnd, int V) {
        checkArguments(rnd, V);
        UndirectedGraph graph = new UndirectedGraph(V);
        List<Integer> order = shuffledVertices(rnd, V);
        for (int i = 1; i < V; i++) {
            graph.addEdge(order.get(i), order.get(rnd.nextInt(i)));
        }
        return graph;
    }

    /**
     * 完全图 K_V。
     *
     * @param V 顶点数
     * @return 完全图
     * @throws IllegalArgumentException 顶点数为负
     */
    public static UndirectedGraph complete(int V) {
        if (V < 0) {
            throw new IllegalArgumentException("顶点数不能为负: " + V);
        }
        UndirectedGraph graph = new UndirectedGraph(V);
        for (int v = 0; v < V; v++) {
            for (int w = v + 1; w < V; w++) {
                graph.addEdge(v, w);
            }
        }
        return graph;
    }

    /**
     * 一条路径 0-1-2-…-(V−1)(无环、两个端点度为 1)。
     *
     * @param V 顶点数
     * @return 路径图
     * @throws IllegalArgumentException 顶点数为负
     */
    public static UndirectedGraph path(int V) {
        if (V < 0) {
            throw new IllegalArgumentException("顶点数不能为负: " + V);
        }
        UndirectedGraph graph = new UndirectedGraph(V);
        for (int v = 0; v + 1 < V; v++) {
            graph.addEdge(v, v + 1);
        }
        return graph;
    }

    /**
     * 一个环 0-1-…-(V−1)-0(所有顶点度为 2)。
     *
     * @param V 顶点数(≥ 3 才是真的环;V ≤ 2 时退化为一条边或空图)
     * @return 环图
     * @throws IllegalArgumentException 顶点数为负
     */
    public static UndirectedGraph cycle(int V) {
        if (V < 0) {
            throw new IllegalArgumentException("顶点数不能为负: " + V);
        }
        UndirectedGraph graph = new UndirectedGraph(V);
        for (int v = 0; v + 1 < V; v++) {
            graph.addEdge(v, v + 1);
        }
        if (V >= 3) {
            graph.addEdge(V - 1, 0);
        }
        return graph;
    }

    /**
     * 随机二分图:左部 {@code leftCount} 个(编号 0..leftCount−1)、右部 {@code rightCount} 个
     * (编号 leftCount..leftCount+rightCount−1),边只连接左右。
     *
     * @param rnd        随机源
     * @param leftCount  左部顶点数
     * @param rightCount 右部顶点数
     * @param E          期望边数
     * @return 二分图
     * @throws IllegalArgumentException 参数非法
     */
    public static UndirectedGraph bipartite(Random rnd, int leftCount, int rightCount, int E) {
        if (rnd == null) {
            throw new IllegalArgumentException("随机源不能为 null");
        }
        if (leftCount < 0 || rightCount < 0) {
            throw new IllegalArgumentException("顶点数不能为负");
        }
        UndirectedGraph graph = new UndirectedGraph(leftCount + rightCount);
        int maxEdges = leftCount * rightCount;
        int target = Math.min(Math.max(E, 0), maxEdges);
        long guard = 0;
        while (graph.E() < target && guard++ < 100L * (target + 1)) {
            int left = rnd.nextInt(leftCount);
            int right = leftCount + rnd.nextInt(rightCount);
            if (!graph.hasEdge(left, right)) {
                graph.addEdge(left, right);
            }
        }
        return graph;
    }

    /**
     * 随机带权图(允许自环与平行边,权值可负)。
     *
     * @param rnd          随机源
     * @param V            顶点数
     * @param E            边数
     * @param allowNegative 是否允许负权
     * @return 带权图
     * @throws IllegalArgumentException 参数非法
     */
    public static EdgeWeightedGraph edgeWeighted(Random rnd, int V, int E, boolean allowNegative) {
        checkArguments(rnd, V);
        EdgeWeightedGraph graph = new EdgeWeightedGraph(V);
        for (int i = 0; i < Math.max(E, 0); i++) {
            double weight = allowNegative ? -2 + rnd.nextDouble() * 6 : 1 + rnd.nextDouble() * 9;
            graph.addEdge(new Edge(rnd.nextInt(V), rnd.nextInt(V), weight));
        }
        return graph;
    }

    /**
     * 随机带权简单图,且<b>权值互不相同</b>(最小生成树因此唯一)。
     *
     * @param rnd 随机源
     * @param V   顶点数
     * @param E   期望边数
     * @return 权值互异的带权图
     * @throws IllegalArgumentException 参数非法
     */
    public static EdgeWeightedGraph edgeWeightedDistinctWeights(Random rnd, int V, int E) {
        checkArguments(rnd, V);
        EdgeWeightedGraph graph = new EdgeWeightedGraph(V);
        int maxEdges = V * (V - 1) / 2;
        int target = Math.min(Math.max(E, 0), maxEdges);
        double[] weights = distinctWeights(rnd, target);
        int index = 0;
        long guard = 0;
        while (graph.E() < target && guard++ < 100L * (target + 1) * (target + 1)) {
            int v = rnd.nextInt(V);
            int w = rnd.nextInt(V);
            if (v != w && !graph.hasEdge(v, w)) {
                graph.addEdge(new Edge(v, w, weights[index++]));
            }
        }
        return graph;
    }

    /**
     * 随机带权简单图,权值全为正(适合 Dijkstra / Prim 的"正常输入")。
     *
     * @param rnd 随机源
     * @param V   顶点数
     * @param E   期望边数
     * @return 正权带权图
     * @throws IllegalArgumentException 参数非法
     */
    public static EdgeWeightedGraph edgeWeightedPositive(Random rnd, int V, int E) {
        checkArguments(rnd, V);
        EdgeWeightedGraph graph = new EdgeWeightedGraph(V);
        int maxEdges = V * (V - 1) / 2;
        int target = Math.min(Math.max(E, 0), maxEdges);
        long guard = 0;
        while (graph.E() < target && guard++ < 100L * (target + 1) * (target + 1)) {
            int v = rnd.nextInt(V);
            int w = rnd.nextInt(V);
            if (v != w && !graph.hasEdge(v, w)) {
                graph.addEdge(new Edge(v, w, 1 + rnd.nextDouble() * 9));
            }
        }
        return graph;
    }

    /**
     * 随机流网络:0 号顶点是源点、{@code V-1} 号是汇点;源点只出不进、汇点只进不出,
     * 其余顶点随机连边,容量取 {@code 1..maxCapacity} 的整数。
     *
     * @param rnd         随机源
     * @param V           顶点数(≥ 2)
     * @param E           "内部"弧的目标条数
     * @param maxCapacity 容量上限(≥ 1)
     * @return 流网络
     * @throws IllegalArgumentException 参数非法
     */
    public static FlowNetwork flowNetwork(Random rnd, int V, int E, int maxCapacity) {
        if (rnd == null) {
            throw new IllegalArgumentException("随机源不能为 null");
        }
        if (V < 2) {
            throw new IllegalArgumentException("流网络至少需要 2 个顶点(源点与汇点),当前 " + V);
        }
        if (maxCapacity < 1) {
            throw new IllegalArgumentException("容量上限至少为 1,当前 " + maxCapacity);
        }
        FlowNetwork network = new FlowNetwork(V);
        int sink = V - 1;
        for (int v = 1; v < sink; v++) {
            network.addEdge(0, v, 1 + rnd.nextInt(maxCapacity));
            network.addEdge(v, sink, 1 + rnd.nextInt(maxCapacity));
        }
        for (int i = 0; i < Math.max(E, 0); i++) {
            int from = rnd.nextInt(V - 1);
            int to = from + 1 + rnd.nextInt(sink - from);
            if (from != to) {
                network.addEdge(from, to, 1 + rnd.nextInt(maxCapacity));
            }
        }
        return network;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 生成 count 个互不相同的权值(用于"最小生成树唯一"的实验) */
    private static double[] distinctWeights(Random rnd, int count) {
        List<Integer> values = new ArrayList<Integer>(count);
        for (int i = 0; i < count; i++) {
            values.add(i + 1);
        }
        Collections.shuffle(values, rnd);
        double[] weights = new double[count];
        for (int i = 0; i < count; i++) {
            weights[i] = values.get(i) + rnd.nextDouble() * 0.5;
        }
        return weights;
    }

    private static List<Integer> shuffledVertices(Random rnd, int V) {
        List<Integer> order = new ArrayList<Integer>(V);
        for (int v = 0; v < V; v++) {
            order.add(v);
        }
        Collections.shuffle(order, rnd);
        return order;
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
     * 演示:同一颗种子下生成几种形状,打印它们的规模与度数特征。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        Random rnd = new Random(20261004L);
        print("simple(10, 15)", simple(rnd, 10, 15));
        print("connected(10, 12)", connected(rnd, 10, 12));
        print("tree(10)", tree(rnd, 10));
        print("cycle(10)", cycle(10));
        print("complete(6)", complete(6));
        print("bipartite(4, 5, 8)", bipartite(rnd, 4, 5, 8));

        EdgeWeightedGraph weighted = edgeWeightedDistinctWeights(rnd, 10, 15);
        System.out.println("edgeWeightedDistinctWeights(10, 15): V=" + weighted.V()
                + ", E=" + weighted.E() + ",最大度 " + weighted.maxDegree());
        System.out.println("  带权边(前 5 条): " + firstEdges(weighted, 5));

        FlowNetwork flow = flowNetwork(rnd, 8, 10, 5);
        System.out.println("flowNetwork(8, 10, 5): V=" + flow.V() + ", E=" + flow.E()
                + ",源点出容量 " + flow.outCapacity(0) + ",汇点入容量 " + flow.inCapacity(flow.V() - 1));
    }

    private static void print(String label, UndirectedGraph graph) {
        System.out.println(label + ": V=" + graph.V() + ", E=" + graph.E()
                + ",最大度 " + graph.maxDegree() + ",最小度 " + graph.minDegree()
                + ",分量数 " + new ConnectedComponents(graph).count());
    }

    private static String firstEdges(EdgeWeightedGraph graph, int limit) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Edge edge : graph.edges()) {
            if (shown++ >= limit) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(edge);
        }
        return sb.toString();
    }
}
