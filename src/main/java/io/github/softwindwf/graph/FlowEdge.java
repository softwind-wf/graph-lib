package io.github.softwindwf.graph;

/**
 * 流网络中的一条<b>有向边</b>:带有<b>容量</b>(capacity)与当前<b>流量</b>(flow),满足
 * {@code 0 ≤ flow ≤ capacity}。
 *
 * <p><b>为什么"剩余容量"要同时看两个方向</b>:一条边 {@code u→v} 上已经有了 f 的流量,
 * 那么从 u 看过去还能再压进 {@code capacity − f};而<b>从 v 看过去</b>可以"反悔"把这 f 退回去,
 * 等价于 v 还能往 u 送 f 的量。所以</p>
 * <pre>
 *   residualCapacityTo(v) = (v == to) ? capacity − flow     // 正向:还能加多少
 *                                     : flow                // 反向:还能退多少
 * </pre>
 * <p>算法(增广路)只在<b>剩余容量 &gt; 0</b> 的方向上走,这正是"网络流可以后悔"的实现方式 ——
 * 不需要真的删除任何流。</p>
 *
 * <p><b>在邻接表里的表示技巧</b>(与 {@link FlowNetwork} 配合):同一条 {@link FlowEdge} 对象
 * 会被放进起点与终点<b>两份</b>邻接表。这样"从 v 出发的所有边(含反向可退的边)"就是
 * {@code FlowNetwork.adj(v)},算法遍历残量图时不用自己拼反图;由于是同一个对象,
 * 流量只存一份,绝不会出现两份数据不一致。</p>
 *
 * <pre>
 * FlowEdge e = new FlowEdge(0, 1, 2.0);   // 容量 2 的管道
 * e.residualCapacityTo(1);                // 2.0 → 从起点 0 出发还能送 2.0
 * e.addResidualFlowTo(1, 1.5);            // 推 1.5 过去
 * e.flow();                               // 1.5
 * e.residualCapacityTo(0);                // 1.5 → 从终点 1 出发可以退回 1.5
 * </pre>
 *
 * @see FlowNetwork
 * @see FordFulkerson
 * @see MinCut
 * @see <a href="https://algs4.cs.princeton.edu/64maxflow">Algorithms, 4th Edition, Section 6.4</a>
 */
public final class FlowEdge {

    /** 起点 */
    private final int from;

    /** 终点 */
    private final int to;

    /** 容量(非负有限实数) */
    private final double capacity;

    /** 当前流量(0 ≤ flow ≤ capacity) */
    private double flow;

    /**
     * 建一条容量为 {@code capacity}、流量为 0 的边。
     *
     * @param from     起点编号
     * @param to       终点编号
     * @param capacity 容量,非负有限实数
     * @throws IllegalArgumentException 编号为负、容量为负或非有限
     */
    public FlowEdge(int from, int to, double capacity) {
        this(from, to, capacity, 0.0);
    }

    /**
     * 建一条边(可用于复制既有流量的网络状态)。
     *
     * @param from     起点编号
     * @param to       终点编号
     * @param capacity 容量,非负有限实数
     * @param flow     初始流量,需满足 {@code 0 ≤ flow ≤ capacity}
     * @throws IllegalArgumentException 参数非法
     */
    public FlowEdge(int from, int to, double capacity, double flow) {
        if (from < 0 || to < 0) {
            throw new IllegalArgumentException("顶点编号不能为负: " + from + " -> " + to);
        }
        if (Double.isNaN(capacity) || Double.isInfinite(capacity)) {
            throw new IllegalArgumentException("容量必须是有限实数,当前为 " + capacity);
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("容量不能为负,当前为 " + capacity);
        }
        if (Double.isNaN(flow) || flow < 0 || flow > capacity) {
            throw new IllegalArgumentException("流量必须落在 [0, " + capacity + "] 内,当前为 " + flow);
        }
        this.from = from;
        this.to = to;
        this.capacity = capacity;
        this.flow = flow;
    }

    /**
     * 复制一条边(容量与流量都照搬)。
     *
     * @param other 待复制的边,不能为 null
     * @throws IllegalArgumentException {@code other} 为 null
     */
    public FlowEdge(FlowEdge other) {
        this(other.from, other.to, other.capacity, other.flow);
    }

    /**
     * @return 起点编号
     */
    public int from() {
        return from;
    }

    /**
     * @return 终点编号
     */
    public int to() {
        return to;
    }

    /**
     * @return 容量
     */
    public double capacity() {
        return capacity;
    }

