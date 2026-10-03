package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Johnson 算法:<b>稀疏图上的全源最短路</b>,允许负权弧,能检测负环。Θ(V·E·log V)。
 *
 * <p><b>要解决的问题</b>:全源最短路有两个现成方案,但各有短板 ——</p>
 * <ul>
 *   <li>{@link FloydWarshall} 简单,但 Θ(V³),稀疏图上太慢;</li>
 *   <li>对每个顶点各跑一次 {@link DijkstraSP} 只要 Θ(V·E·log V),却<b>要求所有弧非负</b>。</li>
 * </ul>
 *
 * <p><b>Johnson 的关键一招:重赋权(reweighting)</b>。先用一次 Bellman–Ford 求出势函数
 * {@code h(v)},再令</p>
 * <pre>
 *   w'(u, v) = w(u, v) + h(u) − h(v)
 * </pre>
 * <p>可以证明 {@code w'(u,v) ≥ 0}(因为 {@code h(v) ≤ h(u) + w(u,v)} 正是最短路的三角不等式),
 * 而<b>每条路径的总权值只改变一个只与端点有关的量</b>:</p>
 * <pre>
 *   沿路径 u → … → v 求和:w' 之和 = w 之和 + h(u) − h(v)
 * </pre>
 * <p>于是"哪条路径最短"完全不变,却可以把图交给 Dijkstra。最后把每个源点的结果
 * 用同一个式子换算回来即可。</p>
 *
 * <p><b>势函数怎么来</b>:加一个虚拟源点 s,向每个顶点连一条权值 0 的弧,
 * 从 s 跑一次 Bellman–Ford,得到 {@code h(v) = distTo(v)};若这次 Bellman–Ford
 * 发现了负环,说明原图有(从任意顶点可达的)负环,全源最短路无定义。</p>
 *
 * <table border="1" summary="Johnson 的两种实现">
 *   <tr><th>模式</th><th>做法</th><th>时间</th></tr>
 *   <tr><td>{@link Mode#REWEIGHT}</td>
 *       <td>真·Johnson:重赋权后对每个顶点跑 Dijkstra</td>
 *       <td>Θ(V·E·log V)</td></tr>
 *   <tr><td>{@link Mode#BELLMAN_FORD_PER_SOURCE}</td>
 *       <td>不做重赋权,对每个顶点直接跑一次 Bellman–Ford(对照组,用于体会重赋权带来的加速)</td>
 *       <td>Θ(V²·E)</td></tr>
 * </table>
 *
 * <p><b>负环的处理</b>:存在负环时构造成功但仍不可用 —— {@link #hasNegativeCycle()} 为 true,
 * {@link #negativeCycle()} 给出一个负环,而 {@link #dist(int, int)}、{@link #path(int, int)}
 * 会抛 {@link IllegalStateException}(与 {@link BellmanFordSP} 的约定一致)。</p>
 *
 * <pre>
 * Johnson johnson = new Johnson(GraphIO.readWeightedDigraphFile("tinyEWDn.txt"));
 * johnson.dist(0, 7);    // 任意两点的最短距离(可含负权弧)
 * johnson.path(0, 7);    // 一条最短路径(原图的边)
 * </pre>
 *
 * @see FloydWarshall
 * @see BellmanFordSP
 * @see DijkstraSP
 * @see AcyclicSP
 * @see <a href="https://algs4.cs.princeton.edu/44sp">Algorithms, 4th Edition, Section 4.4</a>
 */
public final class Johnson {

    /** Johnson 的两种实现 */
    public enum Mode {

        /** 重赋权 + 每个源点一次 Dijkstra(真·Johnson) */
        REWEIGHT,

        /** 不重赋权,每个源点各跑一次 Bellman–Ford(对照组) */
        BELLMAN_FORD_PER_SOURCE
    }

    /** 被求解的图 */
    private final EdgeWeightedDigraph graph;

    /** 实现方式 */
    private final Mode mode;

    /** 势函数 h(v)(仅 REWEIGHT 模式有意义) */
    private final double[] potential;

    /** dist[u][v] = u 到 v 的最短距离 */
    private final double[][] dist;

    /** 是否存在负环 */
    private final boolean hasNegativeCycle;

    /** 一个具体的负环 */
    private final List<DirectedEdge> negativeCycle;

    /** 重赋权后所有弧的最小权值(应当 ≥ 0,用于自检与教学展示;非 REWEIGHT 模式为 NaN) */
    private final double minReweightedWeight;

    /** 重赋权后的图(仅 REWEIGHT 模式;复用它来查路径,避免重复构造) */
    private final EdgeWeightedDigraph reweighted;

    /**
     * 用 Johnson 算法求全源最短路。
     *
     * @param graph 有向加权图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public Johnson(EdgeWeightedDigraph graph) {
        this(graph, Mode.REWEIGHT);
    }

    /**
     * 求全源最短路。
     *
     * @param graph 有向加权图,不能为 null
     * @param mode  实现方式,不能为 null
     * @throws IllegalArgumentException 参数为 null
     */
    public Johnson(EdgeWeightedDigraph graph, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("实现方式不能为 null");
        }
        this.graph = graph;
        this.mode = mode;
        int V = graph.V();
        this.dist = new double[V][V];
        for (double[] row : dist) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }

        BellmanFordSP feasible = solvePotential();
        this.hasNegativeCycle = feasible.hasNegativeCycle();
        this.negativeCycle = feasible.negativeCycle();

        if (hasNegativeCycle) {
            this.potential = null;
            this.reweighted = null;
            this.minReweightedWeight = Double.NaN;
            return;                                        // 距离不可用,dist 保持全 ∞
        }

        this.potential = new double[V];
        for (int v = 0; v < V; v++) {
            potential[v] = feasible.distTo(v);
        }
        this.reweighted = mode == Mode.REWEIGHT ? buildReweighted() : null;
        this.minReweightedWeight = mode == Mode.REWEIGHT ? minWeight(reweighted) : Double.NaN;

        if (mode == Mode.REWEIGHT) {
            solveByReweighting();
        }
        else {
            solveByBellmanFordPerSource();
        }
    }

    // ------------------------------------------------------------------
    // 势函数
    // ------------------------------------------------------------------

    /**
     * 加虚拟源点 V,向每个顶点连一条权值 0 的弧,从它跑一次 Bellman–Ford。
     * 若原图有负环,该负环在扩大的图里从虚拟源点可达,于是会被检测出来。
     *
     * @return 在扩大图上从虚拟源点出发的 Bellman–Ford 结果
     */
    private BellmanFordSP solvePotential() {
        int V = graph.V();
        EdgeWeightedDigraph augmented = new EdgeWeightedDigraph(V + 1);
        for (DirectedEdge edge : graph.edges()) {
            augmented.addEdge(new DirectedEdge(edge.from(), edge.to(), edge.weight()));
        }
        for (int v = 0; v < V; v++) {
            augmented.addEdge(new DirectedEdge(V, v, 0.0));
        }
        return new BellmanFordSP(augmented, V);
    }

    /** 按 w'(u,v) = w(u,v) + h(u) − h(v) 造出重赋权图 */
    private EdgeWeightedDigraph buildReweighted() {
        EdgeWeightedDigraph result = new EdgeWeightedDigraph(graph.V());
        for (DirectedEdge edge : graph.edges()) {
            result.addEdge(new DirectedEdge(edge.from(), edge.to(),
                    edge.weight() + potential[edge.from()] - potential[edge.to()]));
        }
        return result;
    }

    /** 图里最小的弧权(没有边时返回 +∞) */
    private static double minWeight(EdgeWeightedDigraph weighted) {
        double min = Double.POSITIVE_INFINITY;
        for (DirectedEdge edge : weighted.edges()) {
            min = Math.min(min, edge.weight());
        }
        return min;
    }

    // ------------------------------------------------------------------
    // 两种求解方式
    // ------------------------------------------------------------------

    /** 重赋权后用 Dijkstra 求每个源点;最后用 d(u,v) = d'(u,v) − h(u) + h(v) 换算回原权值 */
    private void solveByReweighting() {
        int V = graph.V();
        for (int s = 0; s < V; s++) {
            DijkstraSP sp = new DijkstraSP(reweighted, s);
            dist[s][s] = 0.0;
            for (int v = 0; v < V; v++) {
                if (v != s && sp.hasPathTo(v)) {
                    dist[s][v] = sp.distTo(v) - potential[s] + potential[v];
                }
            }
        }
    }

    /** 对照组:每个源点各跑一次 Bellman–Ford(不做重赋权) */
    private void solveByBellmanFordPerSource() {
        int V = graph.V();
        for (int s = 0; s < V; s++) {
            BellmanFordSP sp = new BellmanFordSP(graph, s);
            if (sp.hasNegativeCycle()) {
                return;                                    // 构造时已判定,这里不会发生
            }
            for (int v = 0; v < V; v++) {
                dist[s][v] = sp.distTo(v);
            }
        }
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
     * @return 本实例所用的实现方式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @return 是否存在负环
     */
    public boolean hasNegativeCycle() {
        return hasNegativeCycle;
    }

    /**
     * @return 一个负环(边列表);无负环返回 {@code null}
     */
    public List<DirectedEdge> negativeCycle() {
        return negativeCycle == null ? null : new ArrayList<DirectedEdge>(negativeCycle);
    }

    /**
     * 重赋权用的势函数 h(v)。
     *
     * @param v 顶点编号
     * @return h(v)(= 虚拟源点到 v 的最短距离)
     * @throws IllegalArgumentException 顶点越界
     * @throws IllegalStateException    图中存在负环(没有势函数),或当前模式不做重赋权
     */
    public double potential(int v) {
        validateVertex(v);
        if (hasNegativeCycle) {
            throw new IllegalStateException("图中存在负环,重赋权不可用");
        }
        if (mode != Mode.REWEIGHT) {
            throw new IllegalStateException("当前模式(" + mode + ")不做重赋权,没有势函数");
        }
        return potential[v];
    }

    /**
     * 重赋权后所有弧的最小权值(应当 ≥ 0 —— 这正是"重赋权后能用 Dijkstra"的依据)。
     *
     * @return 最小权值;当前模式不做重赋权或有负环时返回 {@link Double#NaN}
     */
    public double minReweightedWeight() {
        return minReweightedWeight;
    }

    /**
     * u 到 v 的最短距离。
     *
     * @param from 起点
     * @param to   终点
     * @return 最短距离;不可达返回 {@link Double#POSITIVE_INFINITY}
     * @throws IllegalArgumentException 顶点越界
     * @throws IllegalStateException    图中存在负环
     */
    public double dist(int from, int to) {
        validateVertex(from);
        validateVertex(to);
        checkNoNegativeCycle();
        return dist[from][to];
    }

    /**
     * @param from 起点
     * @param to   终点
     * @return 是否可达
     * @throws IllegalArgumentException 顶点越界
     * @throws IllegalStateException    图中存在负环
     */
    public boolean hasPath(int from, int to) {
        validateVertex(from);
        validateVertex(to);
        checkNoNegativeCycle();
        return dist[from][to] != Double.POSITIVE_INFINITY;
    }

    /**
     * 一条最短路径(<b>原图的边</b>,权值是原权值)。
     *
     * <p>做法:在重赋权图上用 Dijkstra 求路径,再把每条重赋权边
     * {@code (u,v,w')} 还原成原边 {@code (u,v,w' − h(u) + h(v))} ——
     * 这个换算是精确的,不依赖"平行边里挑哪一条"。</p>
     *
     * @param from 起点
     * @param to   终点
     * @return 路径上的边(按行进顺序);不可达返回 null;{@code from == to} 返回空列表
     * @throws IllegalArgumentException 顶点越界
     * @throws IllegalStateException    图中存在负环
     */
    public List<DirectedEdge> path(int from, int to) {
        validateVertex(from);
        validateVertex(to);
        checkNoNegativeCycle();
        if (from == to) {
            return new ArrayList<DirectedEdge>();
        }
        if (!hasPath(from, to)) {
            return null;
        }
        if (mode == Mode.BELLMAN_FORD_PER_SOURCE) {
            return new BellmanFordSP(graph, from).pathTo(to);
        }
        List<DirectedEdge> reweightedPath = new DijkstraSP(reweighted, from).pathTo(to);
        List<DirectedEdge> path = new ArrayList<DirectedEdge>(reweightedPath.size());
        for (DirectedEdge edge : reweightedPath) {
            double original = edge.weight() - potential[edge.from()] + potential[edge.to()];
            path.add(new DirectedEdge(edge.from(), edge.to(), original));
        }
        return path;
    }

    /**
     * @return 全源距离矩阵的副本({@code [u][v]},不可达为 {@code Double.POSITIVE_INFINITY})
     * @throws IllegalStateException 图中存在负环
     */
    public double[][] distances() {
        checkNoNegativeCycle();
        double[][] copy = new double[dist.length][];
        for (int i = 0; i < dist.length; i++) {
            copy[i] = Arrays.copyOf(dist[i], dist[i].length);
        }
        return copy;
    }

    private void checkNoNegativeCycle() {
        if (hasNegativeCycle) {
            throw new IllegalStateException("图中存在负环,全源最短路无定义(可先调用 hasNegativeCycle() 判断)");
        }
    }

    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code Johnson(REWEIGHT): 8 个顶点,15 条边,重赋权后最小弧权 0.00},
     *         随后每行是一个源点的距离表
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("Johnson(").append(mode).append("): ").append(graph.V()).append(" 个顶点,")
                .append(graph.E()).append(" 条边");
        if (mode == Mode.REWEIGHT) {
            sb.append(String.format(",重赋权后最小弧权 %.2f", minReweightedWeight));
        }
        sb.append(newline);
        if (hasNegativeCycle) {
            sb.append("  存在负环: ").append(negativeCycle).append(newline);
            return sb.toString();
        }
        for (int u = 0; u < graph.V(); u++) {
            sb.append("  从 ").append(u).append(" 出发: ");
            for (int v = 0; v < graph.V(); v++) {
                sb.append(dist[u][v] == Double.POSITIVE_INFINITY
                        ? "  ∞" : String.format("%6.2f", dist[u][v]));
            }
            sb.append(newline);
        }
        return sb.toString();
    }

    /**
     * 演示:在 tinyEWDn.txt(含负权、无负环)上跑两种模式并对照,再在 tinyEWDnc.txt(含负环)
     * 上看拒绝行为;命令行给出文件路径时改读文件。
     *
     * @param args 可选:{@code 文件路径}
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyEWDn.txt";
        EdgeWeightedDigraph graph = GraphIO.readWeightedDigraphFile(path);
        System.out.println("数据文件: " + path + "(V=" + graph.V() + ", E=" + graph.E() + ")");
        for (Mode mode : Mode.values()) {
            Johnson johnson = new Johnson(graph, mode);
            System.out.println(johnson);
        }

        Johnson johnson = new Johnson(graph);
        System.out.println("势函数 h = " + Arrays.toString(potentials(johnson)));
        System.out.println("重赋权后最小弧权 = " + johnson.minReweightedWeight()
                + "(≥ 0 就说明可以交给 Dijkstra)");
        System.out.println("0 → 7 的最短路: " + johnson.path(0, 7));

        if (args.length == 0) {
            System.out.println("=== 换成含负环的 tinyEWDnc.txt ===");
            Johnson bad = new Johnson(GraphIO.readWeightedDigraphFile("tinyEWDnc.txt"));
            System.out.println("hasNegativeCycle = " + bad.hasNegativeCycle()
                    + ",负环 = " + bad.negativeCycle());
            try {
                bad.dist(0, 6);
            }
            catch (IllegalStateException e) {
                System.out.println("查距离时的行为: " + e.getMessage());
            }
        }
    }

    private static double[] potentials(Johnson johnson) {
        double[] result = new double[johnson.V()];
        for (int v = 0; v < result.length; v++) {
            result[v] = johnson.potential(v);
        }
        return result;
    }
}
