package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 无权有向图(邻接表表示)。
 *
 * <p><b>与其它三个图类的分工</b>:</p>
 * <table border="1" summary="本包四个图类的分工">
 *   <tr><th>类</th><th>有向?</th><th>带权?</th><th>邻接表里存什么</th></tr>
 *   <tr><td>{@link UndirectedGraph}</td><td>否</td><td>否</td><td>顶点号</td></tr>
 *   <tr><td>本类 {@code Digraph}</td><td><b>是</b></td><td>否</td><td>顶点号</td></tr>
 *   <tr><td>{@link EdgeWeightedGraph}</td><td>否</td><td>是</td><td>{@link Edge}</td></tr>
 *   <tr><td>{@link EdgeWeightedDigraph}</td><td>是</td><td>是</td><td>{@link DirectedEdge}</td></tr>
 * </table>
 *
 * <p><b>正反两张邻接表</b>:除了"出边"(后继){@code adj(v)},本类还维护一张<b>反图</b>
 * {@code reverseAdj(v)} = "入边"(前驱)。多花 Θ(V + E) 空间换来:</p>
 * <ul>
 *   <li>{@link #predecessors(int)} 与 {@link #inDegree(int)} 都是 Θ(1) 起步的查询
 *       —— 拓扑排序(Kahn 入度法)与反向遍历都要频繁用到前驱;</li>
 *   <li>{@link #reverse()} 一步就能造出反向图(Kosaraju 求强连通分量、反向可达性都要它),
 *       不必每次重新扫描全部边。</li>
 * </ul>
 *
 * <p><b>约定</b>:允许自环与平行边(平行边按重数计入 {@link #outDegree(int)} /
 * {@link #inDegree(int)} 与 {@link #E()});顶点数在构造时固定;不是线程安全的。</p>
 *
 * <pre>
 * Digraph g = new Digraph(5);
 * g.addEdge(0, 1);
 * g.addEdge(1, 2);
 * g.predecessors(2);   // [1]
 * g.inDegree(2);       // 1
 * g.reverse().adj(2);  // [1]  —— 反向图里 2 指向 1
 * </pre>
 *
 * @see AOVNetwork
 * @see TopologicalSort
 * @see EdgeWeightedDigraph
 */
public final class Digraph {

    /** 顶点数上限 */
    private static final int MAX_VERTICES = 1 << 24;

    /** 顶点数 */
    private final int V;

    /** 边数(平行边按重数计) */
    private int E;

    /** 出边表:adj[v] = v 的后继(按加入顺序) */
    private final List<Integer>[] adj;

    /** 反图表:reverseAdj[v] = v 的前驱(按加入顺序) */
    private final List<Integer>[] reverseAdj;

    /** inDegree[v] = 指向 v 的边数 */
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
    public Digraph(int V) {
        if (V < 0) {
            throw new IllegalArgumentException("顶点数必须非负,当前为 " + V);
        }
        if (V > MAX_VERTICES) {
            throw new IllegalArgumentException("顶点数不能超过 " + MAX_VERTICES + ",当前为 " + V);
        }
        this.V = V;
        this.E = 0;
        this.selfLoops = 0;
        this.adj = (List<Integer>[]) new List<?>[V];
        this.reverseAdj = (List<Integer>[]) new List<?>[V];
        this.inDegree = new int[V];
        for (int v = 0; v < V; v++) {
            adj[v] = new ArrayList<Integer>();
            reverseAdj[v] = new ArrayList<Integer>();
        }
    }

    /**
     * 深拷贝:副本与原图互不影响,邻接表顺序一致。
     *
     * @param g 待拷贝的图,不能为 null
     * @throws IllegalArgumentException {@code g} 为 null
     */
    @SuppressWarnings("unchecked")
    public Digraph(Digraph g) {
        if (g == null) {
            throw new IllegalArgumentException("待拷贝的图不能为 null");
        }
        this.V = g.V;
        this.E = g.E;
        this.selfLoops = g.selfLoops;
        this.inDegree = new int[V];
        System.arraycopy(g.inDegree, 0, this.inDegree, 0, V);
        this.adj = (List<Integer>[]) new List<?>[V];
        this.reverseAdj = (List<Integer>[]) new List<?>[V];
        for (int v = 0; v < V; v++) {
            this.adj[v] = new ArrayList<Integer>(g.adj[v]);
            this.reverseAdj[v] = new ArrayList<Integer>(g.reverseAdj[v]);
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
     * @return 边数 E(平行边按重数计,自环记 1 条)
     */
    public int E() {
        return E;
    }

    /**
     * @return 自环条数(自环同时计入出度与入度)
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
     * 入度:指向 {@code v} 的边数。
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
     * @return 全部出度之和,恒等于 {@code E()}
     */
    public int outDegreeSum() {
        return E;
    }

    // ------------------------------------------------------------------
    // 边的增查
    // ------------------------------------------------------------------

    /**
     * 加入一条有向边 {@code v -> w}。
     *
     * @param v 起点
     * @param w 终点
     * @throws IllegalArgumentException 端点越界
     */
    public void addEdge(int v, int w) {
        validateVertex(v);
        validateVertex(w);
        adj[v].add(w);
        reverseAdj[w].add(v);
        inDegree[w]++;
        E++;
        if (v == w) {
            selfLoops++;
        }
    }

    /**
     * 顶点 {@code v} 的后继(出边指向的顶点)。
     *
     * @param v 顶点编号
     * @return 后继的可迭代视图(加入顺序)
     * @throws IllegalArgumentException {@code v} 越界
     */
    public Iterable<Integer> adj(int v) {
        validateVertex(v);
        return adj[v];
    }

    /**
     * 顶点 {@code v} 的前驱(有边指向 v 的顶点)—— 由反图直接给出,不必扫描全图。
     *
     * @param v 顶点编号
     * @return 前驱的可迭代视图(加入顺序)
     * @throws IllegalArgumentException {@code v} 越界
     */
    public Iterable<Integer> predecessors(int v) {
        validateVertex(v);
        return reverseAdj[v];
    }

    /**
     * 遍历全部边,每条边恰好一次(平行边按重数返回,自环返回一条)。
     *
     * @return {@code int[]{from, to}} 的可迭代视图
     */
    public Iterable<int[]> edges() {
        return new Iterable<int[]>() {
            public Iterator<int[]> iterator() {
                return new Iterator<int[]>() {
                    private int v = 0;
                    private int index = 0;

                    public boolean hasNext() {
                        while (v < V && index >= adj[v].size()) {
                            v++;
                            index = 0;
                        }
                        return v < V;
                    }

                    public int[] next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException("边已遍历完");
                        }
                        return new int[]{v, adj[v].get(index++)};
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("不支持在遍历边时删除");
                    }
                };
            }
        };
    }

    /**
     * @param v 起点
     * @param w 终点
     * @return 是否存在边 {@code v -> w}
     * @throws IllegalArgumentException 端点越界
     */
    public boolean hasEdge(int v, int w) {
        validateVertex(v);
        validateVertex(w);
        return adj[v].contains(w);
    }

    /**
     * 边 {@code v -> w} 的重数(平行边有几条)。
     *
     * @param v 起点
     * @param w 终点
     * @return 条数;没有该边返回 0
     * @throws IllegalArgumentException 端点越界
     */
    public int countEdges(int v, int w) {
        validateVertex(v);
        validateVertex(w);
        int count = 0;
        for (int x : adj[v]) {
            if (x == w) {
                count++;
            }
        }
        return count;
    }

    /**
     * 反向图:把所有边 {@code v -> w} 倒成 {@code w -> v}。
     *
     * <p>本类已经维护了反图表,所以这里只需按原图的出边重新装配一遍,Θ(V + E)。</p>
     *
     * @return 新的有向图,边全部反向
     */
    public Digraph reverse() {
        Digraph reversed = new Digraph(V);
        for (int v = 0; v < V; v++) {
            for (int w : adj[v]) {
                reversed.addEdge(w, v);
            }
        }
        return reversed;
    }

    // ------------------------------------------------------------------
    // 显示
    // ------------------------------------------------------------------

    /**
     * @return 形如 {@code "5 vertices, 4 edges"},随后逐行列出每个顶点的后继
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder s = new StringBuilder();
        s.append(V).append(" vertices, ").append(E).append(" edges").append(newline);
        for (int v = 0; v < V; v++) {
            s.append(v).append(": ");
            for (int w : adj[v]) {
                s.append(w).append(' ');
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
     * 演示:建一张小有向图,打印出边、入度、前驱与反向图。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        Digraph g = new Digraph(5);
        g.addEdge(0, 1);
        g.addEdge(0, 2);
        g.addEdge(1, 3);
        g.addEdge(2, 3);
        g.addEdge(3, 4);

        System.out.print(g);
        System.out.println("V = " + g.V() + ", E = " + g.E() + ", 出度之和 = " + g.outDegreeSum());
        for (int v = 0; v < g.V(); v++) {
            System.out.println("顶点 " + v + ": 出度 " + g.outDegree(v) + ", 入度 " + g.inDegree(v)
                    + ", 前驱 " + list(g.predecessors(v)));
        }
        System.out.println("反向图:");
        System.out.print(g.reverse());
    }

    private static List<Integer> list(Iterable<Integer> values) {
        List<Integer> result = new ArrayList<Integer>();
        for (int v : values) {
            result.add(v);
        }
        return result;
    }
}
