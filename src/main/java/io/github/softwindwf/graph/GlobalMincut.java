package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 全局最小割(Global Minimum Cut,Stoer–Wagner 算法):<b>不需要指定源点与汇点</b>,
 * 直接把图切成两块,使横跨两块的边权之和最小。
 *
 * <p><b>与 s-t 最小割的关系</b>:{@link FordFulkerson} / {@link Dinic} 求的是"给定源汇"的最小割;
 * 全局最小割要对所有可能的 (s,t) 取最小 —— 暴力做法是跑 V(V−1)/2 次最大流,
 * Stoer–Wagner 一趟 Θ(V³)(稠密矩阵实现)就够了。</p>
 *
 * <p><b>算法核心:最大邻接搜索(Maximum Adjacency Ordering)+ 收缩</b></p>
 * <ol>
 *   <li>每一轮:从任意顶点出发,每次挑"与已选集合连接最紧"的顶点加入,记下顺序
 *       {@code a₁, a₂, …, aₙ};</li>
 *   <li>最后一个顶点 {@code aₙ} 与其余部分的割容量恰好等于它被选中时的连接权重
 *       —— 这就是<b>这一阶段的割值</b>(cut-of-the-phase);用小者更新答案;</li>
 *   <li>把 {@code aₙ} 收缩进 {@code aₙ₋₁}(权重相加),顶点数减一,回到第 1 步;</li>
 *   <li>循环到只剩 2 个顶点,取到的最小值就是全局最小割。</li>
 * </ol>
 *
 * <p><b>为什么对</b>:存在性定理保证"全局最小割"必然在某一阶段作为"该阶段的割"出现
 * (Stoer–Wagner 定理),而收缩不会破坏这个性质 —— 这就是它只需要 Θ(V²) 次权重加法、
 * 总共 Θ(V³) 的原因。实现上用一个 V×V 的权重矩阵表示当前收缩后的图
 * (平行边权值相加,自环不影响割所以直接丢弃)。</p>
 *
 * <pre>
 * GlobalMincut mincut = new GlobalMincut(GraphIO.readWeightedFile("tinyEWG.txt"));
 * mincut.weight();   // 全局最小割容量
 * mincut.sideA();    // 划分的一侧
 * mincut.cut();      // 组成这个割的边
 * </pre>
 *
 * @see FordFulkerson
 * @see Dinic
 * @see MinCut
 * @see EdgeWeightedGraph
 * @see <a href="https://algs4.cs.princeton.edu/64maxflow">Algorithms, 4th Edition, Section 6.4</a>
 */
public final class GlobalMincut {

    /** 浮点比较容差 */
    private static final double EPS = 1e-9;

    /** 顶点数上限:本实现用 V×V 矩阵,再大就不适合(Θ(V²) 空间) */
    private static final int MAX_VERTICES = 2048;

    /** 被分析的图 */
    private final EdgeWeightedGraph graph;

    /** 全局最小割的容量 */
    private final double weight;

    /** sideA[v] = v 是否在最优划分的 A 侧 */
    private final boolean[] sideA;

    /** 组成最小割的边(一端在 A 侧、另一端在 B 侧) */
    private final List<Edge> cutEdges;

    /** 收缩阶段的轮数(= V − 1,便于观察算法过程) */
    private final int phaseCount;

    /**
     * 求全局最小割。
     *
     * @param graph 加权无向图,不能为 null,所有边权必须非负
     * @throws IllegalArgumentException 参数为 null、顶点数超过上限或存在负权边
     */
    public GlobalMincut(EdgeWeightedGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (graph.V() > MAX_VERTICES) {
            throw new IllegalArgumentException("本实现用 Θ(V²) 的权重矩阵,顶点数 " + graph.V()
                    + " 超过上限 " + MAX_VERTICES);
        }
        for (Edge edge : graph.edges()) {
            if (edge.weight() < 0) {
                throw new IllegalArgumentException("全局最小割要求边权非负,但存在负权边 " + edge);
            }
        }
        this.graph = graph;
        int V = graph.V();

        // 1. 聚合成权重矩阵:平行边相加,自环丢弃(自环永远不跨越任何割)
        double[][] w = new double[V][V];
        for (Edge edge : graph.edges()) {
            int a = edge.either();
            int b = edge.other(a);
            if (a != b) {
                w[a][b] += edge.weight();
                w[b][a] += edge.weight();
            }
        }

        // 2. 每个"超级顶点"包含哪些原始顶点
        List<List<Integer>> groups = new ArrayList<List<Integer>>(V);
        for (int v = 0; v < V; v++) {
            List<Integer> group = new ArrayList<Integer>();
            group.add(v);
            groups.add(group);
        }
        boolean[] merged = new boolean[V];
        int remaining = V;
        int phases = 0;
        double best = Double.POSITIVE_INFINITY;
        List<Integer> bestGroup = new ArrayList<Integer>();

        while (remaining > 1) {
            phases++;
            double[] connection = new double[V];            // 到"已选集合"的连接权重
            boolean[] added = new boolean[V];
            int previous = -1;
            int last = -1;
            for (int step = 0; step < remaining; step++) {
                int selected = -1;
                for (int i = 0; i < V; i++) {
                    if (!merged[i] && !added[i] && (selected < 0 || connection[i] > connection[selected])) {
                        selected = i;
                    }
                }
                added[selected] = true;
                if (step == remaining - 1) {
                    last = selected;                        // a_n:本阶段被切出去的那个
                }
                else {
                    previous = selected;                    // a_{n-1}:last 将被收缩进来
                }
                for (int i = 0; i < V; i++) {
                    if (!merged[i] && !added[i]) {
                        connection[i] += w[selected][i];
                    }
                }
            }

            double cutOfPhase = connection[last];
            if (cutOfPhase < best - EPS) {
                best = cutOfPhase;
                bestGroup = new ArrayList<Integer>(groups.get(last));
            }

            // 3. 把 last 收缩进 previous
            for (int i = 0; i < V; i++) {
                if (!merged[i] && i != last && i != previous) {
                    w[previous][i] += w[last][i];
                    w[i][previous] += w[i][last];
                }
            }
            groups.get(previous).addAll(groups.get(last));
            merged[last] = true;
            remaining--;
        }

        this.phaseCount = phases;
        this.weight = V <= 1 ? 0.0 : best;

        boolean[] inA = new boolean[V];
        for (int v : bestGroup) {
            inA[v] = true;
        }
        this.sideA = inA;

        List<Edge> crossing = new ArrayList<Edge>();
        for (Edge edge : graph.edges()) {
            int a = edge.either();
            int b = edge.other(a);
            if (inA[a] != inA[b]) {
                crossing.add(edge);
            }
        }
        this.cutEdges = java.util.Collections.unmodifiableList(crossing);
    }

