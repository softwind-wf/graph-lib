package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 无向图(邻接表)单元测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>邻接表的对称性:一条边在两个顶点的表里各存一份;</li>
 *   <li>度数口径:自环贡献 2 度,平行边按重数累加,握手定理 Σdeg = 2E 恒成立;</li>
 *   <li>删除语义:只删一条(平行边留余)、自环删两条记录、不存在的边抛异常且不改动图;</li>
 *   <li>遍历口径:{@code edges()} 每条边只出一次且条数等于 E,与邻接表内部顺序无关;</li>
 *   <li>独立参照物:用"带重数的邻接矩阵"作 oracle,对 5000 次随机增删逐操作比对
 *       (参照物是另一种表示法,能戳穿"实现与测试共享同一误解"的自测盲区)。</li>
 * </ol>
 *
 * <p>文本/文件读写与 DOT 导出的测试在 {@code GraphIOTest},本类只覆盖数据结构本身。</p>
 */
@DisplayName("UndirectedGraph 无向图(邻接表)测试")
class UndirectedGraphTest {

    /** 工作区根目录下的 algs4 标准样例图:13 个顶点、15 条边 */
    private static final String TINY_G_PATH = "tinyG.txt";

    /** 期望的 tinyG.txt 顶点度数(顶点 0..12) */
    private static final int[] TINY_G_DEGREES = {4, 1, 1, 2, 3, 3, 3, 2, 2, 3, 2, 2, 2};

    private static UndirectedGraph tinyG() {
        return GraphIO.readFile(TINY_G_PATH);
    }

    /** 邻接表的迭代顺序(不依赖具体顺序的断言里,先排序再比) */
    private static TreeSet<Integer> neighborsOf(UndirectedGraph g, int v) {
        TreeSet<Integer> set = new TreeSet<Integer>();
        for (int w : g.adj(v)) {
            set.add(w);
        }
        return set;
    }

    /** 把 edges() 收集成 "v-w" 字符串集合,便于与邻接矩阵参照物比对 */
    private static TreeSet<String> edgeKeys(UndirectedGraph g) {
        TreeSet<String> keys = new TreeSet<String>();
        for (int[] e : g.edges()) {
            int a = Math.min(e[0], e[1]);
            int b = Math.max(e[0], e[1]);
            keys.add(a + "-" + b);
        }
        return keys;
    }

    @Nested
    @DisplayName("构造与规模")
    class ConstructionTest {

        @Test
        @DisplayName("空图:V 个空邻接表,E = 0,所有度为 0")
        void emptyGraph() {
            UndirectedGraph g = new UndirectedGraph(5);
            assertEquals(5, g.V());
            assertEquals(0, g.E());
            assertEquals(0, g.maxDegree());
            assertEquals(0, g.minDegree());
            assertEquals(0, g.degreeSum());
            assertEquals(0.0, g.averageDegree(), 1e-12);
            assertEquals(0, g.selfLoopCount());
            for (int v = 0; v < 5; v++) {
                assertEquals(0, g.degree(v));
                assertFalse(g.adj(v).iterator().hasNext(), "空邻接表不应有邻接点");
            }
        }

        @Test
        @DisplayName("0 个顶点是合法图:任何顶点访问都越界")
        void zeroVertexGraph() {
            UndirectedGraph g = new UndirectedGraph(0);
            assertEquals(0, g.V());
            assertEquals(0, g.E());
            assertEquals(0, g.maxDegree());
            assertEquals(0, g.minDegree());
            assertEquals(0.0, g.averageDegree(), 1e-12);
            assertThrows(IllegalArgumentException.class, () -> g.degree(0));
            assertThrows(IllegalArgumentException.class, () -> g.adj(0));
        }

        @Test
        @DisplayName("顶点数为负或超上限抛 IllegalArgumentException")
        void illegalVertexCount() {
            assertThrows(IllegalArgumentException.class, () -> new UndirectedGraph(-1));
            assertThrows(IllegalArgumentException.class, () -> new UndirectedGraph((1 << 24) + 1));
            assertEquals(0, new UndirectedGraph(0).V());
        }

