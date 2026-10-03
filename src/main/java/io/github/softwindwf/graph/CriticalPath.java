package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/**
 * 关键路径分析(AOE 网上的工程进度问题)。
 *
 * <p><b>要回答的两个问题</b>:整个工程<b>最短要多久</b>?哪些活动<b>一天都不能拖</b>?</p>
 *
 * <p><b>两个核心量</b>(教材符号):</p>
 * <ul>
 *   <li>{@code ve[v]}(事件 v 的<b>最早</b>发生时间):从源事件到 v 的<b>最长路径长度</b>
 *       —— 因为 v 必须等它的所有前驱活动都做完,所以是"取最大"而不是最小:</li>
 * </ul>
 * <pre>
 *   ve[源] = 0,   ve[v] = max{ ve[u] + w(u→v) }        // 按拓扑序正向递推
 *   vl[汇] = ve[汇] = T,   vl[u] = min{ vl[v] - w(u→v) } // 按逆拓扑序反向递推
 * </pre>
 * <p>{@code vl[v]} 是事件 v 的<b>最迟</b>发生时间(再不发生就要拖工期),{@code T} 是总工期
 * (汇事件的最早发生时间,等于全图最长路径长度 = 关键路径长度)。</p>
 *
 * <p><b>活动的三个量</b>(活动 a 是弧 {@code u→v},工期 w):</p>
 * <pre>
 *   e(a) = ve[u]              最早开始时间
 *   l(a) = vl[v] - w          最迟开始时间(保证不拖总工期)
 *   余量 = l(a) - e(a) ≥ 0     可以拖延多久而不影响总工期
 * </pre>
 * <p><b>关键活动</b>就是余量为 0 的活动;<b>关键路径</b>是从源事件到汇事件、全部由关键活动
 * 组成的一条路径,它的长度就是总工期。工程要缩短,只能压缩关键活动;压缩非关键活动
 * (在余量范围内)对总工期没有帮助。</p>
 *
 * <p><b>为什么"最长路径"决定了工期</b>:一条路径上相邻活动首尾相接、必须串行完成,
 * 所以工程至少要花"最长的那条路径"那么久;而所有活动都能在 ve/vl 之间安排,
 * 所以最长路径的长度又恰好做得到 —— 上下界重合,{@code T} 就是它。</p>
 *
 * <p><b>两种实现</b>:</p>
 * <table border="1" summary="关键路径的两种实现">
 *   <tr><th>模式</th><th>做法</th><th>时间</th><th>特点</th></tr>
 *   <tr><td>{@link Mode#TOPO}</td>
 *       <td>先拓扑排序,再按拓扑序正向求 ve、逆拓扑序反向求 vl</td>
 *       <td>Θ(V + E)</td><td>标准做法(默认),教材讲的就是它</td></tr>
 *   <tr><td>{@link Mode#RELAX}</td>
 *       <td>不用拓扑序:像 Bellman–Ford 那样,对全部弧反复松弛 V−1 轮,
 *           ve 取最大、vl 取最小</td>
 *       <td>Θ(V·E)</td><td>更好懂"为什么是最长路",也可当独立参照物</td></tr>
 * </table>
 *
 * <p><b>多源多汇</b>:工程可能同时有多个起点/终点(本类的 AOE 网允许)。此时
 * {@code T} 取所有汇事件的最早发生时间的最大值,每个汇事件的 {@code vl} 都置为 {@code T}
 * (落在这个时间点之前完成都行),其余递推不变;关键路径取"确实达到 T"的那条。</p>
 *
 * <pre>
 * AOENetwork net = GraphIO.readAoeFile("projectAOE.txt");
 * CriticalPath cp = new CriticalPath(net);
 * cp.projectDuration();          // 总工期
 * cp.slack("A15");               // 某个活动的时间余量
 * cp.isCritical("A13");          // 是否关键活动
 * cp.criticalPath();             // 一条关键路径(活动名序列)
 * </pre>
 *
 * @see AOENetwork
 * @see TopologicalSort
 * @see <a href="https://algs4.cs.princeton.edu/44sp">Algorithms, 4th Edition(关键路径属 AOE 专题)</a>
 */
