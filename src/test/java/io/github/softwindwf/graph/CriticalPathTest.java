package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CriticalPath} 关键路径测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>教材 p.484 图 9-24:总工期 <b>33</b>,13 个事件的 ve/vl、18 个活动的 e/l/余量逐项核对,
 *       关键活动 {A1, A4, A8, A9, A12, A13},关键路径 E0→E4→E5→E7→E6→E10→E12;</li>
 *   <li><b>独立验证</b>(不复用算法中间结果):小图上<b>枚举全部源→汇路径</b>,
 *       用暴力法得出"最长路径长度"与"出现在某条最长路上的活动集合",与算法结果对照 ——
 *       "总工期 = 最长路径"与"关键活动 = 位于某条最长路上的活动"正是关键路径的定义;</li>
 *   <li>递推关系自检:ve[源]=0、ve[v]=max(ve[前驱]+工期)、vl[汇]=T、vl[v]=min(vl[后继]−工期)、ve ≤ vl;</li>
 *   <li>两种实现(拓扑序递推 / 全弧松弛)结果完全一致;</li>
 *   <li>改工期的语义:关键活动加 1 周总工期就加 1 周;非关键活动在余量内随便拖、超过余量才会影响工期;</li>
 *   <li>多源多汇、孤立事件、空网、回路检测与参数校验。</li>
 * </ol>
 */
@DisplayName("CriticalPath 关键路径测试")
class CriticalPathTest {

    private static final double EPS = 1e-9;

    /** 工作区根目录下教材 p.484 图 9-24 的数据文件 */
    private static final String AOE_PATH = "projectAOE.txt";

    private static AOENetwork textbook() {
        return GraphIO.readAoeFile(AOE_PATH);
    }

    @Nested
    @DisplayName("教材 p.484 图 9-24:总工期与事件表")
    class TextbookEventTest {

        @Test
        @DisplayName("总工期 33(周),13 个事件的最早/最迟发生时间逐项核对")
        void durationAndEventTimes() {
            CriticalPath cp = new CriticalPath(textbook());
            assertEquals(33.0, cp.projectDuration(), EPS);

            double[] ve = {0, 1, 3, 3, 2, 7, 17, 11, 13, 23, 26, 16, 33};
            double[] vl = {0, 6, 5, 8, 2, 7, 17, 11, 21, 29, 26, 30, 33};
            for (int v = 0; v <= 12; v++) {
                assertEquals(ve[v], cp.earliestEvent(v), EPS, "E" + v + " 的最早发生时间");
                assertEquals(vl[v], cp.latestEvent(v), EPS, "E" + v + " 的最迟发生时间");
                assertEquals(vl[v] - ve[v], cp.eventSlack(v), EPS, "E" + v + " 的余量");
                assertTrue(cp.earliestEvent(v) <= cp.latestEvent(v) + EPS, "ve 不应超过 vl");
            }
        }

        @Test
        @DisplayName("关键事件 = {E0, E4, E5, E6, E7, E10, E12}")
        void criticalEvents() {
            CriticalPath cp = new CriticalPath(textbook());
            assertArrayEquals(new int[]{0, 4, 5, 6, 7, 10, 12}, cp.criticalEvents());
            for (int v : new int[]{0, 4, 5, 6, 7, 10, 12}) {
                assertTrue(cp.isCriticalEvent("E" + v), "E" + v + " 应是关键事件");
            }
            assertFalse(cp.isCriticalEvent("E8"));
            assertEquals(0.0, cp.eventSlack("E0"), EPS);
            assertEquals(8.0, cp.eventSlack("E8"), EPS);
        }
    }

    @Nested
    @DisplayName("教材 p.484 图 9-24:活动表与关键路径")
    class TextbookActivityTest {

        @Test
        @DisplayName("18 个活动的 e / l / 余量逐项核对")
        void activityTimes() {
            CriticalPath cp = new CriticalPath(textbook());
            // {活动, e, l}
            Object[][] table = {
                {"A0", 0.0, 5.0}, {"A1", 0.0, 0.0}, {"A2", 0.0, 2.0}, {"A3", 1.0, 6.0},
                {"A4", 2.0, 2.0}, {"A5", 3.0, 5.0}, {"A6", 3.0, 16.0}, {"A7", 3.0, 8.0},
                {"A8", 7.0, 7.0}, {"A9", 11.0, 11.0}, {"A10", 11.0, 19.0}, {"A11", 17.0, 23.0},
                {"A12", 17.0, 17.0}, {"A13", 26.0, 26.0}, {"A14", 13.0, 21.0}, {"A15", 13.0, 27.0},
                {"A16", 23.0, 29.0}, {"A17", 16.0, 30.0}
            };
            for (Object[] row : table) {
                String activity = (String) row[0];
                double e = (Double) row[1];
                double l = (Double) row[2];
                assertEquals(e, cp.earliestStart(activity), EPS, activity + " 的最早开始时间");
                assertEquals(l, cp.latestStart(activity), EPS, activity + " 的最迟开始时间");
                assertEquals(l - e, cp.slack(activity), EPS, activity + " 的余量");
                assertTrue(cp.slack(activity) >= -EPS, activity + " 的余量不应为负");
                assertEquals(Math.abs(l - e) <= EPS, cp.isCritical(activity), activity + " 的关键性判定");
            }
        }