    /**
     * @return 当前流量
     */
    public double flow() {
        return flow;
    }

    /**
     * 取另一个端点(算法在残量图上走边时需要知道"这条边连到谁")。
     *
     * @param vertex 已知的一个端点
     * @return 另一个端点
     * @throws IllegalArgumentException 顶点不是这条边的端点
     */
    public int other(int vertex) {
        if (vertex == from) {
            return to;
        }
        if (vertex == to) {
            return from;
        }
        throw new IllegalArgumentException("顶点 " + vertex + " 不是边 " + this + " 的端点");
    }

    /**
     * 从 {@code vertex} 出发时,这条边还剩多少可用容量(正向:还能加;
     * 反向:还能把已推的流量退回来)。
     *
     * @param vertex 出发点,必须是这条边的端点
     * @return 剩余容量,非负
     * @throws IllegalArgumentException 顶点不是这条边的端点
     */
    public double residualCapacityTo(int vertex) {
        if (vertex == from) {
            return flow;                                   // 反向:退回
        }
        if (vertex == to) {
            return capacity - flow;                        // 正向:继续加
        }
        throw new IllegalArgumentException("顶点 " + vertex + " 不是边 " + this + " 的端点");
    }

    /**
     * 沿"从 {@code vertex} 出发"的方向改变流量:正向压入 {@code delta},反向退回 {@code delta}。
     *
     * @param vertex 出发点,必须是这条边的端点
     * @param delta  改变量,不能超过该方向的剩余容量
     * @throws IllegalArgumentException 顶点不是端点、{@code delta} 为负或超出剩余容量
     */
    public void addResidualFlowTo(int vertex, double delta) {
        if (Double.isNaN(delta) || delta < 0) {
            throw new IllegalArgumentException("改变量必须非负,当前为 " + delta);
        }
        if (vertex == from) {
            if (delta > flow) {
                throw new IllegalArgumentException("退回 " + delta + " 超过已推流量 " + flow);
            }
            flow -= delta;
        }
        else if (vertex == to) {
            if (delta > capacity - flow) {
                throw new IllegalArgumentException("压入 " + delta + " 超过剩余容量 " + (capacity - flow));
            }
            flow += delta;
        }
        else {
            throw new IllegalArgumentException("顶点 " + vertex + " 不是边 " + this + " 的端点");
        }
    }

    /**
     * @return 形如 {@code "0->1 1.50/2.00"}(流量/容量)
     */
    @Override
    public String toString() {
        return String.format("%d->%d %.2f/%.2f", from, to, flow, capacity);
    }

    /**
     * 容量与流量都参与比较(边本身是不可变的,只有流量会变)。
     *
     * @param other 另一个对象
     * @return 起点、终点、容量、流量都相同
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        FlowEdge that = (FlowEdge) other;
        return from == that.from && to == that.to
                && Double.compare(capacity, that.capacity) == 0
                && Double.compare(flow, that.flow) == 0;
    }

    /**
     * @return 与 {@link #equals(Object)} 一致的散列值
     */
    @Override
    public int hashCode() {
        int result = from;
        result = 31 * result + to;
        result = 31 * result + Double.valueOf(capacity).hashCode();
        return 31 * result + Double.valueOf(flow).hashCode();
    }

    /**
     * 演示:一条容量 2 的管道推 1.5 之后,两个方向的剩余容量。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        FlowEdge edge = new FlowEdge(0, 1, 2.0);
        System.out.println("初始: " + edge + ",从 0 看剩余 " + edge.residualCapacityTo(0)
                + ",从 1 看剩余 " + edge.residualCapacityTo(1));
        edge.addResidualFlowTo(1, 1.5);
        System.out.println("推 1.5 后: " + edge + ",从 0 看剩余 " + edge.residualCapacityTo(0)
                + "(反向可退),从 1 看剩余 " + edge.residualCapacityTo(1) + "(正向可加)");
        edge.addResidualFlowTo(0, 1.5);
        System.out.println("退回后: " + edge);
        try {
            edge.residualCapacityTo(9);
        }
        catch (IllegalArgumentException e) {
            System.out.println("非法端点: " + e.getMessage());
        }
        try {
            new FlowEdge(0, 1, -1.0);
        }
        catch (IllegalArgumentException e) {
            System.out.println("负容量: " + e.getMessage());
        }
        try {
            edge.other(9);
        }
        catch (IllegalArgumentException e) {
            System.out.println("other(9): " + e.getMessage());
        }
    }
}
