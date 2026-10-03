package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * 传递闭包(Transitive Closure):对<i>每一对</i>顶点,回答"从 u 能不能走到 v"。
 *
 * <table border="1" summary="两种实现">
 *   <tr><th>模式</th><th>做法</th><th>时间</th><th>空间</th></tr>
 *   <tr><td>{@link Mode#DFS}</td><td>从每个顶点各做一趟 DFS(默认)</td>
 *       <td>Θ(V·(V+E))</td><td>Θ(V²)</td></tr>
 *   <tr><td>{@link Mode#WARSHALL}</td><td>动态规划:<i>允许经过前 k 个顶点</i>时能否到达</td>
 *       <td>Θ(V³)</td><td>Θ(V²)</td></tr>
 * </table>
 *
 * <p><b>与 {@link FloydWarshall} 的关系</b>:那个算"最短距离"(带权),本类只算"能不能到"(不带权)
 * —— 可以看成把所有边的权值取 1 之后的 Floyd–Warshall,但本类不需要读权值。
 * 与 {@link StronglyConnectedComponents} 的关系:SCC 回答"<b>互相</b>可达",本类回答"<b>单向</b>可达"。</p>
 *
 * <p><b>注意规模</b>:结果本身就是 V² 个布尔值,所以 V 上千时内存就吃紧了
 * (本类在 V 过大时明确报错,而不是让调用方 OOM)。</p>
 *
 * <pre>
 * TransitiveClosure tc = new TransitiveClosure(digraph);
 * tc.reachable(0, 5);          // 0 能到 5 吗
 * tc.reachableFrom(0);         // 0 能到达的全部顶点
 * tc.count();                  // 可达的顶点对总数(含自己到自己)
 * </pre>
 *
 * @see Digraph
 * @see FloydWarshall
 * @see StronglyConnectedComponents
 * @see DirectedPaths
 * @see <a href="https://algs4.cs.princeton.edu/42digraph">Algorithms, 4th Edition, Section 4.2</a>
 */
public final class TransitiveClosure {

    /** 求传递闭包的两种实现 */
    public enum Mode {

        /** 从每个顶点做一趟 DFS */
        DFS,

        /** Warshall 动态规划(V³) */
        WARSHALL
    }

    /** 允许的最大顶点数:结果矩阵是 V² 个布尔值,再大就不适合用这个结构了 */
    private static final int MAX_VERTICES = 1 << 14;

    /** 被分析的图 */
    private final Digraph graph;

    /** 实现方式 */
    private final Mode mode;

    /** reachable[v][w] = v 能否到达 w(含 v == w 时的 true) */
    private final boolean[][] reachable;

    /** 可达顶点对总数(含自己到自己) */
    private final int count;

    /**
     * 用 DFS 求传递闭包。
     *
     * @param graph 有向图,不能为 null
     * @throws IllegalArgumentException 参数为 null 或顶点数超过上限
     */
    public TransitiveClosure(Digraph graph) {
        this(graph, Mode.DFS);
    }

    /**
     * 求传递闭包。
     *
     * @param graph 有向图,不能为 null
     * @param mode  实现方式,不能为 null
     * @throws IllegalArgumentException 参数非法或顶点数超过上限
     */
    public TransitiveClosure(Digraph graph, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("实现方式不能为 null");
        }
        if (graph.V() > MAX_VERTICES) {
            throw new IllegalArgumentException("传递闭包需要 V² 空间,顶点数 " + graph.V()
                    + " 超过上限 " + MAX_VERTICES + ";(单向)可达性请改用 DirectedPaths,互相可达请用 StronglyConnectedComponents");
        }
        this.graph = graph;
        this.mode = mode;
        int V = graph.V();
        this.reachable = new boolean[V][V];

        if (mode == Mode.DFS) {
            for (int v = 0; v < V; v++) {
                markFrom(v);
            }
        }
        else {
            runWarshall();
        }

        int total = 0;
        for (int v = 0; v < V; v++) {
            for (int w = 0; w < V; w++) {
                if (reachable[v][w]) {
                    total++;
                }
            }
        }
        this.count = total;
    }

    /** 从 start 出发的一趟迭代 DFS,把能到达的顶点都标上 */
    private void markFrom(int start) {
        boolean[] marked = reachable[start];
        marked[start] = true;
        Deque<Frame> stack = new ArrayDeque<Frame>();
        stack.push(new Frame(start, graph.adj(start).iterator()));
        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            if (top.neighbors.hasNext()) {
                int w = top.neighbors.next();
                if (!marked[w]) {
                    marked[w] = true;
                    stack.push(new Frame(w, graph.adj(w).iterator()));
                }
            }
            else {
                stack.pop();
            }
        }
    }

    /** Warshall:reachable[v][w] |= reachable[v][k] && reachable[k][w] */
    private void runWarshall() {
        int V = graph.V();
        for (int v = 0; v < V; v++) {
            reachable[v][v] = true;
            for (int w : graph.adj(v)) {
                reachable[v][w] = true;
            }
        }
        for (int k = 0; k < V; k++) {
            for (int v = 0; v < V; v++) {
                if (reachable[v][k]) {
                    for (int w = 0; w < V; w++) {
                        if (reachable[k][w]) {
                            reachable[v][w] = true;
                        }
                    }
                }
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
     * @return 实现方式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @param from 起点
     * @param to   终点
     * @return from 能否到达 to(自己到自己恒为 true)
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean reachable(int from, int to) {
        validateVertex(from);
        validateVertex(to);
        return reachable[from][to];
    }

    /**
     * @param from 起点
     * @return from 能到达的全部顶点(升序,含 from 自己)
     * @throws IllegalArgumentException 顶点越界
     */
    public List<Integer> reachableFrom(int from) {
        validateVertex(from);
        List<Integer> vertices = new ArrayList<Integer>();
        for (int w = 0; w < graph.V(); w++) {
            if (reachable[from][w]) {
                vertices.add(w);
            }
        }
        return vertices;
    }

    /**
     * @return 可达顶点对总数(含自己到自己,所以至少是 V)
     */
    public int count() {
        return count;
    }

    /**
     * @return 全部可达顶点对中"不同顶点"的个数(去掉自反的那些)
     */
    public int countDistinctPairs() {
        return count - graph.V();
    }

    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code TransitiveClosure(DFS): 13 个顶点,可达对 60 个},随后是可达矩阵
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("TransitiveClosure(").append(mode).append("): ").append(graph.V())
                .append(" 个顶点,可达对 ").append(count).append(" 个(含自反)")
                .append(newline);
        if (graph.V() <= 32) {
            sb.append("     ");
            for (int w = 0; w < graph.V(); w++) {
                sb.append(w % 10);
            }
            sb.append(newline);
            for (int v = 0; v < graph.V(); v++) {
                sb.append(String.format("%4d ", v));
                for (int w = 0; w < graph.V(); w++) {
                    sb.append(reachable[v][w] ? '1' : '.');
                }
                sb.append(newline);
            }
        }
        return sb.toString();
    }

    /**
     * 演示:对 tinyDG 样例的传递闭包做两种实现对照,并展示可达矩阵。
     *
     * @param args 可选:有向图数据文件路径
     */
    public static void main(String[] args) {
        Digraph graph = args.length > 0
                ? GraphIO.readDigraphFile(args[0])
                : StronglyConnectedComponents.tinyDigraph();
        for (Mode mode : Mode.values()) {
            TransitiveClosure closure = new TransitiveClosure(graph, mode);
            System.out.println(closure);
        }
        TransitiveClosure dfs = new TransitiveClosure(graph, Mode.DFS);
        TransitiveClosure warshall = new TransitiveClosure(graph, Mode.WARSHALL);
        boolean same = true;
        for (int v = 0; v < graph.V(); v++) {
            for (int w = 0; w < graph.V(); w++) {
                same &= dfs.reachable(v, w) == warshall.reachable(v, w);
            }
        }
        System.out.println("两种实现完全一致: " + same);
    }
}