        @Test
        @DisplayName("关键活动 = {A1, A4, A8, A9, A12, A13}")
        void criticalActivities() {
            CriticalPath cp = new CriticalPath(textbook());
            assertEquals(Arrays.asList("A1", "A4", "A8", "A9", "A12", "A13"), cp.criticalActivities());
        }

        @Test
        @DisplayName("关键路径 E0→E4→E5→E7→E6→E10→E12,长度等于总工期")
        void criticalPath() {
            AOENetwork net = textbook();
            CriticalPath cp = new CriticalPath(net);
            assertEquals(Arrays.asList("A1", "A4", "A8", "A9", "A12", "A13"), cp.criticalPath());
            assertEquals(Arrays.asList("E0", "E4", "E5", "E7", "E6", "E10", "E12"),
                    cp.criticalPathEvents());

            // 路径合法性与长度自检:首尾相接、全是关键活动、工期之和 = 总工期
            List<String> events = cp.criticalPathEvents();
            List<String> activities = cp.criticalPath();
            assertEquals(activities.size() + 1, events.size(), "事件数应比活动数多 1");
            double sum = 0.0;
            for (int i = 0; i < activities.size(); i++) {
                String activity = activities.get(i);
                assertTrue(cp.isCritical(activity), activity + " 应是关键活动");
                assertEquals(events.get(i), net.eventName(net.activityFrom(activity)),
                        activity + " 的起点应是路径上的前一个事件");
                assertEquals(events.get(i + 1), net.eventName(net.activityTo(activity)),
                        activity + " 的终点应是路径上的后一个事件");
                sum += net.activityDuration(activity);
            }
            assertEquals(cp.projectDuration(), sum, EPS);
        }

        @Test
        @DisplayName("拓扑序合法:每条弧的起点都排在终点之前")
        void topologicalOrder() {
            AOENetwork net = textbook();
            CriticalPath cp = new CriticalPath(net);
            int[] order = cp.topologicalOrder();
            int[] position = new int[net.eventCount()];
            for (int index = 0; index < order.length; index++) {
                position[order[index]] = index;
            }
            for (int a = 0; a < net.activityCount(); a++) {
                assertTrue(position[net.activityFrom(a)] < position[net.activityTo(a)],
                        net.activityName(a) + " 的起点应排在终点之前");
            }
            assertEquals(13, order.length);
            assertEquals(13, cp.topologicalOrderNames().size());
        }
    }

    @Nested
    @DisplayName("独立验证:暴力枚举最长路径")
    class BruteForceTest {

        @Test
        @DisplayName("总工期等于所有源→汇路径的最大长度")
        void durationEqualsLongestPath() {
            AOENetwork net = textbook();
            CriticalPath cp = new CriticalPath(net);
            assertEquals(longestPathByBruteForce(net), cp.projectDuration(), EPS);
        }

        @Test
        @DisplayName("关键活动恰好是'出现在某条最长路径上'的活动")
        void criticalActivitiesMatchBruteForce() {
            AOENetwork net = textbook();
            CriticalPath cp = new CriticalPath(net);
            assertEquals(activitiesOnLongestPaths(net), new HashSet<String>(cp.criticalActivities()));
        }

