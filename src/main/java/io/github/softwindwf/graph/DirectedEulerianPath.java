package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 有向图的欧拉路径与欧拉回路:顺着箭头恰好走遍<b>每条弧一次</b>。
 *
 * <p><b>存在条件</b>(忽略孤立顶点):</p>
 * <ul>
 *   <li><b>欧拉回路</b>:每个顶点 <b>入度 = 出度</b>,且所有弧在同一个弱连通块里;</li>
 *   <li><b>欧拉路径</b>:最多一个顶点"出度 − 入度 = 1"(起点),
 *       最多一个顶点"入度 − 出度 = 1"(终点),其余顶点出入度相等,且所有弧弱连通。</li>
 * </ul>
 * <p>与无向图的对照:无向图看<b>度数的奇偶</b>(一个顶点每被"路过"一次消耗 2 度),
 * 有向图看<b>入度与出度的差</b>(每被路过一次消耗一进一出)。</p>
 *
 * <p><b>算法</b>:Hierholzer 拼环法(显式栈,Θ(V+E));弱连通用"沿出边走一遍、沿入边走一遍"检查
 * ({@link Digraph#predecessors(int)} 正好提供反向边)。</p>
 *
 * <p><b>返回值约定</b>与 {@link EulerianPath} 一致:返回顶点序列,回路序列首尾相同,
 * 序列中顶点可重复出现(它描述走法,不是简单路径)。</p>
 *
 * <pre>
 * DirectedEulerianPath euler = new DirectedEulerianPath(digraph);
 * euler.hasEulerianCycle();
 * euler.cycle();     // [0, 1, 2, 0]
 * euler.path();      // 入出度不平衡时的开走法
 * </pre>
 *
 * @see Digraph
 * @see EulerianPath
 * @see <a href="https://algs4.cs.princeton.edu/42digraph">Algorithms, 4th Edition, Section 4.2</a>
 */
public final class DirectedEulerianPath {

    /** 被分析的图 */
    private final Digraph graph;

    /** 是否存在欧拉路径 */
    private final boolean hasPath;

    /** 是否存在欧拉回路 */
    private final boolean hasCycle;

    /** 起点(−1 表示不存在走法) */
    private final int start;

    /** 终点(回路时与起点相同) */
    private final int end;

    /** 走法(顶点序列);不存在为 null */
    private final List<Integer> trail;

    /**
     * 判断并求出一条有向欧拉路径(存在回路时给出回路)。
     *
     * @param graph 有向图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public DirectedEulerianPath(Digraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        this.graph = graph;
        int V = graph.V();

        int startVertex = -1;
        int endVertex = -1;
        int surplus = 0;                                       // 出度比入度多 1 的顶点数
        int deficit = 0;                                       // 入度比出度多 1 的顶点数
        int badlyUnbalanced = 0;                               // |出度 − 入度| ≥ 2 的顶点数(有它在就无解)
        int firstNonIsolated = -1;

        for (int v = 0; v < V; v++) {
            int out = graph.outDegree(v);
            int in = graph.inDegree(v);
            int difference = out - in;
            if (difference == 1) {
                surplus++;
                startVertex = v;
            }
            else if (difference == -1) {
                deficit++;
                endVertex = v;
            }
            else if (difference != 0) {
                badlyUnbalanced++;
            }
            if (out + in > 0 && firstNonIsolated < 0) {
                firstNonIsolated = v;
            }
        }
        boolean connected = firstNonIsolated >= 0 && isWeaklyConnectedAmongNonIsolated(firstNonIsolated);
        boolean degreeConditionsMet = badlyUnbalanced == 0
                && ((surplus == 0 && deficit == 0) || (surplus == 1 && deficit == 1));
        this.hasCycle = degreeConditionsMet && surplus == 0 && deficit == 0 && connected;
        this.hasPath = degreeConditionsMet && connected;

        if (!hasPath) {
            this.start = -1;
            this.end = -1;
            this.trail = null;
            return;
        }
        int startChosen = hasCycle ? firstNonIsolated : startVertex;
        this.start = startChosen;
        this.end = hasCycle ? startChosen : endVertex;
        this.trail = hierholzer(startChosen);
    }

    /** 只用"有弧的顶点"构成的子图是否弱连通(出边走一遍、入边走一遍) */
    private boolean isWeaklyConnectedAmongNonIsolated(int start) {
        int V = graph.V();
        boolean[] visited = new boolean[V];
        Deque<Integer> stack = new ArrayDeque<Integer>();
        visited[start] = true;
        stack.push(start);
        while (!stack.isEmpty()) {
            int v = stack.pop();
            for (int w : graph.adj(v)) {                       // 出边
                if (!visited[w]) {
                    visited[w] = true;
                    stack.push(w);
                }
            }
            for (int w : graph.predecessors(v)) {              // 入边(反向)
                if (!visited[w]) {
                    visited[w] = true;
                    stack.push(w);
                }
            }
        }
        for (int v = 0; v < V; v++) {
            if ((graph.outDegree(v) + graph.inDegree(v)) > 0 && !visited[v]) {
                return false;
            }
        }
        return true;
    }

    /** Hierholzer 拼环法:每个顶点维护一份"剩余出边"的副本,走到无路可走就退栈记录 */
    private List<Integer> hierholzer(int startVertex) {
        int V = graph.V();
        List<Deque<Integer>> remaining = new ArrayList<Deque<Integer>>(V);
        for (int v = 0; v < V; v++) {
            Deque<Integer> out = new ArrayDeque<Integer>();
            for (int w : graph.adj(v)) {
                out.add(w);
            }
            remaining.add(out);
        }
        Deque<Integer> stack = new ArrayDeque<Integer>();
        List<Integer> reversed = new ArrayList<Integer>();
        stack.push(startVertex);
        while (!stack.isEmpty()) {
            int v = stack.peek();
            Deque<Integer> out = remaining.get(v);
            if (out.isEmpty()) {
                reversed.add(stack.pop());
            }
            else {
                stack.push(out.poll());
            }
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
     * @return 弧数
     */
    public int E() {
        return graph.E();
    }

    /**
     * @return 是否存在走遍每条弧一次的走法
     */
    public boolean hasEulerianPath() {
        return hasPath;
    }

    /**
     * @return 是否存在欧拉回路
     */
    public boolean hasEulerianCycle() {
        return hasCycle;
    }

    /**
     * @return 起点;不存在返回 −1
     */
    public int start() {
        return start;
    }

    /**
     * @return 终点(回路时与起点相同);不存在返回 −1
     */
    public int end() {
        return end;
    }

    /**
     * @return 一条开走法(首尾不同);不存在返回 null
     */
    public List<Integer> path() {
        return trail == null || hasCycle ? null : new ArrayList<Integer>(trail);
    }

    /**
     * @return 一条闭合走法(首尾相同);不存在回路返回 null
     */
    public List<Integer> cycle() {
        return trail == null || !hasCycle ? null : new ArrayList<Integer>(trail);
    }

    /**
     * @return 一条走法(不论闭不闭);不存在返回 null
     */
    public List<Integer> trail() {
        return trail == null ? null : new ArrayList<Integer>(trail);
    }

    /**
     * @return 形如 {@code DirectedEulerianPath: 有欧拉回路 0 → … → 0,走法 […]}
     */
    @Override
    public String toString() {
        if (!hasPath) {
            return "DirectedEulerianPath: 无欧拉路径";
        }
        if (hasCycle) {
            return "DirectedEulerianPath: 有欧拉回路 " + start + " → … → " + end + ",走法 " + trail;
        }
        return "DirectedEulerianPath: 有欧拉路径 " + start + " → " + end + ",走法 " + trail;
    }

    /**
     * 演示:一个有向三角形(有回路)、一条有向链(有路径)、一个"入出度不平衡"的无解例子。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        Digraph cycle = new Digraph(3);
        cycle.addEdge(0, 1);
        cycle.addEdge(1, 2);
        cycle.addEdge(2, 0);
        System.out.println("有向三角形: " + new DirectedEulerianPath(cycle));

        Digraph chain = new Digraph(4);
        chain.addEdge(0, 1);
        chain.addEdge(1, 2);
        chain.addEdge(2, 3);
        System.out.println("有向链: " + new DirectedEulerianPath(chain));

        Digraph bad = new Digraph(3);
        bad.addEdge(0, 1);
        bad.addEdge(0, 2);
        bad.addEdge(1, 2);
        System.out.println("0 出度 2 入度 0(差 2): " + new DirectedEulerianPath(bad));

        Digraph tiny = StronglyConnectedComponents.tinyDigraph();
        System.out.println("tinyDG 样例: " + new DirectedEulerianPath(tiny));
    }
}
