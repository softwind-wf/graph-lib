package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 无向图的欧拉路径与欧拉回路:一条恰好走遍<b>每条边一次</b>的走法。
 *
 * <p><b>存在条件</b>(忽略孤立顶点):</p>
 * <ul>
 *   <li><b>欧拉回路</b>(回到起点):所有顶点的度数都是<b>偶数</b>,且所有边在同一个连通块里;</li>
 *   <li><b>欧拉路径</b>(不必回到起点):恰好有 <b>0 或 2</b> 个奇度顶点
 *       —— 有 2 个时,它们就是起点与终点(0 个时等价于回路)。</li>
 * </ul>
 * <p>直觉:走法"经过"一个中间顶点时,进一次出一次,消耗 2 个度数;
 * 只有起点和终点可以"进而不出"/"出而不进",所以才与奇偶性挂钩。</p>
 *
 * <p><b>算法(Hierholzer 拼环法)</b>:从一个合法起点出发,随意走,
 * 走到无路可走时把当前顶点"退栈"记录到结果前面,再继续 —— 这样每凑出一个环就把它接到已有的走法上。
 * 用显式栈实现,Θ(E)。这就是"贪心地先把小环走完,再拼成大环"的过程。</p>
 *
 * <p><b>返回值约定</b>:{@link #cycle()} 与 {@link #path()} 都返回<b>顶点序列</b>;
 * 回路序列首尾顶点相同(闭合),路径序列首尾不同。序列里顶点可以重复出现 —— 因为它描述的是走法,
 * 不是简单路径;相邻两项之间必有边(平行边按重数消耗)。</p>
 *
 * <pre>
 * EulerianPath euler = new EulerianPath(graph);
 * euler.hasEulerianCycle();
 * euler.cycle();        // [0, 1, 2, 0]
 * euler.path();         // 有奇度顶点时的走法(首尾不同)
 * </pre>
 *
 * @see UndirectedGraph
 * @see DirectedEulerianPath
 * @see ConnectedComponents
 * @see <a href="https://algs4.cs.princeton.edu/41graph">Algorithms, 4th Edition, Section 4.1</a>
 */
public final class EulerianPath {

    /** 被分析的图 */
    private final UndirectedGraph graph;

    /** 是否存在欧拉路径 */
    private final boolean hasPath;

    /** 是否存在欧拉回路 */
    private final boolean hasCycle;

    /** 一个合法的起点(−1 表示不存在走法) */
    private final int start;

    /** 一个合法的终点(回路时等于起点) */
    private final int end;

    /** 计算出的走法(顶点序列);不存在为 null */
    private final List<Integer> trail;

    /**
     * 判断并求出一条欧拉路径(存在回路时给出回路)。
     *
     * @param graph 无向图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public EulerianPath(UndirectedGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        this.graph = graph;
        int V = graph.V();

        int oddCount = 0;
        int firstOdd = -1;
        int firstNonIsolated = -1;
        for (int v = 0; v < V; v++) {
            int degree = graph.degree(v);
            if (degree % 2 != 0) {
                oddCount++;
                if (firstOdd < 0) {
                    firstOdd = v;
                }
            }
            if (degree > 0 && firstNonIsolated < 0) {
                firstNonIsolated = v;
            }
        }
        boolean connected = firstNonIsolated >= 0 && isConnectedAmongNonIsolated(firstNonIsolated);
        this.hasCycle = oddCount == 0 && connected;
        this.hasPath = (oddCount == 0 || oddCount == 2) && connected;

        if (!hasPath) {
            this.start = -1;
            this.end = -1;
            this.trail = null;
            return;
        }
        int startVertex = oddCount == 2 ? firstOdd : firstNonIsolated;
        this.start = startVertex;
        this.end = oddCount == 2 ? otherOddVertex(firstOdd) : startVertex;
        this.trail = hierholzer(startVertex);
    }

    /** 找第二个奇度顶点 */
    private int otherOddVertex(int firstOdd) {
        int found = -1;
        for (int v = 0; v < graph.V(); v++) {
            if (graph.degree(v) % 2 != 0 && v != firstOdd) {
                found = v;
            }
        }
        return found;
    }

    /** 只用"非孤立顶点"构成的子图是否连通 */
    private boolean isConnectedAmongNonIsolated(int start) {
        int V = graph.V();
        boolean[] visited = new boolean[V];
        Deque<Integer> stack = new ArrayDeque<Integer>();
        visited[start] = true;
        stack.push(start);
        while (!stack.isEmpty()) {
            int v = stack.pop();
            for (int w : graph.adj(v)) {
                if (!visited[w]) {
                    visited[w] = true;
                    stack.push(w);
                }
            }
        }
        for (int v = 0; v < V; v++) {
            if (graph.degree(v) > 0 && !visited[v]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Hierholzer:反复"走到无路可走",把顶点倒着插进结果,最后整体反转。
     *
     * <p>为了让平行边与自环都正确消耗,这里为每个顶点维护一份"剩余邻接项"的副本:
     * 走过 {@code u-v} 时消耗 u 处与 v 处各一项;走过自环 {@code v-v} 时消耗 v 处两项。</p>
     */
    private List<Integer> hierholzer(int startVertex) {
        int V = graph.V();
        List<Deque<Integer>> remaining = new ArrayList<Deque<Integer>>(V);
        for (int v = 0; v < V; v++) {
            Deque<Integer> neighbors = new ArrayDeque<Integer>();
            for (int w : graph.adj(v)) {
                neighbors.add(w);
            }
            remaining.add(neighbors);
        }

        Deque<Integer> stack = new ArrayDeque<Integer>();
        List<Integer> reversed = new ArrayList<Integer>();
        stack.push(startVertex);
        while (!stack.isEmpty()) {
            int v = stack.peek();
            Deque<Integer> neighbors = remaining.get(v);
            if (neighbors.isEmpty()) {
                reversed.add(stack.pop());
                continue;
            }
            int w = neighbors.poll();
            if (w == v) {
                neighbors.removeFirstOccurrence(v);        // 自环:本顶点处还要消耗一项
            }
            else {
                remaining.get(w).removeFirstOccurrence(v);
            }
            stack.push(w);
        }
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    /**
     * @return 顶点数
     */
    public int V() {
        return graph.V();
    }

    /**
     * @return 边数
     */
    public int E() {
        return graph.E();
    }

    /**
     * @return 是否存在走遍每条边一次的走法(闭或不闭)
     */
    public boolean hasEulerianPath() {
        return hasPath;
    }

    /**
     * @return 是否存在欧拉回路(闭的走法)
     */
    public boolean hasEulerianCycle() {
        return hasCycle;
    }

    /**
     * @return 走法起点;不存在返回 −1
     */
    public int start() {
        return start;
    }

    /**
     * @return 走法终点(回路时与起点相同);不存在返回 −1
     */
    public int end() {
        return end;
    }

    /**
     * 一条欧拉路径(首尾不同的走法)。
     *
     * @return 顶点序列(长度 = 边数 + 1);不存在返回 null
     */
    public List<Integer> path() {
        return trail == null || hasCycle ? null : new ArrayList<Integer>(trail);
    }

    /**
     * 一条欧拉回路(闭合的走法,序列首尾顶点相同)。
     *
     * @return 顶点序列(长度 = 边数 + 1);不存在回路返回 null
     */
    public List<Integer> cycle() {
        return trail == null || !hasCycle ? null : new ArrayList<Integer>(trail);
    }

    /**
     * 一条走法的顶点序列(不论闭不闭)。
     *
     * @return 顶点序列;不存在返回 null
     */
    public List<Integer> trail() {
        return trail == null ? null : new ArrayList<Integer>(trail);
    }

    /**
     * @return 形如 {@code EulerianPath: 有欧拉回路 0 → … → 0},或 {@code 有欧拉路径 a → … → b},或 {@code 无欧拉路径}
     */
    @Override
    public String toString() {
        if (!hasPath) {
            return "EulerianPath: 无欧拉路径";
        }
        if (hasCycle) {
            return "EulerianPath: 有欧拉回路 " + start + " → … → " + end + ",走法 " + trail;
        }
        return "EulerianPath: 有欧拉路径 " + start + " → " + end + ",走法 " + trail;
    }

    /**
     * 演示:三角形(有回路)、一条链(有开路径)、星形 K1,3(四个奇度顶点 → 无解)、
     * 两个共享顶点的三角形(全偶度 → 有回路)。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        UndirectedGraph triangle = new UndirectedGraph(3);
        triangle.addEdge(0, 1);
        triangle.addEdge(1, 2);
        triangle.addEdge(2, 0);
        System.out.println("三角形: " + new EulerianPath(triangle));

        UndirectedGraph chain = new UndirectedGraph(4);
        chain.addEdge(0, 1);
        chain.addEdge(1, 2);
        chain.addEdge(2, 3);
        System.out.println("链 0-1-2-3: " + new EulerianPath(chain));

        UndirectedGraph star = new UndirectedGraph(4);
        star.addEdge(0, 1);
        star.addEdge(0, 2);
        star.addEdge(0, 3);
        System.out.println("星形 K1,3(度 3,1,1,1 全是奇数): " + new EulerianPath(star));

        UndirectedGraph bowtie = new UndirectedGraph(5);
        bowtie.addEdge(0, 1);
        bowtie.addEdge(1, 2);
        bowtie.addEdge(2, 0);
        bowtie.addEdge(0, 3);
        bowtie.addEdge(3, 4);
        bowtie.addEdge(4, 0);
        System.out.println("两个共享顶点的三角形: " + new EulerianPath(bowtie));
    }
}
