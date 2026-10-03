package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 最小割(Minimum Cut):把顶点分成 {@code S}(含源点)与 {@code T}(含汇点)两部分,
 * 让"从 S 到 T 的边的容量之和"最小。
 *
 * <p><b>最大流最小割定理</b>:最大流值 = 最小割容量。这不是巧合,而是同一件事的两面:</p>
 * <ul>
 *   <li>任何割都把源点的流"全部拦下",所以<b>任何</b>割容量 ≥ 最大流值(上界);</li>
 *   <li>最大流跑完之后,在<b>残量图</b>上从源点做一次可达性搜索得到的 S</li>
 * </ul>
 * <p>—— 这个 S 的割容量恰好<b>等于</b>流量,于是上下界重合。本类做的就是后一步:
 * 在残量图(即"剩余容量 &gt; 0")上从源点搜一遍,得到 S,再统计跨越 S→T 的原边。</p>
 *
 * <pre>
 * FordFulkerson maxFlow = new FordFulkerson(network, 0, 5);
 * MinCut cut = maxFlow.minCut();
 * cut.capacity() == maxFlow.value();   // 定理的两个方向都成立
 * cut.edges();                         // 组成这个割的边
 * </pre>
 *
 * @see FordFulkerson
 * @see EdmondsKarp
 * @see Dinic
 * @see FlowNetwork
 * @see <a href="https://algs4.cs.princeton.edu/64maxflow">Algorithms, 4th Edition, Section 6.4</a>
 */
public final class MinCut {

    /** 残量容量的比较容差 */
    private static final double EPS = 1e-9;

    /** 所在的流网络 */
    private final FlowNetwork network;

    /** 源点 */
    private final int source;

    /** 汇点 */
    private final int sink;

    /** inSourceSide[v] = v 是否落在 S 侧 */
    private final boolean[] inSourceSide;

    /** S→T 的边(按起点、终点排序) */
    private final List<FlowEdge> cutEdges;

    /**
     * 在当前流量分布下求最小割:残量图上从 {@code source} 可达的顶点构成 S。
     *
     * @param network 流网络,不能为 null
     * @param source  源点
     * @param sink    汇点
     * @throws IllegalArgumentException 参数为 null、源点/汇点越界或二者相同
     * @throws IllegalStateException    残量图上源点仍能到达汇点(说明当前流还不是最大流,割无意义)
     */
    public MinCut(FlowNetwork network, int source, int sink) {
        if (network == null) {
            throw new IllegalArgumentException("流网络不能为 null");
        }
        validateVertex(network, source);
        validateVertex(network, sink);
        if (source == sink) {
            throw new IllegalArgumentException("源点与汇点不能相同");
        }
        this.network = network;
        this.source = source;
        this.sink = sink;

        int V = network.V();
        this.inSourceSide = new boolean[V];
        Deque<Integer> stack = new ArrayDeque<Integer>();
        inSourceSide[source] = true;
        stack.push(source);
        while (!stack.isEmpty()) {
            int v = stack.pop();
            for (FlowEdge edge : network.adj(v)) {
                int w = edge.other(v);
                if (!inSourceSide[w] && edge.residualCapacityTo(w) > EPS) {
                    inSourceSide[w] = true;
                    stack.push(w);
                }
            }
        }
        if (inSourceSide[sink]) {
            throw new IllegalStateException("残量图上源点 " + source + " 仍能到达汇点 " + sink
                    + ",当前流还不是最大流,最小割尚未成形");
        }

        List<FlowEdge> crossing = new ArrayList<FlowEdge>();
        for (FlowEdge edge : network.edges()) {
            if (inSourceSide[edge.from()] && !inSourceSide[edge.to()]) {
                crossing.add(edge);
            }
        }
        Collections.sort(crossing, new java.util.Comparator<FlowEdge>() {
            public int compare(FlowEdge a, FlowEdge b) {
                int byFrom = Integer.compare(a.from(), b.from());
                return byFrom != 0 ? byFrom : Integer.compare(a.to(), b.to());
            }
        });
        this.cutEdges = Collections.unmodifiableList(crossing);
    }

    /**
     * @return 源点编号
     */
    public int source() {
        return source;
    }

    /**
     * @return 汇点编号
     */
    public int sink() {
        return sink;
    }

    /**
     * @param v 顶点编号
     * @return v 是否在 S 侧(源点一侧)
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean inSourceSide(int v) {
        validateVertex(network, v);
        return inSourceSide[v];
    }

    /**
     * @param v 顶点编号
     * @return v 的割归属:{@code "S"} 或 {@code "T"}
     * @throws IllegalArgumentException 顶点越界
     */
    public String sideOf(int v) {
        return inSourceSide(v) ? "S" : "T";
    }

    /**
     * @return S 侧顶点编号(升序)
     */
    public List<Integer> sourceSide() {
        return side(true);
    }

    /**
     * @return T 侧顶点编号(升序)
     */
    public List<Integer> sinkSide() {
        return side(false);
    }

    private List<Integer> side(boolean sourceSideWanted) {
        List<Integer> vertices = new ArrayList<Integer>();
        for (int v = 0; v < network.V(); v++) {
            if (inSourceSide[v] == sourceSideWanted) {
                vertices.add(v);
            }
        }
        return vertices;
    }

    /**
     * @return 跨越 S→T 的边(组成这个割的边)
     */
    public List<FlowEdge> edges() {
        return cutEdges;
    }

    /**
     * @return 割容量 = 跨越 S→T 的边容量之和(最大流跑完后它等于最大流值)
     */
    public double capacity() {
        double sum = 0.0;
        for (FlowEdge edge : cutEdges) {
            sum += edge.capacity();
        }
        return sum;
    }

    /**
     * @return 跨越 S→T 的边上的流量之和(这些边必定全部饱和,所以等于割容量)
     */
    public double flowAcross() {
        double sum = 0.0;
        for (FlowEdge edge : cutEdges) {
            sum += edge.flow();
        }
        return sum;
    }

    /**
     * @return 顶点数
     */
    public int V() {
        return network.V();
    }

    /**
     * @return 形如 {@code MinCut: S=[0, 1, 2], T=[3, 4, 5], 容量 4.00, 组成边 [0->3 2.00/2.00, ...]}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MinCut: S=").append(sourceSide()).append(", T=").append(sinkSide());
        sb.append(String.format(",容量 %.2f", capacity()));
        sb.append(",组成边 ").append(cutEdges);
        return sb.toString();
    }

    private static void validateVertex(FlowNetwork network, int v) {
        if (v < 0 || v >= network.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (network.V() - 1) + "] 内");
        }
    }

    /**
     * 演示:对 tinyFN.txt 求最大流,再把最小割的两侧与组成边打印出来,
     * 顺便验证"割容量 = 最大流值"这条定理。
     *
     * @param args 可选:流网络数据文件路径(缺省 tinyFN.txt)
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyFN.txt";
        FlowNetwork network = GraphIO.readFlowNetworkFile(path);
        int source = 0;
        int sink = network.V() - 1;
        FordFulkerson maxFlow = new FordFulkerson(network, source, sink);
        MinCut cut = maxFlow.minCut();
        System.out.println("数据文件: " + path);
        System.out.println("最大流 = " + maxFlow.value());
        System.out.println(cut);
        System.out.println("割容量 = " + cut.capacity() + ",横跨割的边上的流量 = " + cut.flowAcross()
                + "(两者相等 ⟹ 这些边全部饱和)");
    }
}
