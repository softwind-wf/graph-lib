package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 有向带权图(邻接表表示,表里挂的是出边)。
 *
 * <p><b>与无向带权图 {@link EdgeWeightedGraph} 的差别</b>:一条有向边 {@code v-&gt;w}
 * <b>只挂在起点 v 的邻接表里一次</b>,不出现在 w 的表里。因此:</p>
 * <ul>
 *   <li>{@link #adj(int)} 返回的是<b>出边</b>(从 v 出发的边),它回答"从 v 能去哪儿";</li>
 *   <li>{@link #edges()} 不必像无向图那样去重 —— 每条边本来就只有一份,直接逐表输出即可;</li>
 *   <li>度数要分两种:{@link #outDegree(int)}(出度,就是邻接表长度)与
 *       {@link #inDegree(int)}(入度,需要在加边时单独累计)。</li>
 * </ul>
 *
 * <p>空间仍是 Θ(V + E)(无向图存 2E 个"边槽",有向图只存 E 个)。</p>
 *
 * <p><b>约定</b>:允许自环(计入出度与入度各 1,对最短路没有意义);
 * 允许平行边(同起点同终点可以有多条,各自独立保存);顶点数在构造时固定;不是线程安全的。</p>
 *
 * <pre>
 * EdgeWeightedDigraph g = new EdgeWeightedDigraph(8);
 * g.addEdge(0, 4, 0.38);
 * g.addEdge(new DirectedEdge(4, 5, 0.35));
 * for (DirectedEdge e : g.adj(0)) { ... }   // 0 的出边
 * </pre>
 *
 * @see DirectedEdge
 * @see EdgeWeightedGraph
 * @see DijkstraSP
 * @see GraphIO
 */
public final class EdgeWeightedDigraph {

    /** 顶点数上限 */
    private static final int MAX_VERTICES = 1 << 24;

    /** 顶点数 */
    private final int V;

    /** 边数 */
    private int E;

    /** 邻接表:adj[v] 是"从 v 出发的全部边" */
    private final List<DirectedEdge>[] adj;

    /** inDegree[w] = 指向 w 的边数 */
    private final int[] inDegree;

    /** 自环条数 */
    private int selfLoops;

    /**
     * 建立含 {@code V} 个顶点、0 条边的空有向图。
     *
     * @param V 顶点数,范围 {@code [0, 2^24]}
     * @throws IllegalArgumentException {@code V} 为负或超过上限
     */
    @SuppressWarnings("unchecked")
    public EdgeWeightedDigraph(int V) {
        if (V < 0) {
            throw new IllegalArgumentException("顶点数必须非负,当前为 " + V);
        }
        if (V > MAX_VERTICES) {
            throw new IllegalArgumentException("顶点数不能超过 " + MAX_VERTICES + ",当前为 " + V);
        }
        this.V = V;
        this.E = 0;
        this.selfLoops = 0;
        this.adj = (List<DirectedEdge>[]) new List<?>[V];
        this.inDegree = new int[V];
        for (int v = 0; v < V; v++) {
            adj[v] = new ArrayList<DirectedEdge>();
        }
    }

    /**
     * 深拷贝:副本与原图互不影响,邻接表顺序一致(共享不可变的 {@link DirectedEdge} 对象)。
     *
     * @param g 待拷贝的图,不能为 null
     * @throws IllegalArgumentException {@code g} 为 null
     */
    @SuppressWarnings("unchecked")
    public EdgeWeightedDigraph(EdgeWeightedDigraph g) {
        if (g == null) {
            throw new IllegalArgumentException("待拷贝的图不能为 null");
        }
        this.V = g.V;
        this.E = g.E;
        this.selfLoops = g.selfLoops;
        this.inDegree = new int[V];
        System.arraycopy(g.inDegree, 0, this.inDegree, 0, V);
        this.adj = (List<DirectedEdge>[]) new List<?>[V];
        for (int v = 0; v < V; v++) {
            this.adj[v] = new ArrayList<DirectedEdge>(g.adj[v]);
        }
    }

    // ------------------------------------------------------------------
    // 规模与度数
    // ------------------------------------------------------------------

    /**
     * @return 顶点数 V
     */
    public int V() {
        return V;
    }

    /**
     * @return 边数 E(平行边按重数计,自环记 1 条)
     */
    public int E() {
        return E;
    }

    /**
     * @return 自环条数
     */
    public int selfLoopCount() {
        return selfLoops;
    }

    /**
     * 出度:从 {@code v} 出发的边数。
     *
     * @param v 顶点编号
     * @return 出度
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int outDegree(int v) {
        validateVertex(v);
        return adj[v].size();
    }

    /**
     * 入度:指向 {@code v} 的边数(自环同时计入出度与入度)。
     *
     * @param v 顶点编号
     * @return 入度
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int inDegree(int v) {
        validateVertex(v);
        return inDegree[v];
    }

    /**
     * @return 最大出度;空图返回 0
     */
    public int maxOutDegree() {
        int max = 0;
        for (int v = 0; v < V; v++) {
            max = Math.max(max, adj[v].size());
        }
        return max;
    }

    /**
     * @return 全部顶点的出度之和,恒等于 {@code E()}
     */
    public int outDegreeSum() {
        return E;
    }

    // ------------------------------------------------------------------
    // 边的增查
    // ------------------------------------------------------------------

    /**
     * 加入一条有向边:只挂在它的起点邻接表里。
     *
     * @param e 待加入的边,不能为 null
     * @throws IllegalArgumentException {@code e} 为 null 或端点越界
     */
    public void addEdge(DirectedEdge e) {
        if (e == null) {
            throw new IllegalArgumentException("边不能为 null");
        }
        validateVertex(e.from());
        validateVertex(e.to());
        adj[e.from()].add(e);
        inDegree[e.to()]++;
        E++;
        if (e.from() == e.to()) {
            selfLoops++;
        }
    }

    /**
     * 便捷方法:{@code addEdge(from, to, weight)}。
     *
     * @param from   起点
     * @param to     终点
     * @param weight 权值(有限实数)
     * @throws IllegalArgumentException 端点越界或权值非法
     */
    public void addEdge(int from, int to, double weight) {
        addEdge(new DirectedEdge(from, to, weight));
    }

    /**
     * 顶点 {@code v} 的出边。
     *
     * @param v 顶点编号
     * @return 出边的可迭代视图(插入顺序)
     * @throws IllegalArgumentException {@code v} 越界
     */
    public Iterable<DirectedEdge> adj(int v) {
        validateVertex(v);
        return adj[v];
    }

    /**
     * 遍历全图所有边(每条边恰好一次,无需去重)。
     *
     * @return 边的可迭代视图
     */
    public Iterable<DirectedEdge> edges() {
        return new Iterable<DirectedEdge>() {
            public Iterator<DirectedEdge> iterator() {
                return new Iterator<DirectedEdge>() {
                    private int v = 0;
                    private int index = 0;
                    private DirectedEdge next;

                    public boolean hasNext() {
                        if (next == null) {
                            next = advance();
                        }
                        return next != null;
                    }

                    public DirectedEdge next() {
                        if (next == null) {
                            next = advance();
                        }
                        if (next == null) {
                            throw new NoSuchElementException("边已遍历完");
                        }
                        DirectedEdge result = next;
                        next = null;
                        return result;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("不支持在遍历边时删除");
                    }

                    private DirectedEdge advance() {
                        while (v < V) {
                            if (index < adj[v].size()) {
                                return adj[v].get(index++);
                            }
                            v++;
                            index = 0;
                        }
                        return null;
                    }
                };
            }
        };
    }

    /**
     * 判断是否存在从 {@code from} 指向 {@code to} 的边(自环也算)。
     *
     * @param from 起点
     * @param to   终点
     * @return 是否存在该有向边
     * @throws IllegalArgumentException 端点越界
     */
    public boolean hasEdge(int from, int to) {
        validateVertex(from);
        validateVertex(to);
        for (DirectedEdge e : adj[from]) {
            if (e.to() == to) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 {@code from} 到 {@code to} 的<b>最轻</b>平行边的权值。
     *
     * @param from 起点
     * @param to   终点
     * @return 最小边权
     * @throws IllegalArgumentException 端点越界
     * @throws NoSuchElementException   没有这样的边
     */
    public double weightOf(int from, int to) {
        validateVertex(from);
        validateVertex(to);
        double min = Double.POSITIVE_INFINITY;
        boolean found = false;
        for (DirectedEdge e : adj[from]) {
            if (e.to() == to) {
                min = Math.min(min, e.weight());
                found = true;
            }
        }
        if (!found) {
            throw new NoSuchElementException("没有从 " + from + " 指向 " + to + " 的边");
        }
        return min;
    }

    // ------------------------------------------------------------------
    // 与其它结构的转换
    // ------------------------------------------------------------------

    /**
     * 取出"只保留拓扑结构"的无权有向图(权值丢弃,平行边按重数保留)。
     * 需要拓扑排序、环检测、强连通分量这类只看结构、不看权值的算法时,
     * 把它交给 {@link TopologicalSort}、{@link DirectedCycle}、{@link StronglyConnectedComponents}。
     *
     * @return 与当前顶点数、边数一致的无权有向图
     */
    public Digraph toDigraph() {
        Digraph digraph = new Digraph(V);
        for (DirectedEdge edge : edges()) {
            digraph.addEdge(edge.from(), edge.to());
        }
        return digraph;
    }

    // ------------------------------------------------------------------
    // 校验与显示
    // ------------------------------------------------------------------

    /**
     * @return 形如 {@code "8 vertices, 15 edges"},随后逐行列出每个顶点的出边(含指向与权值)
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder s = new StringBuilder();
        s.append(V).append(" vertices, ").append(E).append(" edges").append(newline);
        for (int v = 0; v < V; v++) {
            s.append(v).append(": ");
            for (DirectedEdge e : adj[v]) {
                s.append(e.to()).append('(').append(e.weight()).append(") ");
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
     * 演示:读入 tinyEWD.txt 并打印有向带权图的邻接表。
     *
     * @param args 可选:algs4 有向加权图数据文件路径
     */
    public static void main(String[] args) {
        EdgeWeightedDigraph graph = args.length > 0
                ? GraphIO.readWeightedDigraphFile(args[0])
                : new EdgeWeightedDigraph(3);
        if (args.length == 0) {
            graph.addEdge(0, 1, 0.5);
            graph.addEdge(2, 1, 1.5);
        }
        System.out.print(graph);
        System.out.println("V = " + graph.V() + ", E = " + graph.E()
                + ", 出度之和 = " + graph.outDegreeSum()
                + ", 最大出度 = " + graph.maxOutDegree()
                + ", 自环 = " + graph.selfLoopCount());
        for (int v = 0; v < graph.V(); v++) {
            System.out.println("顶点 " + v + ": 出度 " + graph.outDegree(v) + ", 入度 " + graph.inDegree(v));
        }
    }
}
