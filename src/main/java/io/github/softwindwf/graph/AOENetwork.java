package io.github.softwindwf.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AOE 网(Activity On Edge Network,边表示活动的网):用<b>带权有向无环图</b>描述工程进度。
 *
 * <p><b>与 {@link AOVNetwork} 的区别</b>:</p>
 * <table border="1" summary="AOV 网与 AOE 网的对照">
 *   <tr><th></th><th>AOV 网</th><th>AOE 网(本类)</th></tr>
 *   <tr><td>顶点表示</td><td>活动(任务/课程)</td><td><b>事件</b>(Event,里程碑)</td></tr>
 *   <tr><td>弧表示</td><td>先后约束(无权)</td><td><b>活动</b>(Activity),<b>权值 = 工期</b></td></tr>
 *   <tr><td>解决的问题</td><td>拓扑排序(可行顺序)</td><td>关键路径(最短工期、哪些活动不能拖)</td></tr>
 * </table>
 *
 * <p><b>两条语义约定</b>(教材原话):</p>
 * <ol>
 *   <li><b>事件本身不占时间</b>,它只是一个"时间点";弧起点上的事件必须先发生,
 *       弧终点上的事件才可能发生 —— 即"该事件的所有前驱活动都完成"。</li>
 *   <li><b>活动的持续时间体现为弧的权值</b>;时间从事件流向事件。</li>
 * </ol>
 *
 * <p>本类只负责<b>建网与查询</b>(事件/活动的名字与编号、工期、出入度、前驱后继);
 * 求最早/最迟时间与关键路径交给 {@link CriticalPath}。</p>
 *
 * <p><b>建模期校验</b>:事件名不能空、不能重名;活动名不能空、不能重名;
 * 活动两端的事件必须存在;不允许自环(同一个事件到自身);不允许两个事件之间重复的弧;
 * <b>工期必须是非负的有限实数</b>(负工期没有物理意义)。</p>
 *
 * <pre>
 * AOENetwork net = new AOENetwork();
 * net.addEvent("E0");
 * net.addEvent("E1");
 * net.addActivity("A0", "E0", "E1", 1.0);   // 活动 A0:E0 开始、E1 结束,工期 1 周
 * CriticalPath cp = new CriticalPath(net);  // 见 CriticalPath
 * </pre>
 *
 * @see CriticalPath
 * @see AOVNetwork
 * @see EdgeWeightedDigraph
 */
public final class AOENetwork {

    /** 事件名 → 编号 */
    private final Map<String, Integer> eventIndexOf;

    /** 编号 → 事件名 */
    private final List<String> eventNames;

    /** 活动名 → 编号 */
    private final Map<String, Integer> activityIndexOf;

    /** 编号 → 活动名 */
    private final List<String> activityNames;

    /** 活动的起点事件(按活动编号) */
    private final List<Integer> activityFrom;

    /** 活动的终点事件(按活动编号) */
    private final List<Integer> activityTo;

    /** 活动的工期(按活动编号) */
    private final List<Double> activityDuration;

    /** outgoing[v] = 以事件 v 为起点的活动编号 */
    private final List<List<Integer>> outgoing;

    /** incoming[v] = 以事件 v 为终点的活动编号 */
    private final List<List<Integer>> incoming;

    /** 建立空的 AOE 网,事件与活动随后逐个加入 */
    public AOENetwork() {
        this.eventIndexOf = new LinkedHashMap<String, Integer>();
        this.eventNames = new ArrayList<String>();
        this.activityIndexOf = new LinkedHashMap<String, Integer>();
        this.activityNames = new ArrayList<String>();
        this.activityFrom = new ArrayList<Integer>();
        this.activityTo = new ArrayList<Integer>();
        this.activityDuration = new ArrayList<Double>();
        this.outgoing = new ArrayList<List<Integer>>();
        this.incoming = new ArrayList<List<Integer>>();
    }

    // ------------------------------------------------------------------
    // 建模
    // ------------------------------------------------------------------

