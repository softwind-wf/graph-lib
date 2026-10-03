package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Kruskal 算法:按边权从小到大扫描,能加就加,用并查集判环。
 *
 * <p><b>算法一句话</b>:把所有边按权值升序排好,依次考察每一条 ——
 * 若它的两个端点当前还不在同一个连通分量里,就把它加进生成树并合并这两个分量;
 * 否则说明它会成环,丢弃。直到没有任何边可加。</p>
 *
 * <p><b>为什么贪心是对的</b>:设当前考察的边 e 是两个不同分量之间的最小边。
 * 把这两个分量分别当作割的两侧,e 就是横跨该割的最小边,由<b>割性质</b>(cut property)
 * e 必定属于某棵最小生成树;而且 e 的两端此时不连通,说明它不会与已选边成环,
 * 所以"能加就加"永远安全。这与 Prim 用的是同一条性质,只是一个按<b>边</b>推进、
 * 一个按<b>顶点集合</b>推进。</p>
 *
 * <p><b>两种实现</b>:</p>
 * <table border="1" summary="Kruskal 两种实现的对比">
 *   <tr><th>模式</th><th>做法</th><th>时间</th><th>空间</th><th>特点</th></tr>
 *   <tr><td>{@link Mode#SORT}</td>
 *       <td>把全部边收进数组,一次性排序,然后顺序扫描</td>
 *       <td>Θ(E log E)</td><td>Θ(E)</td><td>常数小,需要能一次性拿到全部边(默认)</td></tr>
 *   <tr><td>{@link Mode#HEAP}</td>
 *       <td>把边放进最小堆,逐条出堆处理</td>
 *       <td>Θ(E log E)</td><td>Θ(E)</td><td>边可以是流式给出的,无需先存成数组</td></tr>
 * </table>
 *
 * <p>两者的渐进代价相同(排序/建堆都主导),差别在常数与"什么时候拿到边";
 * 权值互不相同时两者选出的生成树逐条相同,出现等权边时只保证总权值相同。</p>
 *
 * <p><b>提前结束</b>:并查集的集合个数降到 1 就说明所有顶点已经连通,剩下的边不必再看。
 * {@link #edgesExamined()} 给出本次实际检查过的边数,连通图上通常明显小于 E
 * (教材样例 tinyEWG:只看 13 条即可停在 16 条之前)。不连通的图退化为
 * <b>最小生成森林</b>,此时集合个数永远降不到 1,必须把边看完。</p>
 *
 * <p><b>Kruskal 与 Prim 怎么选</b>:</p>
 * <ul>
 *   <li>Kruskal:Θ(E log E),与图是否稠密无关,<b>稀疏图</b>(E 远小于 V²)首选,
 *       而且天然支持"边是现成的列表/文件"这种输入形态;</li>
 *   <li>Prim(稠密版):Θ(V²),<b>稠密图</b>上比 Kruskal 的 E log E 更快,
 *       且不需要一次拿到全部边(邻接表即可);</li>
 *   <li>两者都得同一棵最小生成树(权值必然相同)。</li>
 * </ul>
 *
 * <p><b>边界</b>:自环不会入选(端点相同,并查集判为已连通);平行边只可能取最轻的那条;
 * 权值允许为负或 0。</p>
 *
 * <pre>
 * EdgeWeightedGraph g = GraphIO.readWeightedFile("tinyEWG.txt");
 * KruskalMST mst = new KruskalMST(g);
 * mst.weight();          // 1.81
 * mst.edges();           // 7 条边,按权值升序
 * mst.edgesExamined();   // 13(提前结束,没有看完全部 16 条)
 * </pre>
 *
 * @see Edge
 * @see EdgeWeightedGraph
 * @see UF
 * @see PrimMST
 * @see <a href="https://algs4.cs.princeton.edu/43mst">Algorithms, 4th Edition, Section 4.3</a>
 */
public final class KruskalMST {

    /** 扫描边的方式 */
    public enum Mode {

        /** 一次性排序后顺序扫描:常数小,需要全部边在手(默认) */
        SORT,

        /** 最小堆逐条出边:适合边以流的形式陆续到达 */
        HEAP
    }

    /** 被求解的图 */
    private final EdgeWeightedGraph graph;

    /** 所用实现方式 */
    private final Mode mode;

    /** 最小生成森林的边,按权值升序 */
    private final List<Edge> mstEdges;

    /** 最小生成森林的总权值 */
    private final double totalWeight;

    /** 连通分量数 = 森林中树的棵数 */
    private final int componentCount;

    /** 实际检查(排序扫描或出堆)过的边数,≤ E */
    private final int examined;

    /**
     * 用排序扫描方式求最小生成树(森林)。
     *
     * @param graph 待求解的无向带权图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public KruskalMST(EdgeWeightedGraph graph) {
        this(graph, Mode.SORT);
    }

    /**
     * 求最小生成树(森林)。
     *
     * @param graph 待求解的无向带权图,不能为 null
     * @param mode  实现方式,不能为 null
     * @throws IllegalArgumentException 参数为 null
     */
    public KruskalMST(EdgeWeightedGraph graph, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("实现方式不能为 null");
        }
        this.graph = graph;
        this.mode = mode;

        List<Edge> accepted = new ArrayList<Edge>();
        UF uf = new UF(graph.V());
        int scanned = (mode == Mode.SORT) ? runSorted(graph, uf, accepted) : runHeap(graph, uf, accepted);

        this.mstEdges = Collections.unmodifiableList(accepted);
        this.componentCount = uf.count();
        this.examined = scanned;

        double sum = 0.0;
        for (Edge e : accepted) {
            sum += e.weight();
        }
        this.totalWeight = sum;
    }

    // ------------------------------------------------------------------
    // 两种实现
    // ------------------------------------------------------------------

    /**
     * 排序扫描版:全部边入数组 → 排序 → 依次尝试加入。
     *
     * @return 实际检查过的边数
     */
    private int runSorted(EdgeWeightedGraph g, UF uf, List<Edge> accepted) {
        List<Edge> all = new ArrayList<Edge>();
        for (Edge e : g.edges()) {
            all.add(e);
        }
        Collections.sort(all);

        int scanned = 0;
        for (Edge e : all) {
            scanned++;
            if (accept(e, uf, accepted)) {
                break;                      // 已连通,后面更重的边不必再看
            }
        }
        return scanned;
    }

    /**
     * 最小堆版:边直接进堆,逐条出堆处理(适配"边陆续到达"的场景)。
     *
     * @return 实际检查过的边数
     */
    private int runHeap(EdgeWeightedGraph g, UF uf, List<Edge> accepted) {
        PriorityQueue<Edge> pq = new PriorityQueue<Edge>();
        for (Edge e : g.edges()) {
            pq.add(e);
        }

        int scanned = 0;
        while (!pq.isEmpty()) {
            scanned++;
            if (accept(pq.poll(), uf, accepted)) {
                break;
            }
        }
        return scanned;
    }

    /**
     * 尝试把一条边加入生成森林:两端已连通则丢弃(会成环),否则接受并合并。
     *
     * @return 本次加入后整图是否已经连通(是则外层可以提前结束)
     */
    private boolean accept(Edge e, UF uf, List<Edge> accepted) {
        int v = e.either();
        int w = e.other(v);
        if (uf.union(v, w)) {
            accepted.add(e);
        }
        return uf.count() == 1;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * @return 最小生成森林的边(按权值升序);图连通时就是最小生成树的 V−1 条边
     */
    public List<Edge> edges() {
        return mstEdges;
    }

    /**
     * @return 最小生成森林的总权值;图连通时即最小生成树的权值
     */
    public double weight() {
        return totalWeight;
    }

    /**
     * @return 连通分量数,等于森林中树的棵数;1 表示整图连通
     */
    public int componentCount() {
        return componentCount;
    }

    /**
     * @return 整图是否连通(结果是真正的一棵最小生成树)
     */
    public boolean isConnected() {
        return componentCount == 1;
    }

    /**
     * @return 本次运行实际检查过的边数(≤ E);连通图上会因"并查集集合数降到 1"而提前结束
     */
    public int edgesExamined() {
        return examined;
    }

    /**
     * @return 顶点数
     */
    public int V() {
        return graph.V();
    }

    /**
     * @return 本实例所用的实现方式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @return 形如 {@code KruskalMST(SORT): 7 条边,总权值 1.81,连通分量 1,检查 13/16 条边}
     */
    @Override
    public String toString() {
        return "KruskalMST(" + mode + "): " + mstEdges.size() + " 条边,总权值 " + totalWeight
                + ",连通分量 " + componentCount + ",检查 " + examined + "/" + graph.E() + " 条边";
    }

    // ------------------------------------------------------------------
    // 演示
    // ------------------------------------------------------------------

    /**
     * 演示:读入加权图(缺省用 tinyEWG.txt 的内容),分别用两种实现求最小生成树,
     * 打印边、总权值、检查过的边数,并与 {@link PrimMST} 的结果对照。
     *
     * @param args 可选:algs4 加权图数据文件路径
     */
    public static void main(String[] args) {
        EdgeWeightedGraph graph = args.length > 0 ? GraphIO.readWeightedFile(args[0]) : sampleGraph();
        System.out.println("V = " + graph.V() + ", E = " + graph.E());

        for (Mode mode : Mode.values()) {
            KruskalMST mst = new KruskalMST(graph, mode);
            System.out.println(mst);
            for (Edge e : mst.edges()) {
                System.out.println("  " + e);
            }
        }

        PrimMST prim = new PrimMST(graph);
        KruskalMST kruskal = new KruskalMST(graph);
        System.out.println("对照:Prim(LAZY) 总权值 = " + prim.weight()
                + ",Kruskal(SORT) 总权值 = " + kruskal.weight()
                + ",边集相同 = " + sameEdges(prim.edges(), kruskal.edges()));
    }

    /** 两个边集是否逐条相同(按端点归一后的字符串比较) */
    private static boolean sameEdges(List<Edge> a, List<Edge> b) {
        if (a.size() != b.size()) {
            return false;
        }
        List<String> ka = new ArrayList<String>();
        List<String> kb = new ArrayList<String>();
        for (Edge e : a) {
            ka.add(key(e));
        }
        for (Edge e : b) {
            kb.add(key(e));
        }
        Collections.sort(ka);
        Collections.sort(kb);
        return ka.equals(kb);
    }

    private static String key(Edge e) {
        int v = e.either();
        int w = e.other(v);
        return Math.min(v, w) + "-" + Math.max(v, w) + " " + e.weight();
    }

    /** 内置演示图:与工作区 tinyEWG.txt 相同的 8 顶点 / 16 边加权图(教材样例,MST 权值 1.81) */
    private static EdgeWeightedGraph sampleGraph() {
        EdgeWeightedGraph graph = new EdgeWeightedGraph(8);
        int[][] vw = {
            {4, 5}, {4, 7}, {5, 7}, {0, 7}, {1, 5}, {0, 4}, {2, 3}, {1, 7},
            {0, 2}, {1, 2}, {1, 3}, {2, 7}, {6, 2}, {3, 6}, {6, 0}, {6, 4}
        };
        double[] weights = {
            0.35, 0.37, 0.28, 0.16, 0.32, 0.38, 0.17, 0.19,
            0.26, 0.36, 0.29, 0.34, 0.40, 0.52, 0.58, 0.93
        };
        for (int i = 0; i < vw.length; i++) {
            graph.addEdge(vw[i][0], vw[i][1], weights[i]);
        }
        return graph;
    }
}
