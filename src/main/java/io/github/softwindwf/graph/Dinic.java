package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Dinic 算法(层次图 + 阻塞流):Θ(V²·E),实践中最快的通用最大流算法。
 *
 * <p><b>两步走得比 Edmonds–Karp 更聪明</b>:</p>
 * <ol>
 *   <li><b>层次图(level graph)</b>:在残量图上从 s 做 BFS,给每个顶点标上"离 s 的最短边数"
 *       {@code level};只允许沿 {@code level+1} 的边前进。这样一次 BFS 就把"所有等长的最短路"
 *       组织成了一张分层的有向无环图。</li>
 *   <li><b>阻塞流(blocking flow)</b>:在层次图里一次把"再也推不动"为止 —— 反复找 s→t 路径并推流量,
 *       直到层次图里不存在 s→t 路径。配合<b>当前弧优化</b>(每个顶点记住"上一条试到哪儿了",
 *       同一轮里不回退),整个阻塞流的代价是 Θ(V·E)。</li>
 * </ol>
 * <p>关键在于:每完成一次阻塞流,s 到 t 的最短距离<b>严格变大</b>,而它最多增大到 V,
 * 所以层次图阶段数 ≤ V,总时间 Θ(V²·E) —— 比 Edmonds–Karp 的 Θ(V·E²) 更好
 * (E 很大时差别明显)。</p>
 *
 * <p><b>实现说明</b>:层次图 BFS 与阻塞流的路径搜索都用<b>显式栈/队列</b>,不做递归 ——
 * 深层次图不会栈溢出。</p>
 *
 * <p><b>注意</b>:算法会直接修改传入网络里各条边的流量。想反复实验请先
 * {@link FlowNetwork#copy()} 或 {@link FlowNetwork#clearFlow()}。</p>
 *
 * <pre>
 * Dinic maxFlow = new Dinic(GraphIO.readFlowNetworkFile("tinyFN.txt"), 0, 5);
 * maxFlow.value();
 * maxFlow.phaseCount();          // 建了几轮层次图
 * maxFlow.augmentationCount();   // 一共找了多少条增广路
 * </pre>
 *
 * @see FordFulkerson
 * @see EdmondsKarp
 * @see MinCut
 * @see <a href="https://algs4.cs.princeton.edu/64maxflow">Algorithms, 4th Edition, Section 6.4</a>
 */
public final class Dinic {

    /** 残量容量比较容差 */
    private static final double EPS = 1e-9;

    /** 被求解的流网络(流量会被修改) */
    private final FlowNetwork network;

    /** 源点 */
    private final int source;

    /** 汇点 */
    private final int sink;

    /** 本算法自己的邻接索引(元素与网络里的边是同一批对象,所以流量共享) */
    private final List<List<FlowEdge>> adjacency;

    /** level[v] = 层次图里 v 到源点的最短边数;不进层次图时为 -1 */
    private final int[] level;

    /** iter[v] = 当前弧优化:下一轮该从第几条出边开始试 */
    private final int[] currentArc;

    /** 最大流值 */
    private final double value;

    /** 层次图阶段数(≤ V) */
    private final int phaseCount;

    /** 增广路条数 */
    private final int augmentationCount;

    /** 最小割 */
    private final MinCut minCut;

    /**
     * 求从 {@code source} 到 {@code sink} 的最大流。
     *
     * @param network 流网络,不能为 null
     * @param source  源点
     * @param sink    汇点
     * @throws IllegalArgumentException 参数为 null、源点/汇点越界或二者相同
     */
    public Dinic(FlowNetwork network, int source, int sink) {
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
        this.adjacency = new ArrayList<List<FlowEdge>>(V);
        for (int v = 0; v < V; v++) {
            adjacency.add(new ArrayList<FlowEdge>(network.adj(v)));
        }
        this.level = new int[V];
        this.currentArc = new int[V];

        double flow = 0.0;
        int phases = 0;
        int augmentations = 0;
        while (buildLevelGraph()) {
            phases++;
            Arrays.fill(currentArc, 0);
            double pushed;
            while ((pushed = pushBlockingFlow()) > EPS) {
                flow += pushed;
                augmentations++;
            }
        }
        this.value = flow;
        this.phaseCount = phases;
        this.augmentationCount = augmentations;
        this.minCut = new MinCut(network, source, sink);
    }

    /**
     * 在残量图上 BFS 建层次图。
     *
     * @return 汇点是否可达(不可达说明已经是最大流)
     */
    private boolean buildLevelGraph() {
        Arrays.fill(level, -1);
        level[source] = 0;
        Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.add(source);
        while (!queue.isEmpty()) {
            int v = queue.poll();
            for (FlowEdge edge : adjacency.get(v)) {
                int w = edge.other(v);
                if (level[w] < 0 && edge.residualCapacityTo(w) > EPS) {
                    level[w] = level[v] + 1;
                    queue.add(w);
                }
            }
        }
        return level[sink] >= 0;
    }

    /**
     * 在层次图里求一条 s→t 路径并推入瓶颈容量(阻塞流的一次"推")。
     *
     * <p>用显式栈保存当前路径;若某个顶点再也走不到汇点,就把它的层次置为 -1
     * (相当于剪掉),这样同一个顶点在一轮阻塞流里不会被反复扫描。
     * 外层调用者反复调用本方法,靠<b>当前弧优化</b>({@code currentArc} 在整轮阻塞流里不回退)
     * 保证一整轮的总代价是 Θ(V·E)。</p>
     *
     * @return 本次推入的流量;找不到路径返回 0(该轮的阻塞流已经推完)
     */
    private double pushBlockingFlow() {
        Deque<Arc> path = new ArrayDeque<Arc>();
        int v = source;
        while (true) {
            if (v == sink) {
                double bottleneck = Double.POSITIVE_INFINITY;
                for (Arc arc : path) {
                    bottleneck = Math.min(bottleneck, arc.edge.residualCapacityTo(arc.next));
                }
                for (Arc arc : path) {
                    arc.edge.addResidualFlowTo(arc.next, bottleneck);
                }
                return bottleneck;
            }

            boolean advanced = false;
            List<FlowEdge> edges = adjacency.get(v);
            while (currentArc[v] < edges.size()) {
                FlowEdge edge = edges.get(currentArc[v]);
                int w = edge.other(v);
                if (level[w] == level[v] + 1 && edge.residualCapacityTo(w) > EPS) {
                    path.push(new Arc(edge, v, w));
                    v = w;
                    advanced = true;
                    break;
                }
                currentArc[v]++;
            }
            if (!advanced) {
                level[v] = -1;                             // 从 v 出发再也到不了汇点,剪掉
                if (path.isEmpty()) {
                    return 0.0;
                }
                Arc arc = path.pop();
                v = arc.from;
            }
        }
    }

    /** 层次图上的一条弧:边 + 起点 + 下一个顶点 */
    private static final class Arc {
        final FlowEdge edge;
        final int from;
        final int next;

        Arc(FlowEdge edge, int from, int next) {
            this.edge = edge;
            this.from = from;
            this.next = next;
        }
    }

    /**
     * @return 最大流值
     */
    public double value() {
        return value;
    }

    /**
     * @return 层次图阶段数(每阶段 s→t 的最短距离严格变大,所以不超过 V)
     */
    public int phaseCount() {
        return phaseCount;
    }

    /**
     * @return 增广路条数
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
     * @return 形如 {@code Dinic: 最大流 4.00(3 轮层次图,4 条增广路)}
     */
    @Override
    public String toString() {
        return "Dinic: 最大流 " + String.format("%.2f", value)
                + "(" + phaseCount + " 轮层次图," + augmentationCount + " 条增广路)"
                + System.lineSeparator() + "  " + minCut;
    }

    private static void validateVertex(FlowNetwork network, int v, String what) {
        if (v < 0 || v >= network.V()) {
            throw new IllegalArgumentException(what + " " + v + " 不在 [0, " + (network.V() - 1) + "] 内");
        }
    }

    /**
     * 演示:对 tinyFN.txt 跑三种最大流算法,对照流值、最小割与各自的"工作量"。
     *
     * @param args 可选:{@code 文件路径}
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyFN.txt";
        int source = 0;
        System.out.println("数据文件: " + path);

        FlowNetwork network = GraphIO.readFlowNetworkFile(path);
        int sink = network.V() - 1;
        System.out.println(new Dinic(network, source, sink));

        FlowNetwork edmondsKarpNetwork = GraphIO.readFlowNetworkFile(path);
        EdmondsKarp edmondsKarp = new EdmondsKarp(edmondsKarpNetwork, source, sink);
        System.out.println("Edmonds–Karp:最大流 " + edmondsKarp.value()
                + ",增广 " + edmondsKarp.augmentationCount() + " 次");

        FlowNetwork fordFulkersonNetwork = GraphIO.readFlowNetworkFile(path);
        FordFulkerson fordFulkerson = new FordFulkerson(fordFulkersonNetwork, source, sink);
        System.out.println("Ford–Fulkerson:最大流 " + fordFulkerson.value()
                + ",增广 " + fordFulkerson.augmentationCount() + " 次");

        FlowNetwork finalNetwork = GraphIO.readFlowNetworkFile(path);
        System.out.println("三个数字一致 ⟹ 最小割容量 = "
                + new Dinic(finalNetwork, source, sink).minCut().capacity());
    }
}
