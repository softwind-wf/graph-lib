package io.github.softwindwf.graph;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 无向图(邻接表表示,adjacency-lists representation)。
 *
 * <p><b>数据的存法</b>:顶点用连续整数 {@code 0 .. V-1} 编号;一个长度为 {@code V} 的数组,
 * 第 {@code v} 个元素是顶点 {@code v} 的邻接表 —— 一条存放"与 v 相邻的全部顶点"的单链表。
 * 一条无向边 {@code v-w} 在两个顶点的链表里各存一份(各一个结点):</p>
 * <pre>
 *     adj[0] -&gt; 5 -&gt; 6 -&gt; 2 -&gt; 1        // 顶点 0 与 5、6、2、1 相邻
 *     adj[1] -&gt; 0
 *     ...
 * </pre>
 *
 * <p><b>为什么用链表而不是数组</b>:每条边的存储量是常数(两个 int 结点),
 * 总空间 <b>Θ(V + E)</b>,与"图有多稀疏"无关;而邻接矩阵无论边多少都要 Θ(V²)。
 * 链表头插使 {@code addEdge} 为 <b>Θ(1)</b>;代价是链表不可随机访问,
 * 因此"判断某条边是否存在"要沿着链表找,为 <b>Θ(deg(v))</b>。</p>
 *
 * <table border="1" summary="邻接表与邻接矩阵的代价对比">
 *   <tr><th>操作</th><th>邻接表(本类)</th><th>邻接矩阵</th></tr>
 *   <tr><td>空间</td><td>Θ(V + E)</td><td>Θ(V²)</td></tr>
 *   <tr><td>添加边 v-w</td><td>Θ(1)</td><td>Θ(1)</td></tr>
 *   <tr><td>判断边 v-w 是否存在</td><td>Θ(deg(v))</td><td>Θ(1)</td></tr>
 *   <tr><td>遍历 v 的全部邻接点</td><td>Θ(deg(v))</td><td>Θ(V)</td></tr>
 *   <tr><td>遍历全图所有边</td><td>Θ(V + E)</td><td>Θ(V²)</td></tr>
 * </table>
 *
 * <p><b>约定(与《算法(第4版)》Graph 一致)</b>:</p>
 * <ul>
 *   <li><b>允许自环</b> {@code v-v}:它在 {@code adj[v]} 中出现两次,给顶点 v 贡献 <b>2</b> 度。</li>
 *   <li><b>允许平行边</b> {@code v-w} 重复添加:每一条都单独计数,{@link #E()} 与度都按重数累加。</li>
 *   <li>顶点数 {@code V} 在构造时固定,不支持增删顶点。</li>
 *   <li>邻接表的迭代顺序是"<b>最近插入在前</b>"(头插,LIFO)。顺序属于实现细节,
 *       除本类文档明确承诺的场景(如 {@link #toString()})外,调用方不应依赖它。</li>
 *   <li>不是线程安全的。</li>
 * </ul>
 *
 * <p><b>主要操作</b>:</p>
 * <ul>
 *   <li>{@link #addEdge(int, int)} / {@link #removeEdge(int, int)} —— 加边、删边(删边是教材
 *       {@code Graph} 没有的扩展,见方法说明);</li>
 *   <li>{@link #adj(int)} —— 遍历某顶点的全部邻接点(DFS/BFS 的骨架就是它);</li>
 *   <li>{@link #degree(int)}、{@link #maxDegree()}、{@link #degreeSum()} —— 度数统计
 *       ({@code degreeSum() == 2 * E()} 即握手定理);</li>
 *   <li>{@link #edges()} —— 不重不漏地遍历全部边(只出现一次,与 {@code E()} 个数相等)。</li>
 * </ul>
 *
 * <p><b>只做数据结构,不做文件读写</b>:本类不认识文件、不解析文本;按 algs4 文本格式建图、
 * 把图写回文本、导出 Graphviz DOT,全部由工具类 {@link GraphIO} 承担
 * (文本格式:{@code V E} 后跟 {@code 2E} 个端点,空白分隔)。这样换存储方式不影响 IO,
 * 换数据格式也不用改图本身。{@link #toString()} 保留在类内,但它只是给人看的调试视图,
 * 不是可回读的数据格式 —— 要回读请用 {@link GraphIO#format(UndirectedGraph)}。</p>
 *
 * <p><b>样例数据</b>(工作区根目录 {@code tinyG.txt}):13 个顶点、15 条边,
 * 读入后 {@code degreeSum() = 30 = 2 * 15},{@code maxDegree() = 4}(顶点 0)。</p>
 *
 * <pre>
 * java io.github.softwindwf.graph.UndirectedGraph tinyG.txt
 * </pre>
 *
 * @see GraphIO 图的文本/文件读写工具
 * @see <a href="https://algs4.cs.princeton.edu/41graph">Algorithms, 4th Edition, Section 4.1</a>
 */
public class UndirectedGraph {

    /** 平台换行符,供 {@link #toString()} 使用 */
    private static final String NEWLINE = System.getProperty("line.separator");

    /** 顶点数上限:仅用于挡住明显不合理的构造参数(邻接表数组本身要占 O(V) 空间) */
    private static final int MAX_VERTICES = 1 << 24;

    /** 顶点数(构造后固定) */
    private final int V;

    /** 边数(平行边与自环都按条数计) */
    private int E;

    /** 邻接表:adj[v] 是顶点 v 的邻接点链表 */
    private final AdjacencyList[] adj;

    /** 自环条数(便于判断"是不是简单图"),每次 addEdge(v,v) 加一、removeEdge(v,v) 减一 */
    private int selfLoops;

    /**
     * 邻接表结点:存一个邻接点编号,并链向下一个结点。
     * 只保存 int,不存引用类型的顶点对象 —— 顶点在本类中就是它的编号。
     */
    private static final class Node {
        final int vertex;
        Node next;

        Node(int vertex, Node next) {
            this.vertex = vertex;
            this.next = next;
        }
    }

    /**
     * 单个顶点的邻接表:一条头插单链表。
     *
     * <p>链表长度单独用 {@code size} 维护,使 {@link #degree(int)} 为 Θ(1),
     * 不必每次遍历链表去数(这一点比教材的 Bag 更省)。</p>
     */
    private static final class AdjacencyList implements Iterable<Integer> {

        private Node head;
        private int size;

        /** 头插:新结点成为表头,因此迭代顺序是"最近插入在前" */
        void add(int vertex) {
            head = new Node(vertex, head);
            size++;
        }

        /**
         * 删除<b>一个</b>值为 {@code vertex} 的结点(自环需要调用两次才能删干净)。
         *
         * @return 是否删掉了结点(目标不存在时返回 false)
         */
        boolean remove(int vertex) {
            if (head == null) {
                return false;
            }
            if (head.vertex == vertex) {
                head = head.next;
                size--;
                return true;
            }
            Node prev = head;
            while (prev.next != null) {
                if (prev.next.vertex == vertex) {
                    prev.next = prev.next.next;
                    size--;
                    return true;
                }
                prev = prev.next;
            }
            return false;
        }

        boolean contains(int vertex) {
            for (Node p = head; p != null; p = p.next) {
                if (p.vertex == vertex) {
                    return true;
                }
            }
            return false;
        }

        int size() {
            return size;
        }

        /** 只读迭代器:遍历链表但不暴露结点,且不支持 remove */
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
                private Node current = head;

                public boolean hasNext() {
                    return current != null;
                }

                public Integer next() {
                    if (current == null) {
                        throw new NoSuchElementException("邻接表已遍历完");
                    }
                    int vertex = current.vertex;
                    current = current.next;
                    return Integer.valueOf(vertex);
                }

                public void remove() {
                    throw new UnsupportedOperationException("遍历邻接表时不允许删除;请调用 removeEdge(int, int)");
                }
            };
        }
    }

    /**
     * 建立含 {@code V} 个顶点、0 条边的空图。
     *
     * @param V 顶点数,范围为 {@code [0, 2^24]};顶点编号为 {@code 0 .. V-1}
     * @throws IllegalArgumentException {@code V} 为负或超过上限
     */
    public UndirectedGraph(int V) {
        if (V < 0) {
            throw new IllegalArgumentException("顶点数必须非负,当前为 " + V);
        }
        if (V > MAX_VERTICES) {
            throw new IllegalArgumentException("顶点数不能超过 " + MAX_VERTICES + ",当前为 " + V);
        }
        this.V = V;
        this.E = 0;
        this.selfLoops = 0;
        this.adj = new AdjacencyList[V];
        for (int v = 0; v < V; v++) {
            adj[v] = new AdjacencyList();
        }
    }

    /**
     * 深拷贝构造:{@code G} 与副本之后各自独立,改动一方不影响另一方;
     * 每条邻接表的迭代顺序<b>与 {@code G} 完全一致</b>(逐表逆序回填)。
     *
     * @param G 待拷贝的图,不能为 null
     * @throws IllegalArgumentException {@code G} 为 null
     */
    public UndirectedGraph(UndirectedGraph G) {
        if (G == null) {
            throw new IllegalArgumentException("待拷贝的图不能为 null");
        }
        this.V = G.V;
        this.E = G.E;
        this.selfLoops = G.selfLoops;
        this.adj = new AdjacencyList[V];
        for (int v = 0; v < V; v++) {
            adj[v] = new AdjacencyList();
            // 先把 G 的邻接点取到临时数组,再逆序头插回填 —— 两次头插的逆序正好还原原顺序
            int[] tmp = new int[G.adj[v].size()];
            int i = 0;
            for (int w : G.adj[v]) {
                tmp[i++] = w;
            }
            for (int j = tmp.length - 1; j >= 0; j--) {
                adj[v].add(tmp[j]);
            }
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
     * @return 边数 E(自环记 1 条;平行边按重数累加)
     */
    public int E() {
        return E;
    }

    /**
     * @return 自环条数(0 表示不含自环)
     */
    public int selfLoopCount() {
        return selfLoops;
    }

    /**
     * @return 全部顶点的度数之和;恒等于 {@code 2 * E()}(握手定理),可用于自检
     */
    public int degreeSum() {
        return 2 * E;
    }

    /**
     * 顶点 {@code v} 的度数。自环在邻接表中出现两次,因此自环贡献 2 度。
     *
     * @param v 顶点编号
     * @return v 的度数
     * @throws IllegalArgumentException {@code v} 不在 {@code [0, V)} 内
     */
    public int degree(int v) {
        validateVertex(v);
        return adj[v].size();
    }

    /**
     * @return 全图最大度数;空图(V = 0)返回 0
     */
    public int maxDegree() {
        int max = 0;
        for (int v = 0; v < V; v++) {
            if (adj[v].size() > max) {
                max = adj[v].size();
            }
        }
        return max;
    }

    /**
     * @return 全图最小度数;空图(V = 0)返回 0
     */
    public int minDegree() {
        if (V == 0) {
            return 0;
        }
        int min = Integer.MAX_VALUE;
        for (int v = 0; v < V; v++) {
            if (adj[v].size() < min) {
                min = adj[v].size();
            }
        }
        return min;
    }

    /**
     * @return 平均度数 {@code 2E / V};空图返回 0.0
     */
    public double averageDegree() {
        return V == 0 ? 0.0 : 2.0 * E / V;
    }

    // ------------------------------------------------------------------
    // 边的增删查
    // ------------------------------------------------------------------

    /**
     * 添加无向边 {@code v-w}:在 {@code adj[v]} 与 {@code adj[w]} 中各插入一个结点(头插,O(1))。
     *
     * <p>若 {@code v == w}(自环),两个结点都插进同一条表,度增加 2;
     * 若该边已存在,则形成平行边,{@link #E()} 再增加 1(不自动去重)。</p>
     *
     * @param v 边的一个端点
     * @param w 边的另一个端点
     * @throws IllegalArgumentException 端点不在 {@code [0, V)} 内
     */
    public void addEdge(int v, int w) {
        validateVertex(v);
        validateVertex(w);
        adj[v].add(w);
        adj[w].add(v);
        E++;
        if (v == w) {
            selfLoops++;
        }
    }

    /**
     * 删除一条无向边 {@code v-w}(教材的 {@code Graph} 不支持删边,此处为扩展)。
     *
     * <p>只删除<b>一条</b>:若该边被重复添加过(平行边),删掉其中一条后其余仍然保留。
     * 自环 {@code v-v} 会从 {@code adj[v]} 中摘掉两个结点,度减少 2。</p>
     *
     * @param v 边的一个端点
     * @param w 边的另一个端点
     * @throws IllegalArgumentException 端点不在 {@code [0, V)} 内
     * @throws NoSuchElementException   该边不存在(已删过或从未添加)
     */
    public void removeEdge(int v, int w) {
        validateVertex(v);
        validateVertex(w);
        boolean removedV = adj[v].remove(w);
        boolean removedW = adj[w].remove(v);
        if (!removedV || !removedW) {
            // 正常情况下两个方向必然同时存在;走到这里说明内部状态不一致,直接把两个方向恢复原状
            if (removedV) {
                adj[v].add(w);
            }
            if (removedW) {
                adj[w].add(v);
            }
            throw new NoSuchElementException("边 " + v + "-" + w + " 不存在,无法删除");
        }
        E--;
        if (v == w) {
            selfLoops--;
        }
    }

    /**
     * 判断边 {@code v-w} 是否存在(沿 {@code adj[v]} 查找,Θ(deg(v)))。
     *
     * @param v 边的一个端点
     * @param w 边的另一个端点
     * @return 存在返回 true
     * @throws IllegalArgumentException 端点不在 {@code [0, V)} 内
     */
    public boolean hasEdge(int v, int w) {
        validateVertex(v);
        validateVertex(w);
        return adj[v].contains(w);
    }

    // ------------------------------------------------------------------
    // 遍历
    // ------------------------------------------------------------------

    /**
     * 返回顶点 {@code v} 的全部邻接点(只读,顺序为"最近插入在前")。
     *
     * <p>这是 DFS / BFS / 连通分量等一切图算法访问邻居的统一入口;
     * 遍历一个顶点的开销是 Θ(deg(v)),遍历全图邻居是 Θ(V + E)。</p>
     *
     * @param v 顶点编号
     * @return v 的邻接点集合的可迭代视图
     * @throws IllegalArgumentException {@code v} 不在 {@code [0, V)} 内
     */
    public Iterable<Integer> adj(int v) {
        validateVertex(v);
        return adj[v];
    }

    /**
     * 遍历全图所有边,每条边<b>只返回一次</b>:端点按 {@code v <= w} 归一,
     * 且自环(两条邻接记录)只输出一条。返回条数恒等于 {@link #E()}。
     *
     * @return 边数组 {@code int[]{v, w}} 的可迭代视图
     */
    public Iterable<int[]> edges() {
        return new Iterable<int[]>() {
            public Iterator<int[]> iterator() {
                return new Iterator<int[]>() {
                    private int v = 0;
                    private Node p = V > 0 ? adj[0].head : null;
                    private int selfLoopSeen = 0;
                    private int[] next;

                    public boolean hasNext() {
                        if (next != null) {
                            return true;
                        }
                        next = advance();
                        return next != null;
                    }

                    public int[] next() {
                        if (next == null) {
                            next = advance();
                        }
                        if (next == null) {
                            throw new NoSuchElementException("边已遍历完");
                        }
                        int[] result = next;
                        next = null;
                        return result;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("不支持在遍历边时删除");
                    }

                    /** 找下一条未输出过的边;走到尽头返回 null */
                    private int[] advance() {
                        while (v < V) {
                            while (p != null) {
                                int w = p.vertex;
                                p = p.next;
                                if (w > v) {
                                    return new int[]{v, w};     // 另一半在 adj[w] 里,这里只输出一次
                                }
                                if (w == v) {
                                    // 自环在 adj[v] 里有两条记录,只在第 1、3、5… 条上输出
                                    boolean emit = (selfLoopSeen % 2 == 0);
                                    selfLoopSeen++;
                                    if (emit) {
                                        return new int[]{v, w};
                                    }
                                }
                            }
                            v++;
                            if (v < V) {
                                p = adj[v].head;
                                selfLoopSeen = 0;
                            }
                        }
                        return null;
                    }
                };
            }
        };
    }

    // ------------------------------------------------------------------
    // 文本视图(仅调试用;可回读的数据格式与 DOT 导出见 GraphIO)
    // ------------------------------------------------------------------

    /**
     * 邻接表文本形式:第一行为 {@code V vertices, E edges},随后每行一个顶点的邻接表。
     *
     * <p>注意:这是给人看的调试视图,<b>不能</b>被重新解析回图。要读写的可交换格式请用
     * {@link GraphIO#format(UndirectedGraph)} / {@link GraphIO#parse(String)}。</p>
     *
     * @return 邻接表字符串
     */
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(V).append(" vertices, ").append(E).append(" edges").append(NEWLINE);
        for (int v = 0; v < V; v++) {
            s.append(v).append(": ");
            for (int w : adj[v]) {
                s.append(w).append(' ');
            }
            s.append(NEWLINE);
        }
        return s.toString();
    }

    // ------------------------------------------------------------------
    // 内部校验与演示
    // ------------------------------------------------------------------

    /**
     * 校验顶点编号,越界时抛出带上下界的异常。
     *
     * @param v 顶点编号
     * @throws IllegalArgumentException {@code v} 不在 {@code [0, V)} 内
     */
    private void validateVertex(int v) {
        if (v < 0 || v >= V) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (V - 1) + "] 内");
        }
    }

    /**
     * 演示:不带参数时构造一个 7 顶点的示例图并打印邻接表、度数与 DOT 文本;
     * 带参数时把参数当作 algs4 数据文件路径,交给 {@link GraphIO} 读取。
     *
     * @param args 可选:数据文件路径
     */
    public static void main(String[] args) {
        UndirectedGraph graph;
        if (args.length > 0) {
            graph = GraphIO.readFile(args[0]);
        }
        else {
            graph = new UndirectedGraph(7);
            int[][] demoEdges = {{0, 1}, {0, 2}, {0, 5}, {0, 6}, {3, 4}, {3, 5}, {4, 5}, {4, 6}};
            for (int[] e : demoEdges) {
                graph.addEdge(e[0], e[1]);
            }
        }

        System.out.print(graph);
        System.out.println("V = " + graph.V() + ", E = " + graph.E()
                + ", degreeSum = " + graph.degreeSum()
                + ", maxDegree = " + graph.maxDegree()
                + ", minDegree = " + graph.minDegree()
                + ", averageDegree = " + graph.averageDegree()
                + ", selfLoops = " + graph.selfLoopCount());
        for (int v = 0; v < graph.V(); v++) {
            System.out.println("degree(" + v + ") = " + graph.degree(v));
        }
        System.out.print(GraphIO.toDot(graph));
    }
}
