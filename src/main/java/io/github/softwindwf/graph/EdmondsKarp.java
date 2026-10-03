package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Edmonds–Karp 算法:每次沿<b>最短</b>(边数最少)的增广路推流,Θ(V·E²)。
 *
 * <p><b>它修掉了 Ford–Fulkerson 的什么毛病</b>:增广路法的复杂度里带着"流的数值"|f|,
 * 而且选路不当会使增广次数爆炸(经典构造里,DFS 版本对实数容量甚至可能不终止)。
 * Edmonds–Karp 只改一件事 —— <b>用 BFS 选最短增广路</b>,于是:</p>
 * <ul>
 *   <li>每条边成为"瓶颈边"的次数是 Θ(V)(每次饱和后要让它的两个端点层次差至少 +2,
 *       而层次差不超过 V);</li>
 *   <li>共 E 条边,所以增广次数 ≤ Θ(V·E),与容量具体多大<b>无关</b> —— 实数容量也一定终止。</li>
 * </ul>
 *
 * <p>当然代价是每次增广要 BFS 一遍:总时间 Θ(V·E²)。这也是它与 {@link Dinic}
 * (层次图 + 阻塞流,Θ(V²·E))的分工:小图够用、实现简单,而 Dinic 是"真正要跑大图"时的选择。</p>
 *
 * <p><b>注意</b>:算法会直接修改传入网络里各条边的流量。想反复实验请先
 * {@link FlowNetwork#copy()} 或 {@link FlowNetwork#clearFlow()}。</p>
 *
 * <pre>
 * EdmondsKarp maxFlow = new EdmondsKarp(GraphIO.readFlowNetworkFile("tinyFN.txt"), 0, 5);
 * maxFlow.value();
 * maxFlow.minCut().capacity();       // 相等
 * </pre>
 *
 * @see FordFulkerson
 * @see Dinic
 * @see MinCut
 * @see <a href="https://algs4.cs.princeton.edu/64maxflow">Algorithms, 4th Edition, Section 6.4</a>
 */
public final class EdmondsKarp {

    /** 残量容量比较容差 */
    private static final double EPS = 1e-9;

    /** 被求解的流网络(流量会被修改) */
    private final FlowNetwork network;

    /** 源点 */
    private final int source;

    /** 汇点 */
    private final int sink;

    /** 最大流值 */
    private final double value;

    /** 增广次数(每次都是一条最短增广路) */
    private final int augmentationCount;

    /** 最长的那条增广路用了多少条边(用于观察"路径长度不会无限增长") */
    private final int longestPathEdges;

    /** 最小割 */
    private final MinCut minCut;

    /**
     * 求从 {@code source} 到 {@code sink} 的最大流(用 BFS 找最短增广路)。
     *
     * @param network 流网络,不能为 null
     * @param source  源点
     * @param sink    汇点
     * @throws IllegalArgumentException 参数为 null、源点/汇点越界或二者相同
     */
    public EdmondsKarp(FlowNetwork network, int source, int sink) {
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

        int V = network.V();
        FlowEdge[] edgeTo = new FlowEdge[V];
        int[] distance = new int[V];
        double flow = 0.0;
        int augmentations = 0;
        int longest = 0;
        while (hasAugmentingPath(edgeTo, distance)) {
            double bottleneck = Double.POSITIVE_INFINITY;
            for (int v = sink; v != source; v = edgeTo[v].other(v)) {
                bottleneck = Math.min(bottleneck, edgeTo[v].residualCapacityTo(v));
            }
            int edgesOnPath = 0;
            for (int v = sink; v != source; v = edgeTo[v].other(v)) {
                edgeTo[v].addResidualFlowTo(v, bottleneck);
                edgesOnPath++;
            }
            longest = Math.max(longest, edgesOnPath);
            flow += bottleneck;
            augmentations++;
        }
        this.value = flow;
        this.augmentationCount = augmentations;
        this.longestPathEdges = longest;
        this.minCut = new MinCut(network, source, sink);
    }

    /**
     * BFS 在残量图上找一条 s→t 的<b>最短</b>(边数最少)路径。
     *
     * @param edgeTo   输出:路径上通向 v 的边
     * @param distance 输出:从 s 到 v 的边数(层次)
     * @return 是否存在增广路
     */
    private boolean hasAugmentingPath(FlowEdge[] edgeTo, int[] distance) {
        Arrays.fill(edgeTo, null);
        Arrays.fill(distance, -1);
        Deque<Integer> queue = new ArrayDeque<Integer>();
        distance[source] = 0;
        queue.add(source);
        while (!queue.isEmpty()) {
            int v = queue.poll();
            for (FlowEdge edge : network.adj(v)) {
                int w = edge.other(v);
                if (distance[w] < 0 && edge.residualCapacityTo(w) > EPS) {
                    edgeTo[w] = edge;
                    distance[w] = distance[v] + 1;
                    queue.add(w);
                }
            }
        }
        return distance[sink] >= 0;
    }

    /**
     * @return 最大流值
     */
    public double value() {
        return value;
    }

    /**
     * @return 增广次数(上界是 Θ(V·E),与容量数值无关)
     */
    public int augmentationCount() {
        return augmentationCount;
    }

    /**
     * @return 增广过程中最长的一条路径用了多少条边(不会超过 V−1)
     */
    public int longestPathEdges() {
        return longestPathEdges;
    }

    /**
     * @return 最小割(容量等于 {@link #value()})
     */
    public MinCut minCut() {
        return minCut;
    }

    /**
     * @param v 顶点编号
     * @return v 是否在最小割的 S 侧
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
     * @return 形如 {@code EdmondsKarp: 最大流 4.00(增广 4 次,最长增广路 3 条边)}
     */
    @Override
    public String toString() {
        return "EdmondsKarp: 最大流 " + String.format("%.2f", value)
                + "(增广 " + augmentationCount + " 次,最长增广路 " + longestPathEdges + " 条边)"
                + System.lineSeparator() + "  " + minCut;
    }

    private static void validateVertex(FlowNetwork network, int v, String what) {
        if (v < 0 || v >= network.V()) {
            throw new IllegalArgumentException(what + " " + v + " 不在 [0, " + (network.V() - 1) + "] 内");
        }
    }

    /**
     * 演示:对 tinyFN.txt 求最大流,并与 Ford–Fulkerson 的增广次数对照。
     *
     * @param args 可选:{@code 文件路径}
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyFN.txt";
        int source = 0;

        FlowNetwork edmondsKarpNetwork = GraphIO.readFlowNetworkFile(path);
        EdmondsKarp edmondsKarp = new EdmondsKarp(edmondsKarpNetwork, source, edmondsKarpNetwork.V() - 1);
        System.out.println("数据文件: " + path);
        System.out.println(edmondsKarp);

        FlowNetwork fordFulkersonNetwork = GraphIO.readFlowNetworkFile(path);
        FordFulkerson fordFulkerson =
                new FordFulkerson(fordFulkersonNetwork, source, fordFulkersonNetwork.V() - 1);
        System.out.println("对照 Ford–Fulkerson:最大流 " + fordFulkerson.value()
                + ",增广 " + fordFulkerson.augmentationCount() + " 次");
    }
}