        @Test
        @DisplayName("拷贝构造是深拷贝:结构相同、迭代顺序相同、改动互不影响")
        void deepCopy() {
            UndirectedGraph g = tinyG();
            UndirectedGraph copy = new UndirectedGraph(g);

            assertNotSame(g, copy);
            assertEquals(g.V(), copy.V());
            assertEquals(g.E(), copy.E());
            assertEquals(g.degreeSum(), copy.degreeSum());
            assertEquals(g.toString(), copy.toString(), "拷贝应逐条邻接表还原插入顺序");

            copy.addEdge(0, 3);
            assertEquals(16, copy.E());
            assertEquals(15, g.E(), "改动副本不应影响原图");
            assertEquals(4, g.degree(0));
            assertEquals(5, copy.degree(0));

            g.addEdge(1, 2);
            assertEquals(16, g.E());
            assertEquals(16, copy.E());
            assertFalse(copy.hasEdge(1, 2), "原图改动不应影响副本");
        }

        @Test
        @DisplayName("拷贝 null 抛 IllegalArgumentException")
        void copyNull() {
            assertThrows(IllegalArgumentException.class, () -> new UndirectedGraph((UndirectedGraph) null));
        }
    }

    @Nested
    @DisplayName("加边与度数")
    class AddEdgeTest {

        @Test
        @DisplayName("加一条边:E = 1,两个端点互为邻接点,度各为 1")
        void singleEdge() {
            UndirectedGraph g = new UndirectedGraph(4);
            g.addEdge(0, 3);
            assertEquals(1, g.E());
            assertEquals(1, g.degree(0));
            assertEquals(1, g.degree(3));
            assertTrue(g.hasEdge(0, 3));
            assertTrue(g.hasEdge(3, 0), "无向边必须双向可达");
            assertFalse(g.hasEdge(0, 1));
            assertEquals(2, g.degreeSum());
            assertEquals(1, g.maxDegree());
            assertEquals(0, g.minDegree());
        }

        @Test
        @DisplayName("自环 v-v:邻接表里出现两次,度为 2,E 记 1")
        void selfLoop() {
            UndirectedGraph g = new UndirectedGraph(3);
            g.addEdge(1, 1);
            assertEquals(1, g.E());
            assertEquals(2, g.degree(1));
            assertEquals(1, g.selfLoopCount());
            assertTrue(g.hasEdge(1, 1));
            assertEquals(Arrays.asList(1, 1), toList(g.adj(1)), "自环在邻接表中出现两次");

            UndirectedGraph other = new UndirectedGraph(3);
            other.addEdge(1, 2);
            assertEquals(0, other.selfLoopCount());
        }

        @Test
        @DisplayName("平行边:同一个 v-w 加两次,E = 2,度按重数累加为 2")
        void parallelEdges() {
            UndirectedGraph g = new UndirectedGraph(3);
            g.addEdge(0, 1);
            g.addEdge(0, 1);
            g.addEdge(1, 0);
            assertEquals(3, g.E(), "平行边不自动去重");
            assertEquals(3, g.degree(0));
            assertEquals(3, g.degree(1));
            assertTrue(g.hasEdge(0, 1));
            assertEquals(0, g.selfLoopCount());
            assertEquals(0, g.minDegree(), "顶点 2 是孤立点");
        }

        @Test
        @DisplayName("tinyG.txt:13 顶点 15 边,度数逐点与样例一致,握手定理成立")
        void tinyGFileDegrees() {
            UndirectedGraph g = tinyG();
            assertEquals(13, g.V());
            assertEquals(15, g.E());
            for (int v = 0; v < 13; v++) {
                assertEquals(TINY_G_DEGREES[v], g.degree(v), "顶点 " + v + " 的度数不符");
            }
            assertEquals(30, g.degreeSum());
            assertEquals(30, 2 * g.E(), "握手定理 Σdeg = 2E");
            assertEquals(4, g.maxDegree());
            assertEquals(1, g.minDegree());
            assertEquals(30.0 / 13, g.averageDegree(), 1e-12);
            assertEquals(0, g.selfLoopCount());

            assertTrue(g.hasEdge(0, 1) && g.hasEdge(1, 0));
            assertTrue(g.hasEdge(9, 12) && g.hasEdge(12, 9));
            assertFalse(g.hasEdge(1, 2));
            assertFalse(g.hasEdge(0, 9));
        }