    /**
     * 添加一个事件(里程碑)。
     *
     * @param name 事件名,非空且不与已有事件重名
     * @return 该事件的编号
     * @throws IllegalArgumentException 名称为 null/空白或已存在
     */
    public int addEvent(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("事件名不能为空");
        }
        if (eventIndexOf.containsKey(name)) {
            throw new IllegalArgumentException("事件 \"" + name + "\" 已经存在");
        }
        int id = eventNames.size();
        eventNames.add(name);
        eventIndexOf.put(name, id);
        outgoing.add(new ArrayList<Integer>());
        incoming.add(new ArrayList<Integer>());
        return id;
    }

    /**
     * 添加一个活动(一条带工期的弧)。
     *
     * @param name        活动名,非空且不与已有活动重名
     * @param fromEvent   起点事件名(必须先发生)
     * @param toEvent     终点事件名(必须最后发生)
     * @param duration    工期,非负的有限实数
     * @return 该活动的编号
     * @throws IllegalArgumentException 名称非法/重复、事件不存在、自环、重复的弧或工期为负
     */
    public int addActivity(String name, String fromEvent, String toEvent, double duration) {
        return addActivity(name, requireEvent(fromEvent), requireEvent(toEvent), duration);
    }

    /**
     * 添加一个活动(按事件编号)。
     *
     * @param name     活动名
     * @param from     起点事件编号
     * @param to       终点事件编号
     * @param duration 工期,非负的有限实数
     * @return 该活动的编号
     * @throws IllegalArgumentException 参数非法或违反 AOE 网的约定
     */
    public int addActivity(String name, int from, int to, double duration) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("活动名不能为空");
        }
        if (activityIndexOf.containsKey(name)) {
            throw new IllegalArgumentException("活动 \"" + name + "\" 已经存在");
        }
        validateEvent(from);
        validateEvent(to);
        if (from == to) {
            throw new IllegalArgumentException("活动 \"" + name + "\" 的起点与终点是同一个事件(自环),AOE 网不允许");
        }
        if (Double.isNaN(duration) || Double.isInfinite(duration)) {
            throw new IllegalArgumentException("工期必须是有限实数,当前为 " + duration);
        }
        if (duration < 0) {
            throw new IllegalArgumentException("工期不能为负,当前为 " + duration + "(活动 \"" + name + "\")");
        }
        for (int activity : outgoing.get(from)) {
            if (activityTo.get(activity) == to) {
                throw new IllegalArgumentException("事件 " + eventNames.get(from) + " 与 "
                        + eventNames.get(to) + " 之间已经有活动 \"" + activityNames.get(activity) + "\" 了");
            }
        }

        int id = activityNames.size();
        activityNames.add(name);
        activityIndexOf.put(name, id);
        activityFrom.add(from);
        activityTo.add(to);
        activityDuration.add(duration);
        outgoing.get(from).add(id);
        incoming.get(to).add(id);
        return id;
    }

    /**
     * 深拷贝:事件与活动的编号、顺序、工期都与原网一致;两者互不影响。
     *
     * @param other 待拷贝的 AOE 网,不能为 null
     * @throws IllegalArgumentException {@code other} 为 null
     */
    public AOENetwork(AOENetwork other) {
        this();
        if (other == null) {
            throw new IllegalArgumentException("待拷贝的 AOE 网不能为 null");
        }
        for (String event : other.eventNames) {
            addEvent(event);
        }
        for (int a = 0; a < other.activityCount(); a++) {
            addActivity(other.activityName(a), other.activityFrom(a), other.activityTo(a),
                    other.activityDuration(a));
        }
    }

    /**
     * 返回一个新的 AOE 网:除指定活动的工期换成新值外,其余与原网完全相同(原网不变)。
     * 用于"改工期会怎样"的实验(见 {@link CriticalPath#main(String[])})。
     *
     * @param activityName 活动名
     * @param newDuration  新工期,非负有限实数
     * @return 改过工期的新网
     * @throws IllegalArgumentException 活动不存在或新工期非法
     */
    public AOENetwork withDuration(String activityName, double newDuration) {
        int target = requireActivity(activityName);
        AOENetwork copy = new AOENetwork();
        for (String event : eventNames) {
            copy.addEvent(event);
        }
        for (int a = 0; a < activityNames.size(); a++) {
            copy.addActivity(activityNames.get(a), activityFrom.get(a), activityTo.get(a),
                    a == target ? newDuration : activityDuration.get(a));
        }
        return copy;
    }

    // ------------------------------------------------------------------
    // 与其它结构的转换
    // ------------------------------------------------------------------

    /**
     * 取出"只保留拓扑结构"的无权有向图:事件 = 顶点,活动 = 弧(工期丢弃)。
     * 需要拓扑排序、有向可达性、环检测、强连通分量这类只看结构的算法时,
     * 把它交给 {@link TopologicalSort}、{@link DirectedPaths}、{@link DirectedCycle}、
     * {@link StronglyConnectedComponents}(与 {@link AOVNetwork#toDigraph()} 对称)。
     *
     * @return 与事件数、活动数一致的无权有向图
     */
    public Digraph toDigraph() {
        Digraph digraph = new Digraph(eventNames.size());
        for (int a = 0; a < activityNames.size(); a++) {
            digraph.addEdge(activityFrom.get(a), activityTo.get(a));
        }
        return digraph;
    }

    // ------------------------------------------------------------------
    // 查询:事件
    // ------------------------------------------------------------------

    /**
     * @return 事件个数(顶点数)
     */
    public int eventCount() {
        return eventNames.size();
    }

    /**
     * @param name 事件名
     * @return 事件编号;不存在返回 -1
     */
    public int eventIndexOf(String name) {
        Integer id = eventIndexOf.get(name);
        return id == null ? -1 : id;
    }

    /**
     * @param name 事件名
     * @return 网中是否有该事件
     */
    public boolean hasEvent(String name) {
        return eventIndexOf.containsKey(name);
    }

    /**
     * @param event 事件编号
     * @return 事件名
     * @throws IllegalArgumentException 编号越界
     */
    public String eventName(int event) {
        validateEvent(event);
        return eventNames.get(event);
    }

    /**
     * @return 全部事件名(按编号顺序的副本)
     */
    public List<String> eventNames() {
        return new ArrayList<String>(eventNames);
    }

    /**
     * @param event 事件编号
     * @return 入度 = 以该事件为终点的活动个数
     * @throws IllegalArgumentException 编号越界
     */
    public int inDegree(int event) {
        validateEvent(event);
        return incoming.get(event).size();
    }

    /**
     * @param event 事件编号
     * @return 出度 = 以该事件为起点的活动个数
     * @throws IllegalArgumentException 编号越界
     */
    public int outDegree(int event) {
        validateEvent(event);
        return outgoing.get(event).size();
    }

    /**
     * @param event 事件编号
     * @return 从该事件出发的活动编号(按加入顺序)
     * @throws IllegalArgumentException 编号越界
     */
    public List<Integer> outgoingActivities(int event) {
        validateEvent(event);
        return new ArrayList<Integer>(outgoing.get(event));
    }

    /**
     * @param event 事件编号
     * @return 汇入该事件的活动编号(按加入顺序)
     * @throws IllegalArgumentException 编号越界
     */
    public List<Integer> incomingActivities(int event) {
        validateEvent(event);
        return new ArrayList<Integer>(incoming.get(event));
    }

    /**
     * @return 全部"源事件"(入度为 0,工程的起点),按编号升序
     */
    public List<Integer> sourceEvents() {
        List<Integer> sources = new ArrayList<Integer>();
        for (int v = 0; v < eventNames.size(); v++) {
            if (incoming.get(v).isEmpty()) {
                sources.add(v);
            }
        }
        return sources;
    }

    /**
     * @return 全部"汇事件"(出度为 0,工程的终点),按编号升序
     */
    public List<Integer> sinkEvents() {
        List<Integer> sinks = new ArrayList<Integer>();
        for (int v = 0; v < eventNames.size(); v++) {
            if (outgoing.get(v).isEmpty()) {
                sinks.add(v);
            }
        }
        return sinks;
    }

    // ------------------------------------------------------------------
    // 查询:活动
    // ------------------------------------------------------------------

    /**
     * @return 活动个数(弧数)
     */
    public int activityCount() {
        return activityNames.size();
    }

    /**
     * @param name 活动名
     * @return 活动编号;不存在返回 -1
     */
    public int activityIndexOf(String name) {
        Integer id = activityIndexOf.get(name);
        return id == null ? -1 : id;
    }

    /**
     * @param name 活动名
     * @return 网中是否有该活动
     */
    public boolean hasActivity(String name) {
        return activityIndexOf.containsKey(name);
    }

    /**
     * @param activity 活动编号
     * @return 活动名
     * @throws IllegalArgumentException 编号越界
     */
    public String activityName(int activity) {
        validateActivity(activity);
        return activityNames.get(activity);
    }

    /**
     * @return 全部活动名(按编号顺序的副本)
     */
    public List<String> activityNames() {
        return new ArrayList<String>(activityNames);
    }

    /**
     * @param activity 活动编号
     * @return 起点事件编号
     * @throws IllegalArgumentException 编号越界
     */
    public int activityFrom(int activity) {
        validateActivity(activity);
        return activityFrom.get(activity);
    }

    /**
     * @param activity 活动编号
     * @return 终点事件编号
     * @throws IllegalArgumentException 编号越界
     */
    public int activityTo(int activity) {
        validateActivity(activity);
        return activityTo.get(activity);
    }

    /**
     * @param activity 活动编号
     * @return 工期
     * @throws IllegalArgumentException 编号越界
     */
    public double activityDuration(int activity) {
        validateActivity(activity);
        return activityDuration.get(activity);
    }

    /**
     * @param name 活动名
     * @return 起点事件编号
     * @throws IllegalArgumentException 活动不存在
     */
    public int activityFrom(String name) {
        return activityFrom(requireActivity(name));
    }

    /**
     * @param name 活动名
     * @return 终点事件编号
     * @throws IllegalArgumentException 活动不存在
     */
    public int activityTo(String name) {
        return activityTo(requireActivity(name));
    }

    /**
     * @param name 活动名
     * @return 工期
     * @throws IllegalArgumentException 活动不存在
     */
    public double activityDuration(String name) {
        return activityDuration(requireActivity(name));
    }

    /**
     * @return 全部活动的工期之和(注意:<b>不是</b>工程总工期,那要等关键路径分析)
     */
    public double totalActivityDuration() {
        double sum = 0.0;
        for (double duration : activityDuration) {
            sum += duration;
        }
        return sum;
    }

    // ------------------------------------------------------------------
    // 校验与显示
    // ------------------------------------------------------------------

    private int requireEvent(String name) {
        int id = eventIndexOf(name);
        if (id < 0) {
            throw new IllegalArgumentException("事件 \"" + name + "\" 不在 AOE 网中");
        }
        return id;
    }

    private int requireActivity(String name) {
        int id = activityIndexOf(name);
        if (id < 0) {
            throw new IllegalArgumentException("活动 \"" + name + "\" 不在 AOE 网中");
        }
        return id;
    }

    private void validateEvent(int event) {
        if (event < 0 || event >= eventNames.size()) {
            throw new IllegalArgumentException("事件编号 " + event + " 不在 [0, " + (eventNames.size() - 1) + "] 内");
        }
    }

    private void validateActivity(int activity) {
        if (activity < 0 || activity >= activityNames.size()) {
            throw new IllegalArgumentException("活动编号 " + activity
                    + " 不在 [0, " + (activityNames.size() - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code AOE 网:13 个事件,18 个活动},随后逐行列出每个事件上的入/出活动与工期
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("AOE 网:").append(eventNames.size()).append(" 个事件,")
                .append(activityNames.size()).append(" 个活动").append(newline);
        for (int v = 0; v < eventNames.size(); v++) {
            sb.append("  [").append(v).append("] ").append(eventNames.get(v)).append(": 汇入");
            for (int activity : incoming.get(v)) {
                sb.append(' ').append(activityNames.get(activity))
                        .append('(').append(eventNames.get(activityFrom.get(activity)))
                        .append("->").append(eventNames.get(activityTo.get(activity)))
                        .append(' ').append(activityDuration.get(activity)).append(')');
            }
            sb.append("; 出发");
            for (int activity : outgoing.get(v)) {
                sb.append(' ').append(activityNames.get(activity))
                        .append("->").append(eventNames.get(activityTo.get(activity)))
                        .append(' ').append(activityDuration.get(activity));
            }
            sb.append(newline);
        }
        return sb.toString();
    }

    /**
     * 演示:构造教科书《精讲数据结构(Java 语言实现)》p.484 图 9-24 的 AOE 网
     * (13 个事件、18 个活动),命令行给出文件路径时改读文件(如 {@code projectAOE.txt});
     * 关键路径分析见 {@link CriticalPath#main(String[])}。
     *
     * @param args 可选:AOE 网数据文件路径
     */
    public static void main(String[] args) {
        AOENetwork net = args.length > 0 ? GraphIO.readAoeFile(args[0]) : textbookSample();
        System.out.print(net);
        System.out.println("事件数 = " + net.eventCount() + ",活动数 = " + net.activityCount()
                + ",活动工期之和 = " + net.totalActivityDuration()
                + "(注意这不是总工期)");
        System.out.println("源事件(项目开始)= " + namesOfEvents(net, net.sourceEvents())
                + ",汇事件(项目结束)= " + namesOfEvents(net, net.sinkEvents()));
    }

    private static List<String> namesOfEvents(AOENetwork net, List<Integer> events) {
        List<String> names = new ArrayList<String>();
        for (int event : events) {
            names.add(net.eventName(event));
        }
        return names;
    }

    /**
     * 教材 p.484 图 9-24 的 AOE 网(13 个事件 E0..E12、18 个活动 A0..A17);
     * 活动注释是图 9-23 中的名称。
     */
    static AOENetwork textbookSample() {
        AOENetwork net = new AOENetwork();
        for (int v = 0; v <= 12; v++) {
            net.addEvent("E" + v);
        }
        net.addActivity("A0", "E0", "E1", 1);       // 需求组进场
        net.addActivity("A1", "E0", "E4", 2);       // 开发组进场
        net.addActivity("A2", "E0", "E2", 3);       // 测试组进场
        net.addActivity("A3", "E1", "E3", 2);       // 需求汇总
        net.addActivity("A4", "E4", "E5", 5);       // 开发环境搭建
        net.addActivity("A5", "E2", "E5", 2);       // 测试环境搭建
        net.addActivity("A6", "E3", "E6", 1);       // 需求复审
        net.addActivity("A7", "E3", "E7", 3);       // 需求跟进
        net.addActivity("A8", "E5", "E7", 4);       // 联合开发
        net.addActivity("A9", "E7", "E6", 6);       // 需求审核(图 9-24 中箭头由 E7 指向 E6)
        net.addActivity("A10", "E7", "E8", 2);      // 项目测试
        net.addActivity("A11", "E6", "E9", 6);      // 准备发布资料
        net.addActivity("A12", "E6", "E10", 9);     // 部署跟进
        net.addActivity("A13", "E10", "E12", 7);    // 源码资料整理移交
        net.addActivity("A14", "E8", "E10", 5);     // 部署前微调
        net.addActivity("A15", "E8", "E11", 3);     // 部署环境搭建
        net.addActivity("A16", "E9", "E12", 4);     // 发布终准备
        net.addActivity("A17", "E11", "E12", 3);    // 项目试运行
        return net;
    }
}
