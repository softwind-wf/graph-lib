package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Floyd–Warshall 算法:有向带权图的<b>全源最短路径</b>(所有顶点对之间的最短距离)。
 *
 * <p><b>算法一句话</b>:动态规划。设 {@code dist_k[i][j]} 表示"只允许用顶点 {@code 0..k}
 * 作中间点"时 i 到 j 的最短距离,则</p>
 * <pre>
 *   dist_k[i][j] = min( dist_{k-1}[i][j],  dist_{k-1}[i][k] + dist_{k-1}[k][j] )
 *                  └ 不用 k 作中间点      └ 用 k 作中间点(i→…→k→…→j)
 * </pre>
 * <p>枚举 k、i、j 三重循环即可,时间 <b>Θ(V³)</b>、空间 <b>Θ(V²)</b>。</p>
 *
 * <p><b>为什么可以就地(in-place)更新</b>:第 k 轮里 {@code dist[i][k]} 与 {@code dist[k][j]}
 * 即使在本轮已被刷新,它们表示的路径中间点仍然只来自 {@code 0..k},不会用到还没处理的 {@code k+1..},
 * 所以结果依然正确 —— 这一点使空间从 Θ(V³) 降到 Θ(V²)。本类默认就用就地更新,
 * 另外提供 {@link Mode#COPY} 版(每轮用上一轮的副本计算,严格照着上面的递推式来,
 * 多花一份 Θ(V²) 空间,便于对照与验证两种口径结果相同)。</p>
 *
 * <p><b>与单源算法的对比</b>:</p>
 * <table border="1" summary="全源最短路的几种做法">
 *   <tr><th>做法</th><th>时间</th><th>空间</th><th>适用</th></tr>
 *   <tr><td>Floyd–Warshall(本类)</td><td>Θ(V³)</td><td>Θ(V²)</td>
 *       <td>稠密图、V 不大、需要所有点对;<b>可带负权边</b></td></tr>
 *   <tr><td>对每个源点跑一次 {@link DijkstraSP}</td><td>Θ(V·E log V)</td><td>Θ(V)</td>
 *       <td>稀疏图;要求非负权</td></tr>
 * </table>
 *
 * <p><b>负权边与负环</b>:Floyd–Warshall <b>允许负权边</b>(这正是它相对 Dijkstra 的优势);
 * 但只要存在<b>负环</b>,绕环可以无限变小,"最短距离"就没有意义了。判据很简单:
 * 算法结束后若某个 {@code dist[v][v] &lt; 0},说明 v 处在一个负环上 ——
 * 因为"从 v 出发绕一圈回到 v 且总权为负"才是对角线被压到负数唯一的原因。
 * {@link #hasNegativeCycle()} 给出判断,{@link #negativeCycle()} 给出一个具体的负环。
 * 存在负环时 {@link #dist(int, int)} 与 {@link #path(int, int)} 的数值不可信
 * (它们是被负环"压过"的结果),应先处理负环再谈最短路。</p>
 *
 * <p><b>路径重建</b>:算法同时维护一张<b>后继矩阵</b> {@code next[i][j]}("从 i 去 j 的下一跳"),
 * 每当 {@code dist[i][j]} 被 k 改善就把 {@code next[i][j]} 更新为 {@code next[i][k]}。
 * 之后从 i 一路跟着 next 走即可得到顶点序列,{@link #pathEdges(int, int)} 再把它翻成边序列。
 * 这张矩阵多花 Θ(V²) 空间,但省去了对每个点对重跑路径搜索。</p>
 *
 * <pre>
 * EdgeWeightedDigraph g = GraphIO.readWeightedDigraphFile("tinyEWD.txt");
 * FloydWarshall fw = new FloydWarshall(g);
 * fw.dist(0, 6);        // 1.51
 * fw.path(0, 6);        // [0, 2, 7, 3, 6]
 * fw.pathEdges(0, 6);   // 0-&gt;2 0.26, 2-&gt;7 0.34, 7-&gt;3 0.39, 3-&gt;6 0.52
 * fw.hasNegativeCycle() // false
 * </pre>
 *
 * @see EdgeWeightedDigraph
 * @see DirectedEdge
 * @see DijkstraSP
 * @see <a href="https://algs4.cs.princeton.edu/44sp">Algorithms, 4th Edition, Section 4.4</a>
 */
public final class FloydWarshall {

    /** 距离矩阵的更新方式 */
    public enum Mode {

        /** 就地更新:直接在距离矩阵上滚动,Θ(V²) 空间(默认) */
        IN_PLACE,

        /** 每轮用上一轮的副本计算:严格照着递推式,多一份 Θ(V²) 空间,便于对照 */
        COPY
    }

    /** 被求解的图 */
    private final EdgeWeightedDigraph graph;

    /** 所用更新方式 */
    private final Mode mode;

    /** dist[i][j] = i 到 j 的最短距离;不可达为 +∞ */
    private final double[][] dist;

    /** next[i][j] = i 到 j 的最短路上的下一跳;-1 表示不可达 */
    private final int[][] next;

    /** 是否存在负环 */
    private final boolean hasNegativeCycle;

    /** 一个具体的负环顶点序列(首尾相接,不重复写首顶点);不存在时为 null */
    private final int[] negativeCycle;

    /**
     * 用就地更新方式求全源最短路径。
     *
     * @param graph 有向带权图,不能为 null(允许负权边,不允许负环参与最短距离)
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public FloydWarshall(EdgeWeightedDigraph graph) {
        this(graph, Mode.IN_PLACE);
    }

    /**
     * 求全源最短路径。
     *
     * @param graph 有向带权图,不能为 null
     * @param mode  更新方式,不能为 null
     * @throws IllegalArgumentException 参数为 null
     */
    public FloydWarshall(EdgeWeightedDigraph graph, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("更新方式不能为 null");
        }
        this.graph = graph;
        this.mode = mode;

        int V = graph.V();
        this.dist = new double[V][V];
        this.next = new int[V][V];
        initFromEdges(graph);

        if (mode == Mode.IN_PLACE) {
            relaxInPlace(V);
        }
        else {
            relaxWithCopy(V);
        }

        boolean negative = false;
        for (int v = 0; v < V; v++) {
            if (dist[v][v] < 0) {
                negative = true;
                break;
            }
        }
        this.hasNegativeCycle = negative;
        this.negativeCycle = negative ? extractNegativeCycle() : null;
    }

    // ------------------------------------------------------------------
    // 初始化与两种更新方式
    // ------------------------------------------------------------------

    /** 初始矩阵:对角线 0,直接边取最轻的平行边(负的自环会把对角线压成负数) */
    private void initFromEdges(EdgeWeightedDigraph g) {
        int V = g.V();
        for (int i = 0; i < V; i++) {
            Arrays.fill(dist[i], Double.POSITIVE_INFINITY);
            Arrays.fill(next[i], -1);
            dist[i][i] = 0.0;
        }
        for (DirectedEdge e : g.edges()) {
            int from = e.from();
            int to = e.to();
            if (e.weight() < dist[from][to]) {
                dist[from][to] = e.weight();
                next[from][to] = to;
            }
        }
    }

    /** 就地滚动版:标准的 k、i、j 三重循环 */
    private void relaxInPlace(int V) {
        for (int k = 0; k < V; k++) {
            for (int i = 0; i < V; i++) {
                if (Double.isInfinite(dist[i][k])) {
                    continue;
                }
                for (int j = 0; j < V; j++) {
                    if (Double.isInfinite(dist[k][j])) {
                        continue;
                    }
                    double candidate = dist[i][k] + dist[k][j];
                    if (candidate < dist[i][j]) {
                        dist[i][j] = candidate;
                        next[i][j] = next[i][k];
                    }
                }
            }
        }
    }

    /** 副本版:第 k 轮只用上一轮的结果,严格对应递推式 */
    private void relaxWithCopy(int V) {
        double[][] previousDist = new double[V][V];
        int[][] previousNext = new int[V][V];
        for (int k = 0; k < V; k++) {
            copyMatrix(dist, previousDist);
            copyMatrix(next, previousNext);
            for (int i = 0; i < V; i++) {
                if (Double.isInfinite(previousDist[i][k])) {
                    continue;
                }
                for (int j = 0; j < V; j++) {
                    if (Double.isInfinite(previousDist[k][j])) {
                        continue;
                    }
                    double candidate = previousDist[i][k] + previousDist[k][j];
                    if (candidate < previousDist[i][j]) {
                        dist[i][j] = candidate;
                        next[i][j] = previousNext[i][k];
                    }
                }
            }
        }
    }

    /** 把一个 V×V 的 double 矩阵整体复制 */
    private static void copyMatrix(double[][] source, double[][] target) {
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, target[i], 0, source[i].length);
        }
    }

    /** 把一个 V×V 的 int 矩阵整体复制 */
    private static void copyMatrix(int[][] source, int[][] target) {
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, target[i], 0, source[i].length);
        }
    }

    // ------------------------------------------------------------------
    // 查询:距离
    // ------------------------------------------------------------------

    /**
     * @return 顶点数 V
     */
    public int V() {
        return graph.V();
    }

    /**
     * @return 本实例所用的更新方式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * i 到 j 的最短距离。
     *
     * <p><b>注意</b>:若 {@link #hasNegativeCycle()} 为 true,矩阵里被负环影响过的项
     * 已经不再是最短距离(理论上应为 −∞),此时该值不可信。</p>
     *
     * @param i 起点
     * @param j 终点
     * @return 最短距离;不可达返回 {@link Double#POSITIVE_INFINITY}
     * @throws IllegalArgumentException 顶点越界
     */
    public double dist(int i, int j) {
        validateVertex(i);
        validateVertex(j);
        return dist[i][j];
    }

    /**
     * @param i 起点
     * @param j 终点
     * @return i 能否到达 j
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean hasPath(int i, int j) {
        validateVertex(i);
        validateVertex(j);
        return dist[i][j] < Double.POSITIVE_INFINITY;
    }

    /**
     * 距离矩阵的副本(行 = 起点,列 = 终点),便于外部整体比对或画表。
     *
     * @return V×V 的二维数组副本
     */
    public double[][] distances() {
        double[][] copy = new double[graph.V()][graph.V()];
        copyMatrix(dist, copy);
        return copy;
    }

    // ------------------------------------------------------------------
    // 查询:路径
    // ------------------------------------------------------------------

    /**
     * i 到 j 的最短路径(顶点序列)。
     *
     * @param i 起点
     * @param j 终点
     * @return 顶点序列(首元素 i、末元素 j);不可达返回 {@code null};
     *         {@code i == j} 且无负环时返回只含 i 的单元素列表(空路径)
     * @throws IllegalArgumentException 顶点越界
     */
    public List<Integer> path(int i, int j) {
        validateVertex(i);
        validateVertex(j);
        if (!hasPath(i, j)) {
            return null;
        }
        if (i == j && dist[i][j] >= 0) {
            return Collections.singletonList(i);      // 空路径
        }
        List<Integer> vertices = new ArrayList<Integer>();
        int current = i;
        vertices.add(current);
        while (current != j) {
            int step = next[current][j];
            if (step == -1) {
                return null;                          // 理论上不会发生(除非有负环)
            }
            current = step;
            vertices.add(current);
            if (vertices.size() > graph.V() + 1) {
                throw new IllegalStateException("路径重建出现死循环,说明存在负环;请先检查 hasNegativeCycle()");
            }
        }
        return vertices;
    }

    /**
     * i 到 j 的最短路径(边序列)。
     *
     * @param i 起点
     * @param j 终点
     * @return 边序列;不可达返回 {@code null}
     * @throws IllegalArgumentException 顶点越界
     */
    public List<DirectedEdge> pathEdges(int i, int j) {
        List<Integer> vertices = path(i, j);
        if (vertices == null) {
            return null;
        }
        List<DirectedEdge> edges = new ArrayList<DirectedEdge>();
        for (int index = 0; index + 1 < vertices.size(); index++) {
            edges.add(lightestEdge(vertices.get(index), vertices.get(index + 1)));
        }
        return edges;
    }

    // ------------------------------------------------------------------
    // 负环
    // ------------------------------------------------------------------

    /**
     * @return 是否存在负环(等价于某个 {@code dist[v][v] &lt; 0});存在时所有最短距离都不可信
     */
    public boolean hasNegativeCycle() {
        return hasNegativeCycle;
    }

    /**
     * 一个具体的负环(顶点序列,首尾相接)。用后继矩阵从"对角线为负"的顶点出发回到它自己,
     * 再按图上的真实边权核算该环总权确实为负才返回。
     *
     * @return 负环的顶点序列(如 {@code [3, 6, 4]} 表示 3→6→4→3);不存在负环时返回 {@code null}
     */
    public List<Integer> negativeCycle() {
        if (negativeCycle == null) {
            return null;
        }
        List<Integer> cycle = new ArrayList<Integer>(negativeCycle.length);
        for (int v : negativeCycle) {
            cycle.add(v);
        }
        return cycle;
    }

    /** 从对角线为负的顶点出发,沿"去自己的下一跳"绕回自己,并核算环权确为负 */
    private int[] extractNegativeCycle() {
        int V = graph.V();
        for (int start = 0; start < V; start++) {
            if (dist[start][start] >= 0) {
                continue;
            }
            List<Integer> cycle = new ArrayList<Integer>();
            cycle.add(start);
            int current = next[start][start];
            while (current != -1 && current != start && cycle.size() <= V) {
                cycle.add(current);
                current = next[current][start];
            }
            if (current != start) {
                continue;                              // 没能绕回起点,换一个试
            }
            if (cycleWeight(cycle) < 0) {
                int[] result = new int[cycle.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = cycle.get(i);
                }
                return result;
            }
        }
        return null;
    }

    /** 环的真实总权(按图上的最轻平行边计算,末尾回到起点) */
    private double cycleWeight(List<Integer> cycle) {
        double sum = 0.0;
        for (int index = 0; index < cycle.size(); index++) {
            int from = cycle.get(index);
            int to = cycle.get((index + 1) % cycle.size());
            DirectedEdge e = lightestEdge(from, to);
            if (e == null) {
                return Double.POSITIVE_INFINITY;
            }
            sum += e.weight();
        }
        return sum;
    }

    /** 两个顶点之间最轻的有向边;不存在返回 null */
    private DirectedEdge lightestEdge(int from, int to) {
        DirectedEdge best = null;
        for (DirectedEdge e : graph.adj(from)) {
            if (e.to() == to && (best == null || e.weight() < best.weight())) {
                best = e;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // 显示
    // ------------------------------------------------------------------

    /**
     * @return 形如 {@code FloydWarshall(IN_PLACE): V=8, 存在负环=false};存在负环时附上环
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FloydWarshall(").append(mode).append("): V=").append(graph.V())
                .append(", 存在负环=").append(hasNegativeCycle);
        if (hasNegativeCycle) {
            sb.append(", 例如 ").append(negativeCycle());
        }
        return sb.toString();
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
     * 演示:读入有向带权图(缺省用 tinyEWD.txt 的内容),打印全源距离矩阵、若干点对的最短路径,
     * 并演示负环检测。
     *
     * @param args 可选:algs4 有向加权图数据文件路径
     */
    public static void main(String[] args) {
        EdgeWeightedDigraph graph = args.length > 0 ? GraphIO.readWeightedDigraphFile(args[0]) : sampleGraph();
        System.out.println("V = " + graph.V() + ", E = " + graph.E());

        for (Mode mode : Mode.values()) {
            FloydWarshall fw = new FloydWarshall(graph, mode);
            System.out.println(fw);
            System.out.println("  距离矩阵(∞ 表示不可达):");
            for (int i = 0; i < graph.V(); i++) {
                StringBuilder row = new StringBuilder("    ");
                for (int j = 0; j < graph.V(); j++) {
                    double d = fw.dist(i, j);
                    row.append(Double.isInfinite(d) ? "     ∞" : String.format("%6.2f", d));
                }
                System.out.println(row);
            }
        }

        FloydWarshall fw = new FloydWarshall(graph);
        System.out.println("0 -> 6 距离 " + fw.dist(0, 6) + " 路径 " + fw.path(0, 6));
        for (DirectedEdge e : fw.pathEdges(0, 6)) {
            System.out.println("      " + e);
        }
        System.out.println("6 -> 1 距离 " + fw.dist(6, 1) + " 路径 " + fw.path(6, 1));

        FloydWarshall withCycle = new FloydWarshall(negativeCycleSample());
        System.out.println("负环演示: " + withCycle);
    }

    /** 内置演示图:与工作区 tinyEWD.txt 相同的 8 顶点 / 15 有向边加权图 */
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

    /** 演示用的小图:含一个负环 1→2→3→1(总权 −0.5) */
    private static EdgeWeightedDigraph negativeCycleSample() {
        EdgeWeightedDigraph graph = new EdgeWeightedDigraph(4);
        graph.addEdge(0, 1, 1.0);
        graph.addEdge(1, 2, 1.0);
        graph.addEdge(2, 3, 1.0);
        graph.addEdge(3, 1, -2.5);      // 1→2→3→1 = 1+1-2.5 = -0.5
        return graph;
    }
}
