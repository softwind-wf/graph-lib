package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 符号图(Symbol Graph):把<b>字符串名字</b>的图映射到"整数编号"的 {@link UndirectedGraph} 上。
 *
 * <p><b>为什么需要它</b>:图算法(DFS/BFS/最短路/连通分量)全部按"顶点编号 0..V-1"设计的,
 * 而真实数据里的顶点是<b>机场代码、演员姓名、网页 URL、课程名</b>这样的字符串。
 * 本类就把这层翻译做好:名字 → 编号({@link #indexOf(String)}),编号 → 名字({@link #nameOf(int)}),
 * 查询邻接点也直接给名字({@link #adjacent(String)})。</p>
 *
 * <p><b>数据结构的分工</b>(与本包一贯的做法一致):本类只负责"名字 ↔ 编号 + 邻接表",
 * 真正的结构与算法交给 {@link #toUndirectedGraph()} 出来的 {@code UndirectedGraph}。
 * 文本读写在 {@link GraphIO}({@code readSymbolGraphFile} 等),本类不解析文件。</p>
 *
 * <p><b>约定</b>:</p>
 * <ul>
 *   <li>名字不能为空、不能重复;每加入一个新名字就分配一个新编号(顺序即编号);</li>
 *   <li>加边时两个名字必须都已经存在(打错名字立即报错;批量读取时由 {@link GraphIO} 负责先登记名字);</li>
 *   <li>允许<b>平行边</b>(同一条边出现多次按重数计)与<b>自环</b>,与 {@link UndirectedGraph} 的口径一致;</li>
 *   <li>不重复的顶点名个数就是 V,边的条数就是 E。</li>
 * </ul>
 *
 * <pre>
 * SymbolGraph sg = GraphIO.readSymbolGraphFile("routes.txt");
 * sg.V();                       // 机场数
 * sg.adjacent("ATL");           // ATL 直飞的城市(名字)
 * sg.toUndirectedGraph();       // 交给算法:最短路、连通分量…
 * sg.nameOf(3);                 // 编号 → 名字
 * </pre>
 *
 * @see UndirectedGraph
 * @see GraphIO
 * @see BreadthFirstTraversal
 */
public final class SymbolGraph {

    /** 名字 → 编号 */
    private final Map<String, Integer> indexOfName;

    /** 编号 → 名字 */
    private final List<String> names;

    /** adjacency[v] = 与 v 相邻的顶点编号(每条边存两份,平行边按重数) */
    private final List<List<Integer>> adjacency;

    /** 边数(平行边按重数计) */
    private int edgeCount;

    /** 建立空的符号图,名字随后逐个加入 */
    public SymbolGraph() {
        this.indexOfName = new LinkedHashMap<String, Integer>();
        this.names = new ArrayList<String>();
        this.adjacency = new ArrayList<List<Integer>>();
        this.edgeCount = 0;
    }

    /**
     * 一次给出全部顶点名(数组顺序即编号顺序)。
     *
     * @param vertices 顶点名,不能为 null、不能有 null 项或重名
     * @throws IllegalArgumentException 参数非法或名字重复
     */
    public SymbolGraph(String[] vertices) {
        this();
        if (vertices == null) {
            throw new IllegalArgumentException("顶点名数组不能为 null");
        }
        for (String name : vertices) {
            addVertex(name);
        }
    }

    // ------------------------------------------------------------------
    // 建模
    // ------------------------------------------------------------------

    /**
     * 加入一个顶点(名字)。
     *
     * @param name 名字,非空且不与已有名字重复
     * @return 该名字的编号
     * @throws IllegalArgumentException 名字为 null/空白或已存在
     */
    public int addVertex(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("顶点名不能为空");
        }
        if (indexOfName.containsKey(name)) {
            throw new IllegalArgumentException("顶点 \"" + name + "\" 已经存在");
        }
        int id = names.size();
        names.add(name);
        indexOfName.put(name, id);
        adjacency.add(new ArrayList<Integer>());
        return id;
    }

    /**
     * 加入一条无向边。两个名字都必须已在图里(平行边与自环允许)。
     *
     * @param a 一个端点名
     * @param b 另一个端点名
     * @throws IllegalArgumentException 名字不存在
     */
    public void addEdge(String a, String b) {
        int v = requireIndex(a);
        int w = requireIndex(b);
        adjacency.get(v).add(w);
        adjacency.get(w).add(v);        // 自环时同一条表加两次,与 UndirectedGraph 一致(度 +2)
        edgeCount++;
    }

    // ------------------------------------------------------------------
    // 名字与编号的互查
    // ------------------------------------------------------------------

    /**
     * @return 顶点数(不重复的名字个数)
     */
    public int V() {
        return names.size();
    }

    /**
     * @return 边数(平行边按重数计)
     */
    public int E() {
        return edgeCount;
    }

    /**
     * @param name 名字
     * @return 编号;不存在返回 -1
     */
    public int indexOf(String name) {
        Integer id = indexOfName.get(name);
        return id == null ? -1 : id;
    }

    /**
     * @param name 名字
     * @return 图里是否有这个顶点
     */
    public boolean contains(String name) {
        return indexOfName.containsKey(name);
    }

    /**
     * @param v 顶点编号
     * @return 对应的名字
     * @throws IllegalArgumentException 编号越界
     */
    public String nameOf(int v) {
        validateVertex(v);
        return names.get(v);
    }

    /**
     * @return 全部顶点名(按编号顺序的副本)
     */
    public List<String> vertices() {
        return new ArrayList<String>(names);
    }

    /**
     * @param name 名字
     * @return 它的编号,不存在则抛出可定位的异常
     */
    private int requireIndex(String name) {
        int id = indexOf(name);
        if (id < 0) {
            throw new IllegalArgumentException("顶点 \"" + name + "\" 不在符号图中");
        }
        return id;
    }

    private void validateVertex(int v) {
        if (v < 0 || v >= names.size()) {
            throw new IllegalArgumentException("顶点 " + v + " 不在 [0, " + (names.size() - 1) + "] 内");
        }
    }

    // ------------------------------------------------------------------
    // 邻接
    // ------------------------------------------------------------------

    /**
     * @param v 顶点编号
     * @return 邻接点编号(按加入顺序,含平行边的重复)
     * @throws IllegalArgumentException 编号越界
     */
    public List<Integer> adj(int v) {
        validateVertex(v);
        return new ArrayList<Integer>(adjacency.get(v));
    }

    /**
     * @param name 名字
     * @return 邻接点名字(按加入顺序)
     * @throws IllegalArgumentException 名字不存在
     */
    public List<String> adjacent(String name) {
        return namesOf(adj(requireIndex(name)));
    }

    /**
     * @param name 名字
     * @return 度数(自环计 2,平行边按重数)
     * @throws IllegalArgumentException 名字不存在
     */
    public int degree(String name) {
        return adj(requireIndex(name)).size();
    }

    /**
     * @param v 顶点编号
     * @return 度数
     * @throws IllegalArgumentException 编号越界
     */
    public int degree(int v) {
        return adj(v).size();
    }

    /**
     * @param a 名字
     * @param b 名字
     * @return 两个顶点之间是否至少有一条边
     * @throws IllegalArgumentException 名字不存在
     */
    public boolean hasEdge(String a, String b) {
        int v = requireIndex(a);
        int w = requireIndex(b);
        return adjacency.get(v).contains(w);
    }

    /**
     * 两个顶点之间边的条数(平行边重数)。
     *
     * @param a 名字
     * @param b 名字
     * @return 条数;没有边返回 0
     * @throws IllegalArgumentException 名字不存在
     */
    public int countEdges(String a, String b) {
        int v = requireIndex(a);
        int w = requireIndex(b);
        int count = 0;
        for (int x : adjacency.get(v)) {
            if (x == w) {
                count++;
            }
        }
        return count;
    }

    /**
     * 遍历全部边,每条边只返回一次(端点用名字表示),平行边按重数、自环只一次。
     *
     * @return {@code String[]{名字1, 名字2}} 的可迭代视图
     */
    public List<String[]> edges() {
        List<String[]> result = new ArrayList<String[]>();
        for (int[] pair : edgePairs()) {
            result.add(new String[]{names.get(pair[0]), names.get(pair[1])});
        }
        return result;
    }

    /** 全部边的编号对,每条边一次(与 {@link UndirectedGraph#edges()} 同口径) */
    private List<int[]> edgePairs() {
        List<int[]> result = new ArrayList<int[]>();
        for (int v = 0; v < names.size(); v++) {
            int selfLoopSeen = 0;
            for (int w : adjacency.get(v)) {
                if (w > v) {
                    result.add(new int[]{v, w});
                }
                else if (w == v) {
                    if (selfLoopSeen % 2 == 0) {
                        result.add(new int[]{v, v});
                    }
                    selfLoopSeen++;
                }
            }
        }
        return result;
    }

    /**
     * @return 最大度数;空图返回 0
     */
    public int maxDegree() {
        int max = 0;
        for (List<Integer> neighbors : adjacency) {
            max = Math.max(max, neighbors.size());
        }
        return max;
    }

    // ------------------------------------------------------------------
    // 交给算法
    // ------------------------------------------------------------------

    /**
     * 取出内核无向图(纯整数编号),可直接交给 DFS / BFS / 连通分量等算法;
     * 结果再用 {@link #nameOf(int)} 翻回名字。
     *
     * @return 与当前顶点、边一致的 {@link UndirectedGraph}
     */
    public UndirectedGraph toUndirectedGraph() {
        UndirectedGraph graph = new UndirectedGraph(names.size());
        for (int[] pair : edgePairs()) {
            graph.addEdge(pair[0], pair[1]);
        }
        return graph;
    }

    private List<String> namesOf(List<Integer> ids) {
        List<String> result = new ArrayList<String>(ids.size());
        for (int id : ids) {
            result.add(names.get(id));
        }
        return result;
    }

    /**
     * @return 形如 {@code 符号图:11 个顶点,14 条边},随后逐行列出一个顶点与它的邻居(名字)
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("符号图:").append(names.size()).append(" 个顶点,").append(edgeCount)
                .append(" 条边").append(newline);
        for (int v = 0; v < names.size(); v++) {
            sb.append("  ").append(names.get(v)).append(": ").append(adjacent(names.get(v)))
                    .append(newline);
        }
        return sb.toString();
    }

    /**
     * 演示:读入真实航线数据(缺省 {@code routes.txt}),打印规模、各机场的直飞城市与度数,
     * 再用 {@link BreadthFirstTraversal} 求两个机场之间的最少中转次数。
     *
     * @param args 可选:符号图数据文件路径(每行一条边,两个名字)
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "routes.txt";
        SymbolGraph sg = GraphIO.readSymbolGraphFile(path);
        System.out.println("数据文件: " + path);
        System.out.println("顶点数 V = " + sg.V() + ",边数 E = " + sg.E()
                + ",最大度数 = " + sg.maxDegree());
        for (String name : sg.vertices()) {
            System.out.println("  " + name + "(" + sg.degree(name) + "): " + sg.adjacent(name));
        }

        UndirectedGraph graph = sg.toUndirectedGraph();
        String from = sg.vertices().get(0);
        String to = sg.vertices().get(sg.V() - 1);
        BreadthFirstTraversal bfs = new BreadthFirstTraversal(graph, sg.indexOf(from));
        if (bfs.hasPathTo(sg.indexOf(to))) {
            List<String> route = new ArrayList<String>();
            for (int v : bfs.pathTo(sg.indexOf(to))) {
                route.add(sg.nameOf(v));
            }
            System.out.println(from + " → " + to + " 最少中转 " + (bfs.distTo(sg.indexOf(to)) - 1)
                    + " 次,路径 " + route);
        }
        else {
            System.out.println(from + " 到 " + to + " 不可达");
        }
    }
}
