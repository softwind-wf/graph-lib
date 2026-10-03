package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * 拓扑排序:把有向图的顶点排成一条线,使<b>每条边都从前往后指</b>。
 *
 * <p><b>解决什么问题</b>:AOV 网(见 {@link AOVNetwork})的活动有先后约束,
 * 拓扑序列就是一个"每一步都能开工"的合法执行顺序。图中有回路时不存在这样的顺序
 * (活动互相等待),算法必须报告"不可行"并指出回路在哪。</p>
 *
 * <p><b>三种实现</b>:</p>
 * <table border="1" summary="拓扑排序的三种做法">
 *   <tr><th>模式</th><th>做法</th><th>时间</th><th>特点</th></tr>
 *   <tr><td>{@link Mode#KAHN}</td>
 *       <td>Kahn 入度法:反复取"入度为 0"的顶点输出,并把它后继的入度减一(用<b>队列</b>)</td>
 *       <td>Θ(V + E)</td><td>不用递归、不爆栈;多个候选时"先变成入度 0 的先输出"(默认)</td></tr>
 *   <tr><td>{@link Mode#KAHN_LEX}</td>
 *       <td>同上,但用<b>最小堆</b>选候选</td>
 *       <td>Θ(V log V + E log V)</td><td>给出<b>字典序最小</b>的拓扑序</td></tr>
 *   <tr><td>{@link Mode#DFS}</td>
 *       <td>深度优先遍历求<b>后序</b>,再整体反转(逆后序)</td>
 *       <td>Θ(V + E)</td><td>一趟 DFS 顺带得到后序(拓扑排序、强连通分量共用它)</td></tr>
 * </table>
 *
 * <p><b>为什么逆后序就是拓扑序</b>:DFS 中若存在边 {@code v -> w},则 w 一定在 v 之前完成
 * (要么 w 还没访问、被 v 递归访问完;要么 w 已在栈上 —— 那就是回路)。
 * 所以"完成的先后"反过来就是"先决关系"的先后。</p>
 *
 * <p><b>回路检测</b>:Kahn 法里,若最后输出的顶点数少于 V,说明剩下的顶点入度都大于 0
 * —— 它们互相等待,必然处在某个回路里(或由回路可达);DFS 法里,若遇到指向"正在栈上"的顶点
 * 的边(回边),就是回路。两种判据都会给出 {@link #hasCycle()};
 * 至于<b>具体的回路</b>,由 {@link DirectedCycle} 单独求一遍(DirectedCycle 里有实现说明),
 * 本类在发现回路时直接委托给它,避免两处维护同一段求环代码。</p>
 *
 * <p><b>三种模式的关系</b>:DAG 上它们都给出合法拓扑序,但具体顺序可能不同
 * (合法顺序本来就不唯一);只有 {@link Mode#KAHN_LEX} 保证是字典序最小那一个。
 * 有回路时三者都报告 <code>order() == null</code>。</p>
 *
 * <pre>
 * AOVNetwork net = ...;
 * TopologicalSort ts = new TopologicalSort(net.toDigraph());
 * ts.isDag();                    // 工程是否可行
 * net.namesOf(ts.order());       // 可行的执行顺序(名称)
 * net.namesOf(ts.cycle());       // 不可行时:卡住的那个循环依赖
 * </pre>
 *
 * @see Digraph
 * @see AOVNetwork
 * @see <a href="https://algs4.cs.princeton.edu/42digraph">Algorithms, 4th Edition, Section 4.2</a>
 */
public final class TopologicalSort {

    /** 拓扑排序的实现方式 */
    public enum Mode {

        /** Kahn 入度法 + FIFO 队列:Θ(V + E),不递归(默认) */
        KAHN,

        /** Kahn 入度法 + 最小堆:给出字典序最小的拓扑序 */
        KAHN_LEX,

        /** DFS 逆后序:一趟深搜顺便得到后序 */
        DFS
    }

    /** 被排序的图 */
    private final Digraph graph;

    /** 所用实现方式 */
    private final Mode mode;

    /** 拓扑序;有回路时为 null */
    private final int[] order;

    /** 每个顶点在拓扑序中的位置;有回路时全为 -1 */
    private final int[] position;

    /** 是否无环(存在拓扑序) */
    private final boolean dag;

    /** 有回路时给出的一个回路(顶点序列,首尾相接、不重复首顶点);无环时为 null */
    private final int[] cycle;

    /**
     * 用 Kahn 入度法对图做拓扑排序。
     *
     * @param graph 有向图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public TopologicalSort(Digraph graph) {
        this(graph, Mode.KAHN);
    }

    /**
     * 对图做拓扑排序。
     *
     * @param graph 有向图,不能为 null
     * @param mode  实现方式,不能为 null
     * @throws IllegalArgumentException 参数为 null
     */
    public TopologicalSort(Digraph graph, Mode mode) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("实现方式不能为 null");
        }
        this.graph = graph;
        this.mode = mode;

        if (mode == Mode.DFS) {
            int[] dfsResult = runDfs();
            this.order = dfsResult;
        }
        else {
            this.order = runKahn(mode == Mode.KAHN_LEX);
        }
        this.dag = order != null;

        int V = graph.V();
        this.position = new int[V];
        Arrays.fill(position, -1);
        if (dag) {
            for (int index = 0; index < order.length; index++) {
                position[order[index]] = index;
            }
        }
        this.cycle = dag ? null : new DirectedCycle(graph).cycle();
    }

    // ------------------------------------------------------------------
    // Kahn 入度法
    // ------------------------------------------------------------------

    /**
     * 反复输出入度为 0 的顶点。用队列时顺序是"先变 0 先出";用最小堆时得到字典序最小解。
     *
     * @param lexicographic 是否用最小堆选候选
     * @return 拓扑序;存在回路时返回 null
     */
    private int[] runKahn(boolean lexicographic) {
        int V = graph.V();
        int[] indegree = new int[V];
        for (int v = 0; v < V; v++) {
            indegree[v] = graph.inDegree(v);
        }

        Queue<Integer> ready = lexicographic
                ? new PriorityQueue<Integer>()
                : new ArrayDeque<Integer>();
        for (int v = 0; v < V; v++) {
            if (indegree[v] == 0) {
                ready.add(v);
            }
        }

        int[] result = new int[V];
        int count = 0;
        while (!ready.isEmpty()) {
            int v = ready.poll();
            result[count++] = v;
            for (int w : graph.adj(v)) {
                indegree[w]--;
                if (indegree[w] == 0) {
                    ready.add(w);
                }
            }
        }
        return count == V ? result : null;      // 没输出完 = 有回路
    }

    // ------------------------------------------------------------------
    // DFS 逆后序
    // ------------------------------------------------------------------

    /**
     * 迭代式 DFS(显式栈,不递归)求后序;无回路时把后序反转即为拓扑序。
     *
     * @return 拓扑序;存在回路时返回 null
     */
    private int[] runDfs() {
        int V = graph.V();
        int[] state = new int[V];               // 0 未访问 1 在栈上 2 已完成
        int[] postorder = new int[V];
        int postCount = 0;
        boolean hasCycle = false;

        for (int start = 0; start < V && !hasCycle; start++) {
            if (state[start] != 0) {
                continue;
            }
            Deque<Frame> stack = new ArrayDeque<Frame>();
            state[start] = 1;
            stack.push(new Frame(start, graph.adj(start).iterator()));

            while (!stack.isEmpty()) {
                Frame top = stack.peek();
                if (top.neighbors.hasNext()) {
                    int w = top.neighbors.next();
                    if (state[w] == 0) {
                        state[w] = 1;
                        stack.push(new Frame(w, graph.adj(w).iterator()));
                    }
                    else if (state[w] == 1) {
                        hasCycle = true;            // 回边:指向仍在栈上的顶点
                        break;
                    }
                }
                else {
                    state[top.vertex] = 2;
                    postorder[postCount++] = top.vertex;
                    stack.pop();
                }
            }
        }

        if (hasCycle) {
            return null;
        }
        int[] result = new int[V];
        for (int i = 0; i < V; i++) {
            result[i] = postorder[V - 1 - i];       // 逆后序
        }
        return result;
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
     * @return 本实例所用的实现方式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @return 是否存在拓扑序(等价于图中无有向回路,即 AOV 网描述的工程可行)
     */
    public boolean isDag() {
        return dag;
    }

    /**
     * @return 是否存在有向回路(工程不可行)
     */
    public boolean hasCycle() {
        return !dag;
    }

    /**
     * 拓扑序:一个顶点序列,使每条边 {@code v -> w} 都满足 v 排在 w 之前。
     *
     * @return 拓扑序的副本;存在回路时返回 {@code null}
     */
    public int[] order() {
        return order == null ? null : Arrays.copyOf(order, order.length);
    }

    /**
     * 顶点在拓扑序中的位置(从 0 开始)。
     *
     * @param v 顶点编号
     * @return 位置;存在回路时返回 -1
     * @throws IllegalArgumentException {@code v} 越界
     */
    public int positionOf(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
        return position[v];
    }

    /**
     * 一个具体的有向回路(顶点序列,首尾相接,如 {@code [1, 2, 3]} 表示 1→2→3→1)。
     *
     * @return 回路的副本;无回路时返回 {@code null}
     */
    public int[] cycle() {
        return cycle == null ? null : Arrays.copyOf(cycle, cycle.length);
    }

    /**
     * @return 形如 {@code TopologicalSort(KAHN): V=9, 有拓扑序=true, 顺序 [0, 1, 2, ...]};
     *         有回路时给出回路
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TopologicalSort(").append(mode).append("): V=").append(graph.V())
                .append(", 有拓扑序=").append(dag);
        if (dag) {
            sb.append(", 顺序 ").append(Arrays.toString(order));
        }
        else {
            sb.append(", 回路 ").append(Arrays.toString(cycle));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 演示
    // ------------------------------------------------------------------

    /**
     * 演示:对一张 7 顶点的小有向无环图跑三种实现,再人为加一条回边,展示回路检测。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        Digraph dag = new Digraph(7);
        int[][] edges = {{0, 5}, {0, 2}, {0, 1}, {3, 6}, {3, 5}, {3, 4}, {5, 2}, {6, 4}, {6, 0}, {3, 2}, {1, 4}};
        for (int[] e : edges) {
            dag.addEdge(e[0], e[1]);
        }
        System.out.print(dag);
        for (Mode mode : Mode.values()) {
            System.out.println(new TopologicalSort(dag, mode));
        }

        Digraph cyclic = new Digraph(dag);
        cyclic.addEdge(4, 3);            // 3 → 6 → 4 → 3
        System.out.println("加入回边 4->3 后:");
        for (Mode mode : Mode.values()) {
            TopologicalSort ts = new TopologicalSort(cyclic, mode);
            System.out.println("  " + ts);
        }
    }
}
