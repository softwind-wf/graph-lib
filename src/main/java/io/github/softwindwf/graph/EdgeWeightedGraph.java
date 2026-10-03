package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 无向带权图(邻接表表示,表里挂的是 {@link Edge} 对象)。
 *
 * <p><b>与无权图 {@link UndirectedGraph} 的关系</b>:结构完全同构 ——
 * 长度为 V 的数组,第 v 个元素是顶点 v 的邻接表,一条边在两个端点的表里各存一份;
 * 区别只在表里存的是"另一个端点的编号"还是"整条边对象"。
 * 无权图里用 int 省内存、省对象;带权图里必须拿到权值,于是边被对象化。</p>
 *
 * <table border="1" summary="无权图与带权图的对照">
 *   <tr><th></th><th>{@link UndirectedGraph}</th><th>本类</th></tr>
 *   <tr><td>空间</td><td>Θ(V + E)</td><td>Θ(V + E)(每个"边槽"多一个引用)</td></tr>
 *   <tr><td>邻接表元素</td><td>int 顶点号</td><td>{@code Edge} 对象</td></tr>
 *   <tr><td>遍历某点的邻接点</td><td>{@code adj(v)} → Iterable&lt;Integer&gt;</td>
 *       <td>{@code adj(v)} → Iterable&lt;Edge&gt;,再 {@code e.other(v)}</td></tr>
 *   <tr><td>迭代顺序</td><td>头插(LIFO)</td><td>尾插(插入序)</td></tr>
 * </table>
 *
 * <p><b>约定</b>:</p>
 * <ul>
 *   <li>允许<b>自环</b> {@code v-v}:它在 {@code adj[v]} 中出现两次,度数贡献 2;对最小生成树没有意义,
 *       Prim 会自然忽略它(另一端点已标记);</li>
 *   <li>允许<b>平行边</b>:每条边独立保存、{@link #E()} 按重数累加。注意 {@link Edge#equals(Object)}
 *       只按"端点集合 + 权值"判断,所以同权平行边在集合里会被合并 —— 需要重数时请用 {@code E()} 与
 *       {@link #edges()} 逐条遍历,不要用 {@code Set};</li>
 *   <li>顶点数在构造时固定,不支持增删顶点;不是线程安全的。</li>
 * </ul>
 *
 * <pre>
 * EdgeWeightedGraph g = new EdgeWeightedGraph(8);
 * g.addEdge(0, 7, 0.16);
 * g.addEdge(new Edge(4, 5, 0.35));
 * for (Edge e : g.adj(4)) { ... e.other(4) ... }   // 与 4 相连的每条边
 * </pre>
 *
 * @see Edge
 * @see UndirectedGraph
 * @see PrimMST
 * @see GraphIO
 */
public final class EdgeWeightedGraph {

    /** 顶点数上限(与无权图一致的防护) */
    private static final int MAX_VERTICES = 1 << 24;

    /** 顶点数 */
    private final int V;

    /** 边数(平行边按重数计) */
    private int E;

    /** 邻接表:adj[v] 是"与 v 关联的全部边" */
    private final List<Edge>[] adj;

    /** 自环条数 */
    private int selfLoops;

    /**
     * 建立含 {@code V} 个顶点、0 条边的空图。
     *
     * @param V 顶点数,范围 {@code [0, 2^24]}
     * @throws IllegalArgumentException {@code V} 为负或超过上限
     */
    @SuppressWarnings("unchecked")
    public EdgeWeightedGraph(int V) {
        if (V < 0) {
            throw new IllegalArgumentException("顶点数必须非负,当前为 " + V);
        }
        if (V > MAX_VERTICES) {
            throw new IllegalArgumentException("顶点数不能超过 " + MAX_VERTICES + ",当前为 " + V);
        }
        this.V = V;
        this.E = 0;
        this.selfLoops = 0;
        this.adj = (List<Edge>[]) new List<?>[V];
        for (int v = 0; v < V; v++) {
            adj[v] = new ArrayList<Edge>();
        }
    }

    /**
     * 深拷贝:副本与原图互不影响;每张邻接表的迭代顺序与 {@code g} 相同。
     *
     * @param g 待拷贝的图,不能为 null
     * @throws IllegalArgumentException {@code g} 为 null
     */
    @SuppressWarnings("unchecked")
    public EdgeWeightedGraph(EdgeWeightedGraph g) {
        if (g == null) {
            throw new IllegalArgumentException("待拷贝的图不能为 null");
        }
        this.V = g.V;
        this.E = g.E;
        this.selfLoops = g.selfLoops;
        this.adj = (List<Edge>[]) new List<?>[V];
        for (int v = 0; v < V; v++) {
            // 共享 Edge 对象是安全的:Edge 不可变
            this.adj[v] = new ArrayList<Edge>(g.adj[v]);
        }
    }

    // ------------------------------------------------------------------
    // 规模
    // ------------------------------------------------------------------

    /**
     * @return 顶点数 V
     */
    public int V() {
        return V;
    }

    /**
     * @return 边数 E(自环记 1 条,平行边按重数累加)
     */
    public int E() {
        return E;
    }

    /**
     * @return 自环条数(最小生成树会忽略自环)
     */
    public int selfLoopCount() {
        return selfLoops;
    }

    /**
     * 顶点 {@code v} 的度数:与 v 关联的边数(自环在邻接表里出现两次,贡献 2 度)。
     *
     * @param v 顶点编号
     * @return 度数
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int degree(int v) {
        validateVertex(v);
        return adj[v].size();
    }

    /**
     * @return 最大度数;空图返回 0
     */
    public int maxDegree() {
        int max = 0;
        for (int v = 0; v < V; v++) {
            max = Math.max(max, adj[v].size());
        }
        return max;
    }

    /**
     * @return 全部顶点的度数之和,恒等于 {@code 2 * E()}
     */
    public int degreeSum() {
        return 2 * E;
    }

    // ------------------------------------------------------------------
    // 边的增查
    // ------------------------------------------------------------------

    /**
     * 加入一条边:在它两个端点的邻接表里各存一份(尾插)。
     *
     * @param e 待加入的边,不能为 null
     * @throws IllegalArgumentException {@code e} 为 null 或端点越界
     */
    public void addEdge(Edge e) {
        if (e == null) {
            throw new IllegalArgumentException("边不能为 null");
        }
        int v = e.either();
        int w = e.other(v);
        validateVertex(v);
        validateVertex(w);
        adj[v].add(e);
        adj[w].add(e);
        E++;
        if (v == w) {
            selfLoops++;
        }
    }

    /**
     * 便捷方法:按两个端点与权值加边。
     *
     * @param v      一个端点
     * @param w      另一个端点
     * @param weight 权值(有限实数)
     * @throws IllegalArgumentException 端点越界或权值非法
     */
    public void addEdge(int v, int w, double weight) {
        addEdge(new Edge(v, w, weight));
    }

    /**
     * 顶点 {@code v} 的关联边(每条边的两个端点里有一个是 v)。
     * 遍历时用 {@code e.other(v)} 取另一端。
     *
     * @param v 顶点编号
     * @return 关联边的可迭代视图(插入顺序)
     * @throws IllegalArgumentException {@code v} 越界
     */
    public Iterable<Edge> adj(int v) {
        validateVertex(v);
        return adj[v];
    }

    /**
     * 判断两个顶点之间是否至少有一条边(自环也算,{@code hasEdge(v, v)} 为 true)。
     *
     * @param v 一个端点
     * @param w 另一个端点
     * @return 是否存在连接 v 与 w 的边
     * @throws IllegalArgumentException 端点越界
     */
    public boolean hasEdge(int v, int w) {
        validateVertex(v);
        validateVertex(w);
        for (Edge e : adj[v]) {
            if (e.other(v) == w) {
                return true;
            }
        }
        return false;
    }

    /**
     * 两个顶点之间<b>最轻</b>的那条边的权值(平行边取最小者)。
     *
     * @param v 一个端点
     * @param w 另一个端点
     * @return 最小边权
     * @throws IllegalArgumentException 端点越界
     * @throws NoSuchElementException   两个顶点之间没有边
     */
    public double weightOf(int v, int w) {
        validateVertex(v);
        validateVertex(w);
        double min = Double.POSITIVE_INFINITY;
        boolean found = false;
        for (Edge e : adj[v]) {
            if (e.other(v) == w) {
                min = Math.min(min, e.weight());
                found = true;
            }
        }
        if (!found) {
            throw new NoSuchElementException("顶点 " + v + " 与 " + w + " 之间没有边");
        }
        return min;
    }

    /**
     * 遍历全图所有边,每条边<b>只返回一次</b>(平行边按重数逐条返回,自环只返回一条)。
     *
     * @return 边的可迭代视图
     */
    public Iterable<Edge> edges() {
        return new Iterable<Edge>() {
            public Iterator<Edge> iterator() {
                return new Iterator<Edge>() {
                    private int v = 0;
                    private int index = 0;
                    private int selfLoopSeen = 0;
                    private Edge next;

                    public boolean hasNext() {
                        if (next == null) {
                            next = advance();
                        }
                        return next != null;
                    }

                    public Edge next() {
                        if (next == null) {
                            next = advance();
                        }
                        if (next == null) {
                            throw new NoSuchElementException("边已遍历完");
                        }
                        Edge result = next;
                        next = null;
                        return result;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("不支持在遍历边时删除");
                    }

                    /** 找下一条未输出过的边 */
                    private Edge advance() {
                        while (v < V) {
                            while (index < adj[v].size()) {
                                Edge e = adj[v].get(index++);
                                int w = e.other(v);
                                if (w > v) {
                                    return e;       // 另一半在 adj[w] 里,这里只输出一次
                                }
                                if (w == v) {
                                    // 自环在 adj[v] 里有两条记录,只在第 1、3、5… 条上输出
                                    boolean emit = (selfLoopSeen % 2 == 0);
                                    selfLoopSeen++;
                                    if (emit) {
                                        return e;
                                    }
                                }
                            }
                            v++;
                            index = 0;
                            selfLoopSeen = 0;
                        }
                        return null;
                    }
                };
            }
        };
    }

    // ------------------------------------------------------------------
    // 校验与显示
    // ------------------------------------------------------------------

    /**
     * @return 形如 {@code "8 vertices, 16 edges"} 起头,再逐行列出每个顶点的关联边
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder s = new StringBuilder();
        s.append(V).append(" vertices, ").append(E).append(" edges").append(newline);
        for (int v = 0; v < V; v++) {
            s.append(v).append(": ");
            for (Edge e : adj[v]) {
                s.append(e.other(v)).append('(').append(e.weight()).append(") ");
            }
            s.append(newline);
        }
        return s.toString();
    }

    /** 校验顶点编号 */
    private void validateVertex(int v) {
        if (v < 0 || v >= V) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (V - 1) + "] 内");
        }
    }

    /**
     * 演示:读入 tinyEWG.txt 并打印邻接表形式的加权图。
     *
     * @param args 可选:algs4 加权图数据文件路径
     */
    public static void main(String[] args) {
        EdgeWeightedGraph graph = args.length > 0
                ? GraphIO.readWeightedFile(args[0])
                : new EdgeWeightedGraph(3);
        if (args.length == 0) {
            graph.addEdge(0, 1, 0.5);
            graph.addEdge(1, 2, 1.5);
        }
        System.out.print(graph);
        System.out.println("V = " + graph.V() + ", E = " + graph.E()
                + ", degreeSum = " + graph.degreeSum()
                + ", maxDegree = " + graph.maxDegree()
                + ", selfLoops = " + graph.selfLoopCount());
        System.out.println("全部边: " + edgeList(graph.edges()));
    }

    private static List<Edge> edgeList(Iterable<Edge> edges) {
        List<Edge> list = new ArrayList<Edge>();
        for (Edge e : edges) {
            list.add(e);
        }
        return list;
    }
}
