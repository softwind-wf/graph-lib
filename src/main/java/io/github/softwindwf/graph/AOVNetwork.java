package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AOV 网(Activity On Vertex Network,顶点表示活动的网)。
 *
 * <p><b>它是什么</b>:用一张<b>有向图</b>描述"活动之间的先后约束" ——
 * <b>顶点 = 活动</b>(任务、课程、工序),<b>有向边 {@code a -&gt; b} = a 必须在 b 之前完成</b>。
 * 例如课程表里"离散数学必须先修高等数学",就是一条 {@code 高等数学 -> 离散数学} 的弧。
 * 再比如工程安排里"地基完成后才能砌墙"。</p>
 *
 * <p><b>两条基本性质</b>:</p>
 * <ol>
 *   <li>AOV 网中<b>不允许出现有向回路</b>:一旦 {@code a -&gt; b -&gt; … -&gt; a},就说明
 *       "a 要等 b、b 又要等 a",这组活动永远无法开始,工程不可行(教科书称其为"死锁");
 *       判定办法正是拓扑排序(见 {@link TopologicalSort})。</li>
 *   <li>若网中无回路,则至少存在一个<b>拓扑序列</b>:把全部活动排成一列,使每条弧的起点都排在终点之前
 *       —— 这就是一个可行的执行顺序。</li>
 * </ol>
 *
 * <p><b>本类与 {@link Digraph} 的分工</b>:活动是<b>有名字</b>的,而图算法只需要整数编号。
 * 本类负责"名字 ↔ 编号"的对应与约束的增删校验,真正的结构交给内核 {@link Digraph}
 * (由 {@link #toDigraph()} 给出),拓扑排序等算法跑在编号上,结果再用
 * {@link #namesOf(int[])} 翻回名字。这与本包"顶点不对象化、索引是身份"的一贯做法一致。</p>
 *
 * <p><b>约束的校验(在建模阶段就把错误挡掉)</b>:</p>
 * <ul>
 *   <li>活动名不能为空、不能重名;</li>
 *   <li>{@link #addPrecedence(String, String)} 的两个活动都必须在网里(打错名字立即报错);</li>
 *   <li>不允许"活动以自己为先修"({@code a -> a}),它本身就是最短的回路;</li>
 *   <li>不允许重复添加同一条约束(前驱关系是集合,重复没有意义)。</li>
 * </ul>
 * <p>注意:<b>长回路不会被这里拦下</b>(那需要真的做一次拓扑排序),由 {@link TopologicalSort} 判定。</p>
 *
 * <pre>
 * AOVNetwork net = new AOVNetwork();
 * net.addActivity("高等数学");
 * net.addActivity("离散数学");
 * net.addPrecedence("高等数学", "离散数学");   // 高等数学必须先修
 * Digraph g = net.toDigraph();
 * int[] order = new TopologicalSort(g).order();
 * net.namesOf(order);                          // 可行执行顺序(名称)
 * </pre>
 *
 * @see Digraph
 * @see TopologicalSort
 * @see <a href="https://algs4.cs.princeton.edu/42digraph">Algorithms, 4th Edition, Section 4.2</a>
 */
public final class AOVNetwork {

    /** 活动名 → 编号 */
    private final Map<String, Integer> indexOfName;

    /** 编号 → 活动名 */
    private final List<String> names;

    /** successors[v] = 必须在 v 之后做的活动(出边) */
    private final List<List<Integer>> successors;

    /** predecessors[v] = 必须在 v 之前完成的活动(入边) */
    private final List<List<Integer>> predecessors;

    /** 约束条数 */
    private int precedenceCount;

    /** 建立空的 AOV 网,活动随后用 {@link #addActivity(String)} 添加 */
    public AOVNetwork() {
        this.names = new ArrayList<String>();
        this.indexOfName = new LinkedHashMap<String, Integer>();
        this.successors = new ArrayList<List<Integer>>();
        this.predecessors = new ArrayList<List<Integer>>();
        this.precedenceCount = 0;
    }

    /**
     * 一次给出全部活动(数组顺序即编号顺序),之后只需添加前驱约束。
     *
     * @param activities 活动名,不能为 null、不能有 null 项或重名
     * @throws IllegalArgumentException 参数非法或名称重复
     */
    public AOVNetwork(String[] activities) {
        this();
        if (activities == null) {
            throw new IllegalArgumentException("活动数组不能为 null");
        }
        for (String name : activities) {
            addActivity(name);
        }
    }

    // ------------------------------------------------------------------
    // 建模:加活动 / 加约束
    // ------------------------------------------------------------------

    /**
     * 添加一个活动。
     *
     * @param name 活动名,非空且不得与已有活动重名
     * @return 该活动的编号(即后续算法使用的顶点号)
     * @throws IllegalArgumentException 名称为 null/空白,或已存在同名活动
     */
    public int addActivity(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("活动名不能为空");
        }
        if (indexOfName.containsKey(name)) {
            throw new IllegalArgumentException("活动 \"" + name + "\" 已经存在");
        }
        int id = names.size();
        names.add(name);
        indexOfName.put(name, id);
        successors.add(new ArrayList<Integer>());
        predecessors.add(new ArrayList<Integer>());
        return id;
    }

    /**
     * 添加一条前驱约束:{@code before} 必须在 {@code after} 之前完成(即一条弧 {@code before -> after})。
     *
     * @param before 先做的活动
     * @param after  后做的活动
     * @throws IllegalArgumentException 活动不存在、两者相同,或该约束已存在
     */
    public void addPrecedence(String before, String after) {
        int from = requireIndex(before);
        int to = requireIndex(after);
        if (from == to) {
            throw new IllegalArgumentException("活动 \"" + before + "\" 不能以自己为先修(自身就是回路)");
        }
        if (successors.get(from).contains(to)) {
            throw new IllegalArgumentException("约束 \"" + before + "\" 先于 \"" + after + "\" 已经存在");
        }
        successors.get(from).add(to);
        predecessors.get(to).add(from);
        precedenceCount++;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * @return 活动个数(即内核有向图的顶点数)
     */
    public int activityCount() {
        return names.size();
    }

    /**
     * @return 前驱约束条数(即内核有向图的边数)
     */
    public int precedenceCount() {
        return precedenceCount;
    }

    /**
     * @return 全部活动名(按编号顺序的副本)
     */
    public List<String> activities() {
        return new ArrayList<String>(names);
    }

    /**
     * @param name 活动名
     * @return 名称 → 编号;不存在返回 -1
     */
    public int indexOf(String name) {
        Integer id = indexOfName.get(name);
        return id == null ? -1 : id;
    }

    /**
     * @param name 活动名
     * @return 网中是否有该活动
     */
    public boolean hasActivity(String name) {
        return indexOfName.containsKey(name);
    }

    /**
     * @param v 活动编号
     * @return 对应的活动名
     * @throws IllegalArgumentException 编号越界
     */
    public String nameOf(int v) {
        validateVertex(v);
        return names.get(v);
    }

    /**
     * @param v 活动编号
     * @return 必须在 v 之后做的活动编号
     * @throws IllegalArgumentException 编号越界
     */
    public List<Integer> successors(int v) {
        validateVertex(v);
        return new ArrayList<Integer>(successors.get(v));
    }

    /**
     * @param v 活动编号
     * @return 必须在 v 之前完成的活动编号
     * @throws IllegalArgumentException 编号越界
     */
    public List<Integer> predecessors(int v) {
        validateVertex(v);
        return new ArrayList<Integer>(predecessors.get(v));
    }

    /**
     * @param name 活动名
     * @return 该活动的后继活动名
     * @throws IllegalArgumentException 活动不存在
     */
    public List<String> successorNames(String name) {
        return namesOfIds(successors(requireIndex(name)));
    }

    /**
     * @param name 活动名
     * @return 该活动的先修活动名
     * @throws IllegalArgumentException 活动不存在
     */
    public List<String> predecessorNames(String name) {
        return namesOfIds(predecessors(requireIndex(name)));
    }

    /**
     * @param v 活动编号
     * @return 出度 = 以 v 为先修的活动个数
     * @throws IllegalArgumentException 编号越界
     */
    public int outDegree(int v) {
        validateVertex(v);
        return successors.get(v).size();
    }

    /**
     * @param v 活动编号
     * @return 入度 = v 的先修活动个数
     * @throws IllegalArgumentException 编号越界
     */
    public int inDegree(int v) {
        validateVertex(v);
        return predecessors.get(v).size();
    }

    /**
     * @param name 活动名
     * @return 该活动的入度(先修个数)
     * @throws IllegalArgumentException 活动不存在
     */
    public int inDegree(String name) {
        return inDegree(requireIndex(name));
    }

    /**
     * @param name 活动名
     * @return 该活动的出度(以它为先修的活动个数)
     * @throws IllegalArgumentException 活动不存在
     */
    public int outDegree(String name) {
        return outDegree(requireIndex(name));
    }

    /**
     * @param before 先做的活动
     * @param after  后做的活动
     * @return 是否已有该约束
     */
    public boolean hasPrecedence(String before, String after) {
        int from = indexOf(before);
        int to = indexOf(after);
        return from >= 0 && to >= 0 && successors.get(from).contains(to);
    }

    /**
     * 把编号序列翻成名称序列(拓扑排序结果的可读输出)。
     *
     * @param order 编号序列
     * @return 名称列表;{@code order} 为 null 时返回 null
     * @throws IllegalArgumentException 序列中含越界编号
     */
    public List<String> namesOf(int[] order) {
        if (order == null) {
            return null;
        }
        List<String> result = new ArrayList<String>(order.length);
        for (int v : order) {
            result.add(nameOf(v));
        }
        return result;
    }

    /**
     * 取出内核有向图(固定 V、纯整数编号)交给算法使用,如
     * {@code new TopologicalSort(net.toDigraph())}。
     *
     * @return 与当前活动/约束一致的有向图
     */
    public Digraph toDigraph() {
        Digraph graph = new Digraph(names.size());
        for (int v = 0; v < names.size(); v++) {
            for (int w : successors.get(v)) {
                graph.addEdge(v, w);
            }
        }
        return graph;
    }

    /**
     * @return 形如 {@code AOV 网:9 个活动,11 个前驱约束},随后逐行给出每个活动的先修与后继
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("AOV 网:").append(names.size()).append(" 个活动,")
                .append(precedenceCount).append(" 个前驱约束").append(newline);
        for (int v = 0; v < names.size(); v++) {
            sb.append("  [").append(v).append("] ").append(names.get(v))
                    .append(": 先修 ").append(namesOfIds(predecessors.get(v)))
                    .append(" -> 后继 ").append(namesOfIds(successors.get(v)))
                    .append(newline);
        }
        return sb.toString();
    }

    /** 名称 → 编号,不存在则抛出可定位的异常 */
    private int requireIndex(String name) {
        int id = indexOf(name);
        if (id < 0) {
            throw new IllegalArgumentException("活动 \"" + name + "\" 不在 AOV 网中");
        }
        return id;
    }

    /** 编号列表 → 名称列表 */
    private List<String> namesOfIds(List<Integer> ids) {
        List<String> result = new ArrayList<String>(ids.size());
        for (int id : ids) {
            result.add(names.get(id));
        }
        return result;
    }

    /** 校验活动编号 */
    private void validateVertex(int v) {
        if (v < 0 || v >= names.size()) {
            throw new IllegalArgumentException("活动编号 " + v + " 不在 [0, " + (names.size() - 1) + "] 内");
        }
    }

    // ------------------------------------------------------------------
    // 演示
    // ------------------------------------------------------------------

    /**
     * 演示:<b>教材《精讲数据结构(Java 语言实现)》p.480 表 9-1 / 图 9-20 的课程 AOV 网</b>
     * (11 门课、11 条先修约束,课程编号 0..10 与图 9-20 完全一致);
     * 命令行给出文件路径时改读文件(如 {@code coursesAOV.txt})。
     * 打印三种拓扑排序给出的可行修读顺序,最后加一条会造成回路的约束演示"工程不可行"。
     *
     * @param args 可选:AOV 网数据文件路径
     */
    public static void main(String[] args) {
        AOVNetwork net = args.length > 0 ? GraphIO.readAovFile(args[0]) : textbookSample();
        System.out.print(net);

        Digraph graph = net.toDigraph();
        for (TopologicalSort.Mode mode : TopologicalSort.Mode.values()) {
            TopologicalSort ts = new TopologicalSort(graph, mode);
            System.out.println(mode + ": 有拓扑序(无回路)= " + ts.isDag());
            if (ts.isDag()) {
                System.out.println("  修读顺序(编号): " + java.util.Arrays.toString(ts.order()));
                System.out.println("  修读顺序(课程): " + net.namesOf(ts.order()));
            }
        }

        System.out.println("--- 反面例子:让「数据结构课程设计」反过来成为「高等数学」的先修 ---");
        net.addPrecedence("数据结构课程设计(Java语言实现)", "高等数学");
        TopologicalSort broken = new TopologicalSort(net.toDigraph());
        System.out.println("  " + broken);
        System.out.println("  回路: " + net.namesOf(broken.cycle()));
    }

    /**
     * 教材 p.480 表 9-1 / 图 9-20 的课程 AOV 网。
     *
     * <p>11 门课、11 条先修约束;活动加入顺序就是图 9-20 的课程编号 0..10:</p>
     * <pre>
     *  0 线性代数            —                         6 编程语言基础          —
     *  1 高等数学            —                         7 Java语言编程          编程语言基础
     *  2 离散数学            高等数学、线性代数           8 Android操作系统       Java语言编程、数据结构
     *  3 数据结构            离散数学                    9 数据结构课程设计      数据结构、Java语言编程
     *  4 算法设计与分析       数据结构                   10 Android应用开发       Android操作系统
     *  5 概率论与数理统计     高等数学
     * </pre>
     */
    private static AOVNetwork textbookSample() {
        AOVNetwork net = new AOVNetwork(new String[]{
            "线性代数", "高等数学", "离散数学", "数据结构", "算法设计与分析", "概率论与数理统计",
            "编程语言基础", "Java语言编程", "Android操作系统",
            "数据结构课程设计(Java语言实现)", "Android应用开发"
        });
        net.addPrecedence("线性代数", "离散数学");
        net.addPrecedence("高等数学", "离散数学");
        net.addPrecedence("离散数学", "数据结构");
        net.addPrecedence("数据结构", "算法设计与分析");
        net.addPrecedence("高等数学", "概率论与数理统计");
        net.addPrecedence("编程语言基础", "Java语言编程");
        net.addPrecedence("Java语言编程", "Android操作系统");
        net.addPrecedence("数据结构", "Android操作系统");
        net.addPrecedence("数据结构", "数据结构课程设计(Java语言实现)");
        net.addPrecedence("Java语言编程", "数据结构课程设计(Java语言实现)");
        net.addPrecedence("Android操作系统", "Android应用开发");
        return net;
    }
}
