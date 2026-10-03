package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 有向无环图(DAG)上的<b>最长路径</b>(Critical Path / Longest Path):Θ(V+E)。
 *
 * <p><b>为什么只能是无环图</b>:有环就可以"绕一圈再回来",路径长度没有上界
 * —— 所以"最长路"只在 DAG 上有定义(这也是它与"最短路"最大的不同:
 * {@link BellmanFordSP} 在有负环时同样变得无定义,道理是一样的)。</p>
 *
 * <p><b>算法</b>:与 {@link AcyclicSP} 一模一样 —— 按拓扑序松弛一遍,只是把
 * {@code distTo[w] > distTo[v] + w} 改成 {@code <}:
 * 用"最大"代替"最小",松弛顺序保证每个顶点被处理时已经取到最优值。</p>
 *
 * <p><b>典型用途</b>:项目进度里的"关键路径"(见 {@link CriticalPath} 与 {@link AOENetwork},
 * 它们内部算的就是这件事)、依赖图里最长的依赖链、编译器里最优指令调度的时间下界。</p>
 *
 * <pre>
 * AcyclicLP longest = new AcyclicLP(graph, 0);
 * longest.distTo(v);      // 最长距离(不可达为 −∞)
 * longest.pathTo(v);      // 一条最长路径
 * </pre>
 *
 * @see AcyclicSP
 * @see CriticalPath
 * @see TopologicalSort
 * @see <a href="https://algs4.cs.princeton.edu/44sp">Algorithms, 4th Edition, Section 4.4</a>
 */
public final class AcyclicLP {

    /** 被求解的图 */
    private final EdgeWeightedDigraph graph;

    /** 源点 */
    private final int source;

    /** distTo[v] = 从源点到 v 的最长距离;不可达为 −∞ */
    private final double[] distTo;

    /** edgeTo[v] = 最长路径上通向 v 的最后一条边 */
    private final DirectedEdge[] edgeTo;

    /** 松弛顺序(拓扑序) */
    private final int[] topologicalOrder;

    /**
     * 求从 {@code source} 出发的最长路径。
     *
     * @param graph  有向加权图,不能为 null,且必须<b>无环</b>
     * @param source 源点编号
     * @throws IllegalArgumentException 参数为 null、源点越界,或图中存在环
     */
    public AcyclicLP(EdgeWeightedDigraph graph, int source) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (source < 0 || source >= graph.V()) {
            throw new IllegalArgumentException("源点 " + source + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
        TopologicalSort sorter = new TopologicalSort(graph.toDigraph());
        if (sorter.hasCycle()) {
            throw new IllegalArgumentException("图中有环 " + Arrays.toString(sorter.cycle())
                    + ",最长路径只对无环图有定义(有环就可以绕圈无限变长)");
        }
        this.graph = graph;
        this.source = source;
        this.topologicalOrder = sorter.order();

        int V = graph.V();
        this.distTo = new double[V];
        this.edgeTo = new DirectedEdge[V];
        Arrays.fill(distTo, Double.NEGATIVE_INFINITY);
        distTo[source] = 0.0;

        for (int index = 0; index < topologicalOrder.length; index++) {
            int v = topologicalOrder[index];
            if (distTo[v] == Double.NEGATIVE_INFINITY) {
                continue;
            }
            for (DirectedEdge edge : graph.adj(v)) {
                int w = edge.to();
                if (distTo[w] < distTo[v] + edge.weight()) {
                    distTo[w] = distTo[v] + edge.weight();
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
     * @param v 顶点编号
     * @return 最长距离;不可达返回 {@link Double#NEGATIVE_INFINITY}
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
        return distTo[v] != Double.NEGATIVE_INFINITY;
    }

    /**
     * @param v 顶点编号
     * @return 最长路径上的最后一条边;不可达返回 null
     * @throws IllegalArgumentException 顶点越界
     */
    public DirectedEdge edgeTo(int v) {
        validateVertex(v);
        return edgeTo[v];
    }

    /**
     * 一条最长路径(边的列表)。
     *
     * @param v 顶点编号
     * @return 路径上的边;不可达返回 null
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
     * 一条最长路径(顶点编号列表,含首尾)。
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

    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code AcyclicLP(源点 0): 8 个顶点,13 条边},随后每个顶点一行
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("AcyclicLP(源点 ").append(source).append("): ").append(graph.V())
                .append(" 个顶点,").append(graph.E()).append(" 条边").append(newline);
        for (int v = 0; v < graph.V(); v++) {
            sb.append("  ").append(source).append(" to ").append(v).append(": ");
            if (hasPathTo(v)) {
                sb.append(String.format("%.2f", distTo[v])).append(' ').append(pathTo(v));
            }
            else {
                sb.append("不可达");
            }
            sb.append(newline);
        }
        return sb.toString();
    }

    /**
     * 演示:在 tinyEWDAG.txt(无环、含负权弧)上,最短路与最长路各是什么;
     * 再把所有弧取反来看"最短路取反 = 不成立"的直观例子。
     *
     * @param args 可选:有向加权图数据文件路径
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyEWDAG.txt";
        EdgeWeightedDigraph graph = GraphIO.readWeightedDigraphFile(path);
        System.out.println("数据文件: " + path);
        AcyclicSP shortest = new AcyclicSP(graph, 5);
        AcyclicLP longest = new AcyclicLP(graph, 5);
        System.out.println("顶点  最短路  最长路");
        for (int v = 0; v < graph.V(); v++) {
            System.out.printf("  %d   %6.2f  %6.2f%n", v,
                    shortest.hasPathTo(v) ? shortest.distTo(v) : Double.NaN,
                    longest.hasPathTo(v) ? longest.distTo(v) : Double.NaN);
        }
        System.out.println("最长路 5 -> 2: " + longest.pathTo(2) + "(距离 " + longest.distTo(2) + ")");
    }
}
