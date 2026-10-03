package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AOENetwork} 边表示活动的网测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>事件与活动的编号按加入顺序分配,名称 ↔ 编号双向可查;</li>
 *   <li>活动就是带工期的弧:出入度、前驱后继活动、工期都可查;</li>
 *   <li>建模期校验:重名、未知名、自环、同两点之间的重复弧、负工期、非有限工期全部拒绝;</li>
 *   <li>{@code withDuration}/拷贝构造给出"改工期"的新网,原网不变;</li>
 *   <li>教材 p.484 图 9-24 的 AOE 网:13 个事件、18 个活动、工期与出入度逐条核对。</li>
 * </ol>
 */
@DisplayName("AOENetwork 边表示活动的网测试")
class AOENetworkTest {

    private static final String AOE_PATH = "projectAOE.txt";

    /** 教材 p.484 图 9-24 的 AOE 网 */
    private static AOENetwork textbook() {
        return GraphIO.readAoeFile(AOE_PATH);
    }

    private static AOENetwork sample() {
        AOENetwork net = new AOENetwork();
        net.addEvent("E0");
        net.addEvent("E1");
        net.addEvent("E2");
        net.addActivity("A0", "E0", "E1", 3);
        net.addActivity("A1", "E1", "E2", 4);
        net.addActivity("A2", "E0", "E2", 10);
        return net;
    }

    @Nested
    @DisplayName("事件管理")
    class EventTest {

        @Test
        @DisplayName("事件编号按加入顺序分配,名称与编号双向可查")
        void indexAndName() {
            AOENetwork net = new AOENetwork();
            assertEquals(0, net.addEvent("E0"));
            assertEquals(1, net.addEvent("E1"));
            assertEquals(2, net.eventCount());
            assertEquals(1, net.eventIndexOf("E1"));
            assertEquals(-1, net.eventIndexOf("E9"));
            assertEquals("E0", net.eventName(0));
            assertTrue(net.hasEvent("E1"));
            assertFalse(net.hasEvent("E9"));
            assertEquals(Arrays.asList("E0", "E1"), net.eventNames());
        }

        @Test
        @DisplayName("重名、null、空白事件名都被拒绝")
        void validation() {
            AOENetwork net = new AOENetwork();
            net.addEvent("E0");
            assertThrows(IllegalArgumentException.class, () -> net.addEvent("E0"));
            assertThrows(IllegalArgumentException.class, () -> net.addEvent(null));
            assertThrows(IllegalArgumentException.class, () -> net.addEvent("  "));
            assertEquals(1, net.eventCount());
        }
    }

    @Nested
    @DisplayName("活动(带工期的弧)")
    class ActivityTest {

        @Test
        @DisplayName("活动连接两个事件,工期可查,出入度正确")
        void basics() {
            AOENetwork net = sample();
            assertEquals(3, net.activityCount());
            assertEquals(0, net.activityIndexOf("A0"));
            assertEquals("A1", net.activityName(1));
            assertEquals(0, net.activityFrom("A0"));
            assertEquals(1, net.activityTo("A0"));
            assertEquals(3.0, net.activityDuration("A0"), 0.0);
            assertEquals("E0", net.eventName(net.activityFrom("A0")));

            assertEquals(2, net.outDegree(0));
            assertEquals(2, net.inDegree(2), "A1(E1->E2) 与 A2(E0->E2) 都汇入 E2");
            assertEquals(0, net.inDegree(0));
            assertEquals(Arrays.asList(0, 2), net.outgoingActivities(0));
            assertEquals(Arrays.asList(1, 2), net.incomingActivities(2));
            assertEquals(Arrays.asList(0), net.sourceEvents());
            assertEquals(Arrays.asList(2), net.sinkEvents());
            assertEquals(17.0, net.totalActivityDuration(), 0.0);
        }

