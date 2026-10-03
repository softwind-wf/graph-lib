package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 有向无环图(DAG)上的单源最短路:按<b>拓扑序</b>松弛一遍即可,Θ(V + E)。
 *
 * <p><b>为什么这么快</b>:最短路的通用做法是"反复松弛直到不再变化"(Bellman–Ford 需要 V−1 轮),
 * 但在<b>无环</b>图里每个顶点只依赖"排在自己前面的顶点",于是按拓扑序走一趟,
 * 处理某个顶点时它前面所有顶点的距离都已经最终确定,每条弧只需要松弛一次。</p>
 *
 * <p><b>能处理负权</b>:只要无环,<b>负权弧完全没问题</b> —— 因为不存在"绕一圈回来更短"的可能。
 * 这一点和 {@link DijkstraSP}(要求所有弧非负)正好互补。</p>
 *
 * <table border="1" summary="单源最短路四种场景">
 *   <tr><th>图的性质</th><th>该用谁</th><th>时间</th></tr>
 *   <tr><td>无环(可含负权)</td><td><b>{@link AcyclicSP}</b></td><td>Θ(V + E)</td></tr>
 *   <tr><td>无负权(可有环)</td><td>{@link DijkstraSP}</td><td>Θ(E log V)</td></tr>
 *   <tr><td>一般图(可含负权、含负环)</td><td>{@link BellmanFordSP}</td><td>Θ(V·E)</td></tr>
 *   <tr><td>全源(稀疏图)</td><td>{@link Johnson}</td><td>Θ(V·E·log V)</td></tr>
 * </table>
 *
 * <p>本类在构造时要求图<b>确实无环</b>:先借 {@link TopologicalSort} 求拓扑序,
 * 若发现环就抛出 {@link IllegalArgumentException},并在消息里给出具体环(提示改用 BellmanFordSP)。</p>
 *
 * <pre>
 * EdgeWeightedDigraph g = GraphIO.readWeightedDigraphFile("tinyEWDAG.txt");
 * AcyclicSP sp = new AcyclicSP(g, 5);
 * sp.distTo(0);      // 最短距离(可为负)
 * sp.pathTo(0);      // 一条最短路径(边列表)
 * </pre>
 *
 * @see DijkstraSP
 * @see BellmanFordSP
 * @see Johnson
 * @see TopologicalSort
 * @see <a href="https://algs4.cs.princeton.edu/44sp">Algorithms, 4th Edition, Section 4.4</a>
 */
public final class AcyclicSP {

    /** 被求解的图 */
    private final EdgeWeightedDigraph graph;

    /** 源点 */
    private final int source;

    /** distTo[v] = 从源点到 v 的最短距离;不可达为 Double.POSITIVE_INFINITY */
    private final double[] distTo;

    /** edgeTo[v] = 最短路径上通向 v 的最后一条边;不可达为 null */
    private final DirectedEdge[] edgeTo;

    /** 本次求解使用的拓扑序(即松弛顺序) */
    private final int[] topologicalOrder;

    /** 松弛成功的次数(每条弧最多松弛一次,所以最多 E 次) */
    private final int relaxCount;

    /**
     * 求有向无环图上从 {@code source} 到所有顶点的最短路。
     *
     * @param graph  有向加权图,不能为 null,且必须<b>无环</b>
     * @param source 源点编号
     * @throws IllegalArgumentException 参数为 null、源点越界,或图中存在环(AOE/DAG 之外的一般图请用 BellmanFordSP)
     */
    public AcyclicSP(EdgeWeightedDigraph graph, int source) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (source < 0 || source >= graph.V()) {
            throw new IllegalArgumentException("源点 " + source + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
        this.graph = graph;
        this.source = source;

        TopologicalSort sorter = new TopologicalSort(graph.toDigraph());
        if (sorter.hasCycle()) {
            throw new IllegalArgumentException("图中有环 " + Arrays.toString(sorter.cycle())
                    + ",DAG 最短路不适用(有负权的一般图请用 BellmanFordSP,非负权请用 DijkstraSP)");
        }
        this.topologicalOrder = sorter.order();

        int V = graph.V();
        this.distTo = new double[V];
        this.edgeTo = new DirectedEdge[V];
        Arrays.fill(distTo, Double.POSITIVE_INFINITY);
        distTo[source] = 0.0;

        // 按拓扑序松弛:处理 v 时,v 的距离已经最终确定
        int relaxations = 0;
        for (int index = 0; index < topologicalOrder.length; index++) {
            int v = topologicalOrder[index];
            if (distTo[v] == Double.POSITIVE_INFINITY) {
                continue;                                  // 源点不可达
            }
            for (DirectedEdge edge : graph.adj(v)) {
                int to = edge.to();
                if (distTo[to] > distTo[v] + edge.weight()) {
                    distTo[to] = distTo[v] + edge.weight();
                    edgeTo[to] = edge;
                    relaxations++;
                }
            }
        }
        this.relaxCount = relaxations;
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
     * 从源点到 v 的最短距离。
     *
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
    public DirectedEdge edgeTo(int v) {
        validateVertex(v);
        return edgeTo[v];
    }

    /**
     * 一条最短路径(边的列表,从源点到 v)。
     *
     * @param v 顶点编号
     * @return 路径上的边(按行进顺序);不可达返回 null
     * @throws IllegalArgumentException 顶点越界
     */
    public List<DirectedEdge> pathTo(int v) {
        validateVertex(v);
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
     * @return 本次求解使用的拓扑序(副本)
     */
    public int[] topologicalOrder() {
        return Arrays.copyOf(topologicalOrder, topologicalOrder.length);
    }

    /**
     * @return 松弛成功的次数(每条弧最多一次,所以不超过 E)
     */
    public int relaxCount() {
        return relaxCount;
    }

    /**
     * @return 形如 {@code AcyclicSP(源点 5): 8 个顶点,13 条边,松弛 13 次},
     *         随后每行是 {@code "5 to v: 距离 [边...]"}
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("AcyclicSP(源点 ").append(source).append("): ").append(graph.V())
                .append(" 个顶点,").append(graph.E()).append(" 条边,松弛 ")
                .append(relaxCount).append(" 次").append(newline);
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

    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    /**
     * 演示:在 tinyEWDAG.txt(8 顶点、13 弧、含负权 -1.20 的无环图)上求源点 5 的最短路;
     * 命令行给出文件路径与源点时改读文件。
     *
     * @param args 可选:{@code 文件路径 [源点]}
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyEWDAG.txt";
        EdgeWeightedDigraph graph = GraphIO.readWeightedDigraphFile(path);
        int source = args.length > 1 ? Integer.parseInt(args[1]) : 5;

        System.out.println("数据文件: " + path + "(V=" + graph.V() + ", E=" + graph.E() + ")");
        AcyclicSP sp = new AcyclicSP(graph, source);
        System.out.println(sp);

        // 对比:Dijkstra 在含负权的图上应当直接拒绝
        try {
            new DijkstraSP(graph, source);
            System.out.println("Dijkstra 接受了这张图(不应该发生)");
        }
        catch (IllegalArgumentException e) {
            System.out.println("对照实验 —— Dijkstra 拒绝这张图: " + e.getMessage());
        }
    }
}
