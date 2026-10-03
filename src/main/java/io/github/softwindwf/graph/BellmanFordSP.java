package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Bellman–Ford 算法:一般有向加权图(允许负权)的单源最短路,并能<b>检测负环</b>。Θ(V·E)。
 *
 * <p><b>核心想法(动态规划 / 逐轮逼近)</b>:第 k 轮结束时,{@code distTo[v]} 已经等于
 * "最多用 k 条弧"的最短距离。没有负环时,最短路径最多用 V−1 条弧,所以扫 V−1 轮必然收敛;
 * 若第 V 轮还能把某条弧松弛掉,说明存在一条"越走越短"的环 —— <b>负环</b>,
 * 此时最短路径不存在(可以绕环无限变短)。</p>
 *
 * <table border="1" summary="Bellman-Ford 的两种实现">
 *   <tr><th>模式</th><th>做法</th><th>最坏时间</th><th>特点</th></tr>
 *   <tr><td>{@link Mode#SWEEP}</td>
 *       <td>每轮把<b>全部弧</b>扫一遍,共 V−1 轮</td>
 *       <td>Θ(V·E)</td>
 *       <td>教科书版,最好讲;轮数有明确上界,便于证明</td></tr>
 *   <tr><td>{@link Mode#QUEUE}</td>
 *       <td>SPFA:只有"距离刚被改小"的顶点才重新入队,其余不动</td>
 *       <td>最坏 Θ(V·E),平均快得多</td>
 *       <td>默认。实测(见测试)在随机图上松弛次数远少于全扫</td></tr>
 * </table>
 *
 * <p><b>负环的语义</b>(本类与其它类的约定):</p>
 * <ul>
 *   <li>只把<b>从源点可达</b>的负环算作"有问题"(不可达的负环影响不到源点的最短路);</li>
 *   <li>{@link #hasNegativeCycle()} 是唯一的"先检查"入口;存在负环时
 *       {@link #distTo(int)}、{@link #hasPathTo(int)}、{@link #edgeTo(int)}、{@link #pathTo(int)}
 *       一律抛 {@link IllegalStateException} —— 因为此时"最短路径"根本不存在,
 *       返回任何数字都是误导。(这一点与 {@link FloydWarshall} 不同:那里会照常返回整张矩阵,
 *       只在文档里提醒不可信;本类选择直接报错,宁可吵也不要错。)</li>
 *   <li>{@link #negativeCycle()} 给出一个具体的负环(边列表,首尾相接)。</li>
 * </ul>
 *
 * <p><b>负环是怎么找出来的</b>:松弛过程中记录 {@code edgeTo} 数组,它就构成"前驱子图";
 * 一旦判定存在负环,前驱子图里必然含环,把 {@link DirectedCycle} 用上去取一个环
 * (并把边映射回 {@link DirectedEdge}),再校验其权值之和确实为负。</p>
 *
 * <pre>
 * BellmanFordSP sp = new BellmanFordSP(g, 0);
 * if (sp.hasNegativeCycle()) {
 *     sp.negativeCycle();        // 一个负环
 * } else {
 *     sp.distTo(6);              // 正常查最短路
 * }
 * </pre>
 *
 * @see AcyclicSP
 * @see DijkstraSP
 * @see FloydWarshall
 * @see Johnson
 * @see <a href="https://algs4.cs.princeton.edu/44sp">Algorithms, 4th Edition, Section 4.4</a>
 */
public final class BellmanFordSP {

    /** Bellman–Ford 的两种实现 */
    public enum Mode {

        /** SPFA:只用"刚被改小"的顶点去扩散(默认) */
        QUEUE,

        /** 每轮全弧扫描,共 V−1 轮(教科书版) */
        SWEEP
    }

    /** 被求解的图 */
    private final EdgeWeightedDigraph graph;

    /** 源点 */
    private final int source;

    /** 实现方式 */
    private final Mode mode;

    /** distTo[v] = 从源点到 v 的最短距离(存在可达负环时不可信) */
    private final double[] distTo;

    /** edgeTo[v] = 最短路径上通向 v 的最后一条边 */
    private final DirectedEdge[] edgeTo;

    /** 是否存在"从源点可达"的负环 */
    private final boolean hasNegativeCycle;

    /** 一个具体的负环(边列表);无负环为 null */
    private final List<DirectedEdge> negativeCycle;

    /** 松弛成功的总次数 */
    private final int relaxCount;

    /** QUEUE 模式下队列的峰值长度(衡量"扩散"规模;SWEEP 模式为 0) */
    private final int maxQueueSize;

    /** SWEEP 模式实际跑的全弧扫描轮数(QUEUE 模式为 0) */
    private final int sweepCount;

    /**
     * 用 SPFA(队列优化)求单源最短路。
     *
     * @param graph  有向加权图,不能为 null
     * @param source 源点编号
     * @throws IllegalArgumentException 参数为 null 或源点越界
     */
    public BellmanFordSP(EdgeWeightedDigraph graph, int source) {
        this(graph, source, Mode.QUEUE);
    }

    /**
     * 求单源最短路并检测负环。
     *
     * @param graph  有向加权图,不能为 null
     * @param source 源点编号
     * @param mode   实现方式,不能为 null
     * @throws IllegalArgumentException 参数为 null 或源点越界
     */
    public BellmanFordSP(EdgeWeightedDigraph graph, int source, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (source < 0 || source >= graph.V()) {
            throw new IllegalArgumentException("源点 " + source + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
        if (mode == null) {
            throw new IllegalArgumentException("实现方式不能为 null");
        }
        this.graph = graph;
        this.source = source;
        this.mode = mode;

        int V = graph.V();
        this.distTo = new double[V];
        this.edgeTo = new DirectedEdge[V];
        Arrays.fill(distTo, Double.POSITIVE_INFINITY);
        distTo[source] = 0.0;

        if (mode == Mode.QUEUE) {
            int[] queueStats = runQueue();
            this.maxQueueSize = queueStats[0];
            this.sweepCount = 0;
            this.relaxCount = queueStats[1];
        }
        else {
            int[] sweepStats = runSweep();
            this.maxQueueSize = 0;
            this.sweepCount = sweepStats[0];
            this.relaxCount = sweepStats[1];
        }

        List<DirectedEdge> found = extractNegativeCycleIfAny();
        this.negativeCycle = found;
        this.hasNegativeCycle = found != null;
    }

    // ------------------------------------------------------------------
    // 两种实现
    // ------------------------------------------------------------------

    /**
     * SPFA:队列里只放"距离刚被改小"的顶点。
     *
     * @return {@code {队列峰长, 松弛次数}}
     */
    private int[] runQueue() {
        int V = graph.V();
        boolean[] onQueue = new boolean[V];
        int[] enqueueCount = new int[V];
        Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.add(source);
        onQueue[source] = true;
        enqueueCount[source] = 1;
        int maxSize = 1;
        int relaxations = 0;

        while (!queue.isEmpty()) {
            int v = queue.poll();
            onQueue[v] = false;
            for (DirectedEdge edge : graph.adj(v)) {
                int to = edge.to();
                if (distTo[to] > distTo[v] + edge.weight()) {
                    distTo[to] = distTo[v] + edge.weight();
                    edgeTo[to] = edge;
                    relaxations++;
                    if (!onQueue[to]) {
                        queue.add(to);
                        onQueue[to] = true;
                        maxSize = Math.max(maxSize, queue.size());
                        // 一个顶点被反复入队 V 次,说明它在一条负环上被无限改小
                        if (++enqueueCount[to] >= V) {
                            return new int[]{maxSize, relaxations};
                        }
                    }
                }
            }
        }
        return new int[]{maxSize, relaxations};
    }

    /**
     * 教科书版:每轮把全部弧扫一遍,最多 V−1 轮;第 V 轮仍能松弛 ⟹ 负环。
     *
     * @return {@code {全弧扫描轮数, 松弛次数}}
     */
    private int[] runSweep() {
        int V = graph.V();
        int relaxations = 0;
        int rounds = 0;
        for (int round = 0; round < V; round++) {
            boolean changed = false;
            rounds++;
            for (DirectedEdge edge : graph.edges()) {
                int from = edge.from();
                if (distTo[from] == Double.POSITIVE_INFINITY) {
                    continue;
                }
                int to = edge.to();
                if (distTo[to] > distTo[from] + edge.weight()) {
                    distTo[to] = distTo[from] + edge.weight();
                    edgeTo[to] = edge;
                    changed = true;
                    relaxations++;
                }
            }
            if (!changed) {
                break;                                     // 已经收敛
            }
            if (round == V - 1) {
                break;                                     // 还能松弛 ⟹ 负环(下面统一提取)
            }
        }
        return new int[]{rounds, relaxations};
    }

    // ------------------------------------------------------------------
    // 负环提取
    // ------------------------------------------------------------------

    /**
     * 判断是否存在可达负环,存在则取出一个。
     *
     * <p>判据不能只看"松弛过 V 次"这类计数:更稳妥的做法是
     * <b>把前驱子图(edgeTo)拿出来直接找环</b> —— 存在可达负环时前驱子图必含环,
     * 且其中任何环都是负环。找不到(或取到的环权值非负)时,退回到
     * "再松弛一次、沿前驱链走 V 步"的经典做法。</p>
     *
     * @return 一个负环;不存在返回 null
     */
    private List<DirectedEdge> extractNegativeCycleIfAny() {
        List<DirectedEdge> cycle = findCycleInPredecessorGraph();
        if (cycle != null && weightOf(cycle) < 0) {
            return cycle;
        }
        return findNegativeCycleByWalkBack();
    }

    /** 在前驱子图里找环(复用 {@link DirectedCycle}),再把顶点环翻译成边环 */
    private List<DirectedEdge> findCycleInPredecessorGraph() {
        Digraph predecessor = new Digraph(graph.V());
        for (int v = 0; v < graph.V(); v++) {
            if (edgeTo[v] != null) {
                predecessor.addEdge(edgeTo[v].from(), edgeTo[v].to());
            }
        }
        DirectedCycle finder = new DirectedCycle(predecessor);
        if (!finder.hasCycle()) {
            return null;
        }
        int[] vertices = finder.cycle();
        List<DirectedEdge> cycle = new ArrayList<DirectedEdge>(vertices.length);
        for (int i = 0; i < vertices.length; i++) {
            int to = vertices[(i + 1) % vertices.length];
            if (edgeTo[to] == null || edgeTo[to].from() != vertices[i]) {
                return null;                               // 前驱链对不上,交给下面的兜底做法
            }
            cycle.add(edgeTo[to]);
        }
        return cycle;
    }

    /**
     * 兜底做法:再扫一遍全部弧,只要还有能松弛的弧 {@code v→w},就说明 w 在(或通向)负环;
     * 从 w 沿 {@code edgeTo} 往回走 V 步必然踩到环上的顶点,再绕一圈把环取出来。
     */
    private List<DirectedEdge> findNegativeCycleByWalkBack() {
        int V = graph.V();
        int relaxedTo = -1;
        for (int v = 0; v < V && relaxedTo < 0; v++) {
            if (distTo[v] == Double.POSITIVE_INFINITY) {
                continue;
            }
            for (DirectedEdge edge : graph.adj(v)) {
                if (distTo[edge.to()] > distTo[v] + edge.weight()) {
                    relaxedTo = edge.to();
                    break;
                }
            }
        }
        if (relaxedTo < 0) {
            return null;                                   // 已经收敛,没有负环
        }

        boolean[] visited = new boolean[V];
        int x = relaxedTo;
        while (!visited[x]) {
            visited[x] = true;
            if (edgeTo[x] == null) {
                return null;
            }
            x = edgeTo[x].from();
        }
        // x 在环上:顺着前驱链绕一圈
        List<DirectedEdge> cycle = new ArrayList<DirectedEdge>();
        int current = x;
        do {
            DirectedEdge edge = edgeTo[current];
            if (edge == null) {
                return null;
            }
            cycle.add(edge);
            current = edge.from();
        }
        while (current != x && cycle.size() <= V);
        if (current != x) {
            return null;
        }
        java.util.Collections.reverse(cycle);
        return cycle;
    }

    /** 环上各边权值之和(负环应当小于 0) */
    private static double weightOf(List<DirectedEdge> cycle) {
        double sum = 0.0;
        for (DirectedEdge edge : cycle) {
            sum += edge.weight();
        }
        return sum;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

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
     * @return 源点编号
     */
    public int source() {
        return source;
    }

    /**
     * @return 本实例所用的实现方式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @return 是否存在"从源点可达"的负环
     */
    public boolean hasNegativeCycle() {
        return hasNegativeCycle;
    }

    /**
     * 一个从源点可达的负环。
     *
     * @return 边列表({@code e1, e2, …} 满足 {@code e1.to() == e2.from()},
     *         末边的终点回到首边的起点,权值之和为负);无负环返回 {@code null}
     */
    public List<DirectedEdge> negativeCycle() {
        return negativeCycle == null ? null : new ArrayList<DirectedEdge>(negativeCycle);
    }

    /**
     * @return 负环的权值之和(为负);无负环返回 {@code Double.NaN}
     */
    public double negativeCycleWeight() {
        return negativeCycle == null ? Double.NaN : weightOf(negativeCycle);
    }

    /**
     * 从源点到 v 的最短距离。
     *
     * @param v 顶点编号
     * @return 最短距离;不可达返回 {@link Double#POSITIVE_INFINITY}
     * @throws IllegalArgumentException 顶点越界
     * @throws IllegalStateException    存在可达负环(此时最短距离无定义)
     */
    public double distTo(int v) {
        validateVertex(v);
        checkNoNegativeCycle();
        return distTo[v];
    }

    /**
     * @param v 顶点编号
     * @return 源点能否到达 v
     * @throws IllegalArgumentException 顶点越界
     * @throws IllegalStateException    存在可达负环
     */
    public boolean hasPathTo(int v) {
        validateVertex(v);
        checkNoNegativeCycle();
        return distTo[v] != Double.POSITIVE_INFINITY;
    }

    /**
     * @param v 顶点编号
     * @return 最短路径上通向 v 的最后一条边;不可达返回 null
     * @throws IllegalArgumentException 顶点越界
     * @throws IllegalStateException    存在可达负环
     */
    public DirectedEdge edgeTo(int v) {
        validateVertex(v);
        checkNoNegativeCycle();
        return edgeTo[v];
    }

    /**
     * 一条最短路径(边的列表)。
     *
     * @param v 顶点编号
     * @return 路径上的边(按行进顺序);不可达返回 null
     * @throws IllegalArgumentException 顶点越界
     * @throws IllegalStateException    存在可达负环
     */
    public List<DirectedEdge> pathTo(int v) {
        validateVertex(v);
        checkNoNegativeCycle();
        if (!hasPathTo(v)) {
            return null;
        }
        List<DirectedEdge> path = new ArrayList<DirectedEdge>();
        for (DirectedEdge edge = edgeTo[v]; edge != null; edge = edgeTo[edge.from()]) {
            path.add(edge);
        }
        java.util.Collections.reverse(path);
        return path;
    }

    /**
     * 一条最短路径(顶点编号列表,含首尾)。
     *
     * @param v 顶点编号
     * @return 顶点序列;不可达返回 null
     * @throws IllegalArgumentException 顶点越界
     * @throws IllegalStateException    存在可达负环
     */
    public List<Integer> pathVertices(int v) {
        List<DirectedEdge> path = pathTo(v);
        if (path == null) {
            return null;
        }
        List<Integer> vertices = new ArrayList<Integer>(path.size() + 1);
        vertices.add(source);
        for (DirectedEdge edge : path) {
            vertices.add(edge.to());
        }
        return vertices;
    }

    /**
     * @return 松弛成功的总次数(可与 {@link #E()}、{@link #V()} 对比来看实际工作量)
     */
    public int relaxCount() {
        return relaxCount;
    }

    /**
     * @return QUEUE 模式下队列的峰值长度;SWEEP 模式返回 0
     */
    public int maxQueueSize() {
        return maxQueueSize;
    }

    /**
     * @return SWEEP 模式实际跑的全弧扫描轮数;QUEUE 模式返回 0
     */
    public int sweepCount() {
        return sweepCount;
    }

    private void checkNoNegativeCycle() {
        if (hasNegativeCycle) {
            throw new IllegalStateException("图中存在从源点 " + source
                    + " 可达的负环 " + negativeCycle + ",最短路径无定义(可先调用 hasNegativeCycle() 判断)");
        }
    }

    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code BellmanFordSP(QUEUE, 源点 0): 8 个顶点,15 条边,松弛 28 次,队列峰长 6},
     *         随后每行是 {@code "0 to v: 距离 [边...]"};存在负环时先报告负环
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("BellmanFordSP(").append(mode).append(", 源点 ").append(source).append("): ")
                .append(graph.V()).append(" 个顶点,").append(graph.E()).append(" 条边,松弛 ")
                .append(relaxCount).append(" 次");
        if (mode == Mode.QUEUE) {
            sb.append(",队列峰长 ").append(maxQueueSize);
        }
        else {
            sb.append(",全弧扫描 ").append(sweepCount).append(" 轮");
        }
        sb.append(newline);
        if (hasNegativeCycle) {
            sb.append("  存在可达负环(权值 ").append(negativeCycleWeight()).append("): ")
                    .append(negativeCycle).append(newline);
            return sb.toString();
        }
        for (int v = 0; v < graph.V(); v++) {
            sb.append(source).append(" to ").append(v).append(": ");
            if (hasPathTo(v)) {
                sb.append(String.format("%.2f", distTo[v])).append(' ');
                for (DirectedEdge edge : pathTo(v)) {
                    sb.append("  ").append(edge);
                }
            }
            else {
                sb.append("不可达");
            }
            sb.append(newline);
        }
        return sb.toString();
    }

    /**
     * 演示:在有负权但无负环的 tinyEWDn.txt 上求最短路,再在含负环的 tinyEWDnc.txt 上检测负环;
     * 命令行给出文件路径与源点时改读文件。
     *
     * @param args 可选:{@code 文件路径 [源点]}
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyEWDn.txt";
        int source = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        EdgeWeightedDigraph graph = GraphIO.readWeightedDigraphFile(path);
        System.out.println("数据文件: " + path + "(V=" + graph.V() + ", E=" + graph.E() + ")");
        for (Mode mode : Mode.values()) {
            System.out.println(new BellmanFordSP(graph, source, mode));
        }

        if (args.length == 0) {
            System.out.println("=== 换成含负环的 tinyEWDnc.txt ===");
            EdgeWeightedDigraph cyclic = GraphIO.readWeightedDigraphFile("tinyEWDnc.txt");
            BellmanFordSP sp = new BellmanFordSP(cyclic, 0);
            System.out.println("hasNegativeCycle = " + sp.hasNegativeCycle());
            System.out.println("负环: " + sp.negativeCycle() + " 权值和 = " + sp.negativeCycleWeight());
            try {
                sp.distTo(6);
            }
            catch (IllegalStateException e) {
                System.out.println("查距离时的行为: " + e.getMessage());
            }
            System.out.println("对照 —— FloydWarshall 也认为有负环: "
                    + new FloydWarshall(cyclic).hasNegativeCycle());
        }
    }
}
