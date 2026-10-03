package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * 有向环检测:判断有向图里是否存在<b>有向回路</b>,并给出一个具体的环。
 *
 * <p><b>判据(回边)</b>:深度优先遍历时给顶点三种颜色 ——
 * <b>未访问</b>、<b>在栈上(正在被遍历)</b>、<b>已完成</b>。
 * 若从 v 出发遇到一条指向"仍在栈上"的顶点 w 的边 {@code v -&gt; w},
 * 那么 {@code w -&gt; … -&gt; v -&gt; w} 就是一个有向环;指向"已完成"顶点的边则无害
 * (那条路已经走完,不会再回来)。</p>
 *
 * <p><b>两种常见判据的关系</b>:</p>
 * <ul>
 *   <li>{@link TopologicalSort} 用 Kahn 入度法:输出顶点数少于 V ⟺ 有环;</li>
 *   <li>本类用 DFS 回边:遇到回边 ⟺ 有环。</li>
 * </ul>
 * <p>两者结论必然一致(测试里用随机图交叉验证),但本类能<b>顺手给出环的具体顶点</b>
 * —— 沿父指针从回边终点回溯即可。{@link TopologicalSort} 在发现回路时就是调用本类来求环的。</p>
 *
 * <p><b>实现说明</b>:用<b>显式栈</b>的迭代 DFS,不用递归,深图(例如 10 万顶点的链)也不会栈溢出;
 * 代价是要自己保存"每个顶点邻接表读到哪儿了"。</p>
 *
 * <p><b>环的表示约定</b>:{@link #cycle()} 返回 {@code [v1, v2, …, vk]},
 * 含义是 {@code v1→v2→…→vk→v1},<b>首尾相接但不在末尾重复写首顶点</b>
 * (与 {@link FloydWarshall#negativeCycle()}、{@link TopologicalSort#cycle()} 一致)。
 * 自环返回单元素列表 {@code [v]}。</p>
 *
 * <pre>
 * DirectedCycle finder = new DirectedCycle(graph);
 * finder.hasCycle();     // 是否有环
 * finder.cycle();        // [1, 2, 3] 表示 1→2→3→1
 * </pre>
 *
 * @see Digraph
 * @see TopologicalSort
 * @see StronglyConnectedComponents
 * @see <a href="https://algs4.cs.princeton.edu/42digraph">Algorithms, 4th Edition, Section 4.2</a>
 */
public final class DirectedCycle {

    /** 被检测的图 */
    private final Digraph graph;

    /** 一个具体的环(首尾相接,不重复首顶点);无环时为 null */
    private final int[] cycle;

    /**
     * 检测有向图中的环。
     *
     * @param graph 有向图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public DirectedCycle(Digraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        this.graph = graph;
        this.cycle = findCycle();
    }

    /**
     * 迭代 DFS 找环:颜色 0 = 未访问、1 = 在栈上、2 = 已完成;
     * 遇到指向"在栈上"顶点的边,就沿父指针链把环取出来。
     *
     * @return 环的顶点序列;无环返回 null
     */
    private int[] findCycle() {
        int V = graph.V();
        int[] state = new int[V];
        int[] parent = new int[V];
        Arrays.fill(parent, -1);

        for (int start = 0; start < V; start++) {
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
                        parent[w] = top.vertex;
                        state[w] = 1;
                        stack.push(new Frame(w, graph.adj(w).iterator()));
                    }
                    else if (state[w] == 1) {
                        return buildCycle(parent, w, top.vertex);
                    }
                }
                else {
                    state[top.vertex] = 2;
                    stack.pop();
                }
            }
        }
        return null;
    }

    /**
     * 由父指针链构造环:从 {@code from} 沿父指针回溯到 {@code to},
     * 再加上回边 {@code from -> to} 闭合成环,最后反转成"顺着边走"的顺序。
     */
    private static int[] buildCycle(int[] parent, int to, int from) {
        List<Integer> chain = new ArrayList<Integer>();
        for (int x = from; x != -1 && x != to; x = parent[x]) {
            chain.add(x);
        }
        chain.add(to);
        java.util.Collections.reverse(chain);      // [to, ..., from]
        int[] result = new int[chain.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = chain.get(i);
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

    /**
     * @return 是否存在有向环
     */
    public boolean hasCycle() {
        return cycle != null;
    }

    /**
     * 一个具体的有向环。
     *
     * @return {@code [v1, v2, …, vk]} 表示 {@code v1→v2→…→vk→v1};无环返回 {@code null}
     */
    public int[] cycle() {
        return cycle == null ? null : Arrays.copyOf(cycle, cycle.length);
    }

    /**
     * @return 环的顶点序列(列表形式);无环返回 {@code null}
     */
    public List<Integer> cycleList() {
        if (cycle == null) {
            return null;
        }
        List<Integer> list = new ArrayList<Integer>(cycle.length);
        for (int v : cycle) {
            list.add(v);
        }
        return list;
    }

    /**
     * @return 环上的顶点个数(自环为 1);无环返回 0
     */
    public int cycleLength() {
        return cycle == null ? 0 : cycle.length;
    }

    /**
     * @return 形如 {@code DirectedCycle: 有环 [1, 2, 3]} 或 {@code DirectedCycle: 无环}
     */
    @Override
    public String toString() {
        return hasCycle()
                ? "DirectedCycle: 有环 " + Arrays.toString(cycle)
                : "DirectedCycle: 无环";
    }

    /**
     * 演示:先对一个有向无环图检测,再加入一条回边,展示环的提取。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        Digraph dag = new Digraph(6);
        int[][] edges = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {3, 4}, {4, 5}};
        for (int[] e : edges) {
            dag.addEdge(e[0], e[1]);
        }
        System.out.println("DAG: " + new DirectedCycle(dag));

        Digraph cyclic = new Digraph(dag);
        cyclic.addEdge(5, 2);          // 5 → 2 → 3 → 4 → 5
        DirectedCycle finder = new DirectedCycle(cyclic);
        System.out.println("加入回边 5->2 后: " + finder);
        System.out.println("环长 = " + finder.cycleLength() + ",环上顶点 " + finder.cycleList());

        Digraph selfLoop = new Digraph(3);
        selfLoop.addEdge(0, 1);
        selfLoop.addEdge(1, 1);
        System.out.println("自环: " + new DirectedCycle(selfLoop));
    }
}
