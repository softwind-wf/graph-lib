package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GraphIO} 的单元测试:解析、流/文件读写、格式往返、DOT 导出与异常约定。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>格式:空白一律作分隔符;自环、平行边、0 顶点图都能正确解析;</li>
 *   <li>往返:{@code parse(format(g))} 与 {@code g} 结构一致(V、E、每个顶点的度、边集、自环数);</li>
 *   <li>异常分类:格式/参数问题抛 {@link IllegalArgumentException},真 I/O 故障抛 {@link IllegalStateException};</li>
 *   <li>资源归属:调用方传入的流/Reader/Writer 不被本类关闭,本类自己打开的文件流一定关闭
 *       (用带"关闭标记"的流验证);</li>
 *   <li>DOT 导出:每条边只出一次、自环只出一条。</li>
 * </ol>
 */
@DisplayName("GraphIO 图读写工具测试")
class GraphIOTest {

    /** 工作区根目录下的 algs4 标准样例图:13 个顶点、15 条边 */
    private static final String TINY_G_PATH = "tinyG.txt";

    /** 期望的 tinyG.txt 顶点度数(顶点 0..12) */
    private static final int[] TINY_G_DEGREES = {4, 1, 1, 2, 3, 3, 3, 2, 2, 3, 2, 2, 2};

    @Nested
    @DisplayName("解析")
    class ParseTest {

        @Test
        @DisplayName("空白(空格/制表/换行)一律作为分隔符")
        void parseWhitespace() {
            UndirectedGraph g = GraphIO.parse("4\n3\n0 1\n1\t2\n 2 3 \n");
            assertEquals(4, g.V());
            assertEquals(3, g.E());
            assertTrue(g.hasEdge(0, 1));
            assertTrue(g.hasEdge(1, 2));
            assertTrue(g.hasEdge(2, 3));
            assertEquals(2, g.degree(1));
            assertEquals(2, g.maxDegree(), "顶点 1 同时连着 0 和 2");
            assertEquals(1, g.minDegree(), "顶点 0、3 各只连一条边");
        }

        @Test
        @DisplayName("全部 token 挤在一行也能解析(格式与换行无关)")
        void parseSingleLine() {
            UndirectedGraph g = GraphIO.parse("3 2 0 1 1 2");
            assertEquals(3, g.V());
            assertEquals(2, g.E());
            assertTrue(g.hasEdge(0, 1));
            assertTrue(g.hasEdge(1, 2));
            assertFalse(g.hasEdge(0, 2));
        }

        @Test
        @DisplayName("自环、平行边、自环+平行边的重数都按原样读入")
        void parseSelfLoopAndParallelEdges() {
            UndirectedGraph g = GraphIO.parse("3\n4\n0 1\n0 1\n2 2\n2 2\n");
            assertEquals(4, g.E());
            assertEquals(2, g.degree(0), "两条平行边 0-1");
            assertEquals(2, g.degree(1), "平行边的另一端同样按重数计");
            assertEquals(4, g.degree(2), "两个自环,每个贡献 2 度");
            assertEquals(2, g.selfLoopCount());
            assertEquals(8, g.degreeSum());
        }

        @Test
        @DisplayName("0 个顶点、0 条边是合法输入")
        void parseEmptyGraph() {
            UndirectedGraph g = GraphIO.parse("0\n0\n");
            assertEquals(0, g.V());
            assertEquals(0, g.E());
            assertFalse(g.edges().iterator().hasNext());

            UndirectedGraph g2 = GraphIO.parse("3\n0");
            assertEquals(3, g2.V());
            assertEquals(0, g2.E());
            assertEquals(0, g2.degreeSum());
        }

