package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 最小生成树测试的公共校验工具(Prim 与 Kruskal 两个测试类共用)。
 *
 * <p><b>独立性声明</b>:这里刻意不复用被测代码的 {@link UF},而是自带一份结构不同、
 * 写法独立的并查集({@link UnionFind},路径减半 + 按秩合并),否则"用 Kruskal 自己验证 Kruskal"
 * 就成了自证。校验用的性质(结构 + 环性质)也都是<b>不依赖任何生成树算法</b>的数学刻画。</p>
 */
final class MstTestSupport {

    /** doubles 求和容差 */
    static final double EPS = 1e-12;

    private MstTestSupport() {
        throw new AssertionError("测试工具类不应该被实例化");
    }

    /** 边的规范字符串(端点归一 + 权值),用于逐条比对 */
    static List<String> edgeStrings(Iterable<Edge> edges) {
        List<String> list = new ArrayList<String>();
        for (Edge e : edges) {
            list.add(key(e));
        }
        return list;
    }

    /** 边的规范字符串:{@code "4-5 0.35"} */
    static String key(Edge e) {
        int v = e.either();
        int w = e.other(v);
        return Math.min(v, w) + "-" + Math.max(v, w) + " " + e.weight();
    }

    /** 排序后的边字符串(忽略输出顺序的比对) */
    static List<String> sortedEdgeStrings(Iterable<Edge> edges) {
        List<String> list = edgeStrings(edges);
        Collections.sort(list);
        return list;
    }

    /**
     * 结构验证:每条树边确实属于原图;无自环;边数 = V − 分量数;
     * 树边形成的连通关系与原图<b>逐顶点对</b>一致;权值求和自洽。
     *
     * @param g              原图
     * @param tree           生成森林的边
     * @param componentCount 算法报告的分量数
     * @param weight         算法报告的总权值
     */
    static void assertValidForest(EdgeWeightedGraph g, List<Edge> tree, int componentCount, double weight) {
        int V = g.V();

        // 1) 每条树边都是原图的边(按值多重集扣减)
        List<Edge> graphEdges = new ArrayList<Edge>();
        for (Edge e : g.edges()) {
            graphEdges.add(e);
        }
        for (Edge e : tree) {
            assertTrue(graphEdges.remove(e), "生成树里出现了不属于原图的边: " + e);
        }

        // 2) 无自环、边数正确
        for (Edge e : tree) {
            assertTrue(e.either() != e.other(e.either()), "生成树不应含自环");
        }
        assertEquals(V - componentCount, tree.size(), "边数应等于 V − 分量数");

        // 3) 连通关系与原图一致(并查集:逐顶点对比较)
        UnionFind graphUf = new UnionFind(V);
        for (Edge e : g.edges()) {
            graphUf.union(e.either(), e.other(e.either()));
        }
        UnionFind treeUf = new UnionFind(V);
        for (Edge e : tree) {
            treeUf.union(e.either(), e.other(e.either()));
        }
        assertEquals(rootCount(graphUf), componentCount, "分量数应与并查集统计一致");
        for (int v = 0; v < V; v++) {
            for (int w = v + 1; w < V; w++) {
                assertEquals(graphUf.find(v) == graphUf.find(w), treeUf.find(v) == treeUf.find(w),
                        "顶点 " + v + " 与 " + w + " 的连通关系与图不一致");
            }
        }

        // 4) 权值求和自洽
        double sum = 0.0;
        for (Edge e : tree) {
            sum += e.weight();
        }
        assertEquals(sum, weight, EPS, "weight() 应等于边权之和");
    }

