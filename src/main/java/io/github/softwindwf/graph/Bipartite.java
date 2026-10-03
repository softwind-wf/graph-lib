package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * 二分图判定(二着色):能否把顶点染成两种颜色,使<b>每条边的两个端点颜色不同</b>?
 *
 * <p><b>判据</b>:从任一点染 0 色出发做 BFS/DFS,邻居一律染相反色;
 * 若某条边的两个端点被迫同色,就不是二分图 —— 那条边和它在生成树上的路径合起来恰好是一个
 * <b>奇环</b>。反之,没有奇环就一定能二着色(这是二分图的等价刻画)。</p>
 *
 * <p><b>与 {@link BipartiteMatching} 的关系</b>:匹配算法要求"输入已经是二分图";
 * 本类负责<b>判定</b>并给出两侧顶点集合(以及冲突时的奇环),两者正好接上。</p>
 *
 * <pre>
 * Bipartite bipartite = new Bipartite(graph);
 * bipartite.isBipartite();
 * bipartite.sideA();       // 0 色一侧的顶点
 * bipartite.sideB();       // 1 色一侧的顶点
 * bipartite.oddCycle();    // 不是二分图时给出的一个奇环
 * </pre>
 *
 * @see UndirectedGraph
 * @see BipartiteMatching
 * @see UndirectedCycle
 * @see <a href="https://algs4.cs.princeton.edu/41graph">Algorithms, 4th Edition, Section 4.1</a>
 */
public final class Bipartite {

    /** 被判定的图 */
    private final UndirectedGraph graph;

    /** color[v] = 0 / 1;−1 表示还没着色 */
    private final int[] color;

    /** 生成树上 v 的父顶点 */
    private final int[] parent;

    /** 生成树根到 v 的距离(用于拼奇环) */
    private final int[] distTo;

    /** 是否是二分图 */
    private final boolean bipartite;

    /** 一个奇环(非二分时给出,否则 null) */
    private final int[] oddCycle;

    /**
     * 判定无向图是否二分,并给出两侧顶点。
     *
     * @param graph 无向图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public Bipartite(UndirectedGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        this.graph = graph;
        int V = graph.V();
        this.color = new int[V];
        this.parent = new int[V];
        this.distTo = new int[V];
        Arrays.fill(color, -1);
        Arrays.fill(parent, -1);

        int[] found = null;
        boolean ok = true;
        for (int start = 0; start < V && ok; start++) {
            if (color[start] != -1) {
                continue;
            }
            color[start] = 0;
            distTo[start] = 0;
            Deque<Integer> queue = new ArrayDeque<Integer>();
            queue.add(start);
            while (!queue.isEmpty() && ok) {
                int v = queue.poll();
                for (int w : graph.adj(v)) {
                    if (w == v) {                          // 自环:自己和自己同色,必然不是二分图
                        ok = false;
                        found = new int[]{v};
                        break;
                    }
                    if (color[w] == -1) {
                        color[w] = 1 - color[v];
                        parent[w] = v;
                        distTo[w] = distTo[v] + 1;
                        queue.add(w);
                    }
                    else if (color[w] == color[v]) {        // 冲突:奇环
                        ok = false;
                        found = buildOddCycle(v, w);
                        break;
                    }
                }
            }
        }
        this.bipartite = ok;
        this.oddCycle = ok ? null : found;
    }

    /**
     * 由冲突的边 {@code v-w} 与生成树上的两条路径拼出奇环:
     * 从 v 与 w 各自沿父指针走到最近的公共祖先,再连上冲突边。
     *
     * @return 奇环顶点序列(首尾相接,不重复首顶点)
     */
    private int[] buildOddCycle(int v, int w) {
        List<Integer> pathFromV = new ArrayList<Integer>();
        List<Integer> pathFromW = new ArrayList<Integer>();
        boolean[] onPathFromV = new boolean[graph.V()];

        int a = v;
        while (a != -1) {
            pathFromV.add(a);
            onPathFromV[a] = true;
            a = parent[a];
        }
        int b = w;
        int ancestor = -1;
        while (b != -1) {
            if (onPathFromV[b]) {
                ancestor = b;
                break;
            }
            pathFromW.add(b);
            b = parent[b];
        }
        if (ancestor == -1) {
            return new int[]{v, w};                        // 兜底:理论上不会发生
        }
        // 环:v →(父链)→ ancestor →(反向的 w 父链)→ w → v
        List<Integer> cycle = new ArrayList<Integer>();
        for (int x : pathFromV) {
            cycle.add(x);
            if (x == ancestor) {
                break;
            }
        }
        java.util.Collections.reverse(pathFromW);
        for (int x : pathFromW) {
            cycle.add(x);
        }
        int[] result = new int[cycle.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = cycle.get(i);
        }
        return result;
    }

