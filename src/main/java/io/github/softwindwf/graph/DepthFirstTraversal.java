package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * 图的深度优先遍历(DFS,Depth-First Search / Traversal)。
 *
 * <p><b>算法</b>:从起点 {@code s} 出发,访问 {@code s},然后沿着它的第一个未访问邻接点一路走下去;
 * 走不动了就退回上一个还有未访问邻接点的顶点,继续深入 —— 即"一条路走到黑,走不通再回头"。
 * 每个顶点只被访问一次,每条邻接表记录只被扫一次,因此时间复杂度 <b>Θ(V + E)</b>,
 * 额外空间 <b>Θ(V)</b>(标记数组 + DFS 树 + 遍历序)。</p>
 *
 * <p><b>一次遍历,四种结果</b>(这也是本类没有按教材拆成
 * {@code DepthFirstSearch}/{@code DepthFirstPaths}/{@code DepthFirstOrder} 三个类的原因 ——
 * 拆开意味着同一张图要跑三遍 DFS):</p>
 * <ul>
 *   <li><b>可达性</b>:{@link #marked(int)} / {@link #count()} —— 与 s 连通的顶点集合
 *       ({@code count() == V} 说明整张图连通);</li>
 *   <li><b>前序</b> {@link #preOrder()}:顶点<b>第一次被访问</b>的顺序,就是"遍历序";</li>
 *   <li><b>后序</b> {@link #postOrder()}:顶点<b>邻接点全部处理完</b>的顺序
 *       (拓扑排序用的逆后序、有向图环检测都建立在它上面);</li>
 *   <li><b>DFS 树</b> {@link #parent(int)} / {@link #depth(int)} / {@link #pathTo(int)}:
 *       遍历时"谁发现了我"构成一棵以 s 为根的树,树上路径就是 s 到 v 的一条通路。</li>
 * </ul>
 *
 * <p><b>两种实现,结果逐顶点等价</b>:</p>
 * <ul>
 *   <li>{@link Mode#RECURSIVE} —— 教材写法,用系统调用栈,代码最短;
 *       代价是深度受线程栈限制(默认栈约 0.5–1 MB,深链图会抛 {@link StackOverflowError});</li>
 *   <li>{@link Mode#ITERATIVE} —— 用显式栈,栈在堆上,可处理任意深度。
 *       注意它不是"把邻接点一股脑压栈"的那种简化写法(那样前序顺序会变、后序也无从得到),
 *       而是给每个顶点保存<b>邻接表游标</b>来精确模拟递归的暂停/恢复,
 *       所以两种模式的可达集、前序、后序、DFS 树完全一致(测试里有逐顶点差分验证)。</li>
 * </ul>
 *
 * <p><b>两点易错提醒</b>:</p>
 * <ol>
 *   <li>{@link #pathTo(int)} 给的是 <b>DFS 树上</b>的通路,<b>不是最短路径</b> ——
 *       DFS 只管"能走到",不管"走得短";要最短路径得用 BFS。</li>
 *   <li>遍历顺序依赖邻接表的迭代顺序。{@link UndirectedGraph} 的邻接表是头插 LIFO,
 *       所以同一张图按不同顺序建边,前序/后序会不同,但<b>可达集与"是否存在路径"不变</b>。</li>
 * </ol>
 *
 * <p><b>典型用途</b>:连通性判定、找一条路径、环检测、二分图判定、割点与桥、
 * 拓扑排序(有向图的逆后序)、有向图的强连通分量(Kosaraju 的第一遍)。</p>
 *
 * <pre>
 * DepthFirstTraversal t = new DepthFirstTraversal(graph, 0);
 * t.count();          // 与 0 连通的顶点数
 * t.marked(5);        // 5 是否可达
 * t.preOrder();       // 前序序列
 * t.pathTo(5);        // DFS 树上 0 到 5 的路径(不可达返回 null)
 * </pre>
 *
 * @see UndirectedGraph
 * @see GraphIO
 * @see <a href="https://algs4.cs.princeton.edu/41graph">Algorithms, 4th Edition, Section 4.1</a>
 */
public final class DepthFirstTraversal {

    /** 遍历的实现方式 */
    public enum Mode {

        /** 递归:用系统调用栈,写法最短,深图会 StackOverflowError */
        RECURSIVE,

        /** 迭代:显式栈 + 邻接表游标,逐顶点等价于递归版,深度不受线程栈限制 */
        ITERATIVE
    }

    /** 被遍历的图 */
    private final UndirectedGraph graph;

    /** 起点 */
    private final int source;

    /** 所用实现方式 */
    private final Mode mode;

    /** marked[v] = v 是否被访问过(即与起点连通) */
    private final boolean[] marked;

    /** parent[v] = DFS 树上 v 的父顶点;根与未访问顶点为 -1 */
    private final int[] parent;

    /** depth[v] = v 在 DFS 树中的深度(根为 0) */
    private final int[] depth;

    /** preIndex[v] = v 在前序中的下标;未访问为 -1 */
    private final int[] preIndex;

    /** postIndex[v] = v 在后序中的下标;未访问为 -1 */
    private final int[] postIndex;

    /** 前序序列(只用前 count 个位置) */
    private final int[] preOrder;

    /** 后序序列(只用前 count 个位置) */
    private final int[] postOrder;

    /** 已访问顶点数,同时也是前序序列长度 */
    private int count;

    /** 后序序列长度(与前序相同,单独计数以免和 count 语义混淆) */
    private int postCount;

    /**
     * 以递归方式从 {@code source} 开始深度优先遍历。
     *
     * @param graph  待遍历的图,不能为 null
     * @param source 起点,必须在 {@code [0, V)} 内
     * @throws IllegalArgumentException 参数为 null 或起点越界
     */
    public DepthFirstTraversal(UndirectedGraph graph, int source) {
        this(graph, source, Mode.RECURSIVE);
    }

    /**
     * 从 {@code source} 开始深度优先遍历。
     *
     * @param graph  待遍历的图,不能为 null
     * @param source 起点,必须在 {@code [0, V)} 内
     * @param mode   遍历实现方式,不能为 null
     * @throws IllegalArgumentException 参数为 null 或起点越界
     */
    public DepthFirstTraversal(UndirectedGraph graph, int source, Mode mode) {
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
        this.depth = new int[V];
        this.preIndex = new int[V];
        this.postIndex = new int[V];
        this.preOrder = new int[V];
        this.postOrder = new int[V];
        Arrays.fill(parent, -1);
        Arrays.fill(preIndex, -1);
        Arrays.fill(postIndex, -1);

        if (mode == Mode.RECURSIVE) {
            dfsRecursive(source);
        }
        else {
            dfsIterative(source);
        }
    }

    // ------------------------------------------------------------------
    // 两种遍历实现
    // ------------------------------------------------------------------

    /**
     * 递归版 DFS:访问 v → 依次对未访问邻接点递归。
     *
     * @param v 当前顶点
     */
    private void dfsRecursive(int v) {
        visit(v);
        for (int w : graph.adj(v)) {
            if (!marked[w]) {
                parent[w] = v;
                depth[w] = depth[v] + 1;
                dfsRecursive(w);
            }
        }
        finish(v);
    }

    /**
     * 迭代版 DFS:显式栈保存"每个尚未走完的顶点 + 它邻接表的游标"。
     *
     * <p>循环不变式:栈顶顶点就是递归版此刻正停在的那个顶点。栈顶还有未检查的邻接点,
     * 就取一个:未访问则访问并压栈(相当于递归下降);栈顶邻接点检查完,就弹出并记后序
     * (相当于递归返回)。这样每个顶点的处理时机与递归版完全一致。</p>
     *
     * @param s 起点
     */
    private void dfsIterative(int s) {
        Deque<Frame> stack = new ArrayDeque<Frame>();
        visit(s);
        stack.push(new Frame(s, graph.adj(s).iterator()));

        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            if (top.neighbors.hasNext()) {
                int w = top.neighbors.next();
                if (!marked[w]) {
                    parent[w] = top.vertex;
                    depth[w] = depth[top.vertex] + 1;
                    visit(w);
                    stack.push(new Frame(w, graph.adj(w).iterator()));
                }
            }
            else {
                finish(top.vertex);
                stack.pop();
            }
        }
    }

    /** 一个"暂停中的递归调用":顶点 + 它邻接表的当前位置 */
    private static final class Frame {
        final int vertex;
        final Iterator<Integer> neighbors;

        Frame(int vertex, Iterator<Integer> neighbors) {
            this.vertex = vertex;
            this.neighbors = neighbors;
        }
    }

    /** 首次访问 v:打标记、记前序 */
    private void visit(int v) {
        marked[v] = true;
        preIndex[v] = count;
        preOrder[count++] = v;
    }

    /** v 的邻接点全部处理完:记后序 */
    private void finish(int v) {
        postIndex[v] = postCount;
        postOrder[postCount++] = v;
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
     * @return 与起点连通的顶点数(含起点本身);等于 {@code graph.V()} 时说明整张图连通
     */
    public int count() {
        return count;
    }

    /**
     * @param v 顶点编号
     * @return v 是否与起点连通(即是否被访问过)
     * @throws IllegalArgumentException {@code v} 越界
     */
    public boolean marked(int v) {
        validateVertex(v);
        return marked[v];
    }

    /**
     * @param v 顶点编号
     * @return v 在前序中的下标;未访问返回 -1
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int preOrderIndex(int v) {
        validateVertex(v);
        return preIndex[v];
    }

    /**
     * @param v 顶点编号
     * @return v 在后序中的下标;未访问返回 -1
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int postOrderIndex(int v) {
        validateVertex(v);
        return postIndex[v];
    }

    /**
     * 前序序列(顶点被首次访问的顺序),长度为 {@link #count()}。
     *
     * @return 前序序列的副本(改这个数组不影响本对象)
     */
    public int[] preOrder() {
        return Arrays.copyOf(preOrder, count);
    }

    /**
     * 后序序列(顶点邻接点全部处理完的顺序),长度为 {@link #count()}。
     * 起点总是最后一个。
     *
     * @return 后序序列的副本(改这个数组不影响本对象)
     */
    public int[] postOrder() {
        return Arrays.copyOf(postOrder, postCount);
    }

    /**
     * DFS 树上 v 的父顶点 —— 即"v 是被谁发现的"。起点与不可达顶点返回 -1。
     *
     * @param v 顶点编号
     * @return 父顶点编号,没有则 -1
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int parent(int v) {
        validateVertex(v);
        return parent[v];
    }

    /**
     * v 在 DFS 树中的深度(起点为 0)。深度比路径长度小 1;不可达顶点返回 0,配合
     * {@link #marked(int)} 判断。
     *
     * @param v 顶点编号
     * @return 深度
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int depth(int v) {
        validateVertex(v);
        return depth[v];
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
     * 起点到 v 的路径(DFS 树上的通路,<b>不是最短路径</b>)。
     *
     * <p>路径由 {@link #parent(int)} 链回溯得到,格式为
     * {@code [s, ..., v]},首元素是起点、末元素是 v;相邻两项之间必有边。</p>
     *
     * @param v 顶点编号
     * @return 路径;若 v 与起点不连通返回 {@code null}
     * @throws IllegalArgumentException {@code v} 越界
     */
    public Iterable<Integer> pathTo(int v) {
        validateVertex(v);
        if (!marked[v]) {
            return null;
        }
        // 从 v 顺着 parent 走到 s,再翻转
        int[] reversed = new int[depth[v] + 1];
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
     * @return 形如 {@code DepthFirstTraversal(RECURSIVE, 源 0): 可达 13/13,前序 [0, 5, 4, ...]}
     */
    @Override
    public String toString() {
        return "DepthFirstTraversal(" + mode + ", 源 " + source + "): 可达 "
                + count + "/" + graph.V() + ",前序 " + Arrays.toString(preOrder());
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
     * 演示:先对样例图(命令行给出路径则读文件,否则用内置 7 顶点图)从 0 号顶点跑两种实现的 DFS,
     * 打印可达数、前序、后序与一条路径;最后用一条 20 万顶点的链演示"递归会爆栈、迭代不会"。
     *
     * @param args 可选:algs4 格式的数据文件路径
     */
    public static void main(String[] args) {
        UndirectedGraph graph = args.length > 0 ? GraphIO.readFile(args[0]) : sampleGraph();
        System.out.print(graph);

        int target = graph.V() - 1;
        for (Mode mode : Mode.values()) {
            DepthFirstTraversal t = new DepthFirstTraversal(graph, 0, mode);
            System.out.println(t);
            System.out.println("  后序: " + join(t.postOrder()));
            System.out.print("  路径 0 -> " + target + ": ");
            Iterable<Integer> path = t.pathTo(target);
            System.out.println(path == null ? "不可达" : join(path));
        }

        deepChainDemo(200_000);
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

    /** 深链对比:递归版与迭代版在 20 万层深度下的表现 */
    private static void deepChainDemo(int n) {
        UndirectedGraph chain = new UndirectedGraph(n);
        for (int v = 0; v + 1 < n; v++) {
            chain.addEdge(v, v + 1);
        }
        System.out.println("链长 " + n + ":");
        try {
            DepthFirstTraversal recursive = new DepthFirstTraversal(chain, 0, Mode.RECURSIVE);
            System.out.println("  递归版: 正常结束,可达 " + recursive.count());
        }
        catch (StackOverflowError e) {
            System.out.println("  递归版: StackOverflowError(系统栈深度不够)");
        }
        DepthFirstTraversal iterative = new DepthFirstTraversal(chain, 0, Mode.ITERATIVE);
        System.out.println("  迭代版: 正常结束,可达 " + iterative.count()
                + ",最远顶点深度 " + iterative.depth(n - 1));
    }

    /** 把序列拼成 [a, b, c ...] 形式;元素过多时省略中间 */
    private static String join(int[] values) {
        if (values.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        if (values.length <= 16) {
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(values[i]);
            }
        }
        else {
            for (int i = 0; i < 8; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(values[i]);
            }
            sb.append(", ..., ").append(values[values.length - 1]);
            sb.append("(共 ").append(values.length).append(" 个)");
        }
        return sb.append(']').toString();
    }

    /** 把路径拼成 0 -> 5 -> 4 形式;过长时省略中间 */
    private static String join(Iterable<Integer> values) {
        List<Integer> list = new ArrayList<Integer>();
        for (int v : values) {
            list.add(v);
        }
        if (list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(list.get(0));
        if (list.size() <= 12) {
            for (int i = 1; i < list.size(); i++) {
                sb.append(" -> ").append(list.get(i));
            }
        }
        else {
            for (int i = 1; i < 4; i++) {
                sb.append(" -> ").append(list.get(i));
            }
            sb.append(" -> ... -> ").append(list.get(list.size() - 1));
            sb.append("(共 ").append(list.size()).append(" 个顶点)");
        }
        return sb.toString();
    }
}
