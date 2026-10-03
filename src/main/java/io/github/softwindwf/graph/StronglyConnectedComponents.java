package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * 强连通分量(SCC,Strongly Connected Components):把有向图的顶点分成若干组,
 * 组内<b>任意两点互相可达</b>({@code v⇝w} 且 {@code w⇝v}),组间最多只有一个方向可达。
 *
 * <p><b>为什么值得单独做</b>:分组之后把每组"缩成一个点",就得到一张<b>无环</b>的缩点图
 * (condensation),于是在任意有向图上都能谈"先后":组内是互相依赖的一团,组间是 DAG。
 * 编译器找循环依赖、构建系统找可以并行的模块、代码分析找互相引用的包,用的都是它。</p>
 *
 * <p><b>两种经典算法</b>:</p>
 * <table border="1" summary="SCC 的两种算法">
 *   <tr><th>模式</th><th>做法</th><th>时间/空间</th><th>特点</th></tr>
 *   <tr><td>{@link Mode#KOSARAJU}</td>
 *       <td>① 在<b>反图</b>上做 DFS 求后序、取逆后序;② 按这个顺序在原图上 DFS,每棵树一个分量</td>
 *       <td>Θ(V+E) / 需要反图 Θ(V+E)</td>
 *       <td>思路最好讲(两趟独立 DFS);分量编号恰好是缩点图的<b>拓扑序</b></td></tr>
 *   <tr><td>{@link Mode#TARJAN}</td>
 *       <td>一趟 DFS,用 {@code index}(发现次序)与 {@code low}(能回到的最早顶点)判断"根",
 *           再用一个栈把当前分量弹出来</td>
 *       <td>Θ(V+E) / 不需要反图</td>
 *       <td>常数更小、只需一趟;分量编号是缩点图的<b>逆拓扑序</b></td></tr>
 * </table>
 *
 * <p>两者给出的<b>划分</b>必然相同,但<b>分量编号不同</b>(含义如上,不要跨模式比较编号);
 * 判断"是否同一分量"请用 {@link #stronglyConnected(int, int)} 或比较 {@link #id(int)}。</p>
 *
 * <p><b>实现说明</b>:两种算法都用<b>显式栈</b>的迭代 DFS,不做递归 ——
 * 10 万顶点的深链/长环也不会栈溢出(递归版 Tarjan 在这种图上会直接 StackOverflowError)。</p>
 *
 * <pre>
 * StronglyConnectedComponents scc = new StronglyConnectedComponents(digraph);
 * scc.count();                      // 分量个数
 * scc.id(3) == scc.id(5);           // 3 与 5 是否互相可达
 * scc.component(scc.id(3));         // 该分量的全部顶点
 * scc.components();                 // 全部分量(每个分量内顶点升序,分量按最小顶点升序)
 * </pre>
 *
 * @see Digraph
 * @see DirectedCycle
 * @see <a href="https://algs4.cs.princeton.edu/42digraph">Algorithms, 4th Edition, Section 4.2</a>
 */
public final class StronglyConnectedComponents {

    /** 求强连通分量的算法 */
    public enum Mode {

        /** Kosaraju–Sharir:反图逆后序 + 原图 DFS,两趟(分量编号为缩点图拓扑序) */
        KOSARAJU,

        /** Tarjan:一趟 DFS + lowlink + 栈(分量编号为缩点图逆拓扑序) */
        TARJAN
    }

    /** 被分析的图 */
    private final Digraph graph;

    /** 所用算法 */
    private final Mode mode;

    /** id[v] = 顶点 v 所属分量的编号 */
    private final int[] id;

    /** 分量个数 */
    private final int count;

    /** 各分量的顶点数 */
    private final int[] sizes;

    /**
     * 用 Tarjan 算法求强连通分量。
     *
     * @param graph 有向图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public StronglyConnectedComponents(Digraph graph) {
        this(graph, Mode.TARJAN);
    }

    /**
     * 求强连通分量。
     *
     * @param graph 有向图,不能为 null
     * @param mode  算法,不能为 null
     * @throws IllegalArgumentException 参数为 null
     */
    public StronglyConnectedComponents(Digraph graph, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("算法不能为 null");
        }
        this.graph = graph;
        this.mode = mode;
        this.id = new int[graph.V()];
        Arrays.fill(id, -1);
        this.count = mode == Mode.KOSARAJU ? kosaraju() : tarjan();

        this.sizes = new int[count];
        for (int v = 0; v < graph.V(); v++) {
            sizes[id[v]]++;
        }
    }

    // ------------------------------------------------------------------
    // Kosaraju–Sharir
    // ------------------------------------------------------------------

    /**
     * ① 反图的<b>逆后序</b>;② 按该顺序在原图上 DFS,每个未访问的起点开启一个新分量。
     *
     * <p>直觉:在反图上"最后完成"的顶点,在原图里是"最靠前"的(源头一侧),
     * 从它出发在原图上能走到的顶点都与它强连通 —— 因为反图上能到达它,说明原图上它能到达它们。</p>
     *
     * @return 分量个数
     */
    private int kosaraju() {
        int V = graph.V();
        int[] order = reversePostOrder(graph.reverse());
        int componentCount = 0;
        for (int index = 0; index < V; index++) {
            int start = order[index];
            if (id[start] != -1) {
                continue;
            }
            // 在【原图】上从 start 出发走一遍,凡走到的都属于同一个分量
            Deque<Integer> stack = new ArrayDeque<Integer>();
            stack.push(start);
            id[start] = componentCount;
            while (!stack.isEmpty()) {
                int v = stack.pop();
                for (int w : graph.adj(v)) {
                    if (id[w] == -1) {
                        id[w] = componentCount;
                        stack.push(w);
                    }
                }
            }
            componentCount++;
        }
        return componentCount;
    }

    /** 迭代 DFS 求后序,再整体反转(与 {@link TopologicalSort} 的 DFS 模式同理) */
    private static int[] reversePostOrder(Digraph g) {
        int V = g.V();
        boolean[] marked = new boolean[V];
        int[] postorder = new int[V];
        int postCount = 0;

        for (int start = 0; start < V; start++) {
            if (marked[start]) {
                continue;
            }
            Deque<Frame> stack = new ArrayDeque<Frame>();
            marked[start] = true;
            stack.push(new Frame(start, g.adj(start).iterator()));
            while (!stack.isEmpty()) {
                Frame top = stack.peek();
                if (top.neighbors.hasNext()) {
                    int w = top.neighbors.next();
                    if (!marked[w]) {
                        marked[w] = true;
                        stack.push(new Frame(w, g.adj(w).iterator()));
                    }
                }
                else {
                    postorder[postCount++] = top.vertex;
                    stack.pop();
                }
            }
        }
        int[] order = new int[V];
        for (int i = 0; i < V; i++) {
            order[i] = postorder[V - 1 - i];
        }
        return order;
    }

    // ------------------------------------------------------------------
    // Tarjan
    // ------------------------------------------------------------------

    /**
     * 一趟迭代 DFS:
     * <ul>
     *   <li>{@code index[v]} = v 被发现的次序;{@code low[v]} = 从 v 的子树出发、经最多一条回边
     *       能回到的最早发现次序;</li>
     *   <li>发现的新顶点压入分量栈;访问完 v 后若 {@code low[v] == index[v]},v 就是所在分量的"根",
     *       把栈中 v 及其上面的顶点一起弹出成一个分量;</li>
     *   <li>回边只对"仍在栈上"的顶点有效(指向已完成顶点的边跨分量,不算)。</li>
     * </ul>
     *
     * @return 分量个数
     */
    private int tarjan() {
        int V = graph.V();
        int[] index = new int[V];
        int[] low = new int[V];
        boolean[] onStack = new boolean[V];
        Arrays.fill(index, -1);
        Deque<Integer> componentStack = new ArrayDeque<Integer>();
        int nextIndex = 0;
        int componentCount = 0;

        for (int start = 0; start < V; start++) {
            if (index[start] != -1) {
                continue;
            }
            index[start] = low[start] = nextIndex++;
            componentStack.push(start);
            onStack[start] = true;

            Deque<Frame> frames = new ArrayDeque<Frame>();
            frames.push(new Frame(start, graph.adj(start).iterator()));

            while (!frames.isEmpty()) {
                Frame top = frames.peek();
                if (top.neighbors.hasNext()) {
                    int w = top.neighbors.next();
                    if (index[w] == -1) {
                        index[w] = low[w] = nextIndex++;
                        componentStack.push(w);
                        onStack[w] = true;
                        frames.push(new Frame(w, graph.adj(w).iterator()));
                    }
                    else if (onStack[w]) {
                        low[top.vertex] = Math.min(low[top.vertex], index[w]);
                    }
                }
                else {
                    int v = top.vertex;
                    frames.pop();
                    if (low[v] == index[v]) {                 // v 是分量的根
                        int w;
                        do {
                            w = componentStack.pop();
                            onStack[w] = false;
                            id[w] = componentCount;
                        }
                        while (w != v);
                        componentCount++;
                    }
                    if (!frames.isEmpty()) {
                        int parent = frames.peek().vertex;
                        low[parent] = Math.min(low[parent], low[v]);
                    }
                }
            }
        }
        return componentCount;
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

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * @return 顶点数
     */
    public int V() {
        return graph.V();
    }

    /**
     * @return 本实例所用的算法
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @return 强连通分量个数(空图为 0)
     */
    public int count() {
        return count;
    }

    /**
     * @param v 顶点编号
     * @return v 所属的分量编号
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int id(int v) {
        validateVertex(v);
        return id[v];
    }

    /**
     * @param component 分量编号
     * @return 该分量的顶点个数
     * @throws IllegalArgumentException 分量编号越界
     */
    public int size(int component) {
        validateComponent(component);
        return sizes[component];
    }

    /**
     * @return 最大分量的顶点个数;空图返回 0
     */
    public int largestComponentSize() {
        int max = 0;
        for (int size : sizes) {
            max = Math.max(max, size);
        }
        return max;
    }

    /**
     * @return 整张图是否强连通(只有一个分量);空图返回 false
     */
    public boolean isStronglyConnected() {
        return count == 1;
    }

    /**
     * @param v 顶点编号
     * @param w 顶点编号
     * @return v 与 w 是否互相可达(即属于同一强连通分量)
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean stronglyConnected(int v, int w) {
        validateVertex(v);
        validateVertex(w);
        return id[v] == id[w];
    }

    /**
     * @param component 分量编号
     * @return 该分量的全部顶点(升序)
     * @throws IllegalArgumentException 分量编号越界
     */
    public List<Integer> component(int component) {
        validateComponent(component);
        List<Integer> vertices = new ArrayList<Integer>(sizes[component]);
        for (int v = 0; v < graph.V(); v++) {
            if (id[v] == component) {
                vertices.add(v);
            }
        }
        return vertices;
    }

    /**
     * 全部分量:每个分量内顶点升序,分量之间按"最小顶点"升序 —— 这个<b>规范化</b>形式
     * 与算法无关,便于不同实现之间比对。
     *
     * @return 分量的列表
     */
    public List<List<Integer>> components() {
        List<List<Integer>> result = new ArrayList<List<Integer>>();
        for (int c = 0; c < count; c++) {
            result.add(component(c));
        }
        Collections.sort(result, new java.util.Comparator<List<Integer>>() {
            public int compare(List<Integer> a, List<Integer> b) {
                return Integer.compare(a.get(0), b.get(0));
            }
        });
        return result;
    }

    /**
     * @return 形如 {@code StronglyConnectedComponents(TARJAN): V=13, 分量数=5, 最大分量=4},
     *         随后逐行列出一个分量(顶点不超过 20 个时列出全部)
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("StronglyConnectedComponents(").append(mode).append("): V=").append(graph.V())
                .append(", 分量数=").append(count).append(", 最大分量=").append(largestComponentSize())
                .append(newline);
        for (List<Integer> vertices : components()) {
            if (vertices.size() > 20) {
                sb.append("  分量(共 ").append(vertices.size()).append(" 个顶点): ")
                        .append(vertices.subList(0, 20)).append(" …").append(newline);
            }
            else {
                sb.append("  ").append(vertices).append(newline);
            }
        }
        return sb.toString();
    }

    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    private void validateComponent(int component) {
        if (component < 0 || component >= count) {
            throw new IllegalArgumentException("分量编号 " + component + " 不在 [0, " + (count - 1) + "] 内");
        }
    }

    /**
     * 演示:对 algs4 的 tinyDG 样例(13 个顶点、22 条弧,已知 5 个强连通分量)跑两种算法并对照,
     * 顺便展示有向环检测。
     *
     * <p>注意:工作区根目录的 {@code tinyDG.txt} 头部声明 22 条边、实际却有 23 行
     * (比 algs4 官方多一条 {@code 7 -> 6}),与严格校验数目的本包解析器不兼容,
     * 所以这里用内置样例;命令行给出文件路径时会改读文件(文件得先自洽)。</p>
     *
     * @param args 可选:有向图数据文件路径
     */
    public static void main(String[] args) {
        Digraph graph = args.length > 0 ? GraphIO.readDigraphFile(args[0]) : tinyDigraph();
        System.out.println("V = " + graph.V() + ", E = " + graph.E());
        for (Mode mode : Mode.values()) {
            StronglyConnectedComponents scc = new StronglyConnectedComponents(graph, mode);
            System.out.println(scc);
        }
        System.out.println("有向环检测: " + new DirectedCycle(graph));
    }

    /** algs4 的 tinyDG 样例:13 个顶点、22 条弧、5 个强连通分量 */
    static Digraph tinyDigraph() {
        Digraph graph = new Digraph(13);
        int[][] edges = {
            {4, 2}, {2, 3}, {3, 2}, {6, 0}, {0, 1}, {2, 0}, {11, 12}, {12, 9},
            {9, 10}, {9, 11}, {8, 9}, {10, 12}, {11, 4}, {4, 3}, {3, 5}, {7, 8},
            {8, 7}, {5, 4}, {5, 6}, {0, 5}, {6, 4}, {6, 9}
        };
        for (int[] edge : edges) {
            graph.addEdge(edge[0], edge[1]);
        }
        return graph;
    }
}
