package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * 无向图的连通分量(Connected Components):把顶点分成若干组,<b>组内任意两点都有路径相连</b>,
 * 组间完全没有边。Θ(V+E)。
 *
 * <p><b>与 {@link UF}(并查集)的分工</b>:</p>
 * <ul>
 *   <li>本类是<b>一次性把整张图分好组</b>:一遍 DFS 就能给每个顶点标上分量编号,
 *       之后"同不同分量"是 Θ(1) 的数组查询 —— 适合静态图;</li>
 *   <li>{@link UF} 适合<b>边一条条到来</b>的场合(动态连边),代价是每次操作带反阿克曼函数的开销。</li>
 * </ul>
 * <p>两者结论必然一致(测试里逐顶点交叉验证),但适用的场景不同。</p>
 *
 * <p><b>实现说明</b>:迭代式 DFS(显式栈),深图不栈溢出;自环不影响分量,
 * 平行边也只当一条。</p>
 *
 * <pre>
 * ConnectedComponents cc = new ConnectedComponents(graph);
 * cc.count();                  // 分量个数
 * cc.id(3) == cc.id(5);        // 3 与 5 是否连通
 * cc.component(cc.id(3));      // 该分量的全部顶点
 * cc.components();             // 全部分量(规范化形式,便于与其它实现对照)
 * </pre>
 *
 * @see UndirectedGraph
 * @see UF
 * @see DepthFirstTraversal
 * @see StronglyConnectedComponents
 * @see <a href="https://algs4.cs.princeton.edu/41graph">Algorithms, 4th Edition, Section 4.1</a>
 */
public final class ConnectedComponents {

    /** 被分析的图 */
    private final UndirectedGraph graph;

    /** id[v] = 顶点 v 所属分量编号 */
    private final int[] id;

    /** 分量个数 */
    private final int count;

    /** 各分量的顶点数 */
    private final int[] sizes;

    /**
     * 求连通分量。
     *
     * @param graph 无向图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public ConnectedComponents(UndirectedGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        this.graph = graph;
        int V = graph.V();
        this.id = new int[V];
        java.util.Arrays.fill(id, -1);

        int components = 0;
        for (int start = 0; start < V; start++) {
            if (id[start] != -1) {
                continue;
            }
            markComponent(start, components);
            components++;
        }
        this.count = components;
        this.sizes = new int[count];
        for (int v = 0; v < V; v++) {
            sizes[id[v]]++;
        }
    }

    /** 从 start 出发的一趟迭代 DFS,把同一分量的顶点都标上 component */
    private void markComponent(int start, int component) {
        Deque<Frame> stack = new ArrayDeque<Frame>();
        id[start] = component;
        stack.push(new Frame(start, graph.adj(start).iterator()));
        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            if (top.neighbors.hasNext()) {
                int w = top.neighbors.next();
                if (id[w] == -1) {
                    id[w] = component;
                    stack.push(new Frame(w, graph.adj(w).iterator()));
                }
            }
            else {
                stack.pop();
            }
        }
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
     * @return 分量个数(空图为 0)
     */
    public int count() {
        return count;
    }

    /**
     * @param v 顶点编号
     * @return v 所属分量编号
     * @throws IllegalArgumentException 顶点越界
     */
    public int id(int v) {
        validateVertex(v);
        return id[v];
    }

    /**
     * @param component 分量编号
     * @return 该分量的顶点个数
     * @throws IllegalArgumentException 分量编号越界
     */
    public int size(int component) {
        validateComponent(component);
        return sizes[component];
    }

    /**
     * @param v 顶点编号
     * @param w 顶点编号
     * @return v 与 w 是否连通
     * @throws IllegalArgumentException 顶点越界
     */
    public boolean connected(int v, int w) {
        validateVertex(v);
        validateVertex(w);
        return id[v] == id[w];
    }

    /**
     * @param component 分量编号
     * @return 该分量的全部顶点(升序)
     * @throws IllegalArgumentException 分量编号越界
     */
    public List<Integer> component(int component) {
        validateComponent(component);
        List<Integer> vertices = new ArrayList<Integer>(sizes[component]);
        for (int v = 0; v < graph.V(); v++) {
            if (id[v] == component) {
                vertices.add(v);
            }
        }
        return vertices;
    }

    /**
     * 全部分量:每个分量内顶点升序,分量之间按"最小顶点"升序 —— 这个规范化形式与算法无关。
     *
     * @return 分量的列表
     */
    public List<List<Integer>> components() {
        List<List<Integer>> result = new ArrayList<List<Integer>>();
        for (int c = 0; c < count; c++) {
            result.add(component(c));
        }
        Collections.sort(result, new java.util.Comparator<List<Integer>>() {
            public int compare(List<Integer> a, List<Integer> b) {
                return Integer.compare(a.get(0), b.get(0));
            }
        });
        return result;
    }

    /**
     * @return 最大分量的顶点个数;空图返回 0
     */
    public int largestComponentSize() {
        int max = 0;
        for (int size : sizes) {
            max = Math.max(max, size);
        }
        return max;
    }

    /**
     * @return 整张图是否连通(只有一个分量);空图返回 false
     */
    public boolean isConnected() {
        return count == 1;
    }

    private void validateVertex(int v) {
        if (v < 0 || v >= graph.V()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (graph.V() - 1) + "] 内");
        }
    }

    private void validateComponent(int component) {
        if (component < 0 || component >= count) {
            throw new IllegalArgumentException("分量编号 " + component + " 不在 [0, " + (count - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code ConnectedComponents: V=13, 分量数=3, 最大分量=7},随后逐行列出一个分量
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("ConnectedComponents: V=").append(graph.V()).append(", 分量数=").append(count)
                .append(", 最大分量=").append(largestComponentSize()).append(newline);
        for (List<Integer> vertices : components()) {
            sb.append("  ").append(vertices).append(newline);
        }
        return sb.toString();
    }

    /**
     * 演示:读入 tinyG.txt 或按参数给出的文件,打印分量结构,并与 {@link UF} 的结论对照。
     *
     * @param args 可选:无向图数据文件路径
     */
    public static void main(String[] args) {
        UndirectedGraph graph = args.length > 0
                ? GraphIO.readFile(args[0])
                : GraphIO.readFile("tinyG.txt");
        ConnectedComponents cc = new ConnectedComponents(graph);
        System.out.println(cc);

        UF uf = new UF(graph.V());
        for (int[] edge : graph.edges()) {
            uf.union(edge[0], edge[1]);
        }
        System.out.println("对照并查集:分量数 = " + uf.count()
                + ",两种方法结论一致: " + (uf.count() == cc.count()));
    }
}