    /**
     * 环性质(生成树最小性的充要条件):对每条非树边 f,f 与树上两端点之间的路径构成一个环,
     * f 必须是该环上最重的边之一 —— 即树路径上的最大边权 ≤ f 的权值。
     * 若存在反例,把那条更重的树边换成 f 会得到更小的生成树。
     */
    static void assertCycleProperty(EdgeWeightedGraph g, List<Edge> tree) {
        int V = g.V();
        List<List<Edge>> treeAdj = new ArrayList<List<Edge>>();
        for (int v = 0; v < V; v++) {
            treeAdj.add(new ArrayList<Edge>());
        }
        for (Edge e : tree) {
            int v = e.either();
            int w = e.other(v);
            treeAdj.get(v).add(e);
            treeAdj.get(w).add(e);
        }

        List<Edge> unclaimedTreeEdges = new ArrayList<Edge>(tree);
        int claimed = 0;
        int checked = 0;
        for (Edge f : g.edges()) {
            if (unclaimedTreeEdges.remove(f)) {
                claimed++;
                continue;
            }
            int s = f.either();
            int t = f.other(s);
            if (s == t) {
                checked++;                      // 自环自身成环,性质平凡成立
                continue;
            }
            double maxOnPath = maxWeightOnTreePath(treeAdj, s, t);
            assertTrue(maxOnPath <= f.weight() + EPS,
                    "环性质被破坏:非树边 " + f + " 比树路径上的最大边(" + maxOnPath + ")还轻");
            checked++;
        }
        assertEquals(tree.size(), claimed, "生成树的每条边都应是原图的边");
        assertEquals(g.E(), checked + claimed, "树边 + 非树边应恰好覆盖全部边");
    }

    /** BFS 找出树中 s→t 的路径,返回路径上的最大边权 */
    static double maxWeightOnTreePath(List<List<Edge>> treeAdj, int s, int t) {
        boolean[] seen = new boolean[treeAdj.size()];
        Edge[] parentEdge = new Edge[treeAdj.size()];
        Deque<Integer> queue = new ArrayDeque<Integer>();
        seen[s] = true;
        queue.add(s);
        while (!queue.isEmpty()) {
            int v = queue.poll();
            if (v == t) {
                break;
            }
            for (Edge e : treeAdj.get(v)) {
                int w = e.other(v);
                if (!seen[w]) {
                    seen[w] = true;
                    parentEdge[w] = e;
                    queue.add(w);
                }
            }
        }
        double max = Double.NEGATIVE_INFINITY;
        for (int x = t; x != s; ) {
            Edge e = parentEdge[x];
            assertTrue(e != null, "树中应存在 " + s + " 到 " + t + " 的路径");
            max = Math.max(max, e.weight());
            x = e.other(x);
        }
        return max;
    }

    /** 集合(根)个数 */
    static int rootCount(UnionFind uf) {
        Set<Integer> roots = new HashSet<Integer>();
        for (int v = 0; v < uf.size(); v++) {
            roots.add(uf.find(v));
        }
        return roots.size();
    }

    /** 生成一张随机加权图,所有边权互不相同(便于"不同算法边集逐条相同"的断言) */
    static EdgeWeightedGraph randomGraphWithDistinctWeights(java.util.Random rnd, int V, int edgeCount) {
        return GraphGenerator.edgeWeightedDistinctWeights(rnd, V, edgeCount);
    }

    /** 取一个尚未用过的权值(千分之一精度,互异) */
    static double distinctWeight(java.util.Random rnd, Set<Integer> usedKeys) {
        int key;
        do {
            key = rnd.nextInt(1_000_000);
        }
        while (!usedKeys.add(key));
        return key / 1000.0;
    }

    /**
     * 测试自带的并查集(与被测的 {@link UF} 实现方式不同:按秩合并 + 路径减半),
     * 仅用于独立校验"树边形成的连通关系"。
     */
    static final class UnionFind {
        private final int[] parent;
        private final byte[] rank;

        UnionFind(int n) {
            parent = new int[n];
            rank = new byte[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
        }

        int size() {
            return parent.length;
        }

        int find(int x) {
            while (x != parent[x]) {
                parent[x] = parent[parent[x]];   // 路径减半
                x = parent[x];
            }
            return x;
        }

        boolean union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return false;
            }
            if (rank[ra] < rank[rb]) {
                parent[ra] = rb;
            }
            else if (rank[ra] > rank[rb]) {
                parent[rb] = ra;
            }
            else {
                parent[rb] = ra;
                rank[ra]++;
            }
            return true;
        }
    }
}