public final class CriticalPath {

    /** 求 ve/vl 的实现方式 */
    public enum Mode {

        /** 拓扑序递推:Θ(V + E),标准做法(默认) */
        TOPO,

        /** 全弧反复松弛:Θ(V·E),把"最长路"这件事摊开来看 */
        RELAX
    }

    /** 浮点比较容差(余量为 0 的判定) */
    private static final double EPS = 1e-9;

    /** 被分析的 AOE 网 */
    private final AOENetwork network;

    /** 所用实现方式 */
    private final Mode mode;

    /** 事件的拓扑序(用于展示与 TOPO 模式递推) */
    private final int[] eventOrder;

    /** ve[v]:事件 v 的最早发生时间 */
    private final double[] earliest;

    /** vl[v]:事件 v 的最迟发生时间 */
    private final double[] latest;

    /** e(a):活动 a 的最早开始时间 */
    private final double[] activityEarliest;

    /** l(a):活动 a 的最迟开始时间 */
    private final double[] activityLatest;

    /** 总工期 T */
    private final double duration;

    /** 一条关键路径上的活动编号(按路径顺序) */
    private final List<Integer> criticalPathActivities;

    /** 一条关键路径上的事件编号(含首尾,长度 = 活动数 + 1) */
    private final List<Integer> criticalPathEvents;

    /**
     * 用拓扑序递推法做关键路径分析。
     *
     * @param network AOE 网,不能为 null,且不能含回路
     * @throws IllegalArgumentException {@code network} 为 null,或网中存在回路
     */
    public CriticalPath(AOENetwork network) {
        this(network, Mode.TOPO);
    }