        @Test
        @DisplayName("邻接表对称性:任意随机图,每条边的两个方向都能查到")
        void adjacencySymmetry() {
            Random rnd = new Random(20261003L);
            for (int trial = 0; trial < 50; trial++) {
                int V = 2 + rnd.nextInt(20);
                UndirectedGraph g = new UndirectedGraph(V);
                for (int i = 0; i < 40; i++) {
                    g.addEdge(rnd.nextInt(V), rnd.nextInt(V));
                }
                for (int v = 0; v < V; v++) {
                    for (int w : g.adj(v)) {
                        assertTrue(g.hasEdge(w, v), "adj(" + v + ") 含 " + w + ",但 adj(" + w + ") 不含 " + v);
                    }
                }
                assertEquals(2 * g.E(), g.degreeSum());
            }
        }

        @Test
        @DisplayName("端点越界一律抛 IllegalArgumentException,且不改变图")
        void illegalVertices() {
            UndirectedGraph g = new UndirectedGraph(3);
            g.addEdge(0, 1);
            assertThrows(IllegalArgumentException.class, () -> g.addEdge(-1, 0));
            assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 3));
            assertThrows(IllegalArgumentException.class, () -> g.hasEdge(3, 0));
            assertThrows(IllegalArgumentException.class, () -> g.removeEdge(0, -1));
            assertThrows(IllegalArgumentException.class, () -> g.degree(-1));
            assertThrows(IllegalArgumentException.class, () -> g.adj(3));
            assertEquals(1, g.E(), "非法调用不应改变边数");
            assertEquals(1, g.degree(0));
        }
    }

    @Nested
    @DisplayName("删边")
    class RemoveEdgeTest {

        @Test
        @DisplayName("普通边:E 减 1,两端度数各减 1,双向都查不到")
        void removePlainEdge() {
            UndirectedGraph g = new UndirectedGraph(4);
            g.addEdge(0, 1);
            g.addEdge(0, 2);
            g.removeEdge(0, 1);
            assertEquals(1, g.E());
            assertFalse(g.hasEdge(0, 1));
            assertFalse(g.hasEdge(1, 0));
            assertEquals(1, g.degree(0));
            assertEquals(0, g.degree(1));
            assertTrue(g.hasEdge(0, 2), "删除不应波及其他边");
        }

        @Test
        @DisplayName("自环:一次删掉两条邻接记录,度减 2,E 减 1")
        void removeSelfLoop() {
            UndirectedGraph g = new UndirectedGraph(3);
            g.addEdge(1, 1);
            g.removeEdge(1, 1);
            assertEquals(0, g.E());
            assertEquals(0, g.degree(1));
            assertEquals(0, g.selfLoopCount());
            assertFalse(g.hasEdge(1, 1));
            assertFalse(g.adj(1).iterator().hasNext());
        }

        @Test
        @DisplayName("平行边:一次只删一条,其余保留")
        void removeOneOfParallelEdges() {
            UndirectedGraph g = new UndirectedGraph(2);
            g.addEdge(0, 1);
            g.addEdge(0, 1);
            g.removeEdge(0, 1);
            assertEquals(1, g.E());
            assertEquals(1, g.degree(0));
            assertEquals(1, g.degree(1));
            assertTrue(g.hasEdge(0, 1));
            g.removeEdge(1, 0);
            assertEquals(0, g.E());
            assertFalse(g.hasEdge(0, 1));
        }

        @Test
        @DisplayName("删除不存在的边抛 NoSuchElementException,且图保持原状")
        void removeMissingEdge() {
            UndirectedGraph g = new UndirectedGraph(3);
            g.addEdge(0, 1);
            assertThrows(NoSuchElementException.class, () -> g.removeEdge(0, 2));
            assertThrows(NoSuchElementException.class, () -> g.removeEdge(1, 2));
            assertEquals(1, g.E());
            assertEquals(1, g.degree(0));
            assertEquals(1, g.degree(1));
            assertTrue(g.hasEdge(0, 1));
            assertArrayEqualsBySet(new int[]{0}, neighborsOf(g, 1));
        }

        @Test
        @DisplayName("删到空图后仍可继续加边,状态自洽")
        void removeAllThenAddAgain() {
            UndirectedGraph g = tinyG();
            List<int[]> edges = new ArrayList<int[]>();
            for (int[] e : g.edges()) {
                edges.add(e);
            }
            for (int[] e : edges) {
                g.removeEdge(e[0], e[1]);
            }
            assertEquals(0, g.E());
            assertEquals(0, g.degreeSum());
            assertEquals(0, g.maxDegree());
            assertEquals(0.0, g.averageDegree(), 1e-12);
            for (int v = 0; v < g.V(); v++) {
                assertFalse(g.adj(v).iterator().hasNext());
            }
            for (int[] e : edges) {
                g.addEdge(e[0], e[1]);
            }
            assertEquals(15, g.E());
            assertEquals(30, g.degreeSum());
        }
    }

    @Nested
    @DisplayName("遍历")
    class TraversalTest {

        @Test
        @DisplayName("edges() 每条边只出一次,条数等于 E,端点已归一为 v <= w")
        void edgesOnceEach() {
            UndirectedGraph g = tinyG();
            TreeSet<String> keys = edgeKeys(g);
            assertEquals(g.E(), keys.size(), "edges() 条数必须等于 E");
            assertEquals(15, keys.size());
            assertTrue(keys.contains("0-1"));
            assertTrue(keys.contains("9-12"));
            assertFalse(keys.contains("1-0"), "端点应归一,不应出现反向副本");
        }

        @Test
        @DisplayName("自环与平行边的 edges() 口径:E 记几条就出几条")
        void edgesWithSelfLoopAndParallel() {
            UndirectedGraph g = new UndirectedGraph(4);
            g.addEdge(0, 1);
            g.addEdge(0, 1);
            g.addEdge(2, 2);
            g.addEdge(2, 2);
            g.addEdge(3, 1);
            List<String> list = new ArrayList<String>();
            for (int[] e : g.edges()) {
                list.add(e[0] + "-" + e[1]);
            }
            assertEquals(5, list.size());
            assertEquals(5, g.E());
            assertEquals(2, countOf(list, "0-1", "1-0"));
            assertEquals(2, countOf(list, "2-2"));
            assertEquals(1, countOf(list, "1-3", "3-1"));
            assertEquals(2, g.selfLoopCount());
            assertEquals(4, g.degree(2), "两个自环贡献 4 度");
        }

        @Test
        @DisplayName("邻接表迭代器与 edges() 迭代器都不支持 remove")
        void iteratorsAreReadOnly() {
            UndirectedGraph g = tinyG();
            Iterator<Integer> adjIt = g.adj(0).iterator();
            assertTrue(adjIt.hasNext());
            adjIt.next();
            assertThrows(UnsupportedOperationException.class, adjIt::remove);

            Iterator<int[]> edgeIt = g.edges().iterator();
            assertTrue(edgeIt.hasNext());
            edgeIt.next();
            assertThrows(UnsupportedOperationException.class, edgeIt::remove);
        }

        @Test
        @DisplayName("迭代到尽头再 next() 抛 NoSuchElementException")
        void iteratorExhaustion() {
            UndirectedGraph g = new UndirectedGraph(2);
            g.addEdge(0, 1);
            Iterator<Integer> it = g.adj(0).iterator();
            it.next();
            assertFalse(it.hasNext());
            assertThrows(NoSuchElementException.class, it::next);

            Iterator<int[]> edges = g.edges().iterator();
            edges.next();
            assertFalse(edges.hasNext());
            assertThrows(NoSuchElementException.class, edges::next);
        }

        @Test
        @DisplayName("空图 edges() 为空;0 顶点图也不抛异常")
        void emptyEdges() {
            assertFalse(new UndirectedGraph(3).edges().iterator().hasNext());
            assertFalse(new UndirectedGraph(0).edges().iterator().hasNext());
        }
    }

    @Nested
    @DisplayName("调试视图(toString)")
    class ToStringTest {

        @Test
        @DisplayName("首行为规模,随后每行一个顶点的邻接表")
        void toStringFormat() {
            UndirectedGraph g = new UndirectedGraph(3);
            g.addEdge(0, 1);
            String s = g.toString();
            String newline = System.getProperty("line.separator");
            String[] lines = s.split("\\r?\\n");
            assertEquals("3 vertices, 1 edges", lines[0]);
            assertEquals("0: 1 ", lines[1]);
            assertEquals("1: 0 ", lines[2]);
            assertEquals("2: ", lines[3]);
            assertTrue(s.endsWith(newline));
        }
    }

    @Nested
    @DisplayName("与独立参照物(布尔邻接矩阵)的随机差分")
    class DifferentialTest {

        @Test
        @DisplayName("5000 次随机增删,逐操作比对边数;结束时全量比对邻接表/度数/边集")
        void randomAddRemoveAgainstMatrixModel() {
            final int V = 12;
            Random rnd = new Random(20261003L);
            UndirectedGraph g = new UndirectedGraph(V);

            // 参照物:另一种表示法(带重数的邻接矩阵)+ 独立维护的边数,与实现不共享任何代码。
            // 用"重数"而非布尔,是为了对齐平行边语义:矩阵元素就是两点之间的边数,
            // 行和自然等于度(自环在该行加 2),非零即"边存在"。
            int[][] matrix = new int[V][V];
            int refEdges = 0;
            int refSelfLoops = 0;

            for (int step = 0; step < 5000; step++) {
                int v = rnd.nextInt(V);
                int w = rnd.nextInt(V);
                if (rnd.nextInt(100) < 60) {          // 60% 加边
                    g.addEdge(v, w);
                    matrix[v][w]++;
                    matrix[w][v]++;
                    refEdges++;
                    if (v == w) {
                        refSelfLoops++;
                    }
                }
                else if (matrix[v][w] > 0) {           // 40% 尝试删边
                    g.removeEdge(v, w);
                    matrix[v][w]--;
                    matrix[w][v]--;
                    refEdges--;
                    if (v == w) {
                        refSelfLoops--;
                    }
                }
                assertEquals(refEdges, g.E(), "第 " + step + " 步后边数不符");
                assertEquals(refSelfLoops, g.selfLoopCount(), "第 " + step + " 步后自环数不符");

                // 随机抽查一条边的存在性
                int a = rnd.nextInt(V);
                int b = rnd.nextInt(V);
                assertEquals(matrix[a][b] > 0, g.hasEdge(a, b),
                        "第 " + step + " 步:hasEdge(" + a + "," + b + ") 与邻接矩阵不符");
            }

            // 全量比对
            assertEquals(refEdges, g.E());
            assertEquals(2 * refEdges, g.degreeSum(), "握手定理");
            TreeSet<String> expectedEdgeKeys = new TreeSet<String>();
            int maxDegree = 0;
            for (int v = 0; v < V; v++) {
                int degree = 0;
                for (int w = 0; w < V; w++) {
                    degree += matrix[v][w];
                    if (matrix[v][w] > 0 && v <= w) {
                        expectedEdgeKeys.add(v + "-" + w);
                    }
                }
                assertEquals(degree, g.degree(v), "顶点 " + v + " 的度数不符");
                assertEquals(neighborSet(matrix[v]), neighborsOf(g, v), "顶点 " + v + " 的邻接表不符");
                maxDegree = Math.max(maxDegree, degree);
            }
            assertEquals(maxDegree, g.maxDegree());
            assertEquals(expectedEdgeKeys, edgeKeys(g), "edges() 的边集与邻接矩阵不符");
        }

        /** 邻接矩阵一行中所有重数大于 0 的列号 */
        private TreeSet<Integer> neighborSet(int[] row) {
            TreeSet<Integer> set = new TreeSet<Integer>();
            for (int i = 0; i < row.length; i++) {
                if (row[i] > 0) {
                    set.add(i);
                }
            }
            return set;
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private static List<Integer> toList(Iterable<Integer> it) {
        List<Integer> list = new ArrayList<Integer>();
        for (int x : it) {
            list.add(x);
        }
        return list;
    }

    private static void assertArrayEqualsBySet(int[] expected, TreeSet<Integer> actual) {
        TreeSet<Integer> expectedSet = new TreeSet<Integer>();
        for (int x : expected) {
            expectedSet.add(x);
        }
        assertEquals(expectedSet, actual);
    }

    private static int countOf(List<String> list, String... keys) {
        int count = 0;
        for (String s : list) {
            for (String key : keys) {
                if (key.equals(s)) {
                    count++;
                }
            }
        }
        return count;
    }
}
