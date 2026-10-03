package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AOVNetwork} 顶点表示活动的网测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>建模正确:活动编号按加入顺序分配,名称 ↔ 编号双向可查;</li>
 *   <li>约束口径:{@code addPrecedence(before, after)} 表示一条弧 {@code before -> after},
 *       它同时出现在 before 的后继与 after 的先修里;</li>
 *   <li>建模期校验:重名活动、不存在的活动、自己先于自己、重复约束都被拒绝;</li>
 *   <li>内核 {@link Digraph} 与约束一一对应(双向核对);</li>
 *   <li>{@link #namesOf(int[])} 能把拓扑序编号翻回名称。</li>
 * </ol>
 */
@DisplayName("AOVNetwork 顶点表示活动的网测试")
class AOVNetworkTest {

    private static AOVNetwork courseSample() {
        AOVNetwork net = new AOVNetwork(new String[]{"C1", "C2", "C3", "C4"});
        net.addPrecedence("C1", "C3");
        net.addPrecedence("C2", "C3");
        net.addPrecedence("C3", "C4");
        return net;
    }

    @Nested
    @DisplayName("活动管理")
    class ActivityTest {

        @Test
        @DisplayName("编号按加入顺序分配,名称与编号双向可查")
        void indexAndName() {
            AOVNetwork net = new AOVNetwork();
            assertEquals(0, net.addActivity("高数"));
            assertEquals(1, net.addActivity("离散"));
            assertEquals(2, net.addActivity("数据结构"));
            assertEquals(3, net.activityCount());
            assertEquals(0, net.indexOf("高数"));
            assertEquals(2, net.indexOf("数据结构"));
            assertEquals(-1, net.indexOf("不存在"));
            assertEquals("离散", net.nameOf(1));
            assertTrue(net.hasActivity("高数"));
            assertFalse(net.hasActivity("编译原理"));
            assertEquals(Arrays.asList("高数", "离散", "数据结构"), net.activities());
        }

        @Test
        @DisplayName("批量构造:数组顺序即编号顺序")
        void arrayConstructor() {
            AOVNetwork net = new AOVNetwork(new String[]{"A", "B", "C"});
            assertEquals(3, net.activityCount());
            assertEquals(0, net.precedenceCount());
            assertEquals(1, net.indexOf("B"));
            assertThrows(IllegalArgumentException.class, () -> new AOVNetwork((String[]) null));
        }

        @Test
        @DisplayName("重名、null、空白活动名都被拒绝")
        void activityValidation() {
            AOVNetwork net = new AOVNetwork();
            net.addActivity("A");
            assertThrows(IllegalArgumentException.class, () -> net.addActivity("A"));
            assertThrows(IllegalArgumentException.class, () -> net.addActivity(null));
            assertThrows(IllegalArgumentException.class, () -> net.addActivity("   "));
            assertEquals(1, net.activityCount(), "失败的添加不应改变活动数");
        }

        @Test
        @DisplayName("activities() 返回副本,外部修改不影响网")
        void activitiesAreCopied() {
            AOVNetwork net = new AOVNetwork(new String[]{"A", "B"});
            List<String> copy = net.activities();
            copy.add("X");
            assertEquals(2, net.activityCount());
        }
    }

    @Nested
    @DisplayName("前驱约束")
    class PrecedenceTest {

        @Test
        @DisplayName("约束在两个方向都查得到:before 的后继、after 的先修")
        void bothDirections() {
            AOVNetwork net = courseSample();
            assertEquals(3, net.precedenceCount());
            assertTrue(net.hasPrecedence("C1", "C3"));
            assertFalse(net.hasPrecedence("C3", "C1"), "方向不能反");
            assertEquals(Arrays.asList(2), net.successors(0), "C1 的后继是 C3(编号 2)");
            assertEquals(Arrays.asList(0, 1), net.predecessors(2), "C3 的先修是 C1、C2");
            assertEquals(Arrays.asList("C3"), net.successorNames("C1"));
            assertEquals(Arrays.asList("C1", "C2"), net.predecessorNames("C3"));
            assertEquals(1, net.outDegree(0));
            assertEquals(0, net.inDegree(0), "C1 没有先修");
            assertEquals(2, net.inDegree("C3"));
        }

        @Test
        @DisplayName("不存在的活动、自己先于自己、重复约束都被拒绝")
        void precedenceValidation() {
            AOVNetwork net = courseSample();
            assertThrows(IllegalArgumentException.class, () -> net.addPrecedence("C1", "C9"));
            assertThrows(IllegalArgumentException.class, () -> net.addPrecedence("C9", "C1"));
            assertThrows(IllegalArgumentException.class, () -> net.addPrecedence("C1", "C1"), "自身就是回路");
            assertThrows(IllegalArgumentException.class, () -> net.addPrecedence("C1", "C3"), "重复约束");
            assertEquals(3, net.precedenceCount(), "失败的添加不应改变约束数");
        }

        @Test
        @DisplayName("后继/先修列表返回副本,外部修改不影响网")
        void listsAreCopied() {
            AOVNetwork net = courseSample();
            List<Integer> successors = net.successors(0);
            successors.clear();
            assertEquals(Arrays.asList(2), net.successors(0));
            List<String> names = net.successorNames("C1");
            names.clear();
            assertEquals(Arrays.asList("C3"), net.successorNames("C1"));
        }

        @Test
        @DisplayName("编号越界、名称不存在时抛出可定位的异常")
        void queryValidation() {
            AOVNetwork net = courseSample();
            assertThrows(IllegalArgumentException.class, () -> net.nameOf(4));
            assertThrows(IllegalArgumentException.class, () -> net.nameOf(-1));
            assertThrows(IllegalArgumentException.class, () -> net.successors(4));
            assertThrows(IllegalArgumentException.class, () -> net.predecessors(-1));
            assertThrows(IllegalArgumentException.class, () -> net.outDegree(4));
            assertThrows(IllegalArgumentException.class, () -> net.inDegree(-1));
            assertThrows(IllegalArgumentException.class, () -> net.successorNames("X"));
            assertThrows(IllegalArgumentException.class, () -> net.predecessorNames("X"));
            assertThrows(IllegalArgumentException.class, () -> net.inDegree("X"));
        }
    }

    @Nested
    @DisplayName("内核有向图与序列转换")
    class KernelTest {

        @Test
        @DisplayName("toDigraph():活动数 = V、约束数 = E,且每条约束与每条边一一对应")
        void toDigraphMatchesPrecedences() {
            AOVNetwork net = courseSample();
            Digraph graph = net.toDigraph();
            assertEquals(net.activityCount(), graph.V());
            assertEquals(net.precedenceCount(), graph.E());

            // 正向:网里的每条约束都应是图里的一条边
            for (int v = 0; v < net.activityCount(); v++) {
                for (int w : net.successors(v)) {
                    assertTrue(graph.hasEdge(v, w), "约束 " + net.nameOf(v) + "->" + net.nameOf(w) + " 未进入内核图");
                }
            }
            // 反向:图里的每条边都应对应一条约束
            int edgeCount = 0;
            for (int[] e : graph.edges()) {
                assertTrue(net.hasPrecedence(net.nameOf(e[0]), net.nameOf(e[1])),
                        "内核图里的边 " + e[0] + "->" + e[1] + " 没有对应约束");
                edgeCount++;
            }
            assertEquals(net.precedenceCount(), edgeCount);
            // 度数一致
            for (int v = 0; v < net.activityCount(); v++) {
                assertEquals(net.inDegree(v), graph.inDegree(v));
                assertEquals(net.outDegree(v), graph.outDegree(v));
            }
        }

        @Test
        @DisplayName("多次 toDigraph() 得到独立对象,改动其不影响网")
        void digraphIsIndependent() {
            AOVNetwork net = courseSample();
            Digraph first = net.toDigraph();
            first.addEdge(3, 0);
            Digraph second = net.toDigraph();
            assertFalse(second.hasEdge(3, 0));
            assertEquals(3, second.E());
            assertEquals(3, net.precedenceCount());
        }

        @Test
        @DisplayName("namesOf():编号序列翻成名称;null 进 null 出")
        void namesOf() {
            AOVNetwork net = courseSample();
            assertEquals(Arrays.asList("C1", "C2", "C4"), net.namesOf(new int[]{0, 1, 3}));
            assertNull(net.namesOf(null));
            assertThrows(IllegalArgumentException.class, () -> net.namesOf(new int[]{0, 9}));
        }

        @Test
        @DisplayName("课程样例:9 门课、11 条约束(教科书例题结构)")
        void courseExample() {
            AOVNetwork net = new AOVNetwork(new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9"});
            net.addPrecedence("C1", "C3");
            net.addPrecedence("C2", "C3");
            net.addPrecedence("C3", "C4");
            net.addPrecedence("C2", "C4");
            net.addPrecedence("C2", "C5");
            net.addPrecedence("C4", "C6");
            net.addPrecedence("C5", "C6");
            net.addPrecedence("C4", "C7");
            net.addPrecedence("C9", "C7");
            net.addPrecedence("C1", "C8");
            net.addPrecedence("C8", "C9");

            assertEquals(9, net.activityCount());
            assertEquals(11, net.precedenceCount());
            assertEquals(0, net.inDegree("C1"), "C1 是先修链的起点");
            assertEquals(0, net.inDegree("C2"));
            assertEquals(2, net.inDegree("C3"));
            assertEquals(1, net.outDegree("C9"));
        }

        @Test
        @DisplayName("toString 含活动数、约束数与每个活动的先修/后继")
        void toStringContent() {
            String s = courseSample().toString();
            assertTrue(s.contains("AOV 网:4 个活动,3 个前驱约束"), s);
            assertTrue(s.contains("C1"), s);
            assertTrue(s.contains("先修"), s);
            assertTrue(s.contains("后继"), s);
        }
    }

    @Nested
    @DisplayName("教材 p.480 表 9-1 / 图 9-20 的课程 AOV 网")
    class TextbookCourseNetworkTest {

        /** 工作区根目录下的教材数据文件(表 9-1 逐行誊写,行序即图 9-20 的编号) */
        private static final String AOV_PATH = "coursesAOV.txt";

        private AOVNetwork load() {
            File file = new File(AOV_PATH);
            assertTrue(file.isFile(), "需要工作区根目录下存在 " + AOV_PATH
                    + "(当前目录:" + file.getAbsolutePath() + ")");
            return GraphIO.readAovFile(AOV_PATH);
        }

        private List<String> names(String... values) {
            return Arrays.asList(values);
        }

        @Test
        @DisplayName("11 门课、11 条先修约束;课程编号与图 9-20 完全一致")
        void structure() {
            AOVNetwork net = load();
            assertEquals(11, net.activityCount());
            assertEquals(11, net.precedenceCount());
            assertEquals("线性代数", net.nameOf(0));
            assertEquals("高等数学", net.nameOf(1));
            assertEquals("离散数学", net.nameOf(2));
            assertEquals("数据结构", net.nameOf(3));
            assertEquals("算法设计与分析", net.nameOf(4));
            assertEquals("概率论与数理统计", net.nameOf(5));
            assertEquals("编程语言基础", net.nameOf(6));
            assertEquals("Java语言编程", net.nameOf(7));
            assertEquals("Android操作系统", net.nameOf(8));
            assertEquals("数据结构课程设计(Java语言实现)", net.nameOf(9));
            assertEquals("Android应用开发", net.nameOf(10));
        }

        @Test
        @DisplayName("每门课的前置课程逐行等于表 9-1")
        void prerequisitesMatchTable() {
            AOVNetwork net = load();
            assertEquals(names(), net.predecessorNames("线性代数"));
            assertEquals(names(), net.predecessorNames("高等数学"));
            assertEquals(names("高等数学", "线性代数"), net.predecessorNames("离散数学"));
            assertEquals(names("离散数学"), net.predecessorNames("数据结构"));
            assertEquals(names("数据结构"), net.predecessorNames("算法设计与分析"));
            assertEquals(names("高等数学"), net.predecessorNames("概率论与数理统计"));
            assertEquals(names(), net.predecessorNames("编程语言基础"));
            assertEquals(names("编程语言基础"), net.predecessorNames("Java语言编程"));
            assertEquals(names("Java语言编程", "数据结构"), net.predecessorNames("Android操作系统"));
            assertEquals(names("数据结构", "Java语言编程"),
                    net.predecessorNames("数据结构课程设计(Java语言实现)"));
            assertEquals(names("Android操作系统"), net.predecessorNames("Android应用开发"));
        }

        @Test
        @DisplayName("入度为 0 的只有线性代数、高等数学、编程语言基础;出度为 0 的是四门收尾课")
        void degrees() {
            AOVNetwork net = load();
            List<String> sources = new ArrayList<String>();
            List<String> sinks = new ArrayList<String>();
            for (String course : net.activities()) {
                if (net.inDegree(course) == 0) {
                    sources.add(course);
                }
                if (net.outDegree(course) == 0) {
                    sinks.add(course);
                }
            }
            assertEquals(names("线性代数", "高等数学", "编程语言基础"), sources);
            assertEquals(names("算法设计与分析", "概率论与数理统计", "数据结构课程设计(Java语言实现)",
                    "Android应用开发"), sinks);
        }

        @Test
        @DisplayName("三种拓扑排序都可行,且确切的修读顺序与实现一致")
        void orders() {
            AOVNetwork net = load();
            Digraph graph = net.toDigraph();
            assertTrue(new TopologicalSort(graph).isDag(), "教材的 AOV 网必须无回路");

            assertArrayEquals(new int[]{0, 1, 6, 2, 5, 7, 3, 4, 8, 9, 10},
                    new TopologicalSort(graph, TopologicalSort.Mode.KAHN).order());
            assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
                    new TopologicalSort(graph, TopologicalSort.Mode.KAHN_LEX).order(),
                    "最小堆版给出编号序(字典序最小)");
            assertArrayEquals(new int[]{6, 7, 1, 5, 0, 2, 3, 9, 8, 10, 4},
                    new TopologicalSort(graph, TopologicalSort.Mode.DFS).order());
        }

        @Test
        @DisplayName("按名称独立核对:每种顺序里,每门课的每个先修课都排在它前面")
        void everyPrerequisiteComesFirst() {
            AOVNetwork net = load();
            Digraph graph = net.toDigraph();
            for (TopologicalSort.Mode mode : TopologicalSort.Mode.values()) {
                TopologicalSort ts = new TopologicalSort(graph, mode);
                for (String course : net.activities()) {
                    int coursePosition = ts.positionOf(net.indexOf(course));
                    for (String prerequisite : net.predecessorNames(course)) {
                        assertTrue(ts.positionOf(net.indexOf(prerequisite)) < coursePosition,
                                mode + ":「" + prerequisite + "」应先于「" + course + "」");
                    }
                }
            }
        }

        @Test
        @DisplayName("完整修读链:数据结构课程设计 ← 数据结构 ← 离散数学 ← 高等数学、线性代数")
        void courseChain() {
            AOVNetwork net = load();
            TopologicalSort ts = new TopologicalSort(net.toDigraph(), TopologicalSort.Mode.KAHN_LEX);
            List<String> order = net.namesOf(ts.order());
            int design = order.indexOf("数据结构课程设计(Java语言实现)");
            assertTrue(order.indexOf("数据结构") < design);
            assertTrue(order.indexOf("Java语言编程") < design);
            int dataStructure = order.indexOf("数据结构");
            assertTrue(order.indexOf("离散数学") < dataStructure);
            assertTrue(order.indexOf("高等数学") < order.indexOf("离散数学"));
            assertTrue(order.indexOf("线性代数") < order.indexOf("离散数学"));
        }

        @Test
        @DisplayName("人为制造回路(数据结构课程设计反过来成为高等数学的先修):三种实现一致报不可行")
        void cycleDetection() {
            AOVNetwork net = load();
            net.addPrecedence("数据结构课程设计(Java语言实现)", "高等数学");
            for (TopologicalSort.Mode mode : TopologicalSort.Mode.values()) {
                TopologicalSort ts = new TopologicalSort(net.toDigraph(), mode);
                assertFalse(ts.isDag(), mode + " 应检测出回路");
                assertNull(ts.order());
                assertNotNull(ts.cycle());
                List<String> cycleNames = net.namesOf(ts.cycle());
                assertEquals(4, cycleNames.size(), "回路应含 4 门课:" + cycleNames);
                assertTrue(cycleNames.contains("离散数学"));
                assertTrue(cycleNames.contains("数据结构"));
                assertTrue(cycleNames.contains("数据结构课程设计(Java语言实现)"));
                assertTrue(cycleNames.contains("高等数学"));
            }
        }
    }
}
