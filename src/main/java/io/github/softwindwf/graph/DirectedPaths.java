package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * 有向图上的单源可达性与路径:从源点出发,能到哪些顶点、怎么走。
 *
 * <p><b>为什么单独做</b>:{@link DepthFirstTraversal}/{@link BreadthFirstTraversal} 是<b>无向图</b>的遍历
 * (无向图的"可达"就是"连通",可以互相到达);有向图不是 ——
 * {@code 0→1} 只保证从 0 能到 1,从 1 未必回得来。所以有向图的可达性要单独一套:
 * 沿<b>出边</b>走。</p>
 *
 * <table border="1" summary="有向图遍历的两种模式">
 *   <tr><th>模式</th><th>走法</th><th>路径特点</th><th>时间</th></tr>
 *   <tr><td>{@link Mode#BFS}</td><td>队列,一层一层扩散(默认)</td>
 *       <td><b>边数最少</b>的路径({@link #distTo(int)} 就是最少边数)</td><td>Θ(V+E)</td></tr>
 *   <tr><td>{@link Mode#DFS}</td><td>栈,一条路走到底再回头</td>
 *       <td>任意一条路径({@code distTo} 是 DFS 树里的深度)</td><td>Θ(V+E)</td></tr>
 * </table>
 *
 * <p>两种模式都给出同一件事:{@link #hasPathTo(int)}(能否到达)、{@link #pathTo(int)}(怎么走)、
 * {@link #count()}(能到达多少个顶点)。有向图里"可达集合"不满足对称性,
 * 例如 {@link StronglyConnectedComponents} 判定的就是"互相可达"。</p>
 *
 * <pre>
 * DirectedPaths paths = new DirectedPaths(digraph, 0);
 * paths.hasPathTo(5);
 * paths.pathTo(5);        // [0, 2, 5]
 * paths.pathEdgesTo(5);   // [{0,2}, {2,5}]
 * paths.count();          // 从 0 出发能到达的顶点数(含 0)
 * </pre>
 *
 * @see Digraph
 * @see DepthFirstTraversal
 * @see BreadthFirstTraversal
 * @see StronglyConnectedComponents
 * @see <a href="https://algs4.cs.princeton.edu/42digraph">Algorithms, 4th Edition, Section 4.2</a>
 */
public final class DirectedPaths {

    /** 有向图遍历的两种模式 */
    public enum Mode {

        /** 广度优先:按层扩散,得到边数最少的路径(默认) */
        BFS,

        /** 深度优先:一条路走到底 */
        DFS
    }

    /** 被遍历的有向图 */
    private final Digraph graph;

    /** 源点 */
    private final int source;

    /** 遍历模式 */
    private final Mode mode;

    /** marked[v] = 从源点能否到达 v */
    private final boolean[] marked;

    /** edgeTo[v] = 路径上通向 v 的前一个顶点 */
    private final int[] edgeTo;

    /** distTo[v] = 到 v 的边数(BFS 最短/DFS 树深);不可达 −1 */
    private final int[] distTo;

    /** 访问顺序 */
    private final int[] order;

    /** 可达顶点个数(含源点) */
    private final int count;

    /**
     * 用 BFS 求从 {@code source} 出发的可达性与路径。
     *
     * @param graph  有向图,不能为 null
     * @param source 源点编号
     * @throws IllegalArgumentException 参数为 null 或源点越界
     */
    public DirectedPaths(Digraph graph, int source) {
        this(graph, source, Mode.BFS);
    }

    /**
     * 求从 {@code source} 出发的可达性与路径。
     *
     * @param graph  有向图,不能为 null
     * @param source 源点编号
     * @param mode   遍历模式,不能为 null
     * @throws IllegalArgumentException 参数为 null 或源点越界
     */
    public DirectedPaths(Digraph graph, int source, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("遍历模式不能为 null");
        }
        if (source < 0 || source >= graph.V()) {
            throw new IllegalArgumentException("源点 " + source + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
        this.graph = graph;
        this.source = source;
        this.mode = mode;

        int V = graph.V();
        this.marked = new boolean[V];
        this.edgeTo = new int[V];
        this.distTo = new int[V];
        this.order = new int[V];
        Arrays.fill(edgeTo, -1);
        Arrays.fill(distTo, -1);

        this.count = mode == Mode.BFS ? runBfs() : runDfs();
    }

    /** BFS:队列按层扩散 */
    private int runBfs() {
        Deque<Integer> queue = new ArrayDeque<Integer>();
        marked[source] = true;
        distTo[source] = 0;
        queue.add(source);
        int visited = 0;
        while (!queue.isEmpty()) {
            int v = queue.poll();
            order[visited++] = v;
            for (int w : graph.adj(v)) {
                if (!marked[w]) {
                    marked[w] = true;
                    edgeTo[w] = v;
                    distTo[w] = distTo[v] + 1;
                    queue.add(w);
                }
            }
        }
        return visited;
    }

    /** DFS:显式栈(不用递归,深图不会栈溢出) */
    private int runDfs() {
        Deque<Frame> stack = new ArrayDeque<Frame>();
        marked[source] = true;
        distTo[source] = 0;
        stack.push(new Frame(source, graph.adj(source).iterator()));
        int visited = 0;
        order[visited++] = source;
        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            if (top.neighbors.hasNext()) {
                int w = top.neighbors.next();
                if (!marked[w]) {
                    marked[w] = true;
                    edgeTo[w] = top.vertex;
                    distTo[w] = distTo[top.vertex] + 1;
                    order[visited++] = w;
                    stack.push(new Frame(w, graph.adj(w).iterator()));
                }
            }
            else {
                stack.pop();
            }
        }
        return visited;
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

    /**
     * @return 顶点数
     */
    public int V() {
        return graph.V();
    }

    /**
     * @return 边数
     */
    public int E() {
        return graph.E();
    }

    /**
     * @return 源点编号
     */
    public int source() {
        return source;
    }

    /**
     * @return 遍历模式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @param v 顶点编号
     * @return 源点能否到达 v
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean hasPathTo(int v) {
        validateVertex(v);
        return marked[v];
    }

    /**
     * 与 {@link #hasPathTo(int)} 同义,沿用遍历类的叫法。
     *
     * @param v 顶点编号
     * @return 源点能否到达 v
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean marked(int v) {
        return hasPathTo(v);
    }

    /**
     * @param v 顶点编号
     * @return 源点到 v 的边数(BFS 为最少边数,DFS 为 DFS 树深度);不可达返回 −1
     * @throws IllegalArgumentException 顶点越界
     */
    public int distTo(int v) {
        validateVertex(v);
        return distTo[v];
    }

    /**
     * @return 可达顶点个数(含源点)
     */
    public int count() {
        return count;
    }

    /**
     * 一条从源点到 v 的路径(顶点序列,含首尾)。
     *
     * @param v 顶点编号
     * @return 顶点序列;不可达返回 null
     * @throws IllegalArgumentException 顶点越界
     */
    public List<Integer> pathTo(int v) {
        validateVertex(v);
        if (!marked[v]) {
            return null;
        }
        List<Integer> path = new ArrayList<Integer>(distTo[v] + 1);
        for (int x = v; x != -1; x = edgeTo[x]) {
            path.add(x);
        }
        java.util.Collections.reverse(path);
        return path;
    }

    /**
     * 一条从源点到 v 的路径(边序列,每项是 {@code {from, to}})。
     *
     * @param v 顶点编号
     * @return 边序列;不可达返回 null
     * @throws IllegalArgumentException 顶点越界
     */
    public List<int[]> pathEdgesTo(int v) {
        List<Integer> vertices = pathTo(v);
        if (vertices == null) {
            return null;
        }
        List<int[]> edges = new ArrayList<int[]>(vertices.size() - 1);
        for (int i = 0; i + 1 < vertices.size(); i++) {
            edges.add(new int[]{vertices.get(i), vertices.get(i + 1)});
        }
        return edges;
    }

    /**
     * @return 访问顺序的副本(长度等于 {@link #count()})
     */
    public int[] order() {
        return Arrays.copyOf(order, count);
    }

    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code DirectedPaths(BFS, 源点 0): 13 个顶点,可达 12 个},随后每个可达顶点一行
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("DirectedPaths(").append(mode).append(", 源点 ").append(source).append("): ")
                .append(graph.V()).append(" 个顶点,可达 ").append(count).append(" 个").append(newline);
        for (int v = 0; v < graph.V(); v++) {
            sb.append("  ").append(source).append(" -> ").append(v).append(": ");
            if (marked[v]) {
                sb.append("距离 ").append(distTo[v]).append(",路径 ").append(pathTo(v));
            }
            else {
                sb.append("不可达");
            }
            sb.append(newline);
        }
        return sb.toString();
    }

    /**
     * 演示:在 algs4 的 tinyDG 样例上比较 BFS 与 DFS 的路径(DFS 的路径未必最短)。
     *
     * @param args 可选:有向图数据文件路径
     */
    public static void main(String[] args) {
        Digraph graph = args.length > 0
                ? GraphIO.readDigraphFile(args[0])
                : StronglyConnectedComponents.tinyDigraph();
        for (Mode mode : Mode.values()) {
            DirectedPaths paths = new DirectedPaths(graph, 0, mode);
            System.out.println("[" + mode + "] 可达 " + paths.count() + " 个顶点,访问顺序 "
                    + Arrays.toString(paths.order()));
            if (paths.hasPathTo(5)) {
                System.out.println("  0 -> 5:距离 " + paths.distTo(5) + ",路径 " + paths.pathTo(5));
            }
            System.out.println();
        }
    }
}
