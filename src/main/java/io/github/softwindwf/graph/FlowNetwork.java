package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * 流网络(A flow network):每条有向边带<b>容量</b>,有<b>一个源点 s</b> 和一个<b>汇点 t</b>。
 *
 * <p><b>要解决的问题</b>:从 s 到 t 最多能"推"多少流量?约束有两条 ——</p>
 * <ol>
 *   <li><b>容量约束</b>:每条边 {@code u→v} 上的流量不超过容量;</li>
 *   <li><b>流量守恒</b>:除 s、t 之外的每个顶点,"流入 = 流出"(中间不囤积、不凭空产生)。</li>
 * </ol>
 * <p>s 的净流出量就是<b>流值</b>(value),我们的目标是让它最大。
 * {@link FordFulkerson} 的"增广路"方法与由此得出的
 * <b>最大流最小割定理</b>(最大流值 = 最小割容量,见 {@link MinCut})是整套理论的基石。</p>
 *
 * <p><b>本类的表示</b>:与 {@link EdgeWeightedDigraph}(只存出边)不同,一条 {@link FlowEdge}
 * 会同时出现在起点和终点的邻接表里 —— 因为残量图里"反向可以退流",所以从任一顶点出发
 * 都要能看到它的所有关联边。`adj(v)` 返回的正是这份"含反向可退边"的列表,
 * 算法只需沿着 {@code residualCapacityTo} 大于 0 的方向走。</p>
 *
 * <p><b>约定</b>:</p>
 * <ul>
 *   <li>容量必须非负有限;流量恒满足 {@code 0 ≤ flow ≤ capacity};</li>
 *   <li>允许平行边(两条管道的容量各自独立),<b>不允许自环</b>(自环对流没有意义);</li>
 *   <li>{@link #edges()} 每条边只返回一次(按加入顺序),邻接表里则出现两次;</li>
 *   <li>文本读写见 {@link GraphIO}({@code parseFlowNetwork} / {@code readFlowNetworkFile});</li>
 *   <li>文本格式只记录<b>容量</b>,流量是算法运行后的产物,不参与持久化。</li>
 * </ul>
 *
 * <pre>
 * FlowNetwork network = GraphIO.readFlowNetworkFile("tinyFN.txt");
 * FordFulkerson maxFlow = new FordFulkerson(network, 0, network.V() - 1);
 * maxFlow.value();            // 最大流值
 * maxFlow.minCut().capacity(); // 同一个数字:最小割容量
 * </pre>
 *
 * @see FlowEdge
 * @see FordFulkerson
 * @see EdmondsKarp
 * @see Dinic
 * @see MinCut
 * @see <a href="https://algs4.cs.princeton.edu/64maxflow">Algorithms, 4th Edition, Section 6.4</a>
 */
public final class FlowNetwork {

    /** 最大顶点数(与其它结构一致的上限,防止误传巨大数导致内存爆炸) */
    private static final int MAX_VERTICES = 1 << 24;

    /** 顶点数 */
    private final int vertexCount;

    /** adjacency[v] = 与 v 关联的边(同一条边在两端各出现一次) */
    private final List<List<FlowEdge>> adjacency;

    /** 按加入顺序的全部边(每条一次) */
    private final List<FlowEdge> allEdges;

    /**
     * 建一个含 {@code V} 个顶点、还没有边的流网络。
     *
     * @param V 顶点数,需在 {@code [0, 1<<24]} 内
     * @throws IllegalArgumentException 顶点数越界
     */
    public FlowNetwork(int V) {
        if (V < 0 || V > MAX_VERTICES) {
            throw new IllegalArgumentException("顶点数 " + V + " 不在 [0, " + MAX_VERTICES + "] 内");
        }
        this.vertexCount = V;
        this.adjacency = new ArrayList<List<FlowEdge>>(V);
        for (int v = 0; v < V; v++) {
            adjacency.add(new ArrayList<FlowEdge>());
        }
        this.allEdges = new ArrayList<FlowEdge>();
    }

    /**
     * @return 顶点数
     */
    public int V() {
        return vertexCount;
    }

    /**
     * @return 边数(平行边各算一条)
     */
    public int E() {
        return allEdges.size();
    }

    /**
     * 加入一条边(容量与流量由 {@link FlowEdge} 校验)。
     *
     * @param edge 待加入的边,不能为 null,两端必须在范围内且不能是自环
     * @throws IllegalArgumentException 参数非法
     */
    public void addEdge(FlowEdge edge) {
        if (edge == null) {
            throw new IllegalArgumentException("边不能为 null");
        }
        validateVertex(edge.from());
        validateVertex(edge.to());
        if (edge.from() == edge.to()) {
            throw new IllegalArgumentException("流网络不允许自环: " + edge);
        }
        adjacency.get(edge.from()).add(edge);
        adjacency.get(edge.to()).add(edge);                 // 同一个对象出现两次:反向可退流
        allEdges.add(edge);
    }

    /**
     * 便捷方法:直接按起点、终点、容量加一条边。
     *
     * @param from     起点
     * @param to       终点
     * @param capacity 容量,非负有限实数
     * @throws IllegalArgumentException 参数非法
     */
    public void addEdge(int from, int to, double capacity) {
        addEdge(new FlowEdge(from, to, capacity));
    }

    /**
     * 与顶点 {@code v} 关联的全部边(含"反向可退流"的边,同一条边在两端各出现一次)。
     *
     * @param v 顶点编号
     * @return 该顶点的关联边(按加入顺序)
     * @throws IllegalArgumentException 顶点越界
     */
    public List<FlowEdge> adj(int v) {
        validateVertex(v);
        return new ArrayList<FlowEdge>(adjacency.get(v));
    }

    /**
     * 内部使用:直接访问邻接表(避免每次查询都复制)。返回的列表<b>不要修改</b>。
     *
     * @param v 顶点编号(调用方保证合法)
     * @return 该顶点的关联边列表本体
     */
    List<FlowEdge> rawAdj(int v) {
        return adjacency.get(v);
    }

    /**
     * 全部边,每条只返回一次(按加入顺序)。
     *
     * @return 边列表(副本)
     */
    public List<FlowEdge> edges() {
        return new ArrayList<FlowEdge>(allEdges);
    }

    /**
     * @param v 顶点编号
     * @return v 的出度:以 v 为起点的边数
     * @throws IllegalArgumentException 顶点越界
     */
    public int outDegree(int v) {
        validateVertex(v);
        int degree = 0;
        for (FlowEdge edge : adjacency.get(v)) {
            if (edge.from() == v) {
                degree++;
            }
        }
        return degree;
    }

    /**
     * @param v 顶点编号
     * @return v 的入度:以 v 为终点的边数
     * @throws IllegalArgumentException 顶点越界
     */
    public int inDegree(int v) {
        validateVertex(v);
        int degree = 0;
        for (FlowEdge edge : adjacency.get(v)) {
            if (edge.to() == v) {
                degree++;
            }
        }
        return degree;
    }

    /**
     * @param v 顶点编号
     * @return v 的出容量之和(只算以 v 为起点的边)
     * @throws IllegalArgumentException 顶点越界
     */
    public double outCapacity(int v) {
        validateVertex(v);
        double sum = 0.0;
        for (FlowEdge edge : adjacency.get(v)) {
            if (edge.from() == v) {
                sum += edge.capacity();
            }
        }
        return sum;
    }

    /**
     * @param v 顶点编号
     * @return v 的入容量之和(只算以 v 为终点的边)
     * @throws IllegalArgumentException 顶点越界
     */
    public double inCapacity(int v) {
        validateVertex(v);
        double sum = 0.0;
        for (FlowEdge edge : adjacency.get(v)) {
            if (edge.to() == v) {
                sum += edge.capacity();
            }
        }
        return sum;
    }

    /**
     * 把当前所有边的流量清零(便于对同一张网络重复跑不同算法做对比)。
     */
    public void clearFlow() {
        for (FlowEdge edge : allEdges) {
            double current = edge.flow();
            if (current != 0.0) {
                edge.addResidualFlowTo(edge.from(), current);   // 沿反向把流量全部退回
            }
        }
    }

    /**
     * @return 当前所有边流量之和(可用于观察"总推入量")
     */
    public double totalFlow() {
        double sum = 0.0;
        for (FlowEdge edge : allEdges) {
            sum += edge.flow();
        }
        return sum;
    }

    /**
     * 深拷贝:边是新建的对象,容量与流量照搬(原网络与副本互不影响)。
     *
     * @return 独立副本
     */
    public FlowNetwork copy() {
        FlowNetwork copy = new FlowNetwork(vertexCount);
        for (FlowEdge edge : allEdges) {
            copy.addEdge(new FlowEdge(edge.from(), edge.to(), edge.capacity(), edge.flow()));
        }
        return copy;
    }

    private void validateVertex(int v) {
        if (v < 0 || v >= vertexCount) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (vertexCount - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code "6 vertices, 8 edges"},随后逐行列出每个顶点的关联边(含反向引用)
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append(vertexCount).append(" vertices, ").append(allEdges.size()).append(" edges")
                .append(newline);
        for (int v = 0; v < vertexCount; v++) {
            sb.append(v).append(": ");
            for (FlowEdge edge : adjacency.get(v)) {
                sb.append(edge).append(' ');
            }
            sb.append(newline);
        }
        return sb.toString();
    }

    /**
     * 演示:读入 tinyFN.txt(6 个顶点、8 条弧),打印结构与最大流/最小割(用最基础的增广路法)。
     *
     * @param args 可选:流网络数据文件路径(缺省 tinyFN.txt)
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyFN.txt";
        FlowNetwork network = GraphIO.readFlowNetworkFile(path);
        System.out.println("数据文件: " + path);
        System.out.print(network);
        System.out.println("源点 0 的总出容量 = " + network.outCapacity(0)
                + ",汇点 " + (network.V() - 1) + " 的总入容量 = "
                + network.inCapacity(network.V() - 1));
    }
}