    /**
     * @return 顶点数
     */
    public int V() {
        return graph.V();
    }

    /**
     * @return 是否是二分图
     */
    public boolean isBipartite() {
        return bipartite;
    }

    /**
     * @param v 顶点编号
     * @return v 的颜色(0 或 1);未着色(不可能,连通分量都会被遍历到)返回 −1
     * @throws IllegalArgumentException 顶点越界
     */
    public int color(int v) {
        validateVertex(v);
        return color[v];
    }

    /**
     * @param v 顶点编号
     * @return v 是否在 0 色一侧
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean inSideA(int v) {
        return color(v) == 0;
    }

    /**
     * @return 0 色一侧的顶点(升序)
     */
    public List<Integer> sideA() {
        return side(0);
    }

    /**
     * @return 1 色一侧的顶点(升序)
     */
    public List<Integer> sideB() {
        return side(1);
    }

    private List<Integer> side(int wanted) {
        List<Integer> vertices = new ArrayList<Integer>();
        for (int v = 0; v < graph.V(); v++) {
            if (color[v] == wanted) {
                vertices.add(v);
            }
        }
        return vertices;
    }

    /**
     * 一个奇环(证明"不是二分图"的证据)。
     *
     * @return 奇环顶点序列(首尾相接,不重复首顶点);是二分图时返回 {@code null}
     */
    public List<Integer> oddCycle() {
        if (oddCycle == null) {
            return null;
        }
        List<Integer> list = new ArrayList<Integer>(oddCycle.length);
        for (int v : oddCycle) {
            list.add(v);
        }
        return list;
    }

    /**
     * @return 奇环上的边数;是二分图时返回 0
     */
    public int oddCycleLength() {
        return oddCycle == null ? 0 : oddCycle.length;
    }

    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code Bipartite: 是二分图,两侧 4 / 3},随后列出两侧顶点;非二分图则给出奇环
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        if (bipartite) {
            sb.append("Bipartite: 是二分图,两侧 ").append(sideA().size()).append(" / ")
                    .append(sideB().size()).append(newline);
            sb.append("  A = ").append(sideA()).append(newline);
            sb.append("  B = ").append(sideB());
        }
        else {
            sb.append("Bipartite: 不是二分图,奇环 ").append(Arrays.toString(oddCycle))
                    .append("(长度 ").append(oddCycle.length).append(")");
        }
        return sb.toString();
    }

    /**
     * 演示:偶环(二分)与奇环(非二分)各一个例子。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        UndirectedGraph even = new UndirectedGraph(4);
        int[][] cycle = {{0, 1}, {1, 2}, {2, 3}, {3, 0}};
        for (int[] edge : cycle) {
            even.addEdge(edge[0], edge[1]);
        }
        System.out.println("四点环: " + new Bipartite(even));

        UndirectedGraph odd = new UndirectedGraph(3);
        int[][] triangle = {{0, 1}, {1, 2}, {2, 0}};
        for (int[] edge : triangle) {
            odd.addEdge(edge[0], edge[1]);
        }
        System.out.println("三角形: " + new Bipartite(odd));

        UndirectedGraph five = new UndirectedGraph(5);
        for (int v = 0; v < 5; v++) {
            five.addEdge(v, (v + 1) % 5);
        }
        System.out.println("五点环: " + new Bipartite(five));
    }
}