        @Test
        @DisplayName("建模期校验:未知名、自环、重复弧、负工期、非有限工期")
        void validation() {
            AOENetwork net = sample();
            assertThrows(IllegalArgumentException.class, () -> net.addActivity("A9", "E0", "Ex", 1));
            assertThrows(IllegalArgumentException.class, () -> net.addActivity("A9", "Ex", "E0", 1));
            assertThrows(IllegalArgumentException.class, () -> net.addActivity("A0", "E0", "E0", 1),
                    "自环");
            assertThrows(IllegalArgumentException.class, () -> net.addActivity("A0", "E0", "E2", 1),
                    "重复活动名");
            assertThrows(IllegalArgumentException.class, () -> net.addActivity("A9", "E0", "E2", 1),
                    "同两点之间已有弧");
            assertThrows(IllegalArgumentException.class, () -> net.addActivity("A9", "E0", "E2", -1),
                    "负工期");
            assertThrows(IllegalArgumentException.class, () -> net.addActivity("A9", "E0", "E2", Double.NaN));
            assertThrows(IllegalArgumentException.class,
                    () -> net.addActivity((String) null, "E0", "E2", 1));
            assertThrows(IllegalArgumentException.class, () -> net.addActivity("A9", -1, 2, 1));
            assertEquals(3, net.activityCount(), "失败的添加不应改变活动数");
        }

        @Test
        @DisplayName("工期可以是 0(瞬间「完成」的活动)")
        void zeroDuration() {
            AOENetwork net = new AOENetwork();
            net.addEvent("E0");
            net.addEvent("E1");
            net.addActivity("A0", "E0", "E1", 0.0);
            assertEquals(0.0, net.activityDuration("A0"), 0.0);
        }

        @Test
        @DisplayName("查询越界编号与不存在的名字都抛 IllegalArgumentException")
        void queryValidation() {
            AOENetwork net = sample();
            assertThrows(IllegalArgumentException.class, () -> net.eventName(3));
            assertThrows(IllegalArgumentException.class, () -> net.eventName(-1));
            assertThrows(IllegalArgumentException.class, () -> net.inDegree(3));
            assertThrows(IllegalArgumentException.class, () -> net.outDegree(-1));
            assertThrows(IllegalArgumentException.class, () -> net.outgoingActivities(3));
            assertThrows(IllegalArgumentException.class, () -> net.incomingActivities(-1));
            assertThrows(IllegalArgumentException.class, () -> net.activityName(3));
            assertThrows(IllegalArgumentException.class, () -> net.activityFrom(3));
            assertThrows(IllegalArgumentException.class, () -> net.activityTo(-1));
            assertThrows(IllegalArgumentException.class, () -> net.activityDuration(3));
            assertThrows(IllegalArgumentException.class, () -> net.activityDuration("Ax"));
            assertThrows(IllegalArgumentException.class, () -> net.activityFrom("Ax"));
        }
    }

    @Nested
    @DisplayName("拷贝与改工期")
    class CopyTest {

        @Test
        @DisplayName("拷贝构造:编号、顺序、工期一致,两者互不影响")
        void copyConstructor() {
            AOENetwork net = sample();
            AOENetwork copy = new AOENetwork(net);
            assertEquals(net.eventNames(), copy.eventNames());
            assertEquals(net.activityNames(), copy.activityNames());
            for (String activity : net.activityNames()) {
                assertEquals(net.activityDuration(activity), copy.activityDuration(activity), 0.0);
            }
            copy.addEvent("E9");
            assertEquals(3, net.eventCount(), "改动副本不应影响原网");
            assertEquals(4, copy.eventCount());
            assertThrows(IllegalArgumentException.class, () -> new AOENetwork((AOENetwork) null));
        }

