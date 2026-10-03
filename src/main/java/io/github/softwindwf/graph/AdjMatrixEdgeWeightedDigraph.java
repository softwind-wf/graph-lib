package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 有向加权图的<b>邻接矩阵</b>表示:与 {@link EdgeWeightedDigraph}(邻接表)是同一张图的两种存法。
 *
 * <table border="1" summary="两种表示的对照">
 *   <tr><th></th><th>邻接表(EdgeWeightedDigraph)</th><th>邻接矩阵(本类)</th></tr>
 *   <tr><td>空间</td><td>Θ(V + E)</td><td>Θ(V²)</td></tr>
 *   <tr><td>查"u→v 的权值"</td><td>Θ(出度)</td><td><b>Θ(1)</b></td></tr>
 *   <tr><td>遍历 v 的出边</td><td>Θ(出度)(只碰存在的边)</td><td>Θ(V)(要扫一整行)</td></tr>
 *   <tr><td>加/删边</td><td>Θ(1) / Θ(出度)</td><td>Θ(1)</td></tr>
 *   <tr><td>适用</td><td>稀疏图(绝大多数真实图)</td><td>稠密图 / 需要频繁随机查边</td></tr>
 * </table>
 *
 * <p><b>约定</b>:</p>
 * <ul>
 *   <li>{@code weight[v][w]} 存 u→v 的权值;没有边时用 {@link Double#POSITIVE_INFINITY} 表示
 *       (与 {@link FloydWarshall} 的"不可达"口径一致);</li>
 *   <li>同一对顶点之间重复加边时<b>保留最小的权值</b>(矩阵只能存一个数);</li>
 *   <li>{@link #weightOf(int, int)} 对不存在的边抛异常,{@link #weightOrInfinity(int, int)} 返回 +∞;</li>
 *   <li>与邻接表之间可以互相转换:{@link #AdjMatrixEdgeWeightedDigraph(EdgeWeightedDigraph)} 与
 *       {@link #toEdgeWeightedDigraph()}。</li>
 * </ul>
 *
 * <pre>
 * AdjMatrixEdgeWeightedDigraph matrix = new AdjMatrixEdgeWeightedDigraph(4);
 * matrix.addEdge(0, 1, 0.5);
 * matrix.weightOf(0, 1);        // 0.5
 * matrix.hasEdge(0, 2);         // false
 * matrix.toEdgeWeightedDigraph();   // 转回邻接表,交给 DijkstraSP / BellmanFordSP
 * </pre>
 *
 * @see EdgeWeightedDigraph
 * @see DirectedEdge
 * @see FloydWarshall
 * @see <a href="https://algs4.cs.princeton.edu/44sp">Algorithms, 4th Edition, Section 4.4</a>
 */
public final class AdjMatrixEdgeWeightedDigraph {

    /** 允许的最大顶点数:矩阵是 V² 个 double,太大就不该用这种表示 */
    private static final int MAX_VERTICES = 1 << 13;

    /** 顶点数 */
    private final int vertexCount;

    /** weight[v][w] = 弧 v→w 的权值;没有弧为 +∞ */
    private final double[][] weight;

    /** 弧数(重复加边时按"最小权值"保留,所以后加的重复弧不算新增) */
    private int edgeCount;

    /**
     * 建一个含 {@code V} 个顶点、还没有弧的邻接矩阵图。
     *
     * @param V 顶点数,需在 {@code [0, 1<<13]} 内
     * @throws IllegalArgumentException 顶点数越界
     */
    public AdjMatrixEdgeWeightedDigraph(int V) {
        if (V < 0 || V > MAX_VERTICES) {
            throw new IllegalArgumentException("顶点数 " + V + " 不在 [0, " + MAX_VERTICES + "] 内");
        }
        this.vertexCount = V;
        this.weight = new double[V][V];
        for (double[] row : weight) {
            java.util.Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        this.edgeCount = 0;
    }

    /**
     * 从邻接表复制一份(等价于换一种存法)。
     *
     * @param graph 有向加权图,不能为 null
     * @throws IllegalArgumentException 参数为 null 或顶点数超过上限
     */
    public AdjMatrixEdgeWeightedDigraph(EdgeWeightedDigraph graph) {
        this(requireVertexCount(graph));
        for (DirectedEdge edge : graph.edges()) {
            addEdge(edge);
        }
    }

    /** 校验并取出顶点数(null 图直接报错) */
    private static int requireVertexCount(EdgeWeightedDigraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        return graph.V();
    }

    /**
     * 加一条弧;若同一对顶点已有弧,<b>保留更小的权值</b>。
     *
     * @param edge 待加入的弧,不能为 null
     * @throws IllegalArgumentException 参数为 null 或端点越界
     */
    public void addEdge(DirectedEdge edge) {
        if (edge == null) {
            throw new IllegalArgumentException("边不能为 null");
        }
        addEdge(edge.from(), edge.to(), edge.weight());
    }

    /**
     * 加一条弧。
     *
     * @param from   起点
     * @param to     终点
     * @param weight 权值,有限实数
     * @throws IllegalArgumentException 端点越界、自环或权值非有限
     */
    public void addEdge(int from, int to, double weight) {
        validateVertex(from);
        validateVertex(to);
        if (from == to) {
            throw new IllegalArgumentException("邻接矩阵表示不允许自环: " + from + " -> " + to);
        }
        if (Double.isNaN(weight) || Double.isInfinite(weight)) {
            throw new IllegalArgumentException("权值必须是有限实数,当前为 " + weight);
        }
        if (Double.isInfinite(this.weight[from][to])) {
            edgeCount++;
        }
        this.weight[from][to] = Math.min(this.weight[from][to], weight);
    }

    /**
     * @return 顶点数
     */
    public int V() {
        return vertexCount;
    }

    /**
     * @return 弧数(不同顶点对的数量)
     */
    public int E() {
        return edgeCount;
    }

    /**
     * @param from 起点
     * @param to   终点
     * @return 是否有弧 from→to
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean hasEdge(int from, int to) {
        validateVertex(from);
        validateVertex(to);
        return !Double.isInfinite(weight[from][to]);
    }

    /**
     * 取弧的权值;不存在时抛异常(想拿到 +∞ 请用 {@link #weightOrInfinity(int, int)})。
     *
     * @param from 起点
     * @param to   终点
     * @return 权值
     * @throws IllegalArgumentException 顶点越界
     * @throws NoSuchElementException   没有这条弧
     */
    public double weightOf(int from, int to) {
        validateVertex(from);
        validateVertex(to);
        if (Double.isInfinite(weight[from][to])) {
            throw new NoSuchElementException("没有从 " + from + " 到 " + to + " 的弧");
        }
        return weight[from][to];
    }

    /**
     * 取弧的权值;不存在时返回 {@link Double#POSITIVE_INFINITY}。
     *
     * @param from 起点
     * @param to   终点
     * @return 权值或 +∞
     * @throws IllegalArgumentException 顶点越界
     */
    public double weightOrInfinity(int from, int to) {
        validateVertex(from);
        validateVertex(to);
        return weight[from][to];
    }

    /**
     * @param v 顶点编号
     * @return v 的出度(扫一整行,Θ(V))
     * @throws IllegalArgumentException 顶点越界
     */
    public int outDegree(int v) {
        validateVertex(v);
        int degree = 0;
        for (int w = 0; w < vertexCount; w++) {
            if (!Double.isInfinite(weight[v][w])) {
                degree++;
            }
        }
        return degree;
    }

    /**
     * @param v 顶点编号
     * @return v 的入度(扫一整列,Θ(V))
     * @throws IllegalArgumentException 顶点越界
     */
    public int inDegree(int v) {
        validateVertex(v);
        int degree = 0;
        for (int u = 0; u < vertexCount; u++) {
            if (!Double.isInfinite(weight[u][v])) {
                degree++;
            }
        }
        return degree;
    }

    /**
     * 以 v 为起点的全部弧(顺序与邻接表不同:这里按终点编号升序)。
     *
     * @param v 顶点编号
     * @return 出边列表
     * @throws IllegalArgumentException 顶点越界
     */
    public List<DirectedEdge> adj(int v) {
        validateVertex(v);
        List<DirectedEdge> edges = new ArrayList<DirectedEdge>();
        for (int w = 0; w < vertexCount; w++) {
            if (!Double.isInfinite(weight[v][w])) {
                edges.add(new DirectedEdge(v, w, weight[v][w]));
            }
        }
        return edges;
    }

    /**
     * @return 全部弧,按起点、终点升序
     */
    public List<DirectedEdge> edges() {
        List<DirectedEdge> edges = new ArrayList<DirectedEdge>(edgeCount);
        for (int v = 0; v < vertexCount; v++) {
            for (int w = 0; w < vertexCount; w++) {
                if (!Double.isInfinite(weight[v][w])) {
                    edges.add(new DirectedEdge(v, w, weight[v][w]));
                }
            }
        }
        return edges;
    }

    /**
     * @return 矩阵里最小的弧权;没有弧返回 +∞
     */
    public double minWeight() {
        double min = Double.POSITIVE_INFINITY;
        for (int v = 0; v < vertexCount; v++) {
            for (int w = 0; w < vertexCount; w++) {
                min = Math.min(min, weight[v][w]);
            }
        }
        return min;
    }

    /**
     * 转回邻接表表示,便于交给 {@link DijkstraSP}、{@link BellmanFordSP}、{@link FloydWarshall} 等算法。
     *
     * @return 等价的有向加权图
     */
    public EdgeWeightedDigraph toEdgeWeightedDigraph() {
        EdgeWeightedDigraph graph = new EdgeWeightedDigraph(vertexCount);
        for (DirectedEdge edge : edges()) {
            graph.addEdge(edge);
        }
        return graph;
    }

    private void validateVertex(int v) {
        if (v < 0 || v >= vertexCount) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (vertexCount - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code 4 vertices, 3 edges},随后是矩阵(V ≤ 16 时打印;∞ 用 "∞" 表示)
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append(vertexCount).append(" vertices, ").append(edgeCount).append(" edges").append(newline);
        if (vertexCount <= 16) {
            sb.append("     ");
            for (int w = 0; w < vertexCount; w++) {
                sb.append(String.format("%9d", w));
            }
            sb.append(newline);
            for (int v = 0; v < vertexCount; v++) {
                sb.append(String.format("%4d ", v));
                for (int w = 0; w < vertexCount; w++) {
                    sb.append(Double.isInfinite(weight[v][w])
                            ? String.format("%9s", "∞")
                            : String.format("%9.2f", weight[v][w]));
                }
                sb.append(newline);
            }
        }
        return sb.toString();
    }

    /**
     * 演示:同一张图分别用邻接表与邻接矩阵表示,对照"查边"与"遍历"的不同代价,
     * 并用两种表示跑 Dijkstra 得到一致结果。
     *
     * @param args 可选:有向加权图数据文件路径
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyEWD.txt";
        EdgeWeightedDigraph list = GraphIO.readWeightedDigraphFile(path);
        AdjMatrixEdgeWeightedDigraph matrix = new AdjMatrixEdgeWeightedDigraph(list);
        System.out.println("数据文件: " + path);
        System.out.println("邻接表: V=" + list.V() + ", E=" + list.E());
        System.out.println("邻接矩阵: V=" + matrix.V() + ", E=" + matrix.E()
                + ",最小弧权 " + String.format("%.2f", matrix.minWeight()));

        DijkstraSP listSp = new DijkstraSP(list, 0);
        DijkstraSP matrixSp = new DijkstraSP(matrix.toEdgeWeightedDigraph(), 0);
        boolean same = true;
        for (int v = 0; v < list.V(); v++) {
            same &= Math.abs(listSp.distTo(v) - matrixSp.distTo(v)) < 1e-9;
        }
        System.out.println("两种表示上跑 Dijkstra 结果一致: " + same);
        System.out.println("矩阵的前几行:");
        String[] lines = matrix.toString().split("\\r?\\n");
        for (int i = 0; i < Math.min(lines.length, 6); i++) {
            System.out.println(lines[i]);
        }
    }
}
