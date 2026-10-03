package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 无向加权图的单源最短路(Dijkstra):从源点到每个顶点的最短距离与路径,要求所有边权<b>非负</b>。
 *
 * <p><b>与 {@link DijkstraSP} 的区别</b>:那个跑在<b>有向</b>带权图上({@link EdgeWeightedDigraph}),
 * 本类跑在<b>无向</b>带权图上({@link EdgeWeightedGraph}),路径返回的是 {@link Edge}。
 * 无向边"两个方向都能走",遍历时用 {@link Edge#other(int)} 取对端即可;
 * 底层算法完全一样(贪心 + 优先队列)。有向图版本请用 {@link DijkstraSP}。</p>
 *
 * <table border="1" summary="两种实现">
 *   <tr><th>模式</th><th>做法</th><th>时间</th></tr>
 *   <tr><td>{@link Mode#LAZY}</td><td>优先队列里允许同一顶点多次入队(过期条目直接丢弃)</td>
 *       <td>Θ(E log E)</td></tr>
 *   <tr><td>{@link Mode#DENSE}</td><td>每轮线性扫描未确定顶点里距离最小的(v 较小时更快)</td>
 *       <td>Θ(V²)</td></tr>
 * </table>
 *
 * <p><b>为什么要求非负权</b>:Dijkstra 的"确定即最终"依赖"绕远路不会更短"。
 * 有负权时这条不成立(要绕一圈回来反而更短),应改用
 * {@link BellmanFordSP}(有向)或把它拆成两条有向边后使用(无向负权图基本没有实际意义 ——
 * 一条负权无向边本身就能来回无限变短)。</p>
 *
 * <pre>
 * DijkstraUndirectedSP sp = new DijkstraUndirectedSP(GraphIO.readWeightedFile("tinyEWG.txt"), 0);
 * sp.distTo(5);
 * sp.pathTo(5);        // [Edge 0-7 0.16, Edge 7-5 0.28]
 * sp.pathVertices(5);  // [0, 7, 5]
 * </pre>
 *
 * @see DijkstraSP
 * @see PrimMST
 * @see EdgeWeightedGraph
 * @see <a href="https://algs4.cs.princeton.edu/44sp">Algorithms, 4th Edition, Section 4.4</a>
 */
public final class DijkstraUndirectedSP {

    /** 求最短路的两种实现 */
    public enum Mode {

        /** 懒式:优先队列 + 过期条目丢弃 */
        LAZY,

        /** 稠密式:每轮线性扫描最小距离 */
        DENSE
    }

    /** 被求解的图 */
    private final EdgeWeightedGraph graph;

    /** 源点 */
    private final int source;

    /** 实现方式 */
    private final Mode mode;

    /** distTo[v] = 最短距离;不可达为 +∞ */
    private final double[] distTo;

    /** edgeTo[v] = 最短路径上通向 v 的最后一条边 */
    private final Edge[] edgeTo;

    /**
     * 求无向加权图上的单源最短路。
     *
     * @param graph  无向加权图,不能为 null,所有边权必须非负
     * @param source 源点编号
     * @throws IllegalArgumentException 参数为 null、源点越界,或存在负权边
     */
    public DijkstraUndirectedSP(EdgeWeightedGraph graph, int source) {
        this(graph, source, Mode.LAZY);
    }

    /**
     * 求无向加权图上的单源最短路。
     *
     * @param graph  无向加权图,不能为 null,所有边权必须非负
     * @param source 源点编号
     * @param mode   实现方式,不能为 null
     * @throws IllegalArgumentException 参数非法或存在负权边
     */
    public DijkstraUndirectedSP(EdgeWeightedGraph graph, int source, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("实现方式不能为 null");
        }
        if (source < 0 || source >= graph.V()) {
            throw new IllegalArgumentException("源点 " + source + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
        for (Edge edge : graph.edges()) {
            if (edge.weight() < 0) {
                throw new IllegalArgumentException("Dijkstra 要求边权非负,但存在负权边 " + edge
                        + ";有负权请改用 BellmanFordSP(有向图)或 FloydWarshall(全源)");
            }
        }
        this.graph = graph;
        this.source = source;
        this.mode = mode;
        int V = graph.V();
        this.distTo = new double[V];
        this.edgeTo = new Edge[V];
        Arrays.fill(distTo, Double.POSITIVE_INFINITY);
        distTo[source] = 0.0;

        if (mode == Mode.LAZY) {
            runLazy();
        }
        else {
            runDense();
        }
    }

    /** 懒式:优先队列(距离, 顶点),过期条目丢弃 */
    private void runLazy() {
        boolean[] settled = new boolean[graph.V()];
        PriorityQueue<double[]> queue = new PriorityQueue<double[]>(11, new Comparator<double[]>() {
            public int compare(double[] a, double[] b) {
                int byDistance = Double.compare(a[0], b[0]);
                return byDistance != 0 ? byDistance : Double.compare(a[1], b[1]);
            }
        });
        queue.add(new double[]{0.0, source});
        while (!queue.isEmpty()) {
            double[] entry = queue.poll();
            int v = (int) entry[1];
            if (settled[v]) {
                continue;
            }
            settled[v] = true;
            for (Edge edge : graph.adj(v)) {
                int w = edge.other(v);
                if (distTo[w] > distTo[v] + edge.weight()) {
                    distTo[w] = distTo[v] + edge.weight();
                    edgeTo[w] = edge;
                    queue.add(new double[]{distTo[w], w});
                }
            }
        }
    }

    /** 稠密式:每轮线性扫描 */
    private void runDense() {
        int V = graph.V();
        boolean[] settled = new boolean[V];
        for (int round = 0; round < V; round++) {
            int best = -1;
            for (int v = 0; v < V; v++) {
                if (!settled[v] && (best < 0 || distTo[v] < distTo[best])) {
                    best = v;
                }
            }
            if (best < 0 || distTo[best] == Double.POSITIVE_INFINITY) {
                break;
            }
            settled[best] = true;
            for (Edge edge : graph.adj(best)) {
                int w = edge.other(best);
                if (distTo[w] > distTo[best] + edge.weight()) {
                    distTo[w] = distTo[best] + edge.weight();
                    edgeTo[w] = edge;
                }
            }
        }
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
     * @return 源点编号
     */
    public int source() {
        return source;
    }

    /**
     * @return 实现方式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @param v 顶点编号
     * @return 最短距离;不可达返回 {@link Double#POSITIVE_INFINITY}
     * @throws IllegalArgumentException 顶点越界
     */
    public double distTo(int v) {
        validateVertex(v);
        return distTo[v];
    }

    /**
     * @param v 顶点编号
     * @return 源点能否到达 v
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean hasPathTo(int v) {
        validateVertex(v);
        return distTo[v] != Double.POSITIVE_INFINITY;
    }

    /**
     * @param v 顶点编号
     * @return 最短路径上的最后一条边;不可达返回 null
     * @throws IllegalArgumentException 顶点越界
     */
    public Edge edgeTo(int v) {
        validateVertex(v);
        return edgeTo[v];
    }

    /**
     * 一条最短路径(边的列表)。
     *
     * @param v 顶点编号
     * @return 路径上的边(按行进顺序);不可达返回 null
     * @throws IllegalArgumentException 顶点越界
     */
    public List<Edge> pathTo(int v) {
        validateVertex(v);
        if (!hasPathTo(v)) {
            return null;
        }
        List<Edge> path = new ArrayList<Edge>();
        int current = v;
        while (edgeTo[current] != null) {                  // 沿 edgeTo 一路回溯到源点
            Edge edge = edgeTo[current];
            path.add(edge);
            current = edge.other(current);
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
     */
    public List<Integer> pathVertices(int v) {
        List<Edge> path = pathTo(v);
        if (path == null) {
            return null;
        }
        List<Integer> vertices = new ArrayList<Integer>(path.size() + 1);
        int current = source;
        vertices.add(current);
        for (Edge edge : path) {
            current = edge.other(current);
            vertices.add(current);
        }
        return vertices;
    }

    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code DijkstraUndirectedSP(LAZY, 源点 0): 8 个顶点},随后每个顶点一行
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("DijkstraUndirectedSP(").append(mode).append(", 源点 ").append(source).append("): ")
                .append(graph.V()).append(" 个顶点,").append(graph.E()).append(" 条边").append(newline);
        for (int v = 0; v < graph.V(); v++) {
            sb.append("  ").append(source).append(" to ").append(v).append(": ");
            if (hasPathTo(v)) {
                sb.append(String.format("%.2f", distTo[v])).append(' ');
                for (Edge edge : pathTo(v)) {
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
     * 演示:对 tinyEWG.txt(8 顶点 16 条边)求源点 0 的最短路,并对照有向版 Dijkstra
     * (把无向边拆成两条有向边)的结果。
     *
     * @param args 可选:加权图数据文件路径
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyEWG.txt";
        EdgeWeightedGraph graph = GraphIO.readWeightedFile(path);
        DijkstraUndirectedSP sp = new DijkstraUndirectedSP(graph, 0);
        System.out.println("数据文件: " + path);
        System.out.println(sp);

        EdgeWeightedDigraph doubled = new EdgeWeightedDigraph(graph.V());
        for (Edge edge : graph.edges()) {
            doubled.addEdge(new DirectedEdge(edge.either(), edge.other(edge.either()), edge.weight()));
            doubled.addEdge(new DirectedEdge(edge.other(edge.either()), edge.either(), edge.weight()));
        }
        DijkstraSP directed = new DijkstraSP(doubled, 0);
        boolean same = true;
        for (int v = 0; v < graph.V(); v++) {
            same &= Math.abs(directed.distTo(v) - sp.distTo(v)) < 1e-9;
        }
        System.out.println("对照:把无向边拆成两条有向边后跑 DijkstraSP,距离完全一致: " + same);
    }
}
