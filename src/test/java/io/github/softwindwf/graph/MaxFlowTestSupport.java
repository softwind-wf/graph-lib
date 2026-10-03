package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 最大流测试的共用工具。
 *
 * <p>这里放的是<b>独立参照物</b>,与三种算法的实现无关:</p>
 * <ol>
 *   <li>{@link #bruteForceMinCut} —— 枚举所有"把源点与汇点分开"的顶点子集,
 *       直接算最小割容量。由最大流最小割定理,它必须等于最大流值,
 *       这是最强的独立验证(不依赖任何算法);</li>
 *   <li>{@link #assertFeasibleFlow} —— 逐条边检查容量约束、逐个顶点检查流量守恒、
 *       再核对流值,确保算法给出的确实是<b>一个合法的流</b>而不只是一个数字;</li>
 *   <li>{@link #randomNetwork} —— 随机流网络(保证源点无入边、汇点无出边,便于对拍)。</li>
 * </ol>
 */
final class MaxFlowTestSupport {

    /** 浮点比较容差 */
    static final double EPS = 1e-9;

    private MaxFlowTestSupport() {
    }

    /**
     * 暴力枚举最小割:遍历所有"含 s、不含 t"的顶点集合 S,取割容量的最小值。
     * 只适合小图(V ≤ 20 左右)。
     *
     * @param network 流网络
     * @param source  源点
     * @param sink    汇点
     * @return 最小割容量
     */
    static double bruteForceMinCut(FlowNetwork network, int source, int sink) {
        int V = network.V();
        double best = Double.POSITIVE_INFINITY;
        for (int mask = 0; mask < (1 << V); mask++) {
            if ((mask & (1 << source)) == 0 || (mask & (1 << sink)) != 0) {
                continue;                                  // 必须含 s、不含 t
            }
            double capacity = 0.0;
            for (FlowEdge edge : network.edges()) {
                boolean fromInS = (mask & (1 << edge.from())) != 0;
                boolean toInS = (mask & (1 << edge.to())) != 0;
                if (fromInS && !toInS) {
                    capacity += edge.capacity();
                }
            }
            best = Math.min(best, capacity);
        }
        return best;
    }

    /**
     * 独立检查"当前流量分布确实是一个合法的、值为 expectedValue 的流"。
     *
     * <ul>
     *   <li>容量约束:{@code 0 ≤ flow ≤ capacity};</li>
     *   <li>流量守恒:除 s、t 外每个顶点 流入 = 流出;</li>
     *   <li>流值:s 的净流出 = expectedValue。</li>
     * </ul>
     *
     * @param network       流网络(已跑过算法)
     * @param source        源点
     * @param sink          汇点
     * @param expectedValue 期望的流值
     */
    static void assertFeasibleFlow(FlowNetwork network, int source, int sink, double expectedValue) {
        int V = network.V();
        for (FlowEdge edge : network.edges()) {
            assertTrue(edge.flow() >= -EPS, "流量不应为负: " + edge);
            assertTrue(edge.flow() <= edge.capacity() + EPS, "流量不应超过容量: " + edge);
        }
        double[] netOut = new double[V];
        for (FlowEdge edge : network.edges()) {
            netOut[edge.from()] += edge.flow();
            netOut[edge.to()] -= edge.flow();
        }
        for (int v = 0; v < V; v++) {
            if (v != source && v != sink) {
                assertEquals(0.0, netOut[v], 1e-6, "顶点 " + v + " 不满足流量守恒");
            }
        }
        assertEquals(expectedValue, netOut[source], 1e-6, "源点的净流出应等于流值");
        assertEquals(-expectedValue, netOut[sink], 1e-6, "汇点的净流入应等于流值");
    }

    /**
     * 随机流网络:编号 0 为源点,{@code V-1} 为汇点;源点只出不进、汇点只进不出,
     * 保证任意顶点都能从源点到达(便于三种算法对拍)。
     *
     * @param rnd         随机源
     * @param V           顶点数(≥ 2)
     * @param edgeCount   内部弧的目标条数
     * @param maxCapacity 容量上限(容量取 1..maxCapacity 的整数,便于整数流)
     * @return 随机流网络
     */
    static FlowNetwork randomNetwork(Random rnd, int V, int edgeCount, int maxCapacity) {
        return GraphGenerator.flowNetwork(rnd, V, edgeCount, maxCapacity);
    }

    /** 把网络复制成 algs4 的流网络(用于与参考实现对拍) */
    static edu.princeton.cs.algs4.FlowNetwork toReference(FlowNetwork network) {
        edu.princeton.cs.algs4.FlowNetwork reference =
                new edu.princeton.cs.algs4.FlowNetwork(network.V());
        for (FlowEdge edge : network.edges()) {
            reference.addEdge(new edu.princeton.cs.algs4.FlowEdge(
                    edge.from(), edge.to(), edge.capacity()));
        }
        return reference;
    }

    /** 把边收集成可比较的字符串列表:{@code "from->to flow/capacity"} */
    static List<String> flowStrings(FlowNetwork network) {
        List<String> list = new ArrayList<String>();
        for (FlowEdge edge : network.edges()) {
            list.add(String.format("%d->%d %.2f/%.2f", edge.from(), edge.to(),
                    edge.flow(), edge.capacity()));
        }
        return list;
    }
}
