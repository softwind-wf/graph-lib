package io.github.softwindwf.graph;

/**
 * 无向带权边(加权图的边对象)。
 *
 * <p><b>为什么这里要对象化边</b>:无权图里"边"的全部信息就是两个端点,两个 int 足矣;
 * 带权图的边多了一个权值,而且它需要作为一个整体被比较(优先队列里按权排序)、
 * 被返回(最小生成树的边集)、被打印。把这些塞进 {@code int[]} 或平行数组只会让代码更难读,
 * 所以加权图的边是<b>一等公民</b>——这也是教材的做法:{@code Graph} 用 {@code Bag<Integer>},
 * 而 {@code EdgeWeightedGraph} 用 {@code Bag<Edge>}(顶点仍然是 int)。</p>
 *
 * <p><b>无向边的两个约定</b>:</p>
 * <ul>
 *   <li>{@link #either()} / {@link #other(int)} 让调用方"从任一端点看出去"都能拿到另一端,
 *       遍历邻接表时不必关心这条边当初是按哪个方向存进去的;</li>
 *   <li>{@link #equals(Object)} / {@link #hashCode()} 与方向无关:{@code Edge(4,5)} 与
 *       {@code Edge(5,4)} 相等。<b>注意副作用</b>:权值也参与相等判断,所以两条端点相同、
 *       权值也相同的平行边是"相等"的 —— 用 {@code Set<Edge>} 收集边会把它们合并成一条。
 *       求最小生成树不受影响(Prim 用标记数组而不是集合判重),但要按重数统计边数时不能用 Set。</li>
 * </ul>
 *
 * <p><b>权值的取值范围</b>:可以是负数或 0 —— <b>Prim 算法只要求"能比较大小",不需要非负</b>
 * (Dijkstra 才需要非负)。但不接受 {@code NaN} 与无穷大:它们会让比较失去传递性,
 * 让最小生成树失去意义。构造时即校验,免得错误在算法深处才暴露。</p>
 *
 * @see EdgeWeightedGraph
 * @see PrimMST
 * @see <a href="https://algs4.cs.princeton.edu/43mst">Algorithms, 4th Edition, Section 4.3</a>
 */
public final class Edge implements Comparable<Edge> {

    /** 一个端点 */
    private final int v;

    /** 另一个端点(无向,不区分谁是起点) */
    private final int w;

    /** 权值 */
    private final double weight;

    /**
     * 建立一条连接 {@code v} 与 {@code w}、权值为 {@code weight} 的无向边。
     *
     * @param v      一个端点(非负;上界由所属图校验)
     * @param w      另一个端点;{@code v == w} 表示自环
     * @param weight 权值,必须为有限实数(不允许 NaN / 正负无穷)
     * @throws IllegalArgumentException 端点为负或权值不是有限实数
     */
    public Edge(int v, int w, double weight) {
        if (v < 0 || w < 0) {
            throw new IllegalArgumentException("端点不能为负: " + v + ", " + w);
        }
        if (Double.isNaN(weight) || Double.isInfinite(weight)) {
            throw new IllegalArgumentException("权值必须是有限实数,当前为 " + weight);
        }
        this.v = v;
        this.w = w;
        this.weight = weight;
    }

    /**
     * @return 边的一个端点(自环时两个端点相同)
     */
    public int either() {
        return v;
    }

    /**
     * 从 {@code vertex} 这一端看出去,返回另一端的顶点。
     *
     * @param vertex 必须等于本边的一个端点
     * @return 另一端顶点
     * @throws IllegalArgumentException {@code vertex} 不是本边的端点
     */
    public int other(int vertex) {
        if (vertex == v) {
            return w;
        }
        if (vertex == w) {
            return v;
        }
        throw new IllegalArgumentException("顶点 " + vertex + " 不是边 " + this + " 的端点");
    }

    /**
     * @return 权值
     */
    public double weight() {
        return weight;
    }

    /**
     * 按权值比较,使边能直接进优先队列(这是把边对象化的主要理由之一)。
     *
     * @param that 另一条边
     * @return 权值小者为负;相等为 0
     * @throws IllegalArgumentException {@code that} 为 null
     */
    public int compareTo(Edge that) {
        if (that == null) {
            throw new IllegalArgumentException("比较对象不能为 null");
        }
        return Double.compare(this.weight, that.weight);
    }

    /**
     * 无向边相等:端点集合相同、权值相同(与方向无关,{@code Edge(4,5)} 等于 {@code Edge(5,4)})。
     *
     * @param other 待比较对象
     * @return 是否表示同一条边
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Edge that = (Edge) other;
        boolean sameEndpoints = (this.v == that.v && this.w == that.w)
                || (this.v == that.w && this.w == that.v);
        return sameEndpoints && Double.compare(this.weight, that.weight) == 0;
    }

    /**
     * 与 {@link #equals(Object)} 一致:端点按大小排序后再混合,保证方向无关。
     *
     * @return 散列值
     */
    @Override
    public int hashCode() {
        int min = Math.min(v, w);
        int max = Math.max(v, w);
        int result = 31 * min + max;
        return 31 * result + Double.valueOf(weight).hashCode();
    }

    /**
     * @return 形如 {@code "4-5 0.35"}(自环形如 {@code "2-2 0.5"})
     */
    @Override
    public String toString() {
        return v + "-" + w + " " + weight;
    }
}
