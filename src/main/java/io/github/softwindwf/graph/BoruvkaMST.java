package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Borůvka 算法:最小生成树的第三种做法(另两种是 {@link PrimMST} 与 {@link KruskalMST})。
 *
 * <p><b>核心想法(每一轮让每个连通块各挑一条最便宜的出边)</b>:</p>
 * <ol>
 *   <li>初始时每个顶点自成一个连通块;</li>
 *   <li>一轮里,<b>每个连通块</b>都从自己出发找一条<b>权值最小的出边</b>(只找一条);</li>
 *   <li>把这些挑选出来的边全部加入(用并查集合并),注意一条边可能被两端的块同时挑中,去重即可;</li>
 *   <li>每轮连通块数至少减半,所以最多 Θ(log V) 轮,总时间 Θ(E·log V)。</li>
 * </ol>
 *
 * <p><b>为什么每轮至少减半</b>:每个块都挑了出边并合并,块数从 k 降到至多 ⌈k/2⌉
 * —— 这就是它 Θ(E log V) 的来源,也是它能<b>并行</b>的原因(每个块挑边互不干扰)。
 * 在边的权值大量相同、或要上多核/分布式时, Borůvka 常常是首选。</p>
 *
 * <p><b>三种 MST 算法的分工</b>:</p>
 * <table border="1" summary="三种最小生成树算法">
 *   <tr><th>算法</th><th>时间</th><th>适用</th></tr>
 *   <tr><td>{@link PrimMST}</td><td>Θ(E log V)(懒/稠密两模式)</td><td>稠密图</td></tr>
 *   <tr><td>{@link KruskalMST}</td><td>Θ(E log E)</td><td>稀疏图,实现最简单</td></tr>
 *   <tr><td><b>本类</b></td><td>Θ(E log V)(每轮线性扫边)</td><td>可并行 / 边权大量相等</td></tr>
 * </table>
 * <p>三者给出的<b>总权值必然相同</b>(最小生成树权值唯一);边集合在权值互不相同时也相同
 * (测试里用"互异权值"的随机图做交叉验证)。</p>
 *
 * <pre>
 * BoruvkaMST mst = new BoruvkaMST(GraphIO.readWeightedFile("tinyEWG.txt"));
 * mst.weight();          // 1.81
 * mst.edges();          // 最小生成树的边(按权值升序)
 * mst.componentCount();  // 连不连通(1 表示连通)
 * </pre>
 *
 * @see PrimMST
 * @see KruskalMST
 * @see EdgeWeightedGraph
 * @see UF
 * @see <a href="https://algs4.cs.princeton.edu/43mst">Algorithms, 4th Edition, Section 4.3</a>
 */
public final class BoruvkaMST {

    /** 被求解的图 */
    private final EdgeWeightedGraph graph;

    /** 选中的边(最小生成树,按权值升序) */
    private final List<Edge> edges;

    /** 最小生成树总权值 */
    private final double weight;

    /** 连通块个数(1 表示图连通;大于 1 说明是"最小生成森林") */
    private final int componentCount;

    /** 一共跑了几轮 */
    private final int roundCount;

    /**
     * 求最小生成树(最小生成森林)。
     *
     * @param graph 加权无向图,不能为 null
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public BoruvkaMST(EdgeWeightedGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("图不能为 null");
        }
        this.graph = graph;
        int V = graph.V();
        UF uf = new UF(V);
        List<Edge> chosen = new ArrayList<Edge>();
        double total = 0.0;
        int rounds = 0;

        while (uf.count() > 1) {
            rounds++;
            // cheapest[v] = 第 v 个连通块目前看到的最便宜出边
            Edge[] cheapest = new Edge[V];
            for (Edge edge : graph.edges()) {
                int v = edge.either();
                int w = edge.other(v);
                int rootV = uf.find(v);
                int rootW = uf.find(w);
                if (rootV == rootW) {
                    continue;                              // 已经在同一个块里,跳过
                }
                if (cheapest[rootV] == null || edge.weight() < cheapest[rootV].weight()) {
                    cheapest[rootV] = edge;
                }
                if (cheapest[rootW] == null || edge.weight() < cheapest[rootW].weight()) {
                    cheapest[rootW] = edge;
                }
            }
            boolean merged = false;
            for (int v = 0; v < V; v++) {
                Edge edge = cheapest[v];
                if (edge == null) {
                    continue;
                }
                int a = edge.either();
                int b = edge.other(a);
                if (uf.find(a) != uf.find(b)) {
                    uf.union(a, b);
                    chosen.add(edge);
                    total += edge.weight();
                    merged = true;
                }
            }
            if (!merged) {
                break;                                     // 剩下的块之间没有边(图不连通)
            }
        }

        Collections.sort(chosen, new java.util.Comparator<Edge>() {
            public int compare(Edge a, Edge b) {
                return Double.compare(a.weight(), b.weight());
            }
        });
        this.edges = Collections.unmodifiableList(chosen);
        this.weight = total;
        this.componentCount = uf.count();
        this.roundCount = rounds;
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
     * @return 最小生成树(森林)的边,按权值升序;边数 = V − 连通块数
     */
    public List<Edge> edges() {
        return new ArrayList<Edge>(edges);
    }

    /**
     * @return 总权值
     */
    public double weight() {
        return weight;
    }

    /**
     * @return 连通块个数(1 表示图连通)
     */
    public int componentCount() {
        return componentCount;
    }

    /**
     * @return 图是否连通
     */
    public boolean isConnected() {
        return componentCount == 1;
    }

    /**
     * @return 一共跑了几轮(每轮块数至少减半,所以不超过 Θ(log V))
     */
    public int roundCount() {
        return roundCount;
    }

    /**
     * @return 形如 {@code BoruvkaMST: 7 条边,总权值 1.81,连通块 1 个,跑了 3 轮},随后列出边
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("BoruvkaMST: ").append(edges.size()).append(" 条边,总权值 ")
                .append(String.format("%.2f", weight)).append(",连通块 ")
                .append(componentCount).append(" 个,跑了 ").append(roundCount).append(" 轮")
                .append(newline);
        for (Edge edge : edges) {
            sb.append("  ").append(edge).append(newline);
        }
        return sb.toString();
    }

    /**
     * 演示:对 tinyEWG.txt 求最小生成树,并与 Prim、Kruskal 的权值对照。
     *
     * @param args 可选:加权图数据文件路径
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "tinyEWG.txt";
        EdgeWeightedGraph graph = GraphIO.readWeightedFile(path);
        BoruvkaMST boruvka = new BoruvkaMST(graph);
        System.out.println("数据文件: " + path);
        System.out.println(boruvka);
        System.out.println("对照 Prim 总权值 = " + new PrimMST(graph).weight()
                + ",Kruskal 总权值 = " + new KruskalMST(graph).weight()
                + ",三者一致: " + (Math.abs(boruvka.weight() - new PrimMST(graph).weight()) < 1e-9
                && Math.abs(boruvka.weight() - new KruskalMST(graph).weight()) < 1e-9));
    }
}