        @Test
        @DisplayName("withDuration 返回改过工期的新网,原网不变")
        void withDuration() {
            AOENetwork net = sample();
            AOENetwork changed = net.withDuration("A0", 7.5);
            assertEquals(7.5, changed.activityDuration("A0"), 0.0);
            assertEquals(3.0, net.activityDuration("A0"), 0.0, "原网不应被改动");
            assertEquals(net.activityCount(), changed.activityCount());
            assertEquals(net.eventNames(), changed.eventNames());
            assertThrows(IllegalArgumentException.class, () -> net.withDuration("Ax", 1.0));
            assertThrows(IllegalArgumentException.class, () -> net.withDuration("A0", -1.0));
        }
    }

    @Nested
    @DisplayName("教材 p.484 图 9-24 的 AOE 网")
    class TextbookNetworkTest {

        @Test
        @DisplayName("13 个事件 E0..E12、18 个活动 A0..A17")
        void structure() {
            AOENetwork net = textbook();
            assertEquals(13, net.eventCount());
            assertEquals(18, net.activityCount());
            for (int v = 0; v <= 12; v++) {
                assertEquals("E" + v, net.eventName(v));
            }
            for (int a = 0; a < 18; a++) {
                assertEquals("A" + a, net.activityName(a));
            }
            assertEquals(Arrays.asList(0), net.sourceEvents(), "只有 E0 是源事件(项目开始)");
            assertEquals(Arrays.asList(12), net.sinkEvents(), "只有 E12 是汇事件(项目上线)");
        }

        @Test
        @DisplayName("逐条核对弧与工期(与图 9-24 一致)")
        void arcsAndDurations() {
            AOENetwork net = textbook();
            String[][] arcs = {
                {"A0", "E0", "E1", "1"}, {"A1", "E0", "E4", "2"}, {"A2", "E0", "E2", "3"},
                {"A3", "E1", "E3", "2"}, {"A4", "E4", "E5", "5"}, {"A5", "E2", "E5", "2"},
                {"A6", "E3", "E6", "1"}, {"A7", "E3", "E7", "3"}, {"A8", "E5", "E7", "4"},
                {"A9", "E7", "E6", "6"}, {"A10", "E7", "E8", "2"}, {"A11", "E6", "E9", "6"},
                {"A12", "E6", "E10", "9"}, {"A13", "E10", "E12", "7"}, {"A14", "E8", "E10", "5"},
                {"A15", "E8", "E11", "3"}, {"A16", "E9", "E12", "4"}, {"A17", "E11", "E12", "3"}
            };
            for (String[] arc : arcs) {
                String activity = arc[0];
                assertTrue(net.hasActivity(activity), "缺少活动 " + activity);
                assertEquals(arc[1], net.eventName(net.activityFrom(activity)), activity + " 的起点");
                assertEquals(arc[2], net.eventName(net.activityTo(activity)), activity + " 的终点");
                assertEquals(Double.parseDouble(arc[3]), net.activityDuration(activity), 0.0,
                        activity + " 的工期");
            }
            assertEquals(18, net.activityCount());
        }

        @Test
        @DisplayName("出入度与图 9-24 一致")
        void degrees() {
            AOENetwork net = textbook();
            int[] outDegree = {3, 1, 1, 2, 1, 1, 2, 2, 2, 1, 1, 1, 0};
            int[] inDegree = {0, 1, 1, 1, 1, 2, 2, 2, 1, 1, 2, 1, 3};
            for (int v = 0; v <= 12; v++) {
                assertEquals(outDegree[v], net.outDegree(v), "E" + v + " 的出度");
                assertEquals(inDegree[v], net.inDegree(v), "E" + v + " 的入度");
            }
            assertEquals(68.0, net.totalActivityDuration(), 0.0,
                    "18 个活动工期之和是 68,远大于总工期 33 —— 因为大部分活动可以并行");
        }

        @Test
        @DisplayName("toString 含事件数、活动数与每个事件的出入活动")
        void toStringContent() {
            String s = textbook().toString();
            assertTrue(s.contains("AOE 网:13 个事件,18 个活动"), s);
            assertTrue(s.contains("E0"), s);
            assertTrue(s.contains("汇入"), s);
            assertTrue(s.contains("出发"), s);
        }
    }
}