    /**
     * 做关键路径分析。
     *
     * @param network AOE 网,不能为 null,且不能含回路
     * @param mode    实现方式,不能为 null
     * @throws IllegalArgumentException 参数为 null,或网中存在回路(AOE 网必须无环)
     */
    public CriticalPath(AOENetwork network, Mode mode) {
        if (network == null) {
            throw new IllegalArgumentException("AOE 网不能为 null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("实现方式不能为 null");
        }
        this.network = network;
        this.mode = mode;

        int V = network.eventCount();
        this.earliest = new double[V];
        this.latest = new double[V];

        // 拓扑排序(顺带判定 AOE 网是否有回路)
        this.eventOrder = computeTopologicalOrder();
        if (eventOrder == null) {
            throw new IllegalArgumentException("AOE 网中存在回路,无法计算关键路径(工程不可行)");
        }

        if (mode == Mode.TOPO) {
            computeByTopologicalOrder();
        }
        else {
            computeByRelaxation();
        }

        // 总工期 = 各汇事件最早发生时间的最大值
        double t = 0.0;
        for (int sink : network.sinkEvents()) {
            t = Math.max(t, earliest[sink]);
        }
        this.duration = t;

        // 活动的 e、l 与余量
        int E = network.activityCount();
        this.activityEarliest = new double[E];
        this.activityLatest = new double[E];
        for (int a = 0; a < E; a++) {
            int from = network.activityFrom(a);
            int to = network.activityTo(a);
            activityEarliest[a] = earliest[from];
            activityLatest[a] = latest[to] - network.activityDuration(a);
        }

        this.criticalPathEvents = new ArrayList<Integer>();
        this.criticalPathActivities = new ArrayList<Integer>();
        buildOneCriticalPath();
    }

    // ------------------------------------------------------------------
    // 两种实现
    // ------------------------------------------------------------------

    /** 按拓扑序正向求 ve,再按逆拓扑序反向求 vl */
    private void computeByTopologicalOrder() {
        int V = network.eventCount();
        Arrays.fill(earliest, Double.NEGATIVE_INFINITY);
        for (int source : network.sourceEvents()) {
            earliest[source] = 0.0;
        }
        for (int index = 0; index < V; index++) {
            int v = eventOrder[index];
            if (earliest[v] == Double.NEGATIVE_INFINITY) {
                continue;                              // 该事件没有从任何源事件可达
            }
            for (int activity : network.outgoingActivities(v)) {
                int to = network.activityTo(activity);
                double candidate = earliest[v] + network.activityDuration(activity);
                if (candidate > earliest[to]) {
                    earliest[to] = candidate;
                }
            }
        }

        double t = 0.0;
        for (int sink : network.sinkEvents()) {
            t = Math.max(t, earliest[sink]);
        }

        Arrays.fill(latest, Double.POSITIVE_INFINITY);
        for (int sink : network.sinkEvents()) {
            latest[sink] = t;
        }
        for (int index = V - 1; index >= 0; index--) {
            int v = eventOrder[index];
            if (network.outDegree(v) == 0) {
                continue;                              // 汇事件已置为 T
            }
            double best = Double.POSITIVE_INFINITY;
            for (int activity : network.outgoingActivities(v)) {
                int to = network.activityTo(activity);
                best = Math.min(best, latest[to] - network.activityDuration(activity));
            }
            latest[v] = best;
        }
    }

    /**
     * 不用拓扑序:对全部弧反复松弛 V−1 轮。
     * <ul>
     *   <li>ve:源事件为 0,其余 −∞,反复做 {@code ve[to] = max(ve[to], ve[from] + w)};</li>
     *   <li>vl:全部初始化为 T,反复做 {@code vl[from] = min(vl[from], vl[to] - w)}。</li>
     * </ul>
     */
    private void computeByRelaxation() {
        int V = network.eventCount();
        int E = network.activityCount();

        Arrays.fill(earliest, Double.NEGATIVE_INFINITY);
        for (int source : network.sourceEvents()) {
            earliest[source] = 0.0;
        }
        for (int round = 0; round < V - 1; round++) {
            boolean changed = false;
            for (int a = 0; a < E; a++) {
                int from = network.activityFrom(a);
                if (earliest[from] == Double.NEGATIVE_INFINITY) {
                    continue;
                }
                int to = network.activityTo(a);
                double candidate = earliest[from] + network.activityDuration(a);
                if (candidate > earliest[to] + EPS) {
                    earliest[to] = candidate;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        double t = 0.0;
        for (int sink : network.sinkEvents()) {
            t = Math.max(t, earliest[sink]);
        }

        Arrays.fill(latest, t);
        for (int round = 0; round < V - 1; round++) {
            boolean changed = false;
            for (int a = 0; a < E; a++) {
                int from = network.activityFrom(a);
                int to = network.activityTo(a);
                double candidate = latest[to] - network.activityDuration(a);
                if (candidate < latest[from] - EPS) {
                    latest[from] = candidate;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    // ------------------------------------------------------------------
    // 拓扑序与关键路径提取
    // ------------------------------------------------------------------

    /** Kahn 入度法求事件的拓扑序;有回路时返回 null */
    private int[] computeTopologicalOrder() {
        int V = network.eventCount();
        int[] indegree = new int[V];
        for (int v = 0; v < V; v++) {
            indegree[v] = network.inDegree(v);
        }
        Queue<Integer> ready = new ArrayDeque<Integer>();
        for (int v = 0; v < V; v++) {
            if (indegree[v] == 0) {
                ready.add(v);
            }
        }
        int[] order = new int[V];
        int count = 0;
        while (!ready.isEmpty()) {
            int v = ready.poll();
            order[count++] = v;
            for (int activity : network.outgoingActivities(v)) {
                int to = network.activityTo(activity);
                indegree[to]--;
                if (indegree[to] == 0) {
                    ready.add(to);
                }
            }
        }
        return count == V ? order : null;
    }

    /**
     * 取一条关键路径:从"关键源事件"出发,每次选一条余量为 0、且终点事件也关键的出弧,
     * 一直走到汇事件。编号小的优先,所以结果可复现。
     */
    private void buildOneCriticalPath() {
        int start = -1;
        for (int source : network.sourceEvents()) {
            if (isCriticalEvent(source)) {
                start = source;
                break;
            }
        }
        if (start < 0) {
            return;                                   // 空网或没有关键源事件
        }
        criticalPathEvents.add(start);
        int current = start;
        int guard = network.eventCount() + 1;
        while (network.outDegree(current) > 0 && guard-- > 0) {
            int chosen = -1;
            for (int activity : network.outgoingActivities(current)) {
                if (isCriticalActivity(activity) && isCriticalEvent(network.activityTo(activity))) {
                    chosen = activity;
                    break;
                }
            }
            if (chosen < 0) {
                break;
            }
            criticalPathActivities.add(chosen);
            current = network.activityTo(chosen);
            criticalPathEvents.add(current);
        }
    }

    // ------------------------------------------------------------------
    // 查询:事件
    // ------------------------------------------------------------------

    /**
     * @return 工程总工期 T(= 关键路径长度 = 从源事件到汇事件的最长路径)
     */
    public double projectDuration() {
        return duration;
    }

    /**
     * @param event 事件编号
     * @return 事件的最早发生时间 ve
     * @throws IllegalArgumentException 编号越界
     */
    public double earliestEvent(int event) {
        validateEvent(event);
        return earliest[event];
    }

    /**
     * @param event 事件编号
     * @return 事件的最迟发生时间 vl
     * @throws IllegalArgumentException 编号越界
     */
    public double latestEvent(int event) {
        validateEvent(event);
        return latest[event];
    }

    /**
     * @param event 事件编号
     * @return 事件的时间余量 vl − ve(为 0 说明该事件在关键路径上)
     * @throws IllegalArgumentException 编号越界
     */
    public double eventSlack(int event) {
        validateEvent(event);
        return latest[event] - earliest[event];
    }

    /**
     * @param eventName 事件名
     * @return 最早发生时间
     * @throws IllegalArgumentException 事件不存在
     */
    public double earliestEvent(String eventName) {
        return earliestEvent(requireEvent(eventName));
    }

    /**
     * @param eventName 事件名
     * @return 最迟发生时间
     * @throws IllegalArgumentException 事件不存在
     */
    public double latestEvent(String eventName) {
        return latestEvent(requireEvent(eventName));
    }

    /**
     * @param eventName 事件名
     * @return 事件的时间余量
     * @throws IllegalArgumentException 事件不存在
     */
    public double eventSlack(String eventName) {
        return eventSlack(requireEvent(eventName));
    }

    /**
     * @param event 事件编号
     * @return 是否关键事件(ve == vl)
     * @throws IllegalArgumentException 编号越界
     */
    public boolean isCriticalEvent(int event) {
        return Math.abs(eventSlack(event)) <= EPS;
    }

    /**
     * @param eventName 事件名
     * @return 是否关键事件
     * @throws IllegalArgumentException 事件不存在
     */
    public boolean isCriticalEvent(String eventName) {
        return isCriticalEvent(requireEvent(eventName));
    }

    /**
     * @return 全部关键事件的编号,按编号升序
     */
    public int[] criticalEvents() {
        List<Integer> result = new ArrayList<Integer>();
        for (int v = 0; v < network.eventCount(); v++) {
            if (isCriticalEvent(v)) {
                result.add(v);
            }
        }
        int[] array = new int[result.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = result.get(i);
        }
        return array;
    }

    // ------------------------------------------------------------------
    // 查询:活动
    // ------------------------------------------------------------------

    /**
     * @param activity 活动编号
     * @return 最早开始时间 e(a) = ve[起点]
     * @throws IllegalArgumentException 编号越界
     */
    public double earliestStart(int activity) {
        validateActivity(activity);
        return activityEarliest[activity];
    }

    /**
     * @param activity 活动编号
     * @return 最迟开始时间 l(a) = vl[终点] − 工期
     * @throws IllegalArgumentException 编号越界
     */
    public double latestStart(int activity) {
        validateActivity(activity);
        return activityLatest[activity];
    }

    /**
     * @param activity 活动编号
     * @return 时间余量 l(a) − e(a)(为 0 说明是关键活动)
     * @throws IllegalArgumentException 编号越界
     */
    public double slack(int activity) {
        validateActivity(activity);
        return activityLatest[activity] - activityEarliest[activity];
    }

    /**
     * @param activity 活动编号
     * @return 是否关键活动(余量为 0)
     * @throws IllegalArgumentException 编号越界
     */
    public boolean isCriticalActivity(int activity) {
        return Math.abs(slack(activity)) <= EPS;
    }

    /**
     * @param activityName 活动名
     * @return 最早开始时间
     * @throws IllegalArgumentException 活动不存在
     */
    public double earliestStart(String activityName) {
        return earliestStart(requireActivity(activityName));
    }

    /**
     * @param activityName 活动名
     * @return 最迟开始时间
     * @throws IllegalArgumentException 活动不存在
     */
    public double latestStart(String activityName) {
        return latestStart(requireActivity(activityName));
    }

    /**
     * @param activityName 活动名
     * @return 时间余量
     * @throws IllegalArgumentException 活动不存在
     */
    public double slack(String activityName) {
        return slack(requireActivity(activityName));
    }

    /**
     * @param activityName 活动名
     * @return 是否关键活动
     * @throws IllegalArgumentException 活动不存在
     */
    public boolean isCritical(String activityName) {
        return isCriticalActivity(requireActivity(activityName));
    }

    /**
     * @return 全部关键活动的名称,按活动编号升序
     */
    public List<String> criticalActivities() {
        List<String> result = new ArrayList<String>();
        for (int a = 0; a < network.activityCount(); a++) {
            if (isCriticalActivity(a)) {
                result.add(network.activityName(a));
            }
        }
        return result;
    }

    /**
     * @return 一条关键路径上的活动名(按路径顺序);空网返回空列表
     */
    public List<String> criticalPath() {
        List<String> names = new ArrayList<String>(criticalPathActivities.size());
        for (int activity : criticalPathActivities) {
            names.add(network.activityName(activity));
        }
        return names;
    }

    /**
     * @return 一条关键路径上的事件名(含首尾事件,长度比活动数多 1)
     */
    public List<String> criticalPathEvents() {
        List<String> names = new ArrayList<String>(criticalPathEvents.size());
        for (int event : criticalPathEvents) {
            names.add(network.eventName(event));
        }
        return names;
    }

    // ------------------------------------------------------------------
    // 其它查询与显示
    // ------------------------------------------------------------------

    /**
     * @return 所用实现方式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @return 事件的拓扑序(副本);AOE 网无回路时一定存在
     */
    public int[] topologicalOrder() {
        return Arrays.copyOf(eventOrder, eventOrder.length);
    }

    /**
     * @return 事件的拓扑序(名称形式)
     */
    public List<String> topologicalOrderNames() {
        List<String> names = new ArrayList<String>(eventOrder.length);
        for (int event : eventOrder) {
            names.add(network.eventName(event));
        }
        return names;
    }

    /**
     * @return 形如 {@code CriticalPath(TOPO): 总工期 33,关键活动 [A1, A4, ...]},
     *         随后是事件表(ve/vl/余量)与活动表(e/l/余量/是否关键)
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("CriticalPath(").append(mode).append("): 总工期 ").append(duration)
                .append(",关键活动 ").append(criticalActivities()).append(newline);
        sb.append("  事件表(ve = 最早, vl = 最迟):").append(newline);
        sb.append("    事件      ve      vl    余量").append(newline);
        for (int v = 0; v < network.eventCount(); v++) {
            sb.append(String.format("    %-6s%6.2f%8.2f%8.2f", network.eventName(v),
                    earliest[v], latest[v], eventSlack(v)));
            if (isCriticalEvent(v)) {
                sb.append("   ← 关键事件");
            }
            sb.append(newline);
        }
        sb.append("  活动表(e = 最早开始, l = 最迟开始):").append(newline);
        sb.append("    活动  起点  终点  工期      e      l    余量").append(newline);
        for (int a = 0; a < network.activityCount(); a++) {
            int from = network.activityFrom(a);
            int to = network.activityTo(a);
            sb.append(String.format("    %-5s %-5s %-5s%6.2f%7.2f%7.2f%8.2f",
                    network.activityName(a), network.eventName(from), network.eventName(to),
                    network.activityDuration(a), activityEarliest[a], activityLatest[a], slack(a)));
            if (isCriticalActivity(a)) {
                sb.append("   ← 关键活动");
            }
            sb.append(newline);
        }
        sb.append("  一条关键路径: ").append(criticalPathEvents());
        sb.append("  活动为 ").append(criticalPath());
        return sb.toString();
    }

    private int requireEvent(String name) {
        int event = network.eventIndexOf(name);
        if (event < 0) {
            throw new IllegalArgumentException("事件 \"" + name + "\" 不在 AOE 网中");
        }
        return event;
    }

    private int requireActivity(String name) {
        int activity = network.activityIndexOf(name);
        if (activity < 0) {
            throw new IllegalArgumentException("活动 \"" + name + "\" 不在 AOE 网中");
        }
        return activity;
    }

    private void validateEvent(int event) {
        if (event < 0 || event >= network.eventCount()) {
            throw new IllegalArgumentException("事件编号 " + event
                    + " 不在 [0, " + (network.eventCount() - 1) + "] 内");
        }
    }

    private void validateActivity(int activity) {
        if (activity < 0 || activity >= network.activityCount()) {
            throw new IllegalArgumentException("活动编号 " + activity
                    + " 不在 [0, " + (network.activityCount() - 1) + "] 内");
        }
    }

    // ------------------------------------------------------------------
    // 演示
    // ------------------------------------------------------------------

    /**
     * 演示:对教科书 p.484 图 9-24 的 AOE 网(13 个事件、18 个活动)做关键路径分析,
     * 打印事件表与活动表,再用"改工期"演示关键活动与非关键活动的差别。
     *
     * @param args 可选:AOE 网数据文件路径(如 {@code projectAOE.txt})
     */
    public static void main(String[] args) {
        AOENetwork net = args.length > 0 ? GraphIO.readAoeFile(args[0]) : AOENetwork.textbookSample();
        for (Mode mode : Mode.values()) {
            CriticalPath cp = new CriticalPath(net, mode);
            System.out.println(cp);
            System.out.println();
        }

        CriticalPath cp = new CriticalPath(net);
        System.out.println("=== 改工期实验(总工期 " + cp.projectDuration() + ")===");
        demonstrate(net, "A13", 8.0);      // 关键活动 +1 周
        demonstrate(net, "A15", 10.0);     // 非关键活动,余量 14,仍在余量内
        demonstrate(net, "A15", 18.0);     // 非关键活动,超过余量
    }

    /** 把某个活动的工期改掉,打印总工期与关键活动是否变化 */
    private static void demonstrate(AOENetwork net, String activity, double newDuration) {
        AOENetwork changed = net.withDuration(activity, newDuration);
        CriticalPath after = new CriticalPath(changed);
        System.out.println("  把 " + activity + " 的工期从 " + net.activityDuration(activity)
                + " 改成 " + newDuration + " → 总工期 " + after.projectDuration()
                + ",关键活动 " + after.criticalActivities());
    }
}
