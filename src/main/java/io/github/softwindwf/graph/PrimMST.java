package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Prim 算法:求无向带权图的<b>最小生成树</b>(MST,Minimum Spanning Tree)。
 *
 * <p><b>算法一句话</b>:从任意顶点开始,不断把"一头在树上、一头在树外"的边里<b>权值最小</b>的那条
 * 加进树里,直到所有顶点都进树。每加一条边就让树多一个顶点,加 V−1 条边后结束。</p>
 *
 * <p><b>为什么这样贪心是对的</b>(割性质,cut property):任取把顶点分成两半的割,
 * 横跨该割的边中最小的那条一定属于某棵最小生成树。Prim 每一步取的正是
 * "树内顶点集合"与"树外顶点集合"这个割上的最小横切边,所以每步都能安全地把那条边固定下来。
 * 也正因如此,<b>权值允许为负或 0</b> —— 割性质只要求"能比较大小",不像 Dijkstra 那样要求非负。</p>
 *
 * <p><b>两种实现</b>:</p>
 * <table border="1" summary="Prim 两种实现的代价对比">
 *   <tr><th>模式</th><th>做法</th><th>时间</th><th>空间</th><th>适合</th></tr>
 *   <tr><td>{@link Mode#LAZY}</td>
 *       <td>用最小堆存"横切边",出堆时若两端都已入树就丢弃(延迟删除)</td>
 *       <td>Θ(E log E)</td><td>Θ(E)</td><td>稀疏图(默认)</td></tr>
 *   <tr><td>{@link Mode#DENSE}</td>
 *       <td>用 {@code distTo[]} 记录"每个树外顶点到树的最小边权",每轮线性扫描找最小值</td>
 *       <td>Θ(V²)</td><td>Θ(V)</td><td>稠密图(E ≈ V²)</td></tr>
 * </table>
 *
 * <p>两种实现给出的生成树<b>权值必然相同</b>;当所有边权互不相同时,生成的树本身也唯一,
 * 所以它们的边集逐条一致(测试里做了差分)。若存在等权边,不同实现(甚至同一次运行的入堆顺序)
 * 可能选出不同的最小生成树,但<b>总权值仍然相同</b> —— 这是 MST 的性质,不是缺陷。</p>
 *
 * <p><b>不连通怎么办</b>:算法退化为求<b>最小生成森林</b> —— 每个连通分量各自求一棵最小生成树,
 * {@link #weight()} 是森林的总权值,{@link #componentCount()} 给出树的棵数
 * (孤立顶点自成一个分量,贡献 0 条边、0 权值)。{@link #isConnected()} 为 true 时才是真正的
 * 最小生成树。</p>
 *
 * <p><b>两个边界情况的处理</b>:</p>
 * <ul>
 *   <li><b>自环</b>永远不进堆/不被选中:它的另一端就是自己,已经标记过了;</li>
 *   <li><b>平行边</b>只可能用上最轻的那条(DENSE 模式的 {@code distTo} 只会被更小的权值刷新;
 *       LAZY 模式里较重的那些成了过期边,出堆时被丢弃)。</li>
 * </ul>
 *
 * <p><b>边集的输出顺序</b>:{@link #edges()} 按<b>权值升序</b>返回,两种模式口径一致,
 * 便于打印与逐条对照(LAZY 模式本来就是这个顺序;DENSE 模式的加入顺序取决于顶点被并入树的先后,
 * 没有独立意义,统一排序后更可读)。</p>
 *
 * <pre>
 * EdgeWeightedGraph g = GraphIO.readWeightedFile("tinyEWG.txt");
 * PrimMST mst = new PrimMST(g);
 * mst.weight();          // 1.81
 * mst.edges();           // 7 条边,按权值升序
 * </pre>
 *
 * @see Edge
 * @see EdgeWeightedGraph
 * @see <a href="https://algs4.cs.princeton.edu/43mst">Algorithms, 4th Edition, Section 4.3</a>
 */
public final class PrimMST {

    /** 求最小生成树的实现方式 */
    public enum Mode {

        /** 延迟删除:最小堆存边,出堆时用标记数组过滤过期边,Θ(E log E) */
        LAZY,

        /** 即时删除:数组记录每个树外顶点的最小入树边,每轮线性扫描,Θ(V²) */
        DENSE
    }

    /** 被求解的图 */
    private final EdgeWeightedGraph graph;

    /** 所用实现方式 */
    private final Mode mode;

    /** 最小生成森林的边,按权值升序 */
    private final List<Edge> mstEdges;

    /** 最小生成森林的总权值 */
    private final double totalWeight;

    /** 连通分量数 = 森林中树的棵数;为 1 时整图连通,结果是一棵最小生成树 */
    private final int componentCount;

    /**
     * 用延迟删除的堆版 Prim 求最小生成树(森林)。
     *
     * @param graph 待求解的无向带权图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public PrimMST(EdgeWeightedGraph graph) {
        this(graph, Mode.LAZY);
    }

    /**
     * 求最小生成树(森林)。
     *
     * @param graph 待求解的无向带权图,不能为 null
     * @param mode  实现方式,不能为 null
     * @throws IllegalArgumentException 参数为 null
     */
    public PrimMST(EdgeWeightedGraph graph, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("实现方式不能为 null");
        }
        this.graph = graph;
        this.mode = mode;

        List<Edge> collected = new ArrayList<Edge>();
        if (mode == Mode.LAZY) {
            this.componentCount = primLazy(graph, collected);
        }
        else {
            this.componentCount = primDense(graph, collected);
        }

        // 两种实现统一按权值升序输出,便于打印与对照
        Collections.sort(collected);
        double sum = 0.0;
        for (Edge e : collected) {
            sum += e.weight();
        }
        this.mstEdges = Collections.unmodifiableList(collected);
        this.totalWeight = sum;
    }

    // ------------------------------------------------------------------
    // 两种实现
    // ------------------------------------------------------------------

    /**
     * 延迟删除版 Prim:最小堆里可能残留"两端都已入树"的过期边,出堆时丢弃即可。
     *
     * @return 连通分量数(森林中树的棵数)
     */
    private int primLazy(EdgeWeightedGraph g, List<Edge> out) {
        int V = g.V();
        boolean[] marked = new boolean[V];
        PriorityQueue<Edge> pq = new PriorityQueue<Edge>();
        int components = 0;

        for (int start = 0; start < V; start++) {
            if (marked[start]) {
                continue;
            }
            components++;
            scan(g, start, marked, pq);
            while (!pq.isEmpty()) {
                Edge e = pq.poll();                 // 当前最小横切边
                int v = e.either();
                int w = e.other(v);
                if (marked[v] && marked[w]) {
                    continue;                       // 过期边:两端都在树里
                }
                out.add(e);
                if (!marked[v]) {
                    scan(g, v, marked, pq);         // 新顶点入树,把它的出边补进堆
                }
                if (!marked[w]) {
                    scan(g, w, marked, pq);
                }
            }
        }
        return components;
    }

    /** 把 v 标记为入树,并把所有"通向树外"的边压入最小堆 */
    private void scan(EdgeWeightedGraph g, int v, boolean[] marked, PriorityQueue<Edge> pq) {
        marked[v] = true;
        for (Edge e : g.adj(v)) {
            int w = e.other(v);
            if (!marked[w]) {
                pq.add(e);
            }
        }
    }

    /**
     * 稠密图版 Prim:用 {@code distTo[w]} 记录"树外顶点 w 到树的当前最小边权",
     * 每轮线性扫描选出最小的那个(等价于"没有堆的优先队列"),Θ(V²)。
     *
     * @return 连通分量数
     */
    private int primDense(EdgeWeightedGraph g, List<Edge> out) {
        int V = g.V();
        boolean[] marked = new boolean[V];
        double[] distTo = new double[V];
        Edge[] edgeTo = new Edge[V];
        Arrays.fill(distTo, Double.POSITIVE_INFINITY);
        int components = 0;

        for (int start = 0; start < V; start++) {
            if (marked[start]) {
                continue;
            }
            components++;
            distTo[start] = 0.0;                    // 起点"到树的距离"为 0,保证第一个被选中

            while (true) {
                int v = -1;
                double best = Double.POSITIVE_INFINITY;
                for (int u = 0; u < V; u++) {        // 线性扫描找树外最小 distTo
                    if (!marked[u] && distTo[u] < best) {
                        best = distTo[u];
                        v = u;
                    }
                }
                if (v == -1) {
                    break;                           // 本分量已全部入树
                }
                marked[v] = true;
                if (edgeTo[v] != null) {             // 起点没有入树边
                    out.add(edgeTo[v]);
                }
                for (Edge e : g.adj(v)) {
                    int w = e.other(v);
                    if (!marked[w] && e.weight() < distTo[w]) {
                        distTo[w] = e.weight();
                        edgeTo[w] = e;
                    }
                }
            }
        }
        return components;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * @return 最小生成森林的边(按权值升序);图连通时就是最小生成树的全部 V−1 条边
     */
    public List<Edge> edges() {
        return mstEdges;
    }

    /**
     * @return 最小生成森林的总权值;图连通时即最小生成树的权值
     */
    public double weight() {
        return totalWeight;
    }

    /**
     * @return 连通分量数,等于森林中树的棵数;1 表示整图连通
     */
    public int componentCount() {
        return componentCount;
    }

    /**
     * @return 整图是否连通(结果是真正的一棵最小生成树)
     */
    public boolean isConnected() {
        return componentCount == 1;
    }

    /**
     * @return 顶点数
     */
    public int V() {
        return graph.V();
    }

    /**
     * @return 本实例所用的实现方式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @return 形如 {@code PrimMST(LAZY): 7 条边,总权值 1.81,连通分量 1}
     */
    @Override
    public String toString() {
        return "PrimMST(" + mode + "): " + mstEdges.size() + " 条边,总权值 " + totalWeight
                + ",连通分量 " + componentCount;
    }

    // ------------------------------------------------------------------
    // 演示
    // ------------------------------------------------------------------

    /**
     * 演示:读入加权图(缺省用 tinyEWG.txt 的内容),分别用两种实现求最小生成树并打印边与总权值。
     *
     * @param args 可选:algs4 加权图数据文件路径
     */
    public static void main(String[] args) {
        EdgeWeightedGraph graph = args.length > 0 ? GraphIO.readWeightedFile(args[0]) : sampleGraph();
        System.out.println("V = " + graph.V() + ", E = " + graph.E());
        System.out.print(GraphIO.toDot(graph));

        for (Mode mode : Mode.values()) {
            PrimMST mst = new PrimMST(graph, mode);
            System.out.println(mst);
            for (Edge e : mst.edges()) {
                System.out.println("  " + e);
            }
            System.out.println("  总权值 = " + mst.weight());
        }
    }

    /** 内置演示图:与工作区 tinyEWG.txt 相同的 8 顶点 / 16 边加权图(教材样例,MST 权值 1.81) */
    private static EdgeWeightedGraph sampleGraph() {
        EdgeWeightedGraph graph = new EdgeWeightedGraph(8);
        int[][] vw = {
            {4, 5}, {4, 7}, {5, 7}, {0, 7}, {1, 5}, {0, 4}, {2, 3}, {1, 7},
            {0, 2}, {1, 2}, {1, 3}, {2, 7}, {6, 2}, {3, 6}, {6, 0}, {6, 4}
        };
        double[] weights = {
            0.35, 0.37, 0.28, 0.16, 0.32, 0.38, 0.17, 0.19,
            0.26, 0.36, 0.29, 0.34, 0.40, 0.52, 0.58, 0.93
        };
        for (int i = 0; i < vw.length; i++) {
            graph.addEdge(vw[i][0], vw[i][1], weights[i]);
        }
        return graph;
    }
}
