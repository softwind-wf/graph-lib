package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * 无向图环检测:判断无向图里是否存在<b>环</b>,并给出一个具体的环。
 *
 * <p><b>与 {@link DirectedCycle} 的区别</b>:有向图里"环"必须是顺着箭头能绕回来;
 * 无向图的边没有方向,所以只要存在一条"不重复边的闭合路径"就算环 ——
 * 判据是 DFS 时遇到<b>已访问且不是父顶点</b>的邻居(回边)。</p>
 *
 * <p><b>两个容易踩坑的细节(本类专门处理)</b>:</p>
 * <ul>
 *   <li><b>自环</b>是一个长度为 1 的环,{@link #cycle()} 返回 {@code [v]};</li>
 *   <li><b>平行边</b>(同一对顶点之间有两条边)也构成环。只按"邻居是不是父顶点"来判会漏掉它,
 *       所以本类记录"来时的树边"并且只跳过<b>那一条</b>;同一对顶点之间的第二条边就判为环,
 *       返回 {@code [u, v]}。</li>
 * </ul>
 *
 * <p><b>实现说明</b>:迭代式 DFS(显式栈)。无向图的 DFS 不会出现"横叉边"
 * (每条边要么是树边、要么指向祖先),所以遇到回边时沿父指针回溯即可取到环。</p>
 *
 * <pre>
 * UndirectedCycle finder = new UndirectedCycle(graph);
 * finder.hasCycle();
 * finder.cycle();        // [1, 2, 3] 表示 1-2-3-1(首尾相接,不重复写首顶点)
 * </pre>
 *
 * @see UndirectedGraph
 * @see DirectedCycle
 * @see ConnectedComponents
 * @see <a href="https://algs4.cs.princeton.edu/41graph">Algorithms, 4th Edition, Section 4.1</a>
 */
public final class UndirectedCycle {

    /** 被检测的图 */
    private final UndirectedGraph graph;

    /** 一个具体的环(首尾相接,不重复首顶点);无环时为 null */
    private final int[] cycle;

    /**
     * 检测无向图中的环。
     *
     * @param graph 无向图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public UndirectedCycle(UndirectedGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        this.graph = graph;
        this.cycle = findCycle();
    }

    /**
     * 迭代 DFS 找环。
     *
     * <p>每个栈帧记住"来时的那条边通向哪个顶点,以及还剩几条这样的邻接项要跳过":
     * 跳过一条是正常的树边,再遇到同一条就说明是平行边(环);
     * 遇到已访问且非父顶点的邻居则是回边(环),沿父指针回溯取环。</p>
     *
     * @return 环的顶点序列;无环返回 null
     */
    private int[] findCycle() {
        int V = graph.V();
        boolean[] marked = new boolean[V];
        int[] parent = new int[V];
        Arrays.fill(parent, -1);

        for (int start = 0; start < V; start++) {
            if (marked[start]) {
                continue;
            }
            marked[start] = true;
            Deque<Frame> stack = new ArrayDeque<Frame>();
            stack.push(new Frame(start, graph.adj(start).iterator(), -1, 0));

            while (!stack.isEmpty()) {
                Frame top = stack.peek();
                if (!top.neighbors.hasNext()) {
                    stack.pop();
                    continue;
                }
                int w = top.neighbors.next();
                if (w == top.vertex) {
                    return new int[]{w};                   // 自环
                }
                if (!marked[w]) {
                    marked[w] = true;
                    parent[w] = top.vertex;
                    stack.push(new Frame(w, graph.adj(w).iterator(), top.vertex, 1));
                    continue;
                }
                if (w == top.parentVertex) {
                    if (top.parentSkipsRemaining > 0) {
                        top.parentSkipsRemaining--;        // 这条就是来时的那条树边
                        continue;
                    }
                    return buildCycle(parent, w, top.vertex);   // 平行边
                }
                int[] found = buildCycle(parent, w, top.vertex);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 由父指针链构造环:从 {@code from} 沿父指针回溯到祖先 {@code to},
     * 再加上回边 {@code from-to} 闭合成环,最后反转成"顺着边走"的顺序。
     *
     * @return 环;若 {@code to} 不在 {@code from} 的祖先链上(理论上不会发生)返回 null
     */
    private static int[] buildCycle(int[] parent, int to, int from) {
        List<Integer> chain = new ArrayList<Integer>();
        int current = from;
        int guard = parent.length + 1;
        while (current != -1 && current != to && guard-- > 0) {
            chain.add(current);
            current = parent[current];
        }
        if (current != to) {
            return null;                                   // 兜底:链上没有 to,这次不当作环
        }
        chain.add(to);
        java.util.Collections.reverse(chain);              // [to, ..., from]
        int[] result = new int[chain.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = chain.get(i);
        }
        return result;
    }

    /** 一个"暂停中的递归调用":顶点 + 邻接表位置 + 父顶点 + 还要跳过几条"回父顶点"的邻接项 */
    private static final class Frame {
        final int vertex;
        final Iterator<Integer> neighbors;
        final int parentVertex;
        int parentSkipsRemaining;

        Frame(int vertex, Iterator<Integer> neighbors, int parentVertex, int parentSkipsRemaining) {
            this.vertex = vertex;
            this.neighbors = neighbors;
            this.parentVertex = parentVertex;
            this.parentSkipsRemaining = parentSkipsRemaining;
        }
    }

    /**
     * @return 是否存在环
     */
    public boolean hasCycle() {
        return cycle != null;
    }

    /**
     * 一个具体的环。
     *
     * @return {@code [v1, v2, …, vk]} 表示 {@code v1-v2-…-vk-v1};
     *         自环为 {@code [v]},平行边为 {@code [u, v]};无环返回 {@code null}
     */
    public List<Integer> cycle() {
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
     * 环上的边({@code {a, b}} 对)。
     *
     * @return 边序列;无环返回 {@code null}
     */
    public List<int[]> cycleEdges() {
        if (cycle == null) {
            return null;
        }
        List<int[]> edges = new ArrayList<int[]>(cycle.length);
        for (int i = 0; i < cycle.length; i++) {
            edges.add(new int[]{cycle[i], cycle[(i + 1) % cycle.length]});
        }
        return edges;
    }

    /**
     * @return 环上的顶点个数(自环为 1);无环返回 0
     */
    public int cycleLength() {
        return cycle == null ? 0 : cycle.length;
    }

    /**
     * @return 形如 {@code UndirectedCycle: 有环 [1, 2, 3]} 或 {@code UndirectedCycle: 无环}
     */
    @Override
    public String toString() {
        return hasCycle()
                ? "UndirectedCycle: 有环 " + Arrays.toString(cycle)
                : "UndirectedCycle: 无环";
    }

    /**
     * 演示:一棵树(无环)加上一条边(有环),再展示自环与平行边这两个特例。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        UndirectedGraph tree = new UndirectedGraph(6);
        int[][] edges = {{0, 1}, {0, 2}, {1, 3}, {1, 4}, {2, 5}};
        for (int[] edge : edges) {
            tree.addEdge(edge[0], edge[1]);
        }
        System.out.println("树: " + new UndirectedCycle(tree));

        tree.addEdge(4, 5);                                // 4-1-0-2-5-4
        UndirectedCycle finder = new UndirectedCycle(tree);
        System.out.println("加一条边后: " + finder);
        System.out.println("环长 = " + finder.cycleLength() + ",环上边 " + edgeText(finder.cycleEdges()));

        UndirectedGraph selfLoop = new UndirectedGraph(3);
        selfLoop.addEdge(0, 1);
        selfLoop.addEdge(1, 1);
        System.out.println("自环: " + new UndirectedCycle(selfLoop));

        UndirectedGraph parallel = new UndirectedGraph(3);
        parallel.addEdge(0, 1);
        parallel.addEdge(0, 1);
        System.out.println("平行边: " + new UndirectedCycle(parallel));
    }

    private static String edgeText(List<int[]> edges) {
        StringBuilder sb = new StringBuilder();
        for (int[] edge : edges) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(edge[0]).append('-').append(edge[1]);
        }
        return sb.toString();
    }
}