    /**
     * @return 顶点数
     */
    public int V() {
        return graph.V();
    }

    /**
     * @return 边数
     */
    public int E() {
        return graph.E();
    }

    /**
     * @return 全局最小割的容量(空图或单顶点图按 0 处理)
     */
    public double weight() {
        return weight;
    }

    /**
     * @param v 顶点编号
     * @return v 是否在 A 侧
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean inSideA(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
        return sideA[v];
    }

    /**
     * @return A 侧顶点(升序)
     */
    public List<Integer> sideA() {
        return side(true);
    }

    /**
     * @return B 侧顶点(升序)
     */
    public List<Integer> sideB() {
        return side(false);
    }

    private List<Integer> side(boolean wantA) {
        List<Integer> vertices = new ArrayList<Integer>();
        for (int v = 0; v < graph.V(); v++) {
            if (sideA[v] == wantA) {
                vertices.add(v);
            }
        }
        return vertices;
    }

    /**
     * @return 组成最小割的边(横跨 A/B 两侧的原始边)
     */
    public List<Edge> cut() {
        return new ArrayList<Edge>(cutEdges);
    }

    /**
     * @return 组成最小割的边上的权值之和(应当等于 {@link #weight()})
     */
    public double cutWeight() {
        double sum = 0.0;
        for (Edge edge : cutEdges) {
            sum += edge.weight();
        }
        return sum;
    }

    /**
     * @return 收缩阶段数(等于 V − 1)
     */
    public int phaseCount() {
        return phaseCount;
    }

    /**
     * @return 形如 {@code GlobalMincut: 容量 1.81,A 侧 [0, 2, 7],组成边 [0-7 0.16, …]}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("GlobalMincut: 容量 ").append(String.format("%.2f", weight));
        sb.append(",A 侧 ").append(sideA()).append(",B 侧 ").append(sideB());
        sb.append(System.lineSeparator()).append("  组成边 ").append(cutEdges);
        return sb.toString();
    }

    /**
     * 演示:对 tinyEWG.txt 求全局最小割,并与"枚举所有顶点对跑最大流取最小"的结果对照
     * (小图上这可以作为独立验证)。
     *
     * @param args 可选:加权图数据文件路径
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyEWG.txt";
        EdgeWeightedGraph graph = GraphIO.readWeightedFile(path);
        GlobalMincut mincut = new GlobalMincut(graph);
        System.out.println("数据文件: " + path + "(V=" + graph.V() + ", E=" + graph.E() + ")");
        System.out.println(mincut);
        System.out.println("割上权值之和 = " + mincut.cutWeight() + ",阶段数 = " + mincut.phaseCount());

        if (graph.V() <= 60) {
            double best = Double.POSITIVE_INFINITY;
            int bestPair = -1;
            for (int s = 0; s < graph.V(); s++) {
                for (int t = s + 1; t < graph.V(); t++) {
                    FlowNetwork network = new FlowNetwork(graph.V());
                    for (Edge edge : graph.edges()) {
                        int a = edge.either();
                        int b = edge.other(a);
                        network.addEdge(a, b, edge.weight());
                        network.addEdge(b, a, edge.weight());
                    }
                    double value = new Dinic(network, s, t).value();
                    if (value < best) {
                        best = value;
                        bestPair = s * 100 + t;
                    }
                }
            }
            System.out.println("对照:枚举所有 (s,t) 跑最大流,最小值 = " + best
                    + "(某对源汇 =" + bestPair + "),与全局最小割一致: "
                    + (Math.abs(best - mincut.weight()) < 1e-9));
        }
    }
}
