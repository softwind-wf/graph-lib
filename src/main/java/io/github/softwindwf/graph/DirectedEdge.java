package io.github.softwindwf.graph;

/**
 * 有向带权边(有向图的边对象):从 {@code from} 指向 {@code to},带一个权值。
 *
 * <p><b>与无向边 {@link Edge} 的关键差别</b>:</p>
 * <ul>
 *   <li><b>方向是身份的一部分</b>:{@code 4-&gt;5} 与 {@code 5-&gt;4} 是两条不同的边,
 *       因此 {@link #equals(Object)} / {@link #hashCode()} <b>把方向算进去</b>
 *       (无向边才是方向无关的);</li>
 *   <li>只有 {@link #from()} 与 {@link #to()} 两个"端点",没有"另一端"这种说法 ——
 *       邻接表里每条边只挂在它的起点上,{@code 4-&gt;5} 只出现在 {@code adj(4)} 里。</li>
 * </ul>
 *
 * <p>权值可以为 0 或负数(数据结构本身不限制),但 <b>Dijkstra 要求非负</b> ——
 * 那条约束由 {@link DijkstraSP} 在构造时统一校验并给出明确报错;
 * 带负权的最短路要用 Bellman-Ford。权值必须是有限实数(拒绝 NaN / 无穷)。</p>
 *
 * @see EdgeWeightedDigraph
 * @see DijkstraSP
 * @see <a href="https://algs4.cs.princeton.edu/44sp">Algorithms, 4th Edition, Section 4.4</a>
 */
public final class DirectedEdge implements Comparable<DirectedEdge> {

    /** 起点 */
    private final int from;

    /** 终点 */
    private final int to;

    /** 权值 */
    private final double weight;

    /**
     * 建立一条从 {@code from} 指向 {@code to}、权值为 {@code weight} 的有向边。
     *
     * @param from   起点(非负;上界由所属图校验)
     * @param to     终点;{@code from == to} 表示自环
     * @param weight 权值,必须为有限实数
     * @throws IllegalArgumentException 端点为负或权值不是有限实数
     */
    public DirectedEdge(int from, int to, double weight) {
        if (from < 0 || to < 0) {
            throw new IllegalArgumentException("端点不能为负: " + from + ", " + to);
        }
        if (Double.isNaN(weight) || Double.isInfinite(weight)) {
            throw new IllegalArgumentException("权值必须是有限实数,当前为 " + weight);
        }
        this.from = from;
        this.to = to;
        this.weight = weight;
    }

    /**
     * @return 起点
     */
    public int from() {
        return from;
    }

    /**
     * @return 终点
     */
    public int to() {
        return to;
    }

    /**
     * @return 权值
     */
    public double weight() {
        return weight;
    }

    /**
     * 按权值比较(便于放进优先队列)。
     *
     * @param that 另一条边
     * @return 权值小者为负;相等为 0
     * @throws IllegalArgumentException {@code that} 为 null
     */
    public int compareTo(DirectedEdge that) {
        if (that == null) {
            throw new IllegalArgumentException("比较对象不能为 null");
        }
        return Double.compare(this.weight, that.weight);
    }

    /**
     * 有向边相等:起点、终点、权值全部相同(<b>方向不同即不等</b>)。
     *
     * @param other 待比较对象
     * @return 是否同一条有向边
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        DirectedEdge that = (DirectedEdge) other;
        return this.from == that.from && this.to == that.to
                && Double.compare(this.weight, that.weight) == 0;
    }

    /**
     * @return 与 {@link #equals(Object)} 一致的散列值(方向参与计算)
     */
    @Override
    public int hashCode() {
        int result = 31 * from + to;
        return 31 * result + Double.valueOf(weight).hashCode();
    }

    /**
     * @return 形如 {@code "0->4 0.38"}(自环形如 {@code "2->2 0.5"})
     */
    @Override
    public String toString() {
        return from + "->" + to + " " + weight;
    }
}
