package io.github.softwindwf.graph;

import java.util.Arrays;

/**
 * 并查集(union-find / disjoint sets),也叫"不相交集合"数据结构。
 *
 * <p><b>它解决什么问题</b>:动态连通性 —— 一开始每个元素各成一个集合,
 * 不断有"合并两个元素所在集合"的操作,并随时回答"两个元素是否已经连通"。
 * Kruskal 求最小生成树正是靠它<b>判环</b>:"这条边的两个端点已经连通"就说明这条边会成环,
 * 不能要;否则接受它并合并两个集合。</p>
 *
 * <p><b>两个优化(缺一不可,不然会退化成 O(n))</b>:</p>
 * <ol>
 *   <li><b>按大小合并</b>(union by size):把小的树挂到大的树根上,保证树高不超过
 *       {@code log2 n}(于是 {@link #find(int)} 是 O(log n) 上界);</li>
 *   <li><b>路径压缩</b>(path compression):{@link #find(int)} 途中把经过的结点直接挂到根上,
 *       以后的查找一步到位。</li>
 * </ol>
 * <p>两个优化合起来,单次操作的<b>摊还</b>代价是 Θ(α(n))(反阿克曼函数,对任何现实的 n 都不超过 4),
 * 可以当成常数。整棵结构用两个 int 数组表示,Θ(n) 空间。</p>
 *
 * <p><b>为什么不用邻接表 + DFS 判环</b>:那样每次判环都要 O(V + E);
 * 并查集把"是否连通"降到摊还常数,这正是 Kruskal 能到 Θ(E log E)(排序主导)的原因。
 * 换来的限制是<b>只能合并、不能拆分</b> —— 所以并查集不支持删边。</p>
 *
 * <pre>
 * UF uf = new UF(10);
 * uf.union(3, 4);          // 合并 {3} 与 {4}
 * uf.connected(3, 4);      // true
 * uf.count();              // 当前集合个数(初始 10,合并成功一次减一)
 * </pre>
 *
 * @see KruskalMST
 * @see <a href="https://algs4.cs.princeton.edu/15uf">Algorithms, 4th Edition, Section 1.5</a>
 */
public final class UF {

    /** parent[i] = 结点 i 的父结点;根结点的父结点是自己 */
    private final int[] parent;

    /** size[root] = 以 root 为根的集合元素个数(只有根结点的这一项有意义) */
    private final int[] size;

    /** 当前集合个数 */
    private int count;

    /**
     * 建立含 {@code n} 个元素的并查集,初始时每个元素自成一个集合。
     *
     * @param n 元素个数,必须非负
     * @throws IllegalArgumentException {@code n} 为负
     */
    public UF(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("元素个数必须非负,当前为 " + n);
        }
        this.parent = new int[n];
        this.size = new int[n];
        this.count = n;
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            size[i] = 1;
        }
    }

    /**
     * @return 元素个数 n
     */
    public int size() {
        return parent.length;
    }

    /**
     * @return 当前集合个数;初始为 n,每成功合并一次减一;为 1 表示全部连通
     */
    public int count() {
        return count;
    }

    /**
     * 查找元素 {@code p} 所在集合的标识(根结点编号),同时做路径压缩。
     *
     * @param p 元素编号
     * @return 根结点编号
     * @throws IllegalArgumentException {@code p} 越界
     */
    public int find(int p) {
        validate(p);
        int root = p;
        while (root != parent[root]) {
            root = parent[root];
        }
        // 第二趟:把路径上的结点直接挂到根上(路径压缩)
        while (p != root) {
            int next = parent[p];
            parent[p] = root;
            p = next;
        }
        return root;
    }

    /**
     * 判断两个元素是否属于同一集合。
     *
     * @param p 元素编号
     * @param q 元素编号
     * @return 是否连通
     * @throws IllegalArgumentException 参数越界
     */
    public boolean connected(int p, int q) {
        return find(p) == find(q);
    }

    /**
     * 合并 {@code p} 与 {@code q} 所在的两个集合(按大小合并:小树挂到大树上)。
     *
     * @param p 元素编号
     * @param q 元素编号
     * @return 本次调用是否真的发生了合并;两者本来就连通时返回 false
     *         (Kruskal 正是用这个返回值判断"这条边会不会成环")
     * @throws IllegalArgumentException 参数越界
     */
    public boolean union(int p, int q) {
        int rootP = find(p);
        int rootQ = find(q);
        if (rootP == rootQ) {
            return false;
        }
        if (size[rootP] < size[rootQ]) {
            parent[rootP] = rootQ;
            size[rootQ] += size[rootP];
        }
        else {
            parent[rootQ] = rootP;
            size[rootP] += size[rootQ];
        }
        count--;
        return true;
    }

    /**
     * 元素 {@code p} 所在集合的元素个数。
     *
     * @param p 元素编号
     * @return 集合大小
     * @throws IllegalArgumentException {@code p} 越界
     */
    public int componentSize(int p) {
        return size[find(p)];
    }

    /**
     * 元素 {@code p} 到根的步数(根自己是 0)。
     *
     * <p>暴露这个量是为了能验证两个优化真的生效:按大小合并保证最大深度不超过
     * {@code log2 n};调用 {@link #find(int)} 之后路径上的结点深度变成 1(直连根)。</p>
     *
     * @param p 元素编号
     * @return 到根的边数
     * @throws IllegalArgumentException {@code p} 越界
     */
    public int depth(int p) {
        validate(p);
        int steps = 0;
        while (p != parent[p]) {
            p = parent[p];
            steps++;
        }
        return steps;
    }

    /**
     * @return 形如 {@code UF(n=10, 集合数=3)}
     */
    @Override
    public String toString() {
        return "UF(n=" + parent.length + ", 集合数=" + count + ")";
    }

    /** 校验元素编号 */
    private void validate(int p) {
        int n = parent.length;
        if (p < 0 || p >= n) {
            throw new IllegalArgumentException("元素 " + p + " 不在 [0, " + (n - 1) + "] 内");
        }
    }

    /**
     * 演示:10 个元素做若干次合并,打印集合数与若干连通关系。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        UF uf = new UF(10);
        int[][] unions = {{3, 4}, {4, 9}, {8, 0}, {2, 3}, {5, 6}, {5, 9}, {7, 3}, {4, 8}, {5, 6}};
        for (int[] pair : unions) {
            boolean merged = uf.union(pair[0], pair[1]);
            System.out.println("union(" + pair[0] + ", " + pair[1] + ") -> "
                    + (merged ? "合并成功" : "本就连通(会成环)") + "," + uf);
        }
        System.out.println("connected(0, 9) = " + uf.connected(0, 9));
        System.out.println("connected(0, 5) = " + uf.connected(0, 5));
        System.out.println("componentSize(9) = " + uf.componentSize(9));
        int maxDepth = 0;
        for (int i = 0; i < uf.size(); i++) {
            maxDepth = Math.max(maxDepth, uf.depth(i));
        }
        System.out.println("最大深度 = " + maxDepth + "(按大小合并 + 路径压缩下必然很小)");
        System.out.println("各元素的根 = " + Arrays.toString(rootsOf(uf)));
    }

    /** 仅用于演示:逐个 find 之后各元素所属的根(路径压缩已生效) */
    private static int[] rootsOf(UF uf) {
        int[] roots = new int[uf.size()];
        for (int i = 0; i < uf.size(); i++) {
            roots[i] = uf.find(i);
        }
        return roots;
    }
}