        @Test
        @DisplayName("30 张随机 AOE 网:总工期与关键活动集合都与暴力法一致")
        void randomNetworks() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 30; trial++) {
                int V = 3 + rnd.nextInt(6);
                AOENetwork net = randomAoe(rnd, V, rnd.nextInt(8));
                CriticalPath cp = new CriticalPath(net);

                assertEquals(longestPathByBruteForce(net), cp.projectDuration(), EPS,
                        "第 " + trial + " 张网的总工期");
                assertEquals(activitiesOnLongestPaths(net), new HashSet<String>(cp.criticalActivities()),
                        "第 " + trial + " 张网的关键活动集合");

                // 每个事件都必须满足递推关系与 ve ≤ vl
                for (int v = 0; v < net.eventCount(); v++) {
                    assertTrue(cp.earliestEvent(v) <= cp.latestEvent(v) + EPS, "ve ≤ vl");
                    assertTrue(cp.eventSlack(v) >= -EPS, "事件余量非负");
                    if (net.inDegree(v) > 0) {
                        double best = Double.NEGATIVE_INFINITY;
                        for (int a : net.incomingActivities(v)) {
                            best = Math.max(best,
                                    cp.earliestEvent(net.activityFrom(a)) + net.activityDuration(a));
                        }
                        assertEquals(best, cp.earliestEvent(v), EPS, "事件 " + v + " 的 ve 递推");
                    }
                    else {
                        assertEquals(0.0, cp.earliestEvent(v), EPS, "源事件的 ve 应为 0");
                    }
                }
                // 关键路径本身必须合法、且长度等于总工期
                assertCriticalPathIsValid(net, cp);
            }
        }
    }

    @Nested
    @DisplayName("两种实现一致")
    class ModeConsistencyTest {

        @Test
        @DisplayName("教材网:拓扑序递推与全弧松弛结果完全相同")
        void textbookBothModes() {
            assertModesAgree(textbook());
        }

        @Test
        @DisplayName("30 张随机网:两种实现的事件时间、活动余量、关键路径都相同")
        void randomBothModes() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 30; trial++) {
                assertModesAgree(randomAoe(rnd, 3 + rnd.nextInt(6), rnd.nextInt(8)));
            }
        }

        @Test
        @DisplayName("默认使用拓扑序递推")
        void defaultMode() {
            assertEquals(CriticalPath.Mode.TOPO, new CriticalPath(textbook()).mode());
        }
    }

    @Nested
    @DisplayName("改工期:余量的含义")
    class DurationChangeTest {

        @Test
        @DisplayName("关键活动 A13 加 1 周,总工期也加 1 周")
        void criticalActivityDelaysProject() {
            AOENetwork net = textbook();
            CriticalPath before = new CriticalPath(net);
            assertEquals(33.0, before.projectDuration(), EPS);
            CriticalPath after = new CriticalPath(net.withDuration("A13", 8.0));
            assertEquals(34.0, after.projectDuration(), EPS);
            assertTrue(after.isCritical("A13"));
        }

        @Test
        @DisplayName("非关键活动 A15(余量 14)拖到 10 周不影响总工期,拖到 18 周才影响")
        void nonCriticalActivityHasSlack() {
            AOENetwork net = textbook();
            assertEquals(14.0, new CriticalPath(net).slack("A15"), EPS);

            CriticalPath withinSlack = new CriticalPath(net.withDuration("A15", 10.0));
            assertEquals(33.0, withinSlack.projectDuration(), EPS, "余量内拖延不改变总工期");

            CriticalPath beyondSlack = new CriticalPath(net.withDuration("A15", 18.0));
            assertEquals(34.0, beyondSlack.projectDuration(), EPS, "超过余量就会拖工期");
            assertEquals(0.0, beyondSlack.slack("A15"), EPS, "拖过之后它自己变成了关键活动");
            assertTrue(beyondSlack.criticalActivities().contains("A15"));
        }

        @Test
        @DisplayName("同时压缩多个关键活动才会缩短工期;单个非关键活动压缩不缩短")
        void shortening() {
            AOENetwork net = textbook();
            assertEquals(33.0, new CriticalPath(net.withDuration("A15", 1.0)).projectDuration(), EPS,
                    "压缩非关键活动 A15 对总工期没有帮助");
        }
    }

    @Nested
    @DisplayName("多源多汇与退化情形")
    class EdgeCaseTest {

        @Test
        @DisplayName("两个源、两个汇:总工期取最长的那个汇,各汇 vl 都等于 T")
        void multipleSourcesAndSinks() {
            AOENetwork net = new AOENetwork();
            for (int v = 0; v <= 4; v++) {
                net.addEvent("E" + v);
            }
            net.addActivity("A0", "E0", "E2", 4);     // 一路 4 周
            net.addActivity("A1", "E1", "E3", 7);     // 另一路 7 周
            net.addActivity("A2", "E2", "E4", 1);     // 5 周
            net.addActivity("A3", "E3", "E4", 1);     // 8 周 → 决定工期
            CriticalPath cp = new CriticalPath(net);
            assertEquals(8.0, cp.projectDuration(), EPS);
            assertEquals(0.0, cp.earliestEvent("E0"), EPS);
            assertEquals(0.0, cp.earliestEvent("E1"), EPS);
            assertEquals(8.0, cp.latestEvent("E4"), EPS);
            assertTrue(cp.isCritical("A1") && cp.isCritical("A3"));
            assertFalse(cp.isCritical("A0"));
            assertEquals(Arrays.asList("A1", "A3"), cp.criticalPath());
        }

        @Test
        @DisplayName("空网、单事件、孤立事件")
        void trivialNetworks() {
            CriticalPath empty = new CriticalPath(new AOENetwork());
            assertEquals(0.0, empty.projectDuration(), EPS);
            assertEquals(0, empty.criticalActivities().size());
            assertTrue(empty.criticalPath().isEmpty());
            assertEquals(0, empty.criticalPathEvents().size());

            AOENetwork single = new AOENetwork();
            single.addEvent("E0");
            CriticalPath one = new CriticalPath(single);
            assertEquals(0.0, one.projectDuration(), EPS);
            assertEquals(0.0, one.earliestEvent("E0"), EPS);
            assertTrue(one.isCriticalEvent("E0"));
            assertEquals(Arrays.asList("E0"), one.criticalPathEvents());
        }

        @Test
        @DisplayName("有回路时构造直接抛 IllegalArgumentException(AOE 网必须无环)")
        void cycleRejected() {
            AOENetwork net = new AOENetwork();
            net.addEvent("E0");
            net.addEvent("E1");
            net.addEvent("E2");
            net.addActivity("A0", "E0", "E1", 1);
            net.addActivity("A1", "E1", "E2", 1);
            net.addActivity("A2", "E2", "E0", 1);       // 成环
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new CriticalPath(net));
            assertTrue(e.getMessage().contains("回路"), e.getMessage());
        }

        @Test
        @DisplayName("参数校验:null 网 / null 方式 / 越界编号 / 不存在的名字")
        void validation() {
            AOENetwork net = textbook();
            assertThrows(IllegalArgumentException.class, () -> new CriticalPath(null));
            assertThrows(IllegalArgumentException.class, () -> new CriticalPath(net, null));
            CriticalPath cp = new CriticalPath(net);
            assertThrows(IllegalArgumentException.class, () -> cp.earliestEvent(13));
            assertThrows(IllegalArgumentException.class, () -> cp.earliestEvent(-1));
            assertThrows(IllegalArgumentException.class, () -> cp.latestEvent(13));
            assertThrows(IllegalArgumentException.class, () -> cp.eventSlack(13));
            assertThrows(IllegalArgumentException.class, () -> cp.earliestStart(18));
            assertThrows(IllegalArgumentException.class, () -> cp.latestStart(-1));
            assertThrows(IllegalArgumentException.class, () -> cp.slack(18));
            assertThrows(IllegalArgumentException.class, () -> cp.isCriticalActivity(18));
            assertThrows(IllegalArgumentException.class, () -> cp.slack("Z9"));
            assertThrows(IllegalArgumentException.class, () -> cp.earliestEvent("Z9"));
            assertThrows(IllegalArgumentException.class, () -> cp.isCritical("Z9"));
        }

        @Test
        @DisplayName("toString 含总工期、关键活动、事件表与活动表")
        void toStringContent() {
            String s = new CriticalPath(textbook()).toString();
            assertTrue(s.contains("总工期 33.0"), s);
            assertTrue(s.contains("A13"), s);
            assertTrue(s.contains("事件表"), s);
            assertTrue(s.contains("活动表"), s);
            assertTrue(s.contains("关键路径"), s);
        }
    }

    // ------------------------------------------------------------------
    // 独立参照物:暴力枚举所有源→汇路径
    // ------------------------------------------------------------------

    /** 枚举全部"源事件 → 汇事件"路径,返回最长路径的长度 */
    private static double longestPathByBruteForce(AOENetwork net) {
        double best = 0.0;
        for (List<Integer> path : allSourceSinkPaths(net)) {
            double total = 0.0;
            for (int activity : path) {
                total += net.activityDuration(activity);
            }
            best = Math.max(best, total);
        }
        return best;
    }

    /** 出现在某条<b>最长</b>路径上的活动名集合(即"关键活动"的定义) */
    private static Set<String> activitiesOnLongestPaths(AOENetwork net) {
        List<List<Integer>> paths = allSourceSinkPaths(net);
        double best = 0.0;
        for (List<Integer> path : paths) {
            double total = 0.0;
            for (int activity : path) {
                total += net.activityDuration(activity);
            }
            best = Math.max(best, total);
        }
        Set<String> result = new HashSet<String>();
        for (List<Integer> path : paths) {
            double total = 0.0;
            for (int activity : path) {
                total += net.activityDuration(activity);
            }
            if (Math.abs(total - best) <= EPS) {
                for (int activity : path) {
                    result.add(net.activityName(activity));
                }
            }
        }
        return result;
    }

    /** 深度优先枚举全部源→汇路径(活动编号序列);只用于小图 */
    private static List<List<Integer>> allSourceSinkPaths(AOENetwork net) {
        List<List<Integer>> paths = new ArrayList<List<Integer>>();
        for (int source : net.sourceEvents()) {
            walk(net, source, new ArrayList<Integer>(), paths);
        }
        return paths;
    }

    private static void walk(AOENetwork net, int event, List<Integer> current, List<List<Integer>> paths) {
        if (net.outDegree(event) == 0) {
            paths.add(new ArrayList<Integer>(current));
            return;
        }
        for (int activity : net.outgoingActivities(event)) {
            current.add(activity);
            walk(net, net.activityTo(activity), current, paths);
            current.remove(current.size() - 1);
        }
    }

    /** 关键路径必须是一条真正的路径、全由关键活动组成、长度等于总工期 */
    private static void assertCriticalPathIsValid(AOENetwork net, CriticalPath cp) {
        List<String> activities = cp.criticalPath();
        List<String> events = cp.criticalPathEvents();
        if (activities.isEmpty()) {
            assertTrue(events.isEmpty() || events.size() == 1, "空路径只允许一个事件");
            return;
        }
        assertEquals(activities.size() + 1, events.size(), "事件数应比活动数多 1");
        double sum = 0.0;
        for (int i = 0; i < activities.size(); i++) {
            String activity = activities.get(i);
            assertTrue(cp.isCritical(activity), activity + " 应是关键活动");
            assertEquals(events.get(i), net.eventName(net.activityFrom(activity)),
                    activity + " 的起点应与路径上的前一个事件相同");
            assertEquals(events.get(i + 1), net.eventName(net.activityTo(activity)),
                    activity + " 的终点应与路径上的后一个事件相同");
            sum += net.activityDuration(activity);
        }
        assertEquals(cp.projectDuration(), sum, EPS, "关键路径长度应等于总工期");
        assertTrue(net.inDegree(net.eventIndexOf(events.get(0))) == 0, "路径应从源事件开始");
        assertTrue(net.outDegree(net.eventIndexOf(events.get(events.size() - 1))) == 0,
                "路径应终止于汇事件");
    }

    /** 两种实现的可观测量必须完全一致 */
    private static void assertModesAgree(AOENetwork net) {
        CriticalPath topo = new CriticalPath(net, CriticalPath.Mode.TOPO);
        CriticalPath relax = new CriticalPath(net, CriticalPath.Mode.RELAX);
        assertEquals(topo.projectDuration(), relax.projectDuration(), EPS, "总工期");
        for (int v = 0; v < net.eventCount(); v++) {
            assertEquals(topo.earliestEvent(v), relax.earliestEvent(v), EPS, "ve(" + v + ")");
            assertEquals(topo.latestEvent(v), relax.latestEvent(v), EPS, "vl(" + v + ")");
        }
        for (int a = 0; a < net.activityCount(); a++) {
            assertEquals(topo.earliestStart(a), relax.earliestStart(a), EPS, "e(" + a + ")");
            assertEquals(topo.latestStart(a), relax.latestStart(a), EPS, "l(" + a + ")");
        }
        assertEquals(topo.criticalActivities(), relax.criticalActivities(), "关键活动集合");
        assertEquals(topo.criticalPath(), relax.criticalPath(), "关键路径");
    }

    /** 随机 AOE 网:事件 E0..E(n-1),只加"从前指向后"的弧(必然无环),工期随机 1..9 */
    private static AOENetwork randomAoe(Random rnd, int V, int activityCount) {
        AOENetwork net = new AOENetwork();
        for (int v = 0; v < V; v++) {
            net.addEvent("E" + v);
        }
        Set<String> used = new HashSet<String>();
        int added = 0;
        for (int i = 0; i < activityCount; i++) {
            int from = rnd.nextInt(V - 1);
            int to = from + 1 + rnd.nextInt(V - from - 1);
            if (!used.add(from + "->" + to)) {
                continue;
            }
            net.addActivity("A" + added, "E" + from, "E" + to, 1 + rnd.nextInt(9));
            added++;
        }
        return net;
    }
}
