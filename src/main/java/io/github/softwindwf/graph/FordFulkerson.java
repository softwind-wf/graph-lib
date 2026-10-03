package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Ford–Fulkerson 算法(增广路法):求流网络的最大流,并给出最小割。最基础的版本。
 *
 * <p><b>核心思想(增广路的"反悔"机制)</b>:只要残量图上还存在一条从 s 到 t 的路径
 * (每条边的剩余容量都 &gt; 0),就沿这条路推入"瓶颈容量"那么多的流量。
 * 关键是<b>反向边</b>的存在:一条已推流量的边,反方向看还有"可以退回去"的容量,
 * 于是算法能够"反悔"先前不划算的选择 —— 这正是它能找到最优解的原因。</p>
 *
 * <p><b>终止与最优性</b>:当残量图上再也没有 s→t 路径时,令 S = 残量图上从 s 可达的顶点集合,
 * 则:① 所有 S→T 的边都<b>饱和</b>(否则 T 侧会有顶点可达);② 流值 = 这些边的容量和。
 * 于是"任何割 ≥ 任意流"与"这个割 = 这个流"合起来就证明了
 * <b>最大流 = 最小割</b>(见 {@link MinCut})。</p>
 *
 * <p><b>复杂度与选择</b>:每条增广路至少推 1 个单位(整数容量时),
 * 所以最多跑 |f| 次,Θ(E·|f|)。缺点是"选路的运气"很重要 ——
 * 对某些图 DFS 会反复走同一条冤枉路({@link EdmondsKarp} 用 BFS 修掉了这个问题)。
 * 与其它算法对比:</p>
 * <table border="1" summary="三种最大流算法">
 *   <tr><th>类</th><th>选路方式</th><th>增广次数上界</th><th>时间</th></tr>
 *   <tr><td><b>本类</b></td><td>DFS(任意路径)</td><td>|f|(整数容量)</td><td>Θ(E·|f|)</td></tr>
 *   <tr><td>{@link EdmondsKarp}</td><td>BFS(最短路径)</td><td>Θ(V·E)</td><td>Θ(V·E²)</td></tr>
 *   <tr><td>{@link Dinic}</td><td>层次图 + 阻塞流</td><td>Θ(V·E)</td><td>Θ(V²·E)</td></tr>
 * </table>
 *
 * <p><b>注意</b>:算法会直接修改传入网络里各条边的流量(这正是"计算最大流"的产物)。
 * 想对同一张网络反复实验,请先 {@link FlowNetwork#copy()} 或 {@link FlowNetwork#clearFlow()}。</p>
 *
 * <pre>
 * FlowNetwork network = GraphIO.readFlowNetworkFile("tinyFN.txt");
 * FordFulkerson maxFlow = new FordFulkerson(network, 0, network.V() - 1);
 * maxFlow.value();                 // 最大流值
 * maxFlow.augmentationCount();     // 增广了几次
 * maxFlow.minCut().capacity();     // 与 value() 相等
 * </pre>
 *
 * @see EdmondsKarp
 * @see Dinic
 * @see MinCut
 * @see <a href="https://algs4.cs.princeton.edu/64maxflow">Algorithms, 4th Edition, Section 6.4</a>
 */
public final class FordFulkerson {

    /** 残量容量比较容差 */
    private static final double EPS = 1e-9;

    /**
     * 增广次数上限。实数容量下增广路法理论上可能不终止(存在经典的反例构造),
     * 这里设一个护栏,超过就明确报错而不是让程序转圈。
     */
    private static final int MAX_AUGMENTATIONS = 1_000_000;

    /** 被求解的流网络(流量会被修改) */
    private final FlowNetwork network;

    /** 源点 */
    private final int source;

    /** 汇点 */
    private final int sink;

    /** 最大流值 */
    private final double value;

    /** 增广次数 */
    private final int augmentationCount;

    /** 最小割(由残量图直接得出) */
    private final MinCut minCut;

    /**
     * 求从 {@code source} 到 {@code sink} 的最大流(用 DFS 找增广路)。
     *
     * @param network 流网络,不能为 null
     * @param source  源点
     * @param sink    汇点
     * @throws IllegalArgumentException 参数为 null、源点/汇点越界或二者相同
     * @throws IllegalStateException    增广次数超过护栏(实数容量下的病态输入)
     */
    public FordFulkerson(FlowNetwork network, int source, int sink) {
        if (network == null) {
            throw new IllegalArgumentException("流网络不能为 null");
        }
        validateVertex(network, source, "源点");
        validateVertex(network, sink, "汇点");
        if (source == sink) {
            throw new IllegalArgumentException("源点与汇点不能相同");
        }
        this.network = network;
        this.source = source;
        this.sink = sink;

        FlowEdge[] edgeTo = new FlowEdge[network.V()];
        double flow = 0.0;
        int augmentations = 0;
        while (hasAugmentingPath(edgeTo)) {
            if (++augmentations > MAX_AUGMENTATIONS) {
                throw new IllegalStateException("增广次数超过 " + MAX_AUGMENTATIONS
                        + " 次仍未收敛:实数容量下增广路法可能不终止,请改用整数容量或 EdmondsKarp/Dinic");
            }
            double bottleneck = Double.POSITIVE_INFINITY;
            for (int v = sink; v != source; v = edgeTo[v].other(v)) {
                bottleneck = Math.min(bottleneck, edgeTo[v].residualCapacityTo(v));
            }
            for (int v = sink; v != source; v = edgeTo[v].other(v)) {
                edgeTo[v].addResidualFlowTo(v, bottleneck);
            }
            flow += bottleneck;
        }
        this.value = flow;
        this.augmentationCount = augmentations;
        this.minCut = new MinCut(network, source, sink);
    }

    /**
     * 在残量图上用<b>迭代式 DFS</b>找一条 s→t 路径;找到就填好 {@code edgeTo} 并返回 true。
     *
     * @param edgeTo 输出参数:{@code edgeTo[v]} = 路径上通向 v 的那条边
     * @return 是否存在增广路
     */
    private boolean hasAugmentingPath(FlowEdge[] edgeTo) {
        int V = network.V();
        Arrays.fill(edgeTo, null);
        boolean[] marked = new boolean[V];
        Deque<Integer> stack = new ArrayDeque<Integer>();
        marked[source] = true;
        stack.push(source);
        while (!stack.isEmpty()) {
            int v = stack.pop();
            for (FlowEdge edge : network.adj(v)) {
                int w = edge.other(v);
                if (!marked[w] && edge.residualCapacityTo(w) > EPS) {
                    edgeTo[w] = edge;
                    marked[w] = true;
                    stack.push(w);
                }
            }
        }
        return marked[sink];
    }

    /**
     * @return 最大流值
     */
    public double value() {
        return value;
    }

    /**
     * @return 增广路被使用的次数(每次至少推入一个正量,整数容量时可直接与最大流值对比)
     */
    public int augmentationCount() {
        return augmentationCount;
    }

    /**
     * @return 最小割(容量等于 {@link #value()})
     */
    public MinCut minCut() {
        return minCut;
    }

    /**
     * @param v 顶点编号
     * @return v 是否在最小割的 S 侧(等价于"残量图上源点可达 v")
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean inSourceSide(int v) {
        return minCut.inSourceSide(v);
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
     * @return 形如 {@code FordFulkerson: 最大流 4.00(增广 3 次)},随后是 S/T 两侧与组成割的边
     */
    @Override
    public String toString() {
        return "FordFulkerson: 最大流 " + String.format("%.2f", value)
                + "(增广 " + augmentationCount + " 次)" + System.lineSeparator() + "  " + minCut;
    }

    private static void validateVertex(FlowNetwork network, int v, String what) {
        if (v < 0 || v >= network.V()) {
            throw new IllegalArgumentException(what + " " + v + " 不在 [0, " + (network.V() - 1) + "] 内");
        }
    }

    /**
     * 演示:对 tinyFN.txt 求最大流;命令行给出文件路径时改读文件。
     *
     * @param args 可选:{@code 文件路径}
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyFN.txt";
        FlowNetwork network = GraphIO.readFlowNetworkFile(path);
        int source = 0;
        int sink = network.V() - 1;
        FordFulkerson maxFlow = new FordFulkerson(network, source, sink);
        System.out.println("数据文件: " + path + "(V=" + network.V() + ", E=" + network.E() + ")");
        System.out.println(maxFlow);
        System.out.println("每条边上的流量:");
        for (FlowEdge edge : network.edges()) {
            System.out.println("  " + edge);
        }
    }
}
