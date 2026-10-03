package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * 图的广度优先遍历(BFS,Breadth-First Search / Traversal)。
 *
 * <p><b>算法</b>:从起点 {@code s} 出发,先访问所有与 s 直接相邻的顶点,再访问"离 s 两步"的顶点,
 * 依此类推,像水波一样一圈一圈扩散。实现上是"队列 + 入队即标记":
 * 取出队首顶点,把它的未访问邻接点全部标记、记录来源、入队。</p>
 *
 * <p>每个顶点出队一次、每条邻接表记录被扫一次,所以时间 <b>Θ(V + E)</b>,额外空间 <b>Θ(V)</b>。
 * 与 DFS 的差别不在复杂度,而在<b>顺序</b>:BFS 按距离非递减地访问,
 * 因此它顺带解决了无权图的<b>最短路径</b>问题;DFS 只管"走得到"。</p>
 *
 * <p><b>一次遍历,四种结果</b>:</p>
 * <ul>
 *   <li><b>可达性</b>:{@link #marked(int)} / {@link #count()};</li>
 *   <li><b>距离</b>:{@link #distTo(int)} —— 从起点到 v 的<b>最少边数</b>(不可达为 -1);</li>
 *   <li><b>最短路径</b>:{@link #pathTo(int)} / {@link #parent(int)} —— BFS 树的路径就是最短路径;
 *       路径长度恒为 {@code distTo(v) + 1};</li>
 *   <li><b>层次</b>:{@link #levelCount()} / {@link #verticesAtLevel(int)} —— 第 d 层恰好是
 *       距离为 d 的所有顶点,层次结构由 BFS 自然产生。</li>
 * </ul>
 *
 * <p><b>两种实现,结果完全一致</b>:</p>
 * <ul>
 *   <li>{@link Mode#QUEUE} —— 单个 FIFO 队列,标准写法,最简;</li>
 *   <li>{@link Mode#LEVEL} —— 用"当前层 / 下一层"两张表逐层推进,不显式用队列,
 *       层次结构一目了然(教材里"按层遍历"的常见写法)。</li>
 * </ul>
 *
 * <p><b>一个必须记住的实现细节</b>:标记要在<b>入队时</b>完成,而不是出队时。
 * 若在出队时才标记,同一个顶点会被多个邻居重复入队(顶点虽只被展开一次,
 * 但队列长度最坏从 Θ(V) 涨到 Θ(E),白做大量判重)。测试里有"每个顶点最多入队一次"的断言。</p>
 *
 * <p><b>与 {@link DepthFirstTraversal} 的对照</b>(tinyG.txt 从 0 号顶点出发到 12 号):</p>
 * <pre>
 * BFS 路径 0 -&gt; 6 -&gt; 7 -&gt; 8 -&gt; 10 -&gt; 9 -&gt; 12   共 6 条边   ← 最短
 * DFS 路径 0 -&gt; 5 -&gt; 4 -&gt; 6 -&gt; 7 -&gt; 8 -&gt; 10 -&gt; 9 -&gt; 12   共 8 条边   ← 不是最短
 * </pre>
 *
 * <p><b>适用边界</b>:BFS 的最短路径结论只对<b>无权图(或所有边等权)</b>成立;
 * 边权为正且不等时要换成 Dijkstra(带优先队列的"广义 BFS")。DFS 版仍然有它的用处 ——
 * 后序/逆后序用于拓扑排序、环检测,这些是 BFS 给不出来的。</p>
 *
 * <pre>
 * BreadthFirstTraversal b = new BreadthFirstTraversal(graph, 0);
 * b.distTo(12);              // 最少几步
 * b.pathTo(12);              // 一条最短路径
 * b.verticesAtLevel(2);      // 距离为 2 的全部顶点
 * </pre>
 *
 * @see UndirectedGraph
 * @see DepthFirstTraversal
 * @see <a href="https://algs4.cs.princeton.edu/41graph">Algorithms, 4th Edition, Section 4.1</a>
 */
public final class BreadthFirstTraversal {

    /** 遍历的实现方式 */
    public enum Mode {

        /** 单队列:FIFO 队列 + 入队即标记,标准写法 */
        QUEUE,

        /** 按层推进:维护"当前层/下一层"两张表,不使用显式队列 */
        LEVEL
    }

    /** 被遍历的图 */
    private final UndirectedGraph graph;

    /** 起点 */
    private final int source;

    /** 所用实现方式 */
    private final Mode mode;

    /** marked[v] = v 是否可达 */
    private final boolean[] marked;

    /** parent[v] = BFS 树上 v 的父顶点;起点与不可达顶点为 -1 */
    private final int[] parent;

    /** dist[v] = 起点到 v 的最少边数;不可达为 -1 */
    private final int[] dist;

    /** orderIndex[v] = v 在访问序中的下标;不可达为 -1 */
    private final int[] orderIndex;

    /** 访问序(按距离非递减,只用前 count 个位置) */
    private final int[] order;

    /** 可达顶点数,也是访问序长度 */
    private int count;

    /** 层数(距离 0 .. maxDistance 各一层);至少为 1 */
    private int levelCount;

    /** levelStart[d] = 第 d 层在访问序中的起始下标;末尾多一个 levelCount 作为收尾 */
    private int[] levelStart;

    /**
     * 以单队列方式从 {@code source} 开始广度优先遍历。
     *
     * @param graph  待遍历的图,不能为 null
     * @param source 起点,必须在 {@code [0, V)} 内
     * @throws IllegalArgumentException 参数为 null 或起点越界
     */
    public BreadthFirstTraversal(UndirectedGraph graph, int source) {
        this(graph, source, Mode.QUEUE);
    }

    /**
     * 从 {@code source} 开始广度优先遍历。
     *
     * @param graph  待遍历的图,不能为 null
     * @param source 起点,必须在 {@code [0, V)} 内
     * @param mode   遍历实现方式,不能为 null
     * @throws IllegalArgumentException 参数为 null 或起点越界
     */
    public BreadthFirstTraversal(UndirectedGraph graph, int source, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("遍历方式不能为 null");
        }
        if (source < 0 || source >= graph.V()) {
            throw new IllegalArgumentException("起点 " + source + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }

        this.graph = graph;
        this.source = source;
        this.mode = mode;

        int V = graph.V();
        this.marked = new boolean[V];
        this.parent = new int[V];
        this.dist = new int[V];
        this.orderIndex = new int[V];
        this.order = new int[V];
        Arrays.fill(parent, -1);
        Arrays.fill(dist, -1);
        Arrays.fill(orderIndex, -1);

        if (mode == Mode.QUEUE) {
            bfsQueue(source);
        }
        else {
            bfsLevels(source);
        }
        buildLevels();
    }

    // ------------------------------------------------------------------
    // 两种遍历实现
    // ------------------------------------------------------------------

    /**
     * 单队列版:取队首 → 把未访问邻接点标记、入队。
     *
     * @param s 起点
     */
    private void bfsQueue(int s) {
        Deque<Integer> queue = new ArrayDeque<Integer>();
        discover(s, -1, 0);
        queue.add(s);

        while (!queue.isEmpty()) {
            int v = queue.poll();
            for (int w : graph.adj(v)) {
                if (!marked[w]) {
                    discover(w, v, dist[v] + 1);
                    queue.add(w);   // 入队即标记:每个顶点最多入队一次
                }
            }
        }
    }

    /**
     * 按层推进版:current 是当前这一层的全部顶点,next 收集下一层。
     * 不使用队列,层次结构直接体现在两层表上。
     *
     * @param s 起点
     */
    private void bfsLevels(int s) {
        discover(s, -1, 0);
        List<Integer> current = new ArrayList<Integer>();
        current.add(s);

        while (!current.isEmpty()) {
            List<Integer> next = new ArrayList<Integer>();
            for (int v : current) {
                for (int w : graph.adj(v)) {
                    if (!marked[w]) {
                        discover(w, v, dist[v] + 1);
                        next.add(w);
                    }
                }
            }
            current = next;
        }
    }

    /** 首次发现 v:p 是发现者(父顶点),d 是距离 */
    private void discover(int v, int p, int d) {
        marked[v] = true;
        parent[v] = p;
        dist[v] = d;
        orderIndex[v] = count;
        order[count++] = v;
    }

    /**
     * 由访问序与距离算出各层的起止下标。
     *
     * <p>BFS 的访问序按距离非递减,所以同一层的顶点在 {@code order} 中必然连续:
     * 先统计每层个数,再做前缀和,得到每层起点。</p>
     */
    private void buildLevels() {
        int maxDist = 0;
        for (int i = 0; i < count; i++) {
            if (dist[order[i]] > maxDist) {
                maxDist = dist[order[i]];
            }
        }
        levelCount = maxDist + 1;
        levelStart = new int[levelCount + 1];
        for (int i = 0; i < count; i++) {
            levelStart[dist[order[i]] + 1]++;
        }
        for (int d = 0; d < levelCount; d++) {
            levelStart[d + 1] += levelStart[d];
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * @return 起点
     */
    public int source() {
        return source;
    }

    /**
     * @return 本实例所用的遍历实现方式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @return 与起点连通的顶点数(含起点)
     */
    public int count() {
        return count;
    }

    /**
     * @param v 顶点编号
     * @return v 是否可达
     * @throws IllegalArgumentException {@code v} 越界
     */
    public boolean marked(int v) {
        validateVertex(v);
        return marked[v];
    }

    /**
     * 起点到 v 的最少边数 —— 无权图的<b>最短距离</b>。
     *
     * @param v 顶点编号
     * @return 最短距离;不可达返回 -1
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int distTo(int v) {
        validateVertex(v);
        return dist[v];
    }

    /**
     * @param v 顶点编号
     * @return v 在访问序中的下标;不可达返回 -1
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int orderIndex(int v) {
        validateVertex(v);
        return orderIndex[v];
    }

    /**
     * 访问序:顶点被发现(入队)的顺序,按距离非递减,长度为 {@link #count()}。
     *
     * @return 访问序的副本(改这个数组不影响本对象)
     */
    public int[] order() {
        return Arrays.copyOf(order, count);
    }

    /**
     * BFS 树上 v 的父顶点(即"v 是被谁发现的"),也是最短路径上 v 的前一个顶点。
     *
     * @param v 顶点编号
     * @return 父顶点编号;起点与不可达顶点返回 -1
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int parent(int v) {
        validateVertex(v);
        return parent[v];
    }

    /**
     * @return 最远可达顶点的距离(即最大层号);只有起点可达时为 0
     */
    public int maxDistance() {
        return levelCount - 1;
    }

    /**
     * @return 层数,即距离 0 .. {@link #maxDistance()} 的层数;至少为 1
     */
    public int levelCount() {
        return levelCount;
    }

    /**
     * 距离恰为 {@code level} 的全部顶点,按访问序排列。
     *
     * @param level 层号,取值范围 {@code [0, levelCount())}
     * @return 该层顶点数组的副本(长度为该层顶点个数)
     * @throws IllegalArgumentException {@code level} 越界
     */
    public int[] verticesAtLevel(int level) {
        if (level < 0 || level >= levelCount) {
            throw new IllegalArgumentException("层号 " + level + " 不在 [0, " + (levelCount - 1) + "] 内");
        }
        return Arrays.copyOfRange(order, levelStart[level], levelStart[level + 1]);
    }

    /**
     * @param v 顶点编号
     * @return 起点能否走到 v
     * @throws IllegalArgumentException {@code v} 越界
     */
    public boolean hasPathTo(int v) {
        validateVertex(v);
        return marked[v];
    }

    /**
     * 起点到 v 的一条<b>最短路径</b>(边数最少)。
     *
     * <p>路径由 {@link #parent(int)} 链回溯得到,格式为 {@code [s, ..., v]},
     * 长度恒等于 {@code distTo(v) + 1};相邻两项之间必有边。不可达时返回 {@code null}。</p>
     *
     * @param v 顶点编号
     * @return 最短路径;不可达返回 {@code null}
     * @throws IllegalArgumentException {@code v} 越界
     */
    public Iterable<Integer> pathTo(int v) {
        validateVertex(v);
        if (!marked[v]) {
            return null;
        }
        int[] reversed = new int[dist[v] + 1];
        int n = 0;
        for (int x = v; x != -1; x = parent[x]) {
            reversed[n++] = x;
        }
        List<Integer> path = new ArrayList<Integer>(n);
        for (int i = n - 1; i >= 0; i--) {
            path.add(reversed[i]);
        }
        return path;
    }

    /**
     * @return 形如 {@code BreadthFirstTraversal(QUEUE, 源 0): 可达 13/13,最远距离 6,层数 7}
     */
    @Override
    public String toString() {
        return "BreadthFirstTraversal(" + mode + ", 源 " + source + "): 可达 " + count + "/" + graph.V()
                + ",最远距离 " + maxDistance() + ",层数 " + levelCount;
    }

    /** 校验顶点编号 */
    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    // ------------------------------------------------------------------
    // 演示
    // ------------------------------------------------------------------

    /**
     * 演示:对样例图(命令行给出路径则读文件)从 0 号顶点跑 BFS,打印访问序、层次与各点最短距离;
     * 再与 {@link DepthFirstTraversal} 的路径长度逐点对照,展示"BFS 最短、DFS 不一定最短"。
     *
     * @param args 可选:algs4 格式的数据文件路径
     */
    public static void main(String[] args) {
        UndirectedGraph graph = args.length > 0 ? GraphIO.readFile(args[0]) : sampleGraph();
        System.out.print(graph);

        for (Mode mode : Mode.values()) {
            BreadthFirstTraversal b = new BreadthFirstTraversal(graph, 0, mode);
            System.out.println(b);
            System.out.println("  访问序: " + Arrays.toString(b.order()));
            for (int d = 0; d < b.levelCount(); d++) {
                System.out.println("  第 " + d + " 层(距离 " + d + "): "
                        + Arrays.toString(b.verticesAtLevel(d)));
            }
        }

        compareWithDfs(graph);
    }

    /** 逐点对照 BFS 最短路径与 DFS 路径的长度 */
    private static void compareWithDfs(UndirectedGraph graph) {
        BreadthFirstTraversal bfs = new BreadthFirstTraversal(graph, 0);
        DepthFirstTraversal dfs = new DepthFirstTraversal(graph, 0);
        int target = graph.V() - 1;
        System.out.println("  顶点  BFS 最短边数  DFS 路径边数");
        for (int v = 0; v < graph.V(); v++) {
            if (!bfs.marked(v)) {
                continue;
            }
            int bfsEdges = length(bfs.pathTo(v)) - 1;
            int dfsEdges = length(dfs.pathTo(v)) - 1;
            System.out.printf("  %4d  %10d  %11d%s%n", v, bfsEdges, dfsEdges,
                    bfsEdges < dfsEdges ? "   <-- DFS 多绕了" : "");
        }
        System.out.println("  0 -> " + target + " 最短路径: " + join(bfs.pathTo(target)));
        System.out.println("  0 -> " + target + " DFS 路径: " + join(dfs.pathTo(target)));
    }

    private static int length(Iterable<Integer> path) {
        int n = 0;
        for (int ignored : path) {
            n++;
        }
        return n;
    }

    private static String join(Iterable<Integer> path) {
        StringBuilder sb = new StringBuilder();
        for (int v : path) {
            if (sb.length() > 0) {
                sb.append(" -> ");
            }
            sb.append(v);
        }
        return sb.toString();
    }

    /** 内置演示图(7 个顶点、8 条边) */
    private static UndirectedGraph sampleGraph() {
        UndirectedGraph graph = new UndirectedGraph(7);
        int[][] edges = {{0, 1}, {0, 2}, {0, 5}, {0, 6}, {3, 4}, {3, 5}, {4, 5}, {4, 6}};
        for (int[] e : edges) {
            graph.addEdge(e[0], e[1]);
        }
        return graph;
    }
}