        @Test
        @DisplayName("格式错误:空输入、缺项、多项、非整数、边数为负、端点越界、null")
        void parseErrors() {
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse(""));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("   "));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("\n\t\n"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("5"), "缺边数");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("x 0"), "顶点数非整数");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("5 y"), "边数非整数");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("5 -1"), "边数为负");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("5 2 0 1"), "端点数少于声明");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("5 1 0 1 2 3"), "端点数多于声明");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("5 1 x 1"), "端点非整数");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("5 1 0 5"), "端点越界");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("5 1 -1 0"), "端点为负");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse("-3 0"), "顶点数为负");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parse(null), "文本为 null");
        }
    }

    @Nested
    @DisplayName("读取(字符流/字节流/文件)")
    class ReadTest {

        @Test
        @DisplayName("readFile(tinyG.txt):V、E、逐点度数与样例一致")
        void readTinyGFile() {
            File file = new File(TINY_G_PATH);
            assertTrue(file.isFile(), "需要工作区根目录下存在 " + TINY_G_PATH
                    + "(当前目录:" + file.getAbsolutePath() + ")");
            UndirectedGraph g = GraphIO.readFile(TINY_G_PATH);
            assertEquals(13, g.V());
            assertEquals(15, g.E());
            for (int v = 0; v < 13; v++) {
                assertEquals(TINY_G_DEGREES[v], g.degree(v), "顶点 " + v + " 度数不符");
            }
            assertEquals(30, g.degreeSum());
            assertEquals(4, g.maxDegree());
            assertEquals(1, g.minDegree());
        }

        @Test
        @DisplayName("parse / read(StringReader) / read(ByteArrayInputStream) 三者结果一致")
        void threeEntryPointsAgree() {
            String text = "5\n4\n0 1\n1 2\n2 3\n3 4\n";
            UndirectedGraph a = GraphIO.parse(text);
            UndirectedGraph b = GraphIO.read(new StringReader(text));
            UndirectedGraph c = GraphIO.read(new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8"))));
            assertSameStructure(a, b);
            assertSameStructure(a, c);
            assertEquals(a.toString(), c.toString(), "同一份输入,邻接表顺序也应一致");
        }

        @Test
        @DisplayName("中文/非 ASCII 环境不影响解析(按 UTF-8 读字节流)")
        void utf8ByteStream() {
            String text = "2\n1\n0 1\n";
            UndirectedGraph g = GraphIO.read(new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8"))));
            assertEquals(2, g.V());
            assertTrue(g.hasEdge(0, 1));
        }

        @Test
        @DisplayName("参数为 null 或文件不存在都抛 IllegalArgumentException")
        void readErrors() {
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readFile(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readFile("no-such-graph-file.txt"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.read((Reader) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.read((InputStream) null));
        }

        @Test
        @DisplayName("调用方传入的 Reader / InputStream 不被 GraphIO 关闭")
        void callerOwnedStreamsAreNotClosed() {
            TrackingReader reader = new TrackingReader("2\n1\n0 1\n");
            UndirectedGraph fromReader = GraphIO.read(reader);
            assertEquals(2, fromReader.V());
            assertFalse(reader.closed, "GraphIO 不应关闭调用方传入的 Reader");

            TrackingInputStream in = new TrackingInputStream(
                    new ByteArrayInputStream("2\n1\n0 1\n".getBytes(Charset.forName("UTF-8"))));
            UndirectedGraph fromStream = GraphIO.read(in);
            assertEquals(2, fromStream.V());
            assertFalse(in.closed, "GraphIO 不应关闭调用方传入的 InputStream");

            reader.close();
            assertTrue(reader.closed, "调用方自己关闭后应生效");
        }

        @Test
        @DisplayName("读取时的 I/O 故障包装成 IllegalStateException(保留原因)")
        void ioFailureIsWrapped() {
            Reader failing = new Reader() {
                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    throw new IOException("模拟磁盘故障");
                }

                @Override
                public void close() {
                    // 调用方自己关闭,这里无需处理
                }
            };
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> GraphIO.read(failing));
            assertNotNull(e.getCause(), "应保留原始 IOException 作为 cause");
            assertTrue(e.getMessage().contains("模拟磁盘故障"), "异常信息应带上原因:" + e.getMessage());
        }
    }

    @Nested
    @DisplayName("格式化与写出")
    class WriteTest {

        @Test
        @DisplayName("format 输出 algs4 格式:第 1 行 V、第 2 行 E、其后每行一条边")
        void formatShape() {
            UndirectedGraph g = new UndirectedGraph(4);
            g.addEdge(0, 1);
            g.addEdge(1, 2);
            g.addEdge(2, 2);
            String text = GraphIO.format(g);
            String[] lines = text.split("\\r?\\n");
            assertEquals("4", lines[0]);
            assertEquals("3", lines[1]);
            assertEquals(2 + g.E(), lines.length, "总共 2 + E 行");
            assertEquals("0 1", lines[2]);
            assertEquals("1 2", lines[3]);
            assertEquals("2 2", lines[4], "自环只写一条");
        }

        @Test
        @DisplayName("往返:parse(format(g)) 与 g 的 V/E/度数/边集/自环数完全一致")
        void roundTrip() {
            UndirectedGraph g = new UndirectedGraph(6);
            g.addEdge(0, 1);
            g.addEdge(0, 1);      // 平行边
            g.addEdge(2, 2);      // 自环
            g.addEdge(2, 2);      // 再来一个自环
            g.addEdge(3, 4);
            g.addEdge(5, 5);
            g.addEdge(0, 5);

            UndirectedGraph copy = GraphIO.parse(GraphIO.format(g));
            assertSameStructure(g, copy);
            assertEquals(g.E(), copy.E());
            assertEquals(7, copy.E(), "2(平行边)+2(两自环)+1+1+1");
            assertEquals(3, copy.selfLoopCount());
            assertEquals(4, copy.degree(2), "两个自环 → 4 度");
            assertEquals(3, copy.degree(0), "两条平行边 0-1 保留重数,再加 0-5");
        }

        @Test
        @DisplayName("无边的图往返后仍为无边")
        void roundTripEmptyEdges() {
            UndirectedGraph g = new UndirectedGraph(3);
            UndirectedGraph copy = GraphIO.parse(GraphIO.format(g));
            assertSameStructure(g, copy);
            assertEquals(0, copy.E());
        }

        @Test
        @DisplayName("write(StringWriter) 写出的内容与 format 相同,且不关闭该 Writer")
        void writeToWriter() {
            UndirectedGraph g = GraphIO.parse("3\n2\n0 1\n1 2\n");
            StringWriter writer = new StringWriter();
            GraphIO.write(g, writer);
            assertEquals(GraphIO.format(g), writer.toString());
            assertSameStructure(g, GraphIO.parse(writer.toString()));
        }

        @Test
        @DisplayName("write 到文件再 readFile 回来,结构与原图一致")
        void writeAndReadFile() {
            UndirectedGraph g = GraphIO.parse("4\n4\n0 1\n0 1\n2 2\n3 0\n");
            File dir = new File("target/graph-io-test");
            assertTrue(dir.isDirectory() || dir.mkdirs(), "无法创建测试目录:" + dir.getAbsolutePath());
            File file = new File(dir, "round-trip-" + System.nanoTime() + ".txt");
            try {
                GraphIO.write(g, file.getPath());
                assertTrue(file.isFile());
                UndirectedGraph back = GraphIO.readFile(file.getPath());
                assertSameStructure(g, back);
                assertEquals(4, back.E());
                assertEquals(1, back.selfLoopCount());
                assertEquals(3, back.degree(0), "两条平行边 0-1 保留重数,再加 0-3");
            }
            finally {
                assertTrue(!file.exists() || file.delete(), "测试文件应能删除:" + file.getAbsolutePath());
            }
        }

        @Test
        @DisplayName("参数为 null 抛 IllegalArgumentException")
        void writeNullArguments() {
            UndirectedGraph g = new UndirectedGraph(1);
            assertThrows(IllegalArgumentException.class, () -> GraphIO.format((UndirectedGraph) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.write((UndirectedGraph) null, "target/x.txt"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.write(g, (String) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.write(g, (java.io.OutputStream) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.write(g, (java.io.Writer) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.toDot((UndirectedGraph) null));
        }

        @Test
        @DisplayName("目标目录不存在时抛 IllegalArgumentException")
        void writeToMissingDirectory() {
            UndirectedGraph g = GraphIO.parse("2\n1\n0 1\n");
            String path = "target/graph-io-test/no-such-dir-x/graph.txt";
            assertThrows(IllegalArgumentException.class, () -> GraphIO.write(g, path));
        }

        @Test
        @DisplayName("调用方传入的 OutputStream 不被 GraphIO 关闭")
        void callerOwnedOutputStreamIsNotClosed() {
            UndirectedGraph g = GraphIO.parse("2\n1\n0 1\n");
            TrackingOutputStream out = new TrackingOutputStream();
            GraphIO.write(g, (java.io.OutputStream) out);
            assertFalse(out.closed, "GraphIO 不应关闭调用方传入的 OutputStream");
            assertTrue(out.size() > 0, "应已写出内容");
        }
    }

    @Nested
    @DisplayName("Graphviz DOT 导出")
    class DotTest {

        @Test
        @DisplayName("graph 包裹、边只输出一次、自环只输出一条")
        void toDotFormat() {
            UndirectedGraph g = new UndirectedGraph(3);
            g.addEdge(0, 1);
            g.addEdge(2, 2);
            String dot = GraphIO.toDot(g);
            assertTrue(dot.startsWith("graph {"));
            assertTrue(dot.trim().endsWith("}"));
            assertEquals(1, occurrences(dot, "0 -- 1"));
            assertEquals(0, occurrences(dot, "1 -- 0"), "无向边只写一个方向");
            assertEquals(1, occurrences(dot, "2 -- 2"), "自环的两个端点相同,只画一条");
            assertTrue(dot.contains("node[shape=circle"), "应带默认节点样式");
        }

        @Test
        @DisplayName("空图导出后只有 graph 头、节点样式与收尾,没有边行")
        void toDotEmptyGraph() {
            String dot = GraphIO.toDot(new UndirectedGraph(0));
            String[] lines = dot.split("\\r?\\n");
            assertEquals("graph {", lines[0]);
            assertEquals("}", lines[lines.length - 1]);
            assertEquals(0, occurrences(dot, "--"));
            assertEquals(0, occurrences(dot, " -- "));
        }
    }

    @Nested
    @DisplayName("加权图读写")
    class WeightedIoTest {

        private static final String TINY_EWG_PATH = "tinyEWG.txt";

        @Test
        @DisplayName("readWeightedFile(tinyEWG.txt):V=8、E=16、逐点度数与样例一致")
        void readTinyEWGFile() {
            File file = new File(TINY_EWG_PATH);
            assertTrue(file.isFile(), "需要工作区根目录下存在 " + TINY_EWG_PATH);
            EdgeWeightedGraph g = GraphIO.readWeightedFile(TINY_EWG_PATH);
            assertEquals(8, g.V());
            assertEquals(16, g.E());
            assertEquals(4, g.degree(0));
            assertEquals(4, g.degree(4));
            assertEquals(5, g.degree(2));
            assertEquals(5, g.degree(7));
            assertEquals(32, g.degreeSum());
            assertEquals(0, g.selfLoopCount());
        }

        @Test
        @DisplayName("parseWeighted / readWeighted(StringReader) / readWeighted(字节流) 三者一致")
        void threeEntryPointsAgree() {
            String text = "3\n3\n0 1 0.5\n1 2 1.5\n0 2 2.5\n";
            EdgeWeightedGraph a = GraphIO.parseWeighted(text);
            EdgeWeightedGraph b = GraphIO.readWeighted(new StringReader(text));
            EdgeWeightedGraph c = GraphIO.readWeighted(new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8"))));
            assertSameWeightedStructure(a, b);
            assertSameWeightedStructure(a, c);
            assertEquals(a.toString(), c.toString(), "同一份输入,邻接表顺序也应一致");
        }

        @Test
        @DisplayName("往返:parseWeighted(format(g)) 的 V/E/边集/度数一致(含负权与自环)")
        void roundTrip() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(5);
            g.addEdge(0, 1, 0.35);
            g.addEdge(0, 1, 0.37);      // 平行边
            g.addEdge(2, 2, -1.5);      // 自环 + 负权
            g.addEdge(3, 4, 0.0);
            g.addEdge(4, 0, 12.25);

            EdgeWeightedGraph copy = GraphIO.parseWeighted(GraphIO.format(g));
            assertSameWeightedStructure(g, copy);
            assertEquals(g.E(), copy.E());
            assertSameEdgeMultiset(g, copy);
        }

        @Test
        @DisplayName("write 到文件再 readWeightedFile 回来,结构一致")
        void writeAndReadFile() {
            EdgeWeightedGraph g = GraphIO.parseWeighted("4\n3\n0 1 0.25\n1 2 -0.5\n2 2 0.75\n");
            File dir = new File("target/graph-io-test");
            assertTrue(dir.isDirectory() || dir.mkdirs(), "无法创建测试目录:" + dir.getAbsolutePath());
            File file = new File(dir, "weighted-round-trip-" + System.nanoTime() + ".txt");
            try {
                GraphIO.write(g, file.getPath());
                assertTrue(file.isFile());
                EdgeWeightedGraph back = GraphIO.readWeightedFile(file.getPath());
                assertSameWeightedStructure(g, back);
                assertSameEdgeMultiset(g, back);
                assertEquals(-0.5, back.weightOf(1, 2), 1e-12);
            }
            finally {
                assertTrue(!file.exists() || file.delete(), "测试文件应能删除:" + file.getAbsolutePath());
            }
        }

        @Test
        @DisplayName("格式错误:空输入、缺边数、三元组数目不符、非整数端点、非实数权值、NaN/Infinity、越界、null")
        void parseWeightedErrors() {
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted(""));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("   "));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("5"), "缺边数");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("5 x"), "边数非整数");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("5 -1"), "边数为负");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("5 2 0 1 0.5"), "三元组少于声明");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("5 1 0 1 0.5 2 3"), "三元组多于声明");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("5 1 0 1 abc"), "权值非实数");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("5 1 x 1 0.5"), "端点非整数");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("5 1 0 1 NaN"), "NaN 权值");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("5 1 0 1 Infinity"), "无穷权值");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("5 1 0 5 0.5"), "端点越界");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted("-3 0"), "顶点数为负");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeighted(null), "文本为 null");

            assertThrows(IllegalArgumentException.class, () -> GraphIO.readWeightedFile(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readWeightedFile("no-such-weighted-file.txt"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readWeighted((Reader) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readWeighted((InputStream) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.format((EdgeWeightedGraph) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.toDot((EdgeWeightedGraph) null));
        }

        @Test
        @DisplayName("加权 DOT:边带上权值标签,每条边一次")
        void toDotWeighted() {
            EdgeWeightedGraph g = new EdgeWeightedGraph(3);
            g.addEdge(0, 1, 0.35);
            g.addEdge(1, 2, 1.5);
            String dot = GraphIO.toDot(g);
            assertTrue(dot.startsWith("graph {"), dot);
            assertTrue(dot.contains("0 -- 1 [label=\"0.35\"]"), dot);
            assertTrue(dot.contains("1 -- 2 [label=\"1.5\"]"), dot);
            assertEquals(1, occurrences(dot, "0 -- 1"));
            assertEquals(0, occurrences(dot, "1 -- 0"), "无向边只写一个方向");
        }

        @Test
        @DisplayName("调用方传入的 Reader 不被关闭")
        void callerOwnedReaderNotClosed() {
            TrackingReader reader = new TrackingReader("2\n1\n0 1 0.5\n");
            EdgeWeightedGraph g = GraphIO.readWeighted(reader);
            assertEquals(2, g.V());
            assertFalse(reader.closed, "GraphIO 不应关闭调用方传入的 Reader");
            reader.close();
        }

        /** V、E、逐点度数、边集(按字符串排序)一致 */
        private void assertSameWeightedStructure(EdgeWeightedGraph expected, EdgeWeightedGraph actual) {
            assertEquals(expected.V(), actual.V(), "顶点数");
            assertEquals(expected.E(), actual.E(), "边数");
            assertEquals(expected.degreeSum(), actual.degreeSum(), "总度数");
            assertEquals(expected.selfLoopCount(), actual.selfLoopCount(), "自环数");
            for (int v = 0; v < expected.V(); v++) {
                assertEquals(expected.degree(v), actual.degree(v), "顶点 " + v + " 的度数");
            }
            assertSameEdgeMultiset(expected, actual);
        }

        /** 边按 "v-w weight" 字符串排序后逐条比对(端点归一,便于跨实现比较) */
        private void assertSameEdgeMultiset(EdgeWeightedGraph expected, EdgeWeightedGraph actual) {
            List<String> a = normalizedEdges(expected);
            List<String> b = normalizedEdges(actual);
            assertEquals(a, b, "边集(含权值)不一致");
        }

        private List<String> normalizedEdges(EdgeWeightedGraph g) {
            List<String> list = new ArrayList<String>();
            for (Edge e : g.edges()) {
                int v = e.either();
                int w = e.other(v);
                list.add(Math.min(v, w) + "-" + Math.max(v, w) + " " + e.weight());
            }
            java.util.Collections.sort(list);
            return list;
        }
    }

    @Nested
    @DisplayName("有向加权图读写")
    class WeightedDigraphIoTest {

        private static final String TINY_EWD_PATH = "tinyEWD.txt";

        @Test
        @DisplayName("readWeightedDigraphFile(tinyEWD.txt):V=8、E=15,出度/入度与样例一致")
        void readTinyEWDFile() {
            File file = new File(TINY_EWD_PATH);
            assertTrue(file.isFile(), "需要工作区根目录下存在 " + TINY_EWD_PATH);
            EdgeWeightedDigraph g = GraphIO.readWeightedDigraphFile(TINY_EWD_PATH);
            assertEquals(8, g.V());
            assertEquals(15, g.E());
            assertEquals(2, g.outDegree(0));
            assertEquals(1, g.inDegree(0));
            assertEquals(3, g.outDegree(5));
            assertEquals(2, g.inDegree(5));
            assertEquals(3, g.outDegree(6));
            assertEquals(1, g.inDegree(6));
            assertEquals(15, g.outDegreeSum());
        }

        @Test
        @DisplayName("三种入口一致(文本 / 字符流 / 字节流)")
        void threeEntryPointsAgree() {
            String text = "3\n3\n0 1 0.5\n1 2 1.5\n2 0 2.5\n";
            EdgeWeightedDigraph a = GraphIO.parseWeightedDigraph(text);
            EdgeWeightedDigraph b = GraphIO.readWeightedDigraph(new StringReader(text));
            EdgeWeightedDigraph c = GraphIO.readWeightedDigraph(new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8"))));
            assertSameDirectedStructure(a, b);
            assertSameDirectedStructure(a, c);
            assertEquals(a.toString(), c.toString(), "同一份输入,邻接表顺序也应一致");
        }

        @Test
        @DisplayName("格式与无向加权图相同:同一个文本,解析成有向图与无向图的边数一致、解释不同")
        void sameTextFormatAsUndirected() {
            String text = "3\n2\n0 1 0.5\n1 2 1.5\n";
            EdgeWeightedDigraph digraph = GraphIO.parseWeightedDigraph(text);
            EdgeWeightedGraph undirected = GraphIO.parseWeighted(text);
            assertEquals(undirected.V(), digraph.V());
            assertEquals(undirected.E(), digraph.E());
            assertTrue(digraph.hasEdge(0, 1));
            assertFalse(digraph.hasEdge(1, 0), "有向图里反向边不存在");
            assertTrue(undirected.hasEdge(1, 0), "无向图里反向可达");
        }

        @Test
        @DisplayName("往返:parseWeightedDigraph(format(g)) 的 V/E/边三元组一致(含自环与平行边)")
        void roundTrip() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(5);
            g.addEdge(0, 1, 0.35);
            g.addEdge(0, 1, 0.37);      // 平行边
            g.addEdge(2, 2, 1.5);       // 自环
            g.addEdge(4, 0, 12.25);
            g.addEdge(1, 0, 0.0);       // 反向 + 零权

            EdgeWeightedDigraph copy = GraphIO.parseWeightedDigraph(GraphIO.format(g));
            assertSameDirectedStructure(g, copy);
            assertEquals(g.E(), copy.E());
            assertEquals(1, copy.selfLoopCount());
        }

        @Test
        @DisplayName("write 到文件再 readWeightedDigraphFile 回来,结构一致")
        void writeAndReadFile() {
            EdgeWeightedDigraph g = GraphIO.parseWeightedDigraph("4\n3\n0 1 0.25\n1 2 0.5\n3 0 0.75\n");
            File dir = new File("target/graph-io-test");
            assertTrue(dir.isDirectory() || dir.mkdirs(), "无法创建测试目录:" + dir.getAbsolutePath());
            File file = new File(dir, "digraph-round-trip-" + System.nanoTime() + ".txt");
            try {
                GraphIO.write(g, file.getPath());
                assertTrue(file.isFile());
                EdgeWeightedDigraph back = GraphIO.readWeightedDigraphFile(file.getPath());
                assertSameDirectedStructure(g, back);
                assertEquals(0.25, back.weightOf(0, 1), 1e-12);
            }
            finally {
                assertTrue(!file.exists() || file.delete(), "测试文件应能删除:" + file.getAbsolutePath());
            }
        }

        @Test
        @DisplayName("format 的形态:第 1 行 V、第 2 行 E,随后每行 from to weight")
        void formatShape() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            g.addEdge(0, 2, 1.5);
            String[] lines = GraphIO.format(g).split("\\r?\\n");
            assertEquals("3", lines[0]);
            assertEquals("1", lines[1]);
            assertEquals("0 2 1.5", lines[2]);
            assertEquals(3, lines.length);
        }

        @Test
        @DisplayName("格式错误与 null 参数:与无向加权图同一套校验")
        void errors() {
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeightedDigraph(""));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeightedDigraph("5"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeightedDigraph("5 1 0 1 0.5 2 3"), "三元组多于声明");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeightedDigraph("5 1 0 1 abc"), "权值非实数");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeightedDigraph("5 1 0 1 NaN"), "NaN 权值");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeightedDigraph("5 1 0 5 0.5"), "端点越界");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseWeightedDigraph(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readWeightedDigraphFile(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readWeightedDigraphFile("no-such-digraph.txt"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readWeightedDigraph((Reader) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readWeightedDigraph((InputStream) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.format((EdgeWeightedDigraph) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.toDot((EdgeWeightedDigraph) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.write((EdgeWeightedDigraph) null, "target/x.txt"));
        }

        @Test
        @DisplayName("有向 DOT:digraph + 箭头 + 权值标签")
        void toDotDigraph() {
            EdgeWeightedDigraph g = new EdgeWeightedDigraph(3);
            g.addEdge(0, 1, 0.35);
            g.addEdge(1, 0, 1.5);
            String dot = GraphIO.toDot(g);
            assertTrue(dot.startsWith("digraph {"), dot);
            assertTrue(dot.contains("0 -> 1 [label=\"0.35\"]"), dot);
            assertTrue(dot.contains("1 -> 0 [label=\"1.5\"]"), dot);
            assertEquals(1, occurrences(dot, "0 -> 1"));
            assertEquals(1, occurrences(dot, "1 -> 0"), "反向边是另一条边,必须各自输出");
        }

        /** V、E、出度、入度、自环数与边三元组多重集一致 */
        private void assertSameDirectedStructure(EdgeWeightedDigraph expected, EdgeWeightedDigraph actual) {
            assertEquals(expected.V(), actual.V(), "顶点数");
            assertEquals(expected.E(), actual.E(), "边数");
            assertEquals(expected.outDegreeSum(), actual.outDegreeSum(), "出度之和");
            assertEquals(expected.selfLoopCount(), actual.selfLoopCount(), "自环数");
            for (int v = 0; v < expected.V(); v++) {
                assertEquals(expected.outDegree(v), actual.outDegree(v), "顶点 " + v + " 的出度");
                assertEquals(expected.inDegree(v), actual.inDegree(v), "顶点 " + v + " 的入度");
            }
            List<String> a = new ArrayList<String>();
            List<String> b = new ArrayList<String>();
            for (DirectedEdge e : expected.edges()) {
                a.add(e.from() + "->" + e.to() + " " + e.weight());
            }
            for (DirectedEdge e : actual.edges()) {
                b.add(e.from() + "->" + e.to() + " " + e.weight());
            }
            java.util.Collections.sort(a);
            java.util.Collections.sort(b);
            assertEquals(a, b, "边多重集(含方向与权值)不一致");
        }
    }

    @Nested
    @DisplayName("AOV 网读写")
    class AovIoTest {

        /** 教材 p.480 表 9-1 / 图 9-20 的课程 AOV 网 */
        private static final String AOV_PATH = "coursesAOV.txt";

        @Test
        @DisplayName("readAovFile(coursesAOV.txt):11 门课、11 条约束,编号与图 9-20 一致")
        void readTextbookFile() {
            File file = new File(AOV_PATH);
            assertTrue(file.isFile(), "需要工作区根目录下存在 " + AOV_PATH);
            AOVNetwork net = GraphIO.readAovFile(AOV_PATH);
            assertEquals(11, net.activityCount());
            assertEquals(11, net.precedenceCount());
            assertEquals("线性代数", net.nameOf(0));
            assertEquals("数据结构", net.nameOf(3));
            assertEquals("数据结构课程设计(Java语言实现)", net.nameOf(9));
            assertEquals("Android应用开发", net.nameOf(10));
            assertEquals(Arrays.asList("高等数学", "线性代数"), net.predecessorNames("离散数学"));
            assertEquals(Arrays.asList("Java语言编程", "数据结构"), net.predecessorNames("Android操作系统"));
        }

        @Test
        @DisplayName("三种入口一致(文本 / 字符流 / 字节流)")
        void threeEntryPointsAgree() {
            String text = "A\nB: A\nC: A, B\n";
            AOVNetwork a = GraphIO.parseAov(text);
            AOVNetwork b = GraphIO.readAov(new StringReader(text));
            AOVNetwork c = GraphIO.readAov(new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8"))));
            assertEquals(a.toString(), b.toString());
            assertEquals(a.toString(), c.toString());
            assertEquals(3, a.activityCount());
            assertEquals(3, a.precedenceCount(), "A→B、A→C、B→C 三条约束");
        }

        @Test
        @DisplayName("往返无损:孤立活动也保留,约束集合不变")
        void roundTripIsLossless() {
            AOVNetwork net = new AOVNetwork(new String[]{"甲", "乙", "丙", "孤立活动"});
            net.addPrecedence("甲", "乙");
            net.addPrecedence("乙", "丙");
            net.addPrecedence("甲", "丙");

            String text = GraphIO.format(net);
            assertTrue(text.contains("孤立活动"), "孤立活动必须出现在文本里:\n" + text);
            AOVNetwork copy = GraphIO.parseAov(text);

            assertEquals(net.activities(), copy.activities(), "活动顺序(编号)一致");
            assertEquals(net.activityCount(), copy.activityCount());
            assertEquals(net.precedenceCount(), copy.precedenceCount());
            for (String activity : net.activities()) {
                assertEquals(new java.util.HashSet<String>(net.predecessorNames(activity)),
                        new java.util.HashSet<String>(copy.predecessorNames(activity)),
                        activity + " 的先修集合");
            }
        }

        @Test
        @DisplayName("format 形态:每个活动一行,有先修时写「活动: 先修1, 先修2」")
        void formatShape() {
            AOVNetwork net = new AOVNetwork(new String[]{"A", "B"});
            net.addPrecedence("A", "B");
            String[] lines = GraphIO.format(net).split("\\r?\\n");
            assertEquals(2, lines.length);
            assertEquals("A", lines[0]);
            assertEquals("B: A", lines[1]);
        }

        @Test
        @DisplayName("两遍解析:先修可以写在后面(支持前向引用)")
        void forwardReference() {
            AOVNetwork net = GraphIO.parseAov("后续课程: 基础课程\n基础课程\n");
            assertEquals(2, net.activityCount());
            assertEquals(0, net.indexOf("后续课程"), "行的先后决定编号");
            assertEquals(1, net.indexOf("基础课程"));
            assertEquals(Arrays.asList("基础课程"), net.predecessorNames("后续课程"));
        }

        @Test
        @DisplayName("容忍全角标点与教材里表示「无先修」的破折号")
        void punctuationTolerance() {
            AOVNetwork net = GraphIO.parseAov("# 注释\n甲: —\n乙：甲，丙\n丙\n\n");
            assertEquals(3, net.activityCount());
            assertEquals(2, net.precedenceCount());
            assertEquals(Arrays.asList("甲", "丙"), net.predecessorNames("乙"),
                    "「乙：甲，丙」表示甲、丙都是乙的先修");
            assertEquals(0, net.predecessorNames("甲").size(), "「甲: —」表示甲没有先修");
        }

        @Test
        @DisplayName("格式错误:空活动名、重复活动、未知先修、自己先于自己都能定位到行")
        void parseErrors() {
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseAov(": 甲\n"));
            IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseAov("甲\n甲\n"));
            assertTrue(duplicate.getMessage().contains("第 2 行"), duplicate.getMessage());
            IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseAov("甲\n乙: 不存在的课\n"));
            assertTrue(unknown.getMessage().contains("第 2 行"), unknown.getMessage());
            assertTrue(unknown.getMessage().contains("不在 AOV 网中"), unknown.getMessage());
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseAov("甲: 甲\n"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseAov(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readAovFile(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readAovFile("no-such-aov.txt"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readAov((Reader) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readAov((InputStream) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.format((AOVNetwork) null));
        }

        @Test
        @DisplayName("纯注释与空行的文件解析成空网")
        void commentsOnly() {
            AOVNetwork empty = GraphIO.parseAov("# 只有注释\n\n   \n");
            assertEquals(0, empty.activityCount());
            assertEquals(0, empty.precedenceCount());
        }

        @Test
        @DisplayName("write 到文件再读回来,结构与约束一致")
        void writeAndReadFile() {
            AOVNetwork net = GraphIO.parseAov("甲\n乙: 甲\n丙: 甲, 乙\n");
            File dir = new File("target/graph-io-test");
            assertTrue(dir.isDirectory() || dir.mkdirs(), "无法创建测试目录:" + dir.getAbsolutePath());
            File file = new File(dir, "aov-round-trip-" + System.nanoTime() + ".txt");
            try {
                GraphIO.write(net, file.getPath());
                assertTrue(file.isFile());
                AOVNetwork back = GraphIO.readAovFile(file.getPath());
                assertEquals(net.activities(), back.activities());
                assertEquals(net.precedenceCount(), back.precedenceCount());
                assertEquals(Arrays.asList("甲", "乙"), back.predecessorNames("丙"));
            }
            finally {
                assertTrue(!file.exists() || file.delete(), "测试文件应能删除:" + file.getAbsolutePath());
            }
        }

        @Test
        @DisplayName("调用方传入的 Reader 不被关闭")
        void callerOwnedReaderNotClosed() {
            TrackingReader reader = new TrackingReader("甲\n乙: 甲\n");
            AOVNetwork net = GraphIO.readAov(reader);
            assertEquals(2, net.activityCount());
            assertFalse(reader.closed, "GraphIO 不应关闭调用方传入的 Reader");
            reader.close();
        }
    }

    @Nested
    @DisplayName("AOE 网读写")
    class AoeIoTest {

        /** 教材 p.484 图 9-24 的 AOE 网 */
        private static final String AOE_PATH = "projectAOE.txt";

        @Test
        @DisplayName("readAoeFile(projectAOE.txt):13 个事件、18 个活动,弧与工期逐条核对")
        void readTextbookFile() {
            File file = new File(AOE_PATH);
            assertTrue(file.isFile(), "需要工作区根目录下存在 " + AOE_PATH);
            AOENetwork net = GraphIO.readAoeFile(AOE_PATH);
            assertEquals(13, net.eventCount());
            assertEquals(18, net.activityCount());
            assertEquals("E0", net.eventName(0));
            assertEquals("E12", net.eventName(12));
            assertEquals("E0", net.eventName(net.activityFrom("A0")));
            assertEquals("E1", net.eventName(net.activityTo("A0")));
            assertEquals(1.0, net.activityDuration("A0"), 0.0);
            assertEquals("E7", net.eventName(net.activityFrom("A9")));
            assertEquals("E6", net.eventName(net.activityTo("A9")));
            assertEquals(6.0, net.activityDuration("A9"), 0.0);
            assertEquals(9.0, net.activityDuration("A12"), 0.0);
            assertEquals(Arrays.asList(0), net.sourceEvents());
            assertEquals(Arrays.asList(12), net.sinkEvents());
        }

        @Test
        @DisplayName("三种入口一致(文本 / 字符流 / 字节流)")
        void threeEntryPointsAgree() {
            String text = "E0 E1 E2\nA0 E0 E1 3\nA1 E1 E2 4\n";
            AOENetwork a = GraphIO.parseAoe(text);
            AOENetwork b = GraphIO.readAoe(new StringReader(text));
            AOENetwork c = GraphIO.readAoe(new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8"))));
            assertEquals(a.toString(), b.toString());
            assertEquals(a.toString(), c.toString());
            assertEquals(3, a.eventCount());
            assertEquals(2, a.activityCount());
        }

        @Test
        @DisplayName("往返无损:孤立事件也保留,活动与工期不变")
        void roundTripIsLossless() {
            AOENetwork net = new AOENetwork();
            net.addEvent("E0");
            net.addEvent("E1");
            net.addEvent("E2");
            net.addEvent("孤立事件");
            net.addActivity("A0", "E0", "E1", 2);
            net.addActivity("A1", "E1", "E2", 0.5);

            String text = GraphIO.format(net);
            assertTrue(text.contains("孤立事件"), "孤立事件必须出现在事件清单里:\n" + text);
            AOENetwork copy = GraphIO.parseAoe(text);

            assertEquals(net.eventNames(), copy.eventNames(), "事件编号顺序一致");
            assertEquals(net.activityNames(), copy.activityNames());
            for (String activity : net.activityNames()) {
                assertEquals(net.activityDuration(activity), copy.activityDuration(activity), 0.0);
                assertEquals(net.eventName(net.activityFrom(activity)),
                        copy.eventName(copy.activityFrom(activity)));
                assertEquals(net.eventName(net.activityTo(activity)),
                        copy.eventName(copy.activityTo(activity)));
            }
        }

        @Test
        @DisplayName("format 形态:第一行事件清单,其后每个活动一行")
        void formatShape() {
            AOENetwork net = new AOENetwork();
            net.addEvent("E0");
            net.addEvent("E1");
            net.addActivity("A0", "E0", "E1", 1.0);
            String[] lines = GraphIO.format(net).split("\\r?\\n");
            assertEquals(2, lines.length);
            assertEquals("E0 E1", lines[0]);
            assertEquals("A0 E0 E1 1.0", lines[1]);
        }

        @Test
        @DisplayName("空行与行内 # 注释都被忽略")
        void commentsAndBlankLines() {
            AOENetwork net = GraphIO.parseAoe(
                    "# 事件清单\n\nE0 E1 E2   # 三个事件\n\nA0 E0 E1 3   # 第一条活动\nA1 E1 E2 4\n");
            assertEquals(3, net.eventCount());
            assertEquals(2, net.activityCount());
            assertEquals(3.0, net.activityDuration("A0"), 0.0);
        }

        @Test
        @DisplayName("格式错误:缺少事件清单、活动行项数不对、事件不存在、工期非法都能定位到行")
        void parseErrors() {
            IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseAoe("# 只有注释\n\n"));
            assertTrue(missing.getMessage().contains("事件清单"), missing.getMessage());

            IllegalArgumentException tokens = assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseAoe("E0 E1\nA0 E0 E1\n"));
            assertTrue(tokens.getMessage().contains("第 2 行"), tokens.getMessage());

            IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseAoe("E0 E1\nA0 E0 E9 3\n"));
            assertTrue(unknown.getMessage().contains("第 2 行"), unknown.getMessage());
            assertTrue(unknown.getMessage().contains("E9"), unknown.getMessage());

            IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseAoe("E0 E1\nA0 E0 E1 -3\n"));
            assertTrue(negative.getMessage().contains("工期不能为负"), negative.getMessage());

            assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseAoe("E0 E1\nA0 E0 E1 abc\n"), "工期非数字");
            assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseAoe("E0 E0\n"), "事件重名");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseAoe(null));
        }

        @Test
        @DisplayName("write 到文件再读回来,事件与活动一致")
        void writeAndReadFile() {
            AOENetwork net = GraphIO.parseAoe("E0 E1 E2\nA0 E0 E1 2\nA1 E1 E2 3\n");
            File dir = new File("target/graph-io-test");
            assertTrue(dir.isDirectory() || dir.mkdirs(), "无法创建测试目录:" + dir.getAbsolutePath());
            File file = new File(dir, "aoe-round-trip-" + System.nanoTime() + ".txt");
            try {
                GraphIO.write(net, file.getPath());
                assertTrue(file.isFile());
                AOENetwork back = GraphIO.readAoeFile(file.getPath());
                assertEquals(net.eventNames(), back.eventNames());
                assertEquals(net.activityNames(), back.activityNames());
                assertEquals(3.0, back.activityDuration("A1"), 0.0);
            }
            finally {
                assertTrue(!file.exists() || file.delete(), "测试文件应能删除:" + file.getAbsolutePath());
            }
        }

        @Test
        @DisplayName("参数为 null 抛 IllegalArgumentException")
        void nullArguments() {
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readAoeFile(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readAoeFile("no-such-aoe.txt"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readAoe((Reader) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readAoe((InputStream) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.format((AOENetwork) null));
            assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.write((AOENetwork) null, "target/x.txt"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.write(new AOENetwork(), (String) null));
        }

        @Test
        @DisplayName("调用方传入的 Reader 不被关闭")
        void callerOwnedReaderNotClosed() {
            TrackingReader reader = new TrackingReader("E0 E1\nA0 E0 E1 1\n");
            AOENetwork net = GraphIO.readAoe(reader);
            assertEquals(2, net.eventCount());
            assertFalse(reader.closed, "GraphIO 不应关闭调用方传入的 Reader");
            reader.close();
        }
    }

    @Nested
    @DisplayName("有向图(无权)读写")
    class DigraphIoTest {

        @Test
        @DisplayName("parseDigraph:V、E 与方向都对")
        void parse() {
            Digraph graph = GraphIO.parseDigraph("4\n3\n0 1\n1 2\n3 0\n");
            assertEquals(4, graph.V());
            assertEquals(3, graph.E());
            assertTrue(graph.hasEdge(0, 1));
            assertFalse(graph.hasEdge(1, 0), "有向边的反方向不应存在");
            assertEquals(1, graph.outDegree(0));
            assertEquals(1, graph.inDegree(0), "3->0 汇入 0");
            assertEquals(0, graph.inDegree(3));
            assertEquals(1, graph.inDegree(2));
        }

        @Test
        @DisplayName("三种入口一致(文本 / 字符流 / 字节流)")
        void threeEntryPointsAgree() {
            String text = "4\n3\n0 1\n1 2\n3 0\n";
            Digraph a = GraphIO.parseDigraph(text);
            Digraph b = GraphIO.readDigraph(new StringReader(text));
            Digraph c = GraphIO.readDigraph(new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8"))));
            assertEquals(a.toString(), b.toString());
            assertEquals(a.toString(), c.toString());
        }

        @Test
        @DisplayName("format 形态与往返一致")
        void formatAndRoundTrip() {
            Digraph graph = GraphIO.parseDigraph("4\n3\n0 1\n1 2\n3 0\n");
            String[] lines = GraphIO.format(graph).split("\\r?\\n");
            assertEquals("4", lines[0]);
            assertEquals("3", lines[1]);
            assertEquals("0 1", lines[2]);

            Digraph copy = GraphIO.parseDigraph(GraphIO.format(graph));
            assertEquals(graph.V(), copy.V());
            assertEquals(graph.E(), copy.E());
            assertEquals(edgeStrings(graph), edgeStrings(copy));
        }

        @Test
        @DisplayName("端点数目与声明的边数不符时直接报错")
        void countMismatch() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseDigraph("13\n22\n4 2\n2 3\n3 2\n"));
            assertTrue(e.getMessage().contains("端点"), e.getMessage());
        }

        @Test
        @DisplayName("格式错误与 null 参数")
        void errors() {
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseDigraph(""));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseDigraph("5"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseDigraph("5 1 0 5"), "端点越界");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseDigraph("5 -1"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseDigraph(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readDigraph((Reader) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readDigraph((InputStream) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readDigraphFile(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readDigraphFile("no-such-digraph.txt"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.format((Digraph) null));
            assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.write((Digraph) null, "target/x.txt"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.write(new Digraph(1), (String) null));
        }

        @Test
        @DisplayName("toDot 用 -> 连接有向边")
        void toDot() {
            String dot = GraphIO.toDot(GraphIO.parseDigraph("3\n2\n0 1\n1 2\n"));
            assertTrue(dot.startsWith("digraph {"), dot);
            assertTrue(dot.contains("0 -> 1"), dot);
            assertFalse(dot.contains("0 -- 1"), "有向图不该用无向符号");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.toDot((Digraph) null));
        }

        private List<String> edgeStrings(Digraph graph) {
            List<String> list = new ArrayList<String>();
            for (int[] edge : graph.edges()) {
                list.add(edge[0] + "->" + edge[1]);
            }
            java.util.Collections.sort(list);
            return list;
        }
    }

    @Nested
    @DisplayName("符号图读写")
    class SymbolGraphIoTest {

        @Test
        @DisplayName("parseSymbolGraph:顶点名按首次出现顺序编号")
        void parse() {
            SymbolGraph sg = GraphIO.parseSymbolGraph("甲 乙\n乙 丙\n");
            assertEquals(3, sg.V());
            assertEquals(2, sg.E());
            assertEquals(Arrays.asList("甲", "乙", "丙"), sg.vertices());
            assertEquals(Arrays.asList("乙"), sg.adjacent("甲"));
            assertEquals(1, sg.degree("丙"));
        }

        @Test
        @DisplayName("空行、行内注释与多余空白都忽略")
        void commentsAndWhitespace() {
            SymbolGraph sg = GraphIO.parseSymbolGraph("# 航线\n\n  JFK   ORD \nORD DEN   # 干线\n");
            assertEquals(3, sg.V());
            assertEquals(2, sg.E());
            assertTrue(sg.hasEdge("JFK", "ORD"));
            assertTrue(sg.hasEdge("ORD", "DEN"));
            assertEquals("JFK", sg.nameOf(0));
            assertEquals("DEN", sg.nameOf(2));
        }

        @Test
        @DisplayName("三种入口一致(文本 / 字符流 / 字节流)")
        void threeEntryPointsAgree() {
            String text = "甲 乙\n乙 丙\n";
            SymbolGraph a = GraphIO.parseSymbolGraph(text);
            SymbolGraph b = GraphIO.readSymbolGraph(new StringReader(text));
            SymbolGraph c = GraphIO.readSymbolGraph(
                    new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8"))));
            assertEquals(a.toString(), b.toString());
            assertEquals(a.toString(), c.toString());
        }

        @Test
        @DisplayName("format 往返:顶点、边数与平行边重数都保持")
        void roundTrip() {
            SymbolGraph sg = GraphIO.parseSymbolGraph("甲 乙\n甲 乙\n乙 丙\n");
            String text = GraphIO.format(sg);
            assertEquals(3, text.split("\\r?\\n").length, "三条边各一行");
            SymbolGraph copy = GraphIO.parseSymbolGraph(text);
            assertEquals(sg.V(), copy.V());
            assertEquals(sg.E(), copy.E());
            assertEquals(2, copy.countEdges("甲", "乙"));
            assertEquals(Arrays.asList("甲", "乙", "丙"), copy.vertices());
        }

        @Test
        @DisplayName("routes.txt:11 个机场、14 条航线")
        void routesFile() {
            File file = new File("routes.txt");
            assertTrue(file.isFile(), "需要工作区根目录下存在 routes.txt");
            SymbolGraph sg = GraphIO.readSymbolGraphFile("routes.txt");
            assertEquals(11, sg.V());
            assertEquals(14, sg.E());
            assertEquals("JFK", sg.nameOf(0));
            assertEquals("MEX", sg.nameOf(10));
        }

        @Test
        @DisplayName("写完再读回来一致")
        void writeAndReadFile() {
            SymbolGraph sg = GraphIO.parseSymbolGraph("甲 乙\n乙 丙\n");
            File dir = new File("target/graph-io-test");
            assertTrue(dir.isDirectory() || dir.mkdirs(), "无法创建测试目录:" + dir.getAbsolutePath());
            File file = new File(dir, "symbol-round-trip-" + System.nanoTime() + ".txt");
            try {
                GraphIO.write(sg, file.getPath());
                SymbolGraph back = GraphIO.readSymbolGraphFile(file.getPath());
                assertEquals(sg.vertices(), back.vertices());
                assertEquals(sg.E(), back.E());
            }
            finally {
                assertTrue(!file.exists() || file.delete(), "测试文件应能删除:" + file.getAbsolutePath());
            }
        }

        @Test
        @DisplayName("每行必须是两个名字,错误能定位到行")
        void parseErrors() {
            IllegalArgumentException three = assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseSymbolGraph("甲 乙\n甲 乙 丙\n"));
            assertTrue(three.getMessage().contains("第 2 行"), three.getMessage());
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseSymbolGraph("甲\n"),
                    "只有一个名字的行无法确定边");
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseSymbolGraph(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readSymbolGraph((Reader) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readSymbolGraph((InputStream) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readSymbolGraphFile(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readSymbolGraphFile("no-such-symbol.txt"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.format((SymbolGraph) null));
        }

        @Test
        @DisplayName("调用方传入的 Reader 不被关闭")
        void callerOwnedReaderNotClosed() {
            TrackingReader reader = new TrackingReader("甲 乙\n");
            SymbolGraph sg = GraphIO.readSymbolGraph(reader);
            assertEquals(2, sg.V());
            assertFalse(reader.closed, "GraphIO 不应关闭调用方传入的 Reader");
            reader.close();
        }
    }

    @Nested
    @DisplayName("流网络读写")
    class FlowNetworkIoTest {

        @Test
        @DisplayName("readFlowNetworkFile(tinyFN.txt):6 个顶点、8 条弧,容量逐条核对")
        void readTinyFN() {
            File file = new File("tinyFN.txt");
            assertTrue(file.isFile(), "需要工作区根目录下存在 tinyFN.txt");
            FlowNetwork network = GraphIO.readFlowNetworkFile("tinyFN.txt");
            assertEquals(6, network.V());
            assertEquals(8, network.E());
            assertEquals(5.0, network.outCapacity(0), 1e-9, "0->1 (2.0) 与 0->2 (3.0)");
            assertEquals(5.0, network.inCapacity(5), 1e-9, "3->5 (2.0) 与 4->5 (3.0)");
            for (FlowEdge edge : network.edges()) {
                assertEquals(0.0, edge.flow(), 1e-9, "刚读进来时流量为 0");
            }
        }

        @Test
        @DisplayName("三种入口一致(文本 / 字符流 / 字节流)")
        void threeEntryPointsAgree() {
            String text = "3\n2\n0 1 2.5\n1 2 3.5\n";
            FlowNetwork a = GraphIO.parseFlowNetwork(text);
            FlowNetwork b = GraphIO.readFlowNetwork(new StringReader(text));
            FlowNetwork c = GraphIO.readFlowNetwork(
                    new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8"))));
            assertEquals(a.toString(), b.toString());
            assertEquals(a.toString(), c.toString());
            assertEquals(2, a.E());
        }

        @Test
        @DisplayName("format 形态与往返:只写容量,流量不参与持久化")
        void roundTrip() {
            FlowNetwork network = GraphIO.parseFlowNetwork("3\n2\n0 1 2.5\n1 2 3.5\n");
            new FordFulkerson(network, 0, 2);
            String[] lines = GraphIO.format(network).split("\\r?\\n");
            assertEquals("3", lines[0]);
            assertEquals("2", lines[1]);
            assertEquals("0 1 2.5", lines[2]);

            FlowNetwork copy = GraphIO.parseFlowNetwork(GraphIO.format(network));
            assertEquals(network.V(), copy.V());
            assertEquals(network.E(), copy.E());
            for (FlowEdge edge : copy.edges()) {
                assertEquals(0.0, edge.flow(), 1e-9, "重新读入后流量归零");
            }
        }

        @Test
        @DisplayName("格式/语义错误:负容量、自环、项数不对、端点越界都能定位")
        void parseErrors() {
            IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseFlowNetwork("3\n1\n0 1 -2.0\n"));
            assertTrue(negative.getMessage().contains("第 1 条边"), negative.getMessage());
            assertTrue(negative.getMessage().contains("容量不能为负"), negative.getMessage());

            IllegalArgumentException selfLoop = assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.parseFlowNetwork("3\n1\n1 1 2.0\n"));
            assertTrue(selfLoop.getMessage().contains("自环"), selfLoop.getMessage());

            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseFlowNetwork("3\n1\n0 1\n"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseFlowNetwork("3\n1\n0 5 1.0\n"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.parseFlowNetwork(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readFlowNetworkFile(null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readFlowNetworkFile("no-such-fn.txt"));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readFlowNetwork((Reader) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.readFlowNetwork((InputStream) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.format((FlowNetwork) null));
            assertThrows(IllegalArgumentException.class, () -> GraphIO.toDot((FlowNetwork) null));
        }

        @Test
        @DisplayName("注释与空行被忽略(V/E 式格式现在也支持 #)")
        void comments() {
            FlowNetwork network = GraphIO.parseFlowNetwork(
                    "# 源点 0,汇点 2\n3\n2\n0 1 2.5   # 干管\n\n1 2 3.5\n");
            assertEquals(3, network.V());
            assertEquals(2, network.E());
        }

        @Test
        @DisplayName("toDot 标出流量/容量")
        void toDot() {
            FlowNetwork network = GraphIO.parseFlowNetwork("3\n2\n0 1 2.0\n1 2 3.0\n");
            new FordFulkerson(network, 0, 2);
            String dot = GraphIO.toDot(network);
            assertTrue(dot.startsWith("digraph {"), dot);
            assertTrue(dot.contains("0 -> 1 [label=\"2.00/2.00\"]"), dot);
        }

        @Test
        @DisplayName("写完再读回来一致")
        void writeAndReadFile() {
            FlowNetwork network = GraphIO.parseFlowNetwork("3\n2\n0 1 2.5\n1 2 3.5\n");
            File dir = new File("target/graph-io-test");
            assertTrue(dir.isDirectory() || dir.mkdirs(), "无法创建测试目录:" + dir.getAbsolutePath());
            File file = new File(dir, "flow-round-trip-" + System.nanoTime() + ".txt");
            try {
                GraphIO.write(network, file.getPath());
                FlowNetwork back = GraphIO.readFlowNetworkFile(file.getPath());
                assertEquals(network.V(), back.V());
                assertEquals(network.E(), back.E());
                assertEquals(2.5, back.outCapacity(0), 1e-9);
            }
            finally {
                assertTrue(!file.exists() || file.delete(), "测试文件应能删除:" + file.getAbsolutePath());
            }
        }

        @Test
        @DisplayName("调用方传入的 Reader 不被关闭")
        void callerOwnedReaderNotClosed() {
            TrackingReader reader = new TrackingReader("3\n1\n0 1 1.0\n");
            FlowNetwork network = GraphIO.readFlowNetwork(reader);
            assertEquals(3, network.V());
            assertFalse(reader.closed, "GraphIO 不应关闭调用方传入的 Reader");
            reader.close();
        }
    }

    @Nested
    @DisplayName("其它导出:网络类图与邻接矩阵")
    class ExportTest {

        @Test
        @DisplayName("toDot(AOVNetwork):顶点用活动名,弧是先后约束")
        void toDotAov() {
            AOVNetwork network = GraphIO.readAovFile("coursesAOV.txt");
            String dot = GraphIO.toDot(network);
            assertTrue(dot.startsWith("digraph {"), dot);
            assertTrue(dot.contains("\"线性代数\""), dot);
            assertTrue(dot.contains("\"高等数学\" -> \"离散数学\""), dot);
            assertThrows(IllegalArgumentException.class, () -> GraphIO.toDot((AOVNetwork) null));
        }

        @Test
        @DisplayName("toDot(AOENetwork):弧上标出活动名与工期")
        void toDotAoe() {
            AOENetwork network = GraphIO.readAoeFile("projectAOE.txt");
            String dot = GraphIO.toDot(network);
            assertTrue(dot.startsWith("digraph {"), dot);
            assertTrue(dot.contains("E0 -> E1 [label=\"A0/1\"]"), dot);
            assertTrue(dot.contains("E10 -> E12 [label=\"A13/7\"]"), dot);
            assertThrows(IllegalArgumentException.class, () -> GraphIO.toDot((AOENetwork) null));
        }

        @Test
        @DisplayName("toDot(SymbolGraph):顶点标签是名字,边用无向符号")
        void toDotSymbolGraph() {
            String dot = GraphIO.toDot(GraphIO.readSymbolGraphFile("routes.txt"));
            assertTrue(dot.startsWith("graph {"), dot);
            assertTrue(dot.contains("\"JFK\""), dot);
            assertTrue(dot.contains("\"JFK\" -- \"ORD\""), dot);
            assertThrows(IllegalArgumentException.class, () -> GraphIO.toDot((SymbolGraph) null));
        }

        @Test
        @DisplayName("邻接矩阵的写出与读回(用加权有向图格式)")
        void matrixFormatAndRoundTrip() {
            EdgeWeightedDigraph list = GraphIO.readWeightedDigraphFile("tinyEWD.txt");
            AdjMatrixEdgeWeightedDigraph matrix = new AdjMatrixEdgeWeightedDigraph(list);
            String[] lines = GraphIO.format(matrix).split("\\r?\\n");
            assertEquals(String.valueOf(matrix.V()), lines[0]);
            assertEquals(String.valueOf(matrix.E()), lines[1]);

            EdgeWeightedDigraph back = GraphIO.parseWeightedDigraph(GraphIO.format(matrix));
            assertEquals(list.V(), back.V());
            assertEquals(list.E(), back.E());
            for (int v = 0; v < list.V(); v++) {
                for (DirectedEdge edge : list.adj(v)) {
                    assertEquals(edge.weight(), back.weightOf(edge.from(), edge.to()), 1e-9);
                }
            }
            assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.format((AdjMatrixEdgeWeightedDigraph) null));
            assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.write((AdjMatrixEdgeWeightedDigraph) null, "target/x.txt"));
            assertThrows(IllegalArgumentException.class,
                    () -> GraphIO.write(matrix, (String) null));
        }

        @Test
        @DisplayName("邻接矩阵写文件再读回")
        void matrixWriteFile() {
            AdjMatrixEdgeWeightedDigraph matrix = new AdjMatrixEdgeWeightedDigraph(
                    GraphIO.readWeightedDigraphFile("tinyEWD.txt"));
            File dir = new File("target/graph-io-test");
            assertTrue(dir.isDirectory() || dir.mkdirs(), "无法创建测试目录:" + dir.getAbsolutePath());
            File file = new File(dir, "matrix-" + System.nanoTime() + ".txt");
            try {
                GraphIO.write(matrix, file.getPath());
                EdgeWeightedDigraph back = GraphIO.readWeightedDigraphFile(file.getPath());
                assertEquals(matrix.V(), back.V());
                assertEquals(matrix.E(), back.E());
            }
            finally {
                assertTrue(!file.exists() || file.delete(), "测试文件应能删除:" + file.getAbsolutePath());
            }
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /**
     * 结构等价:V、E、自环数、总度数、逐点度数、逐点邻接点集合、去重后的边集。
     *
     * <p>刻意<b>不</b>比较邻接表的迭代顺序 —— 写出行是按顶点下标、读入是按边表顺序,
     * 两次装配的插入顺序不同,顺序属于实现细节(结构一致才是契约)。</p>
     */
    private static void assertSameStructure(UndirectedGraph expected, UndirectedGraph actual) {
        assertEquals(expected.V(), actual.V(), "顶点数");
        assertEquals(expected.E(), actual.E(), "边数");
        assertEquals(expected.selfLoopCount(), actual.selfLoopCount(), "自环数");
        assertEquals(expected.degreeSum(), actual.degreeSum(), "总度数");
        assertEquals(expected.maxDegree(), actual.maxDegree(), "最大度数");
        assertEquals(expected.minDegree(), actual.minDegree(), "最小度数");
        for (int v = 0; v < expected.V(); v++) {
            assertEquals(expected.degree(v), actual.degree(v), "顶点 " + v + " 的度数");
            assertEquals(neighborsOf(expected, v), neighborsOf(actual, v), "顶点 " + v + " 的邻接表");
        }
        assertEquals(edgeKeys(expected), edgeKeys(actual), "去重后的边集");
    }

    private static TreeSet<Integer> neighborsOf(UndirectedGraph g, int v) {
        TreeSet<Integer> set = new TreeSet<Integer>();
        for (int w : g.adj(v)) {
            set.add(w);
        }
        return set;
    }

    private static TreeSet<String> edgeKeys(UndirectedGraph g) {
        TreeSet<String> keys = new TreeSet<String>();
        for (int[] e : g.edges()) {
            keys.add(Math.min(e[0], e[1]) + "-" + Math.max(e[0], e[1]));
        }
        return keys;
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    /** 带"是否被关闭"标记的 Reader,用来验证资源归属 */
    private static final class TrackingReader extends StringReader {
        private boolean closed;

        TrackingReader(String text) {
            super(text);
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }
    }

    /** 带"是否被关闭"标记的输入流 */
    private static final class TrackingInputStream extends FilterInputStream {
        private boolean closed;

        TrackingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    /** 带"是否被关闭"标记的内存输出流 */
    private static final class TrackingOutputStream extends java.io.ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
