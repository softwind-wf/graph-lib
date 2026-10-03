package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Dijkstra 算法:有向带权图的<b>单源最短路径</b>(要求边权非负)。
 *
 * <p><b>算法一句话</b>:把起点距离设为 0,其余为 +∞;每轮从"还没定下来的顶点"里挑出
 * <b>当前距离最小</b>的那个并<b>确定它的最短距离</b>(settle),然后用它所有的出边去
 * <b>松弛</b>(relax)邻居:{@code distTo[w] = min(distTo[w], distTo[v] + w(v,w))}。
 * 重复 V 轮(或直到队列空)。</p>
 *
 * <p><b>为什么"挑最小的那个定下来"是对的</b>:设 v 是当前未确定顶点中 distTo 最小的。
 * 任何从 s 到 v 的其他路径都必须先经过某个未确定顶点 u,而 distTo[u] ≥ distTo[v],
 * 再加上非负的边权,总长只会更大 —— 所以 distTo[v] 已经不可能再被改小了。
 * 这一步依赖 <b>边权非负</b>;只要有一条负边,该论证就崩了
 * (经典反例:s→a 权 1、s→b 权 2、b→a 权 -2,先定下 a 就错了)。
 * 因此本类在构造时就会检查全部边权,发现负数立即抛异常并提示改用 Bellman-Ford。</p>
 *
 * <p><b>两种实现</b>:</p>
 * <table border="1" summary="Dijkstra 两种实现的对比">
 *   <tr><th>模式</th><th>做法</th><th>时间</th><th>适合</th></tr>
 *   <tr><td>{@link Mode#LAZY}</td>
 *       <td>最小堆里放"候选路径"(顶点可能重复入堆),出堆时跳过已确定的陈旧条目</td>
 *       <td>Θ(E log E)</td><td>稀疏图(默认)</td></tr>
 *   <tr><td>{@link Mode#DENSE}</td>
 *       <td>每轮线性扫描 {@code distTo[]} 找未确定的最小值,不需要堆</td>
 *       <td>Θ(V²)</td><td>稠密图</td></tr>
 * </table>
 *
 * <p>两种实现的"确定顺序"相同(距离升序,距离相同时顶点编号小者优先),因此
 * {@link #distTo(int)} 与 {@link #pathTo(int)} 完全一致(测试里逐点差分)。</p>
 *
 * <p><b>几个约定</b>:</p>
 * <ul>
 *   <li>不可达顶点的 {@link #distTo(int)} 返回 {@code Double.POSITIVE_INFINITY}
 *       (与"距离为 0 的起点"区分开);{@link #hasPathTo(int)} 为 false,
 *       {@link #pathTo(int)} 返回 {@code null};</li>
 *   <li>路径按<b>边</b>返回({@link #pathTo(int)},每条边带权值),也可用
 *       {@link #pathVertices(int)} 拿顶点序列;</li>
 *   <li>等长路径的取舍规则固定为"顶点编号小的先被确定",因此结果可复现;</li>
 *   <li>自环与平行边无需特判:自环松弛自己不会更优;平行边自然各relax一次,取更轻的那条。</li>
 * </ul>
 *
 * <p><b>与 BFS 的关系</b>:把每条边都看成权值 1,{@link BreadthFirstTraversal} 与
 * Dijkstra 给出同一套距离 —— BFS 其实就是"边权全相等"时 Dijkstra 的退化(队列替代优先队列)。
 * 本类可以看作 BFS 在带权图上的推广。</p>
 *
 * <pre>
 * EdgeWeightedDigraph g = GraphIO.readWeightedDigraphFile("tinyEWD.txt");
 * DijkstraSP sp = new DijkstraSP(g, 0);
 * sp.distTo(6);        // 1.51
 * sp.pathTo(6);        // 0-&gt;2 0.26, 2-&gt;7 0.34, 7-&gt;3 0.39, 3-&gt;6 0.52
 * sp.pathVertices(6);  // [0, 2, 7, 3, 6]
 * </pre>
 *
 * @see DirectedEdge
 * @see EdgeWeightedDigraph
 * @see BreadthFirstTraversal
 * @see <a href="https://algs4.cs.princeton.edu/44sp">Algorithms, 4th Edition, Section 4.4</a>
 */
public final class DijkstraSP {

    /** 最短路的实现方式 */
    public enum Mode {

        /** 惰性删除:最小堆存候选路径,出堆时跳过陈旧条目,Θ(E log E) */
        LAZY,

        /** 稠密版:每轮线性扫描 distTo[] 找最小值,Θ(V²) */
        DENSE
    }

    /** 被求解的图 */
    private final EdgeWeightedDigraph graph;

    /** 源点 */
    private final int source;

    /** 所用实现方式 */
    private final Mode mode;

    /** distTo[v] = 源点到 v 的最短距离;不可达为 +∞ */
    private final double[] distTo;

    /** edgeTo[v] = 最短路径上进入 v 的那条边;源点与不可达顶点为 null */
    private final DirectedEdge[] edgeTo;

    /** settled[v] = v 的最短距离是否已经确定 */
    private final boolean[] settled;

    /** 顶点被确定(settle)的顺序,即距离升序 */
    private final int[] settleOrder;

    /** 已确定顶点数 = 可达顶点数(含源点) */
    private int settledCount;

    /**
     * 用惰性删除的堆版 Dijkstra 求源点 {@code source} 到各顶点的最短路径。
     *
     * @param graph  有向带权图,不能为 null,且不允许负权边
     * @param source 源点,必须在 {@code [0, V)} 内
     * @throws IllegalArgumentException 参数为 null、源点越界,或图中存在负权边
     */
    public DijkstraSP(EdgeWeightedDigraph graph, int source) {
        this(graph, source, Mode.LAZY);
    }

    /**
     * 求单源最短路径。
     *
     * @param graph  有向带权图,不能为 null,且不允许负权边
     * @param source 源点,必须在 {@code [0, V)} 内
     * @param mode   实现方式,不能为 null
     * @throws IllegalArgumentException 参数为 null、源点越界,或图中存在负权边
     */
    public DijkstraSP(EdgeWeightedDigraph graph, int source, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("实现方式不能为 null");
        }
        if (source < 0 || source >= graph.V()) {
            throw new IllegalArgumentException("源点 " + source + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
        checkNonNegative(graph);

        this.graph = graph;
        this.source = source;
        this.mode = mode;

        int V = graph.V();
        this.distTo = new double[V];
        this.edgeTo = new DirectedEdge[V];
        this.settled = new boolean[V];
        this.settleOrder = new int[V];
        Arrays.fill(distTo, Double.POSITIVE_INFINITY);

        if (mode == Mode.LAZY) {
            runLazy(source);
        }
        else {
            runDense(source);
        }
    }

    /** Dijkstra 的前提:全部边权非负。构造时一次性查完,报错要能直接定位到那条边 */
    private static void checkNonNegative(EdgeWeightedDigraph graph) {
        for (DirectedEdge e : graph.edges()) {
            if (e.weight() < 0) {
                throw new IllegalArgumentException("Dijkstra 要求边权非负,但存在负权边 " + e
                        + ";带负权的最短路径请用 Bellman-Ford");
            }
        }
    }

    // ------------------------------------------------------------------
    // 两种实现
    // ------------------------------------------------------------------

    /** 惰性删除版:堆里存候选距离,顶点可能被多次入堆,出堆时用 settled/距离双重校验跳过陈旧条目 */
    private void runLazy(int s) {
        PriorityQueue<Entry> pq = new PriorityQueue<Entry>();
        distTo[s] = 0.0;
        pq.add(new Entry(0.0, s));

        while (!pq.isEmpty()) {
            Entry entry = pq.poll();
            int v = entry.vertex;
            if (settled[v] || entry.distance > distTo[v]) {
                continue;                       // 陈旧条目
            }
            settle(v);
            for (DirectedEdge e : graph.adj(v)) {
                int w = e.to();
                double candidate = distTo[v] + e.weight();
                if (candidate < distTo[w]) {
                    distTo[w] = candidate;
                    edgeTo[w] = e;
                    pq.add(new Entry(candidate, w));
                }
            }
        }
    }

    /** 稠密版:每轮线性扫描 distTo[] 找出未确定的最小者(编号小的优先,保证与堆版一致) */
    private void runDense(int s) {
        distTo[s] = 0.0;
        while (true) {
            int v = -1;
            double best = Double.POSITIVE_INFINITY;
            for (int u = 0; u < graph.V(); u++) {
                if (!settled[u] && distTo[u] < best) {
                    best = distTo[u];
                    v = u;
                }
            }
            if (v == -1) {
                break;                          // 剩下的都不可达
            }
            settle(v);
            for (DirectedEdge e : graph.adj(v)) {
                int w = e.to();
                double candidate = distTo[v] + e.weight();
                if (candidate < distTo[w]) {
                    distTo[w] = candidate;
                    edgeTo[w] = e;
                }
            }
        }
    }

    /** 确定 v 的最短距离 */
    private void settle(int v) {
        settled[v] = true;
        settleOrder[settledCount++] = v;
    }

    /** 堆元素:候选距离 + 顶点。距离相同按顶点编号升序,保证两种实现结果一致 */
    private static final class Entry implements Comparable<Entry> {
        final double distance;
        final int vertex;

        Entry(double distance, int vertex) {
            this.distance = distance;
            this.vertex = vertex;
        }

        public int compareTo(Entry that) {
            int byDistance = Double.compare(this.distance, that.distance);
            return byDistance != 0 ? byDistance : Integer.compare(this.vertex, that.vertex);
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * @return 源点
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
     * @return 顶点数
     */
    public int V() {
        return graph.V();
    }

    /**
     * @return 可达顶点数(含源点本身)
     */
    public int count() {
        return settledCount;
    }

    /**
     * 源点到 {@code v} 的最短距离。
     *
     * @param v 顶点编号
     * @return 最短距离;不可达返回 {@link Double#POSITIVE_INFINITY}
     * @throws IllegalArgumentException {@code v} 越界
     */
    public double distTo(int v) {
        validateVertex(v);
        return distTo[v];
    }

    /**
     * @param v 顶点编号
     * @return 源点能否到达 v
     * @throws IllegalArgumentException {@code v} 越界
     */
    public boolean hasPathTo(int v) {
        validateVertex(v);
        return distTo[v] < Double.POSITIVE_INFINITY;
    }

    /**
     * 最短路径上进入 {@code v} 的最后一条边(即 v 的"前驱边")。
     *
     * @param v 顶点编号
     * @return 前驱边;源点与不可达顶点返回 null
     * @throws IllegalArgumentException {@code v} 越界
     */
    public DirectedEdge edgeTo(int v) {
        validateVertex(v);
        return edgeTo[v];
    }

    /**
     * 源点到 {@code v} 的最短路径(按边返回,起点在前的顺序)。
     *
     * @param v 顶点编号
     * @return 边序列;不可达返回 {@code null};v 为源点时返回空列表
     * @throws IllegalArgumentException {@code v} 越界
     */
    public List<DirectedEdge> pathTo(int v) {
        validateVertex(v);
        if (!hasPathTo(v)) {
            return null;
        }
        List<DirectedEdge> edges = new ArrayList<DirectedEdge>();
        for (DirectedEdge e = edgeTo[v]; e != null; e = edgeTo[e.from()]) {
            edges.add(e);
        }
        Collections.reverse(edges);
        return edges;
    }

    /**
     * 源点到 {@code v} 的最短路径(按顶点返回)。
     *
     * @param v 顶点编号
     * @return 顶点序列(首元素是源点);不可达返回 {@code null}
     * @throws IllegalArgumentException {@code v} 越界
     */
    public List<Integer> pathVertices(int v) {
        validateVertex(v);
        if (!hasPathTo(v)) {
            return null;
        }
        List<DirectedEdge> edges = pathTo(v);
        List<Integer> vertices = new ArrayList<Integer>(edges.size() + 1);
        vertices.add(source);
        for (DirectedEdge e : edges) {
            vertices.add(e.to());
        }
        return vertices;
    }

    /**
     * 顶点被确定最短距离的顺序 —— 必然是距离升序(这正是 Dijkstra 的贪心性质)。
     *
     * @return 顺序数组的副本,长度为 {@link #count()}
     */
    public int[] settleOrder() {
        return Arrays.copyOf(settleOrder, settledCount);
    }

    /**
     * @return 形如 {@code DijkstraSP(LAZY, 源 0): 可达 8/8,最远距离 1.51}
     */
    @Override
    public String toString() {
        double max = 0.0;
        for (int v = 0; v < graph.V(); v++) {
            if (hasPathTo(v)) {
                max = Math.max(max, distTo[v]);
            }
        }
        return "DijkstraSP(" + mode + ", 源 " + source + "): 可达 " + settledCount + "/" + graph.V()
                + ",最远距离 " + max;
    }

    /** 校验顶点编号 */
    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    // ------------------------------------------------------------------
    // 演示
    // ------------------------------------------------------------------

    /**
     * 演示:读入有向带权图(缺省用 tinyEWD.txt 的内容),从 0 号顶点求最短路径,
     * 打印各点的距离与路径(边 + 顶点),并对照两种实现。
     *
     * @param args 可选:algs4 有向加权图数据文件路径
     */
    public static void main(String[] args) {
        EdgeWeightedDigraph graph = args.length > 0 ? GraphIO.readWeightedDigraphFile(args[0]) : sampleGraph();
        System.out.println("V = " + graph.V() + ", E = " + graph.E());

        DijkstraSP sp = new DijkstraSP(graph, 0);
        System.out.println(sp);
        System.out.println("确定顺序(距离升序): " + Arrays.toString(sp.settleOrder()));
        for (int v = 0; v < graph.V(); v++) {
            if (!sp.hasPathTo(v)) {
                System.out.println("0 -> " + v + ": 不可达");
                continue;
            }
            System.out.println("0 -> " + v + " 距离 " + sp.distTo(v)
                    + "  路径 " + sp.pathVertices(v));
            for (DirectedEdge e : sp.pathTo(v)) {
                System.out.println("      " + e);
            }
        }

        DijkstraSP dense = new DijkstraSP(graph, 0, Mode.DENSE);
        boolean same = true;
        for (int v = 0; v < graph.V(); v++) {
            if (Math.abs(dense.distTo(v) - sp.distTo(v)) > 1e-12) {
                same = false;
            }
        }
        System.out.println("对照:LAZY 与 DENSE 距离完全相同 = " + same);
    }

    /** 内置演示图:与工作区 tinyEWD.txt 相同的 8 顶点 / 15 有向边加权图(教材样例) */
    private static EdgeWeightedDigraph sampleGraph() {
        EdgeWeightedDigraph graph = new EdgeWeightedDigraph(8);
        int[][] fromTo = {
            {4, 5}, {5, 4}, {4, 7}, {5, 7}, {7, 5}, {5, 1}, {0, 4}, {0, 2},
            {7, 3}, {1, 3}, {2, 7}, {6, 2}, {3, 6}, {6, 0}, {6, 4}
        };
        double[] weights = {
            0.35, 0.35, 0.37, 0.28, 0.28, 0.32, 0.38, 0.26,
            0.39, 0.29, 0.34, 0.40, 0.52, 0.58, 0.93
        };
        for (int i = 0; i < fromTo.length; i++) {
            graph.addEdge(fromTo[i][0], fromTo[i][1], weights[i]);
        }
        return graph;
    }
}
