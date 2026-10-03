package io.github.softwindwf.graph;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * 图的文本/文件读写工具:把"外部文本格式"和"图的数据结构"彻底分开。
 *
 * <p>{@link UndirectedGraph} 只负责图本身(顶点、边、遍历),不认识文件、不解析文本;
 * 本类承担全部格式转换:algs4 文本格式的解析与输出、Graphviz DOT 的导出。
 * 这样做的好处是:数据结构换存储方式(邻接表→邻接矩阵)不必动 IO;
 * 换数据格式(比如改用 CSV、改用加权图的五元组)也只动本类。</p>
 *
 * <p><b>algs4 文本格式</b>(空白分隔,空格/制表/换行等价):</p>
 * <pre>
 * 第一项      顶点数 V
 * 第二项      边数 E
 * 随后 2E 项  每条边的两个端点,依次给出
 * </pre>
 *
 * <p>示例({@code tinyG.txt},13 个顶点、15 条边):</p>
 * <pre>
 * 13
 * 15
 * 0 1
 * 0 2
 * ...
 * 11 12
 * </pre>
 *
 * <p><b>方法一览</b>:</p>
 * <ul>
 *   <li>无权无向图 —— 读:{@link #parse(String)}、{@link #read(Reader)}、{@link #read(InputStream)}、
 *       {@link #readFile(String)}(默认 UTF-8);写:{@link #format(UndirectedGraph)} 等;</li>
 *   <li>无权有向图 —— 读:{@link #parseDigraph(String)}、{@link #readDigraph(InputStream)}、
 *       {@link #readDigraphFile(String)}(格式与无权无向图相同);</li>
 *   <li>符号图 —— 读:{@link #parseSymbolGraph(String)}、{@link #readSymbolGraph(InputStream)}、
 *       {@link #readSymbolGraphFile(String)}(每行一条边、两个名字;顶点按首次出现编号);</li>
 *   <li>加权无向图 —— 读:{@link #parseWeighted(String)}、{@link #readWeighted(InputStream)}、
 *       {@link #readWeightedFile(String)};写:{@link #format(EdgeWeightedGraph)} 等;</li>
 *   <li>加权有向图 —— 读:{@link #parseWeightedDigraph(String)}、
 *       {@link #readWeightedDigraph(InputStream)}、{@link #readWeightedDigraphFile(String)}
 *       (文本格式与加权无向图相同);</li>
 *   <li>AOV 网 —— 读:{@link #parseAov(String)}、{@link #readAovFile(String)};
 *       写:{@link #format(AOVNetwork)} 等;</li>
 *   <li>AOE 网 —— 读:{@link #parseAoe(String)}、{@link #readAoeFile(String)};
 *       写:{@link #format(AOENetwork)} 等;</li>
 *   <li>导出:{@link #toDot(UndirectedGraph)}、{@link #toDot(EdgeWeightedGraph)}、
 *       {@link #toDot(EdgeWeightedDigraph)} 与 {@link #toDot(Digraph)}
 *       (Graphviz DOT,可用 {@code dot -Tsvg} 出图)。</li>
 * </ul>
 *
 * <p><b>命名规律</b>:{@code parse/read/readFile} = 无权无向,{@code …Digraph} = 无权有向,
 * {@code …SymbolGraph} = 符号图,{@code …Weighted} = 加权无向,{@code …WeightedDigraph} = 加权有向,
 * {@code …Aov/…Aoe} = AOV/AOE 网。</p>
 *
 * <p><b>加权图的文本格式</b>(algs4 的 {@code EdgeWeightedGraph(In)})与无权图只差在
 * 每条边多一个权值 —— 前两项仍是 V、E,随后是 {@code 3E} 项:{@code v w weight} 三元组。
 * 权值可以是负数或 0,但必须是有限实数。</p>
 * <pre>
 * 8
 * 16
 * 4 5 0.35
 * 4 7 0.37
 * ...
 * </pre>
 *
 * <p><b>与 {@link UndirectedGraph#toString()} 的区别</b>:{@code toString()} 是给人看的调试视图
 * (首行写 {@code "13 vertices, 15 edges"},逐行列出每个顶点的邻接表,<b>不能</b>再被解析);
 * 本类的 {@link #format(UndirectedGraph)} 才是可交换的数据格式(V、E、边表),可以回读。
 * 两者刻意分开,避免"展示格式"和"数据格式"互相绑死。</p>
 *
 * <p><b>异常约定</b>:参数为 null、文本格式错误(缺项、多项、非整数、边数为负、端点越界)
 * 一律抛 {@link IllegalArgumentException};文件不存在也归入这一类(路径本身有问题),
 * 而读写过程中真正发生的 I/O 故障抛 {@link IllegalStateException}。
 * 由调用方传入的流/Reader/Writer <b>不由本类关闭</b>;本类自己打开的文件流一定关闭。</p>
 *
 * <p><b>输入输出样例</b>:</p>
 * <pre>
 * UndirectedGraph g = GraphIO.readFile("tinyG.txt");
 * System.out.print(GraphIO.toDot(g));            // 画图用
 * GraphIO.write(g, "copy.txt");                  // 写回 algs4 格式
 * </pre>
 *
 * @see UndirectedGraph
 */
public final class GraphIO {

    /** UTF-8:本类默认读写编码 */
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** 工具类不允许实例化 */
    private GraphIO() {
        throw new AssertionError("GraphIO 是工具类,不应该被实例化");
    }

    // ------------------------------------------------------------------
    // 读:文本 / 流 / 文件
    // ------------------------------------------------------------------

    /**
     * 按 algs4 文本格式解析字符串建图。
     *
     * @param text 图数据文本,不能为 null
     * @return 解析出的图
     * @throws IllegalArgumentException 文本为 null、为空、格式错误或端点越界
     */
    public static UndirectedGraph parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("输入文本不能为 null");
        }
        return build(tokenize(text));
    }

    /**
     * 从字符流读取并按 algs4 文本格式建图;<b>流由调用方关闭</b>。
     *
     * @param reader 字符流,不能为 null
     * @return 解析出的图
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static UndirectedGraph read(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("字符流不能为 null");
        }
        try {
            return parse(readAll(reader));
        }
        catch (IOException e) {
            throw new IllegalStateException("读取图数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节流读取(UTF-8)并按 algs4 文本格式建图;<b>流由调用方关闭</b>。
     *
     * @param in 字节流,不能为 null
     * @return 解析出的图
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static UndirectedGraph read(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("输入流不能为 null");
        }
        return read(new InputStreamReader(in, UTF_8));
    }

    /**
     * 按 algs4 文本格式读取文件(UTF-8)。本类负责关闭文件流。
     *
     * @param path 文件路径,不能为 null
     * @return 解析出的图
     * @throws IllegalArgumentException 路径为 null、文件不存在、内容格式错误或端点越界
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static UndirectedGraph readFile(String path) {
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            return read(in);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("文件不存在: " + path, e);
        }
        finally {
            closeQuietly(in);
        }
    }

    // ------------------------------------------------------------------
    // 读:有向图(无权)
    // ------------------------------------------------------------------

    /**
     * 按 algs4 文本格式解析字符串建有向图。<b>格式与无权无向图相同</b>
     * (第一项 V、第二项 E、随后 2E 个端点),区别只在解释成有向边。
     *
     * @param text 图数据文本,不能为 null
     * @return 解析出的有向图
     * @throws IllegalArgumentException 文本为 null、为空、格式错误或端点越界
     */
    public static Digraph parseDigraph(String text) {
        if (text == null) {
            throw new IllegalArgumentException("输入文本不能为 null");
        }
        return buildDigraph(tokenize(text));
    }

    /**
     * 从字符流读取有向图;<b>流由调用方关闭</b>。
     *
     * @param reader 字符流,不能为 null
     * @return 解析出的有向图
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static Digraph readDigraph(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("字符流不能为 null");
        }
        try {
            return parseDigraph(readAll(reader));
        }
        catch (IOException e) {
            throw new IllegalStateException("读取图数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节流读取(UTF-8)有向图;<b>流由调用方关闭</b>。
     *
     * @param in 字节流,不能为 null
     * @return 解析出的有向图
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static Digraph readDigraph(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("输入流不能为 null");
        }
        return readDigraph(new InputStreamReader(in, UTF_8));
    }

    /**
     * 读取有向图文件(UTF-8)。本类负责关闭文件流。
     *
     * @param path 文件路径,不能为 null
     * @return 解析出的有向图
     * @throws IllegalArgumentException 路径为 null、文件不存在、内容格式错误或端点越界
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static Digraph readDigraphFile(String path) {
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            return readDigraph(in);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("文件不存在: " + path, e);
        }
        finally {
            closeQuietly(in);
        }
    }

    // ------------------------------------------------------------------
    // 读:符号图(顶点是字符串名字)
    // ------------------------------------------------------------------

    /**
     * 解析符号图文本:<b>每行一条边,两个顶点名用空白分隔</b>
     * (与 algs4 的 {@code routes.txt} / {@code movies.txt} 一致;{@code #} 注释与空行忽略)。
     *
     * <p>顶点名按<b>首次出现</b>的顺序登记为编号 0、1、2……。
     * 注意:名字要先在某条边里出现才能被登记,所以<b>孤立顶点无法用这种格式表达</b>
     * (需要的话请用 {@link SymbolGraph#addVertex(String)} 添加)。</p>
     *
     * @param text 符号图文本,不能为 null
     * @return 解析出的符号图
     * @throws IllegalArgumentException 文本为 null,或某行不是两个名字
     */
    public static SymbolGraph parseSymbolGraph(String text) {
        if (text == null) {
            throw new IllegalArgumentException("输入文本不能为 null");
        }
        return buildSymbolGraph(text);
    }

    /**
     * 从字符流读取符号图;<b>流由调用方关闭</b>。
     *
     * @param reader 字符流,不能为 null
     * @return 解析出的符号图
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static SymbolGraph readSymbolGraph(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("字符流不能为 null");
        }
        try {
            return parseSymbolGraph(readAll(reader));
        }
        catch (IOException e) {
            throw new IllegalStateException("读取符号图数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节流读取(UTF-8)符号图;<b>流由调用方关闭</b>。
     *
     * @param in 字节流,不能为 null
     * @return 解析出的符号图
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static SymbolGraph readSymbolGraph(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("输入流不能为 null");
        }
        return readSymbolGraph(new InputStreamReader(in, UTF_8));
    }

    /**
     * 读取符号图文件(UTF-8)。本类负责关闭文件流。
     *
     * @param path 文件路径,不能为 null
     * @return 解析出的符号图
     * @throws IllegalArgumentException 路径为 null、文件不存在或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static SymbolGraph readSymbolGraphFile(String path) {
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            return readSymbolGraph(in);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("文件不存在: " + path, e);
        }
        finally {
            closeQuietly(in);
        }
    }

    // ------------------------------------------------------------------
    // 读:流网络
    // ------------------------------------------------------------------

    /**
     * 解析流网络文本:与加权有向图<b>同形</b> —— 第一项 V、第二项 E,随后 E 个
     * {@code from to capacity} 三元组(容量即权值,必须非负有限)。
     *
     * @param text 流网络文本,不能为 null
     * @return 解析出的流网络(流量全部为 0)
     * @throws IllegalArgumentException 文本为 null/为空/格式错误、容量非法、端点越界或自环
     */
    public static FlowNetwork parseFlowNetwork(String text) {
        if (text == null) {
            throw new IllegalArgumentException("输入文本不能为 null");
        }
        return buildFlowNetwork(tokenize(text));
    }

    /**
     * 从字符流读取流网络;<b>流由调用方关闭</b>。
     *
     * @param reader 字符流,不能为 null
     * @return 解析出的流网络
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static FlowNetwork readFlowNetwork(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("字符流不能为 null");
        }
        try {
            return parseFlowNetwork(readAll(reader));
        }
        catch (IOException e) {
            throw new IllegalStateException("读取流网络数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节流读取(UTF-8)流网络;<b>流由调用方关闭</b>。
     *
     * @param in 字节流,不能为 null
     * @return 解析出的流网络
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static FlowNetwork readFlowNetwork(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("输入流不能为 null");
        }
        return readFlowNetwork(new InputStreamReader(in, UTF_8));
    }

    /**
     * 读取流网络文件(UTF-8)。本类负责关闭文件流。
     *
     * @param path 文件路径,不能为 null
     * @return 解析出的流网络
     * @throws IllegalArgumentException 路径为 null、文件不存在或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static FlowNetwork readFlowNetworkFile(String path) {
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            return readFlowNetwork(in);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("文件不存在: " + path, e);
        }
        finally {
            closeQuietly(in);
        }
    }

    // ------------------------------------------------------------------
    // 读:加权图
    // ------------------------------------------------------------------

    /**
     * 按 algs4 加权图文本格式解析字符串建图:V、E、然后 E 个 {@code v w weight} 三元组。
     *
     * @param text 图数据文本,不能为 null
     * @return 解析出的加权图
     * @throws IllegalArgumentException 文本为 null/为空/格式错误、权值非有限实数或端点越界
     */
    public static EdgeWeightedGraph parseWeighted(String text) {
        if (text == null) {
            throw new IllegalArgumentException("输入文本不能为 null");
        }
        return buildWeighted(tokenize(text));
    }

    /**
     * 从字符流读取并按 algs4 加权图格式建图;<b>流由调用方关闭</b>。
     *
     * @param reader 字符流,不能为 null
     * @return 解析出的加权图
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static EdgeWeightedGraph readWeighted(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("字符流不能为 null");
        }
        try {
            return parseWeighted(readAll(reader));
        }
        catch (IOException e) {
            throw new IllegalStateException("读取图数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节流读取(UTF-8)并按 algs4 加权图格式建图;<b>流由调用方关闭</b>。
     *
     * @param in 字节流,不能为 null
     * @return 解析出的加权图
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static EdgeWeightedGraph readWeighted(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("输入流不能为 null");
        }
        return readWeighted(new InputStreamReader(in, UTF_8));
    }

    /**
     * 按 algs4 加权图文本格式读取文件(UTF-8)。本类负责关闭文件流。
     *
     * @param path 文件路径,不能为 null
     * @return 解析出的加权图
     * @throws IllegalArgumentException 路径为 null、文件不存在、内容格式错误或端点越界
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static EdgeWeightedGraph readWeightedFile(String path) {
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            return readWeighted(in);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("文件不存在: " + path, e);
        }
        finally {
            closeQuietly(in);
        }
    }

    // ------------------------------------------------------------------
    // 读:有向加权图
    // ------------------------------------------------------------------

    /**
     * 按 algs4 有向加权图文本格式解析字符串建图。<b>格式与无向加权图完全相同</b>
     * (V、E、然后 E 个 {@code from to weight} 三元组),区别只在解释成有向边。
     *
     * @param text 图数据文本,不能为 null
     * @return 解析出的有向加权图
     * @throws IllegalArgumentException 文本为 null/为空/格式错误、权值非有限实数或端点越界
     */
    public static EdgeWeightedDigraph parseWeightedDigraph(String text) {
        if (text == null) {
            throw new IllegalArgumentException("输入文本不能为 null");
        }
        return buildWeightedDigraph(tokenize(text));
    }

    /**
     * 从字符流读取并按 algs4 有向加权图格式建图;<b>流由调用方关闭</b>。
     *
     * @param reader 字符流,不能为 null
     * @return 解析出的有向加权图
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static EdgeWeightedDigraph readWeightedDigraph(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("字符流不能为 null");
        }
        try {
            return parseWeightedDigraph(readAll(reader));
        }
        catch (IOException e) {
            throw new IllegalStateException("读取图数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节流读取(UTF-8)并按 algs4 有向加权图格式建图;<b>流由调用方关闭</b>。
     *
     * @param in 字节流,不能为 null
     * @return 解析出的有向加权图
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static EdgeWeightedDigraph readWeightedDigraph(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("输入流不能为 null");
        }
        return readWeightedDigraph(new InputStreamReader(in, UTF_8));
    }

    /**
     * 按 algs4 有向加权图文本格式读取文件(UTF-8)。本类负责关闭文件流。
     *
     * @param path 文件路径,不能为 null
     * @return 解析出的有向加权图
     * @throws IllegalArgumentException 路径为 null、文件不存在、内容格式错误或端点越界
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static EdgeWeightedDigraph readWeightedDigraphFile(String path) {
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            return readWeightedDigraph(in);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("文件不存在: " + path, e);
        }
        finally {
            closeQuietly(in);
        }
    }

    // ------------------------------------------------------------------
    // 读:AOV 网(顶点表示活动的网)
    // ------------------------------------------------------------------

    /**
     * 解析 AOV 网的文本表示。<b>格式(逐行、无损,孤立活动也能表达)</b>:
     * <pre>
     *   活动名                      ← 该活动没有前置(如"线性代数")
     *   活动名: 前置1, 前置2         ← 该活动的前置课程
     *   # 注释行 / 空行             ← 忽略
     * </pre>
     * 行的先后顺序就是活动编号;活动名里不要出现冒号与逗号。冒号支持中英文两种写法,
     * 前置列表里允许写 {@code —}(破折号,教材里表示"无前置")。
     * 解析分两遍:先登记全部活动,再连约束 —— 所以前置可以写在后面(支持前向引用)。
     *
     * @param text AOV 网文本,不能为 null
     * @return 解析出的 AOV 网
     * @throws IllegalArgumentException 文本为 null、活动名为空或重复、前置活动不存在等
     */
    public static AOVNetwork parseAov(String text) {
        if (text == null) {
            throw new IllegalArgumentException("输入文本不能为 null");
        }
        return buildAov(text);
    }

    /**
     * 从字符流读取 AOV 网;<b>流由调用方关闭</b>。
     *
     * @param reader 字符流,不能为 null
     * @return 解析出的 AOV 网
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static AOVNetwork readAov(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("字符流不能为 null");
        }
        try {
            return parseAov(readAll(reader));
        }
        catch (IOException e) {
            throw new IllegalStateException("读取 AOV 网数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节流读取(UTF-8)AOV 网;<b>流由调用方关闭</b>。
     *
     * @param in 字节流,不能为 null
     * @return 解析出的 AOV 网
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static AOVNetwork readAov(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("输入流不能为 null");
        }
        return readAov(new InputStreamReader(in, UTF_8));
    }

    /**
     * 读取 AOV 网文件(UTF-8)。本类负责关闭文件流。
     *
     * @param path 文件路径,不能为 null
     * @return 解析出的 AOV 网
     * @throws IllegalArgumentException 路径为 null、文件不存在或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static AOVNetwork readAovFile(String path) {
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            return readAov(in);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("文件不存在: " + path, e);
        }
        finally {
            closeQuietly(in);
        }
    }

    // ------------------------------------------------------------------
    // 读:AOE 网(边表示活动的网)
    // ------------------------------------------------------------------

    /**
     * 解析 AOE 网的文本表示。<b>格式(两段式、无损)</b>:
     * <pre>
     *   # 第一行有效内容:事件清单(空格分隔,顺序即编号)
     *   E0 E1 E2 ... E12
     *   # 其后每行一个活动:活动名 起点事件 终点事件 工期
     *   A0 E0 E1 1
     *   A1 E0 E4 2
     * </pre>
     * 空行与 {@code #} 开头的注释行忽略。事件清单必须写全,这样"孤立事件"(没有任何活动的
     * 里程碑)也不会丢;活动的两端必须已在事件清单里。
     *
     * @param text AOE 网文本,不能为 null
     * @return 解析出的 AOE 网
     * @throws IllegalArgumentException 文本为 null、缺少事件清单、活动行格式错误、工期非法等
     */
    public static AOENetwork parseAoe(String text) {
        if (text == null) {
            throw new IllegalArgumentException("输入文本不能为 null");
        }
        return buildAoe(text);
    }

    /**
     * 从字符流读取 AOE 网;<b>流由调用方关闭</b>。
     *
     * @param reader 字符流,不能为 null
     * @return 解析出的 AOE 网
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static AOENetwork readAoe(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("字符流不能为 null");
        }
        try {
            return parseAoe(readAll(reader));
        }
        catch (IOException e) {
            throw new IllegalStateException("读取 AOE 网数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节流读取(UTF-8)AOE 网;<b>流由调用方关闭</b>。
     *
     * @param in 字节流,不能为 null
     * @return 解析出的 AOE 网
     * @throws IllegalArgumentException 参数为 null 或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static AOENetwork readAoe(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("输入流不能为 null");
        }
        return readAoe(new InputStreamReader(in, UTF_8));
    }

    /**
     * 读取 AOE 网文件(UTF-8)。本类负责关闭文件流。
     *
     * @param path 文件路径,不能为 null
     * @return 解析出的 AOE 网
     * @throws IllegalArgumentException 路径为 null、文件不存在或内容格式错误
     * @throws IllegalStateException    读取过程中发生 I/O 错误
     */
    public static AOENetwork readAoeFile(String path) {
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            return readAoe(in);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("文件不存在: " + path, e);
        }
        finally {
            closeQuietly(in);
        }
    }

    // ------------------------------------------------------------------
    // 写:文本 / 流 / 文件
    // ------------------------------------------------------------------

    /**
     * 把图转成 algs4 文本格式(可被 {@link #parse(String)} 原样读回)。
     *
     * <p>输出三部分:顶点数、边数、每条边一行两个端点。边取自 {@link UndirectedGraph#edges()},
     * 因此平行边按重数逐条输出、自环只输出一条 —— 重新读入后 V、E、每个顶点的度数都与原图一致。</p>
     *
     * @param graph 待输出的图,不能为 null
     * @return algs4 文本格式的字符串
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public static String format(UndirectedGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("待输出的图不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append(graph.V()).append(newline);
        sb.append(graph.E()).append(newline);
        for (int[] edge : graph.edges()) {
            sb.append(edge[0]).append(' ').append(edge[1]).append(newline);
        }
        return sb.toString();
    }

    /**
     * 把图以 algs4 文本格式(UTF-8)写入字符流;<b>流由调用方关闭</b>。
     *
     * @param graph 待输出的图,不能为 null
     * @param writer 目标字符流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(UndirectedGraph graph, Writer writer) {
        if (writer == null) {
            throw new IllegalArgumentException("输出字符流不能为 null");
        }
        try {
            writer.write(format(graph));
            writer.flush();
        }
        catch (IOException e) {
            throw new IllegalStateException("写出图数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把图以 algs4 文本格式(UTF-8)写入字节流;<b>流由调用方关闭</b>。
     *
     * @param graph 待输出的图,不能为 null
     * @param out 目标字节流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(UndirectedGraph graph, OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("输出流不能为 null");
        }
        write(graph, new OutputStreamWriter(out, UTF_8));
    }

    /**
     * 把图以 algs4 文本格式(UTF-8)写入文件。本类负责关闭文件流。
     *
     * @param graph 待输出的图,不能为 null
     * @param path 目标文件路径,不能为 null
     * @throws IllegalArgumentException 参数为 null;目标路径无法创建(如目录不存在)
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(UndirectedGraph graph, String path) {
        if (graph == null) {
            throw new IllegalArgumentException("待输出的图不能为 null");
        }
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(path);
            write(graph, out);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("无法写入文件: " + path, e);
        }
        finally {
            closeQuietly(out);
        }
    }

    // ------------------------------------------------------------------
    // 写:有向图(无权)
    // ------------------------------------------------------------------

    /**
     * 把有向图转成 algs4 文本格式(可被 {@link #parseDigraph(String)} 原样读回):
     * 第 1 行顶点数、第 2 行边数,其后每条边一行 {@code "from to"}。
     *
     * @param graph 待输出的有向图,不能为 null
     * @return 文本
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public static String format(Digraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("待输出的图不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append(graph.V()).append(newline);
        sb.append(graph.E()).append(newline);
        for (int[] edge : graph.edges()) {
            sb.append(edge[0]).append(' ').append(edge[1]).append(newline);
        }
        return sb.toString();
    }

    /**
     * 把有向图写入字符流;<b>流由调用方关闭</b>。
     *
     * @param graph  待输出的有向图,不能为 null
     * @param writer 目标字符流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(Digraph graph, Writer writer) {
        if (writer == null) {
            throw new IllegalArgumentException("输出字符流不能为 null");
        }
        try {
            writer.write(format(graph));
            writer.flush();
        }
        catch (IOException e) {
            throw new IllegalStateException("写出图数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把有向图写入字节流(UTF-8);<b>流由调用方关闭</b>。
     *
     * @param graph 待输出的有向图,不能为 null
     * @param out   目标字节流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(Digraph graph, OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("输出流不能为 null");
        }
        write(graph, new OutputStreamWriter(out, UTF_8));
    }

    /**
     * 把有向图写入文件(UTF-8)。本类负责关闭文件流。
     *
     * @param graph 待输出的有向图,不能为 null
     * @param path  目标文件路径,不能为 null
     * @throws IllegalArgumentException 参数为 null;目标路径无法创建
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(Digraph graph, String path) {
        if (graph == null) {
            throw new IllegalArgumentException("待输出的图不能为 null");
        }
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(path);
            write(graph, out);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("无法写入文件: " + path, e);
        }
        finally {
            closeQuietly(out);
        }
    }

    // ------------------------------------------------------------------
    // 写:符号图
    // ------------------------------------------------------------------

    /**
     * 把符号图写成边表:每行 {@code "名字 名字"}(每条边一次,平行边按重数)。
     *
     * <p><b>注意</b>:这是<b>有损</b>格式 —— 不属于任何边的"孤立顶点"写不出来
     * (边表格式的固有局限,与 algs4 的 {@code routes.txt} 一致);
     * 顶点编号顺序按首次出现决定,重新读入后一般会保持不变。</p>
     *
     * @param symbolGraph 待输出的符号图,不能为 null
     * @return 文本
     * @throws IllegalArgumentException {@code symbolGraph} 为 null
     */
    public static String format(SymbolGraph symbolGraph) {
        if (symbolGraph == null) {
            throw new IllegalArgumentException("待输出的符号图不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        for (String[] edge : symbolGraph.edges()) {
            sb.append(edge[0]).append(' ').append(edge[1]).append(newline);
        }
        return sb.toString();
    }

    /**
     * 把符号图写入字符流;<b>流由调用方关闭</b>。
     *
     * @param symbolGraph 待输出的符号图,不能为 null
     * @param writer      目标字符流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(SymbolGraph symbolGraph, Writer writer) {
        if (writer == null) {
            throw new IllegalArgumentException("输出字符流不能为 null");
        }
        try {
            writer.write(format(symbolGraph));
            writer.flush();
        }
        catch (IOException e) {
            throw new IllegalStateException("写出符号图数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把符号图写入字节流(UTF-8);<b>流由调用方关闭</b>。
     *
     * @param symbolGraph 待输出的符号图,不能为 null
     * @param out         目标字节流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(SymbolGraph symbolGraph, OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("输出流不能为 null");
        }
        write(symbolGraph, new OutputStreamWriter(out, UTF_8));
    }

    /**
     * 把符号图写入文件(UTF-8)。本类负责关闭文件流。
     *
     * @param symbolGraph 待输出的符号图,不能为 null
     * @param path        目标文件路径,不能为 null
     * @throws IllegalArgumentException 参数为 null;目标路径无法创建
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(SymbolGraph symbolGraph, String path) {
        if (symbolGraph == null) {
            throw new IllegalArgumentException("待输出的符号图不能为 null");
        }
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(path);
            write(symbolGraph, out);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("无法写入文件: " + path, e);
        }
        finally {
            closeQuietly(out);
        }
    }

    // ------------------------------------------------------------------
    // 写:流网络
    // ------------------------------------------------------------------

    /**
     * 把流网络写成 {@link #parseFlowNetwork(String)} 能读回的文本(V、E、每条边一行
     * {@code "from to capacity"})。
     *
     * <p><b>只写容量,不写流量</b>:容量是这张网络的"结构",流量是算法跑出来的结果状态;
     * 需要保留流量请直接用 {@link FlowNetwork#copy()}。</p>
     *
     * @param network 待输出的流网络,不能为 null
     * @return 流网络文本
     * @throws IllegalArgumentException {@code network} 为 null
     */
    public static String format(FlowNetwork network) {
        if (network == null) {
            throw new IllegalArgumentException("待输出的流网络不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append(network.V()).append(newline);
        sb.append(network.E()).append(newline);
        for (FlowEdge edge : network.edges()) {
            sb.append(edge.from()).append(' ').append(edge.to()).append(' ')
                    .append(edge.capacity()).append(newline);
        }
        return sb.toString();
    }

    /**
     * 把流网络写入字符流;<b>流由调用方关闭</b>。
     *
     * @param network 待输出的流网络,不能为 null
     * @param writer  目标字符流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(FlowNetwork network, Writer writer) {
        if (writer == null) {
            throw new IllegalArgumentException("输出字符流不能为 null");
        }
        try {
            writer.write(format(network));
            writer.flush();
        }
        catch (IOException e) {
            throw new IllegalStateException("写出流网络数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把流网络写入字节流(UTF-8);<b>流由调用方关闭</b>。
     *
     * @param network 待输出的流网络,不能为 null
     * @param out     目标字节流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(FlowNetwork network, OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("输出流不能为 null");
        }
        write(network, new OutputStreamWriter(out, UTF_8));
    }

    /**
     * 把流网络写入文件(UTF-8)。本类负责关闭文件流。
     *
     * @param network 待输出的流网络,不能为 null
     * @param path    目标文件路径,不能为 null
     * @throws IllegalArgumentException 参数为 null;目标路径无法创建
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(FlowNetwork network, String path) {
        if (network == null) {
            throw new IllegalArgumentException("待输出的流网络不能为 null");
        }
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(path);
            write(network, out);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("无法写入文件: " + path, e);
        }
        finally {
            closeQuietly(out);
        }
    }

    // ------------------------------------------------------------------
    // 写:加权图
    // ------------------------------------------------------------------

    /**
     * 把加权图转成 algs4 加权图文本格式(可被 {@link #parseWeighted(String)} 原样读回)。
     * 每条边输出为 {@code v w weight} 一行;权值打印形式与 {@link Edge#toString()} 一致
     * (整数权值不会带 {@code .0} 之外的多余尾数)。
     *
     * @param graph 待输出的加权图,不能为 null
     * @return algs4 加权图文本格式的字符串
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public static String format(EdgeWeightedGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("待输出的图不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append(graph.V()).append(newline);
        sb.append(graph.E()).append(newline);
        for (Edge e : graph.edges()) {
            int v = e.either();
            int w = e.other(v);
            sb.append(v).append(' ').append(w).append(' ').append(e.weight()).append(newline);
        }
        return sb.toString();
    }

    /**
     * 把加权图以 algs4 格式(UTF-8)写入字符流;<b>流由调用方关闭</b>。
     *
     * @param graph  待输出的加权图,不能为 null
     * @param writer 目标字符流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(EdgeWeightedGraph graph, Writer writer) {
        if (writer == null) {
            throw new IllegalArgumentException("输出字符流不能为 null");
        }
        try {
            writer.write(format(graph));
            writer.flush();
        }
        catch (IOException e) {
            throw new IllegalStateException("写出图数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把加权图以 algs4 格式(UTF-8)写入字节流;<b>流由调用方关闭</b>。
     *
     * @param graph 待输出的加权图,不能为 null
     * @param out   目标字节流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(EdgeWeightedGraph graph, OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("输出流不能为 null");
        }
        write(graph, new OutputStreamWriter(out, UTF_8));
    }

    /**
     * 把加权图以 algs4 格式(UTF-8)写入文件。本类负责关闭文件流。
     *
     * @param graph 待输出的加权图,不能为 null
     * @param path  目标文件路径,不能为 null
     * @throws IllegalArgumentException 参数为 null;目标路径无法创建(如目录不存在)
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(EdgeWeightedGraph graph, String path) {
        if (graph == null) {
            throw new IllegalArgumentException("待输出的图不能为 null");
        }
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(path);
            write(graph, out);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("无法写入文件: " + path, e);
        }
        finally {
            closeQuietly(out);
        }
    }

    // ------------------------------------------------------------------
    // 写:有向加权图
    // ------------------------------------------------------------------

    /**
     * 把有向加权图转成 algs4 文本格式(可被 {@link #parseWeightedDigraph(String)} 原样读回):
     * 每条边输出为 {@code from to weight} 一行。
     *
     * @param graph 待输出的有向加权图,不能为 null
     * @return algs4 文本格式的字符串
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public static String format(EdgeWeightedDigraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("待输出的图不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append(graph.V()).append(newline);
        sb.append(graph.E()).append(newline);
        for (DirectedEdge e : graph.edges()) {
            sb.append(e.from()).append(' ').append(e.to()).append(' ').append(e.weight()).append(newline);
        }
        return sb.toString();
    }

    /**
     * 把有向加权图以 algs4 格式(UTF-8)写入字符流;<b>流由调用方关闭</b>。
     *
     * @param graph  待输出的有向加权图,不能为 null
     * @param writer 目标字符流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(EdgeWeightedDigraph graph, Writer writer) {
        if (writer == null) {
            throw new IllegalArgumentException("输出字符流不能为 null");
        }
        try {
            writer.write(format(graph));
            writer.flush();
        }
        catch (IOException e) {
            throw new IllegalStateException("写出图数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把有向加权图以 algs4 格式(UTF-8)写入字节流;<b>流由调用方关闭</b>。
     *
     * @param graph 待输出的有向加权图,不能为 null
     * @param out   目标字节流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(EdgeWeightedDigraph graph, OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("输出流不能为 null");
        }
        write(graph, new OutputStreamWriter(out, UTF_8));
    }

    /**
     * 把有向加权图以 algs4 格式(UTF-8)写入文件。本类负责关闭文件流。
     *
     * @param graph 待输出的有向加权图,不能为 null
     * @param path  目标文件路径,不能为 null
     * @throws IllegalArgumentException 参数为 null;目标路径无法创建(如目录不存在)
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(EdgeWeightedDigraph graph, String path) {
        if (graph == null) {
            throw new IllegalArgumentException("待输出的图不能为 null");
        }
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(path);
            write(graph, out);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("无法写入文件: " + path, e);
        }
        finally {
            closeQuietly(out);
        }
    }

    // ------------------------------------------------------------------
    // 写:AOV 网
    // ------------------------------------------------------------------

    /**
     * 把 AOV 网写成 {@link #parseAov(String)} 能原样读回的文本:每个活动一行,
     * 有前置时写 {@code "活动名: 前置1, 前置2"}。这是<b>无损</b>格式 ——
     * 每个活动都占一行,所以没有任何约束的孤立活动也不会丢失,活动编号顺序即行序。
     *
     * @param network 待输出的 AOV 网,不能为 null
     * @return AOV 网文本
     * @throws IllegalArgumentException {@code network} 为 null
     */
    public static String format(AOVNetwork network) {
        if (network == null) {
            throw new IllegalArgumentException("待输出的 AOV 网不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        for (int v = 0; v < network.activityCount(); v++) {
            String name = network.nameOf(v);
            List<String> prerequisites = network.predecessorNames(name);
            sb.append(name);
            if (!prerequisites.isEmpty()) {
                sb.append(": ");
                for (int i = 0; i < prerequisites.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(prerequisites.get(i));
                }
            }
            sb.append(newline);
        }
        return sb.toString();
    }

    /**
     * 把 AOV 网写入字符流;<b>流由调用方关闭</b>。
     *
     * @param network 待输出的 AOV 网,不能为 null
     * @param writer  目标字符流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(AOVNetwork network, Writer writer) {
        if (writer == null) {
            throw new IllegalArgumentException("输出字符流不能为 null");
        }
        try {
            writer.write(format(network));
            writer.flush();
        }
        catch (IOException e) {
            throw new IllegalStateException("写出 AOV 网数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把 AOV 网写入字节流(UTF-8);<b>流由调用方关闭</b>。
     *
     * @param network 待输出的 AOV 网,不能为 null
     * @param out     目标字节流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(AOVNetwork network, OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("输出流不能为 null");
        }
        write(network, new OutputStreamWriter(out, UTF_8));
    }

    /**
     * 把 AOV 网写入文件(UTF-8)。本类负责关闭文件流。
     *
     * @param network 待输出的 AOV 网,不能为 null
     * @param path    目标文件路径,不能为 null
     * @throws IllegalArgumentException 参数为 null;目标路径无法创建
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(AOVNetwork network, String path) {
        if (network == null) {
            throw new IllegalArgumentException("待输出的 AOV 网不能为 null");
        }
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(path);
            write(network, out);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("无法写入文件: " + path, e);
        }
        finally {
            closeQuietly(out);
        }
    }

    // ------------------------------------------------------------------
    // 写:AOE 网
    // ------------------------------------------------------------------

    /**
     * 把 AOE 网写成 {@link #parseAoe(String)} 能原样读回的文本:第一行是事件清单,
     * 其后每个活动一行 {@code "活动名 起点事件 终点事件 工期"}。这是<b>无损</b>格式 ——
     * 事件清单单列一行,所以孤立事件也不会丢;事件与活动的编号顺序即行序。
     *
     * @param network 待输出的 AOE 网,不能为 null
     * @return AOE 网文本
     * @throws IllegalArgumentException {@code network} 为 null
     */
    public static String format(AOENetwork network) {
        if (network == null) {
            throw new IllegalArgumentException("待输出的 AOE 网不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        for (int v = 0; v < network.eventCount(); v++) {
            if (v > 0) {
                sb.append(' ');
            }
            sb.append(network.eventName(v));
        }
        sb.append(newline);
        for (int a = 0; a < network.activityCount(); a++) {
            sb.append(network.activityName(a)).append(' ')
                    .append(network.eventName(network.activityFrom(a))).append(' ')
                    .append(network.eventName(network.activityTo(a))).append(' ')
                    .append(network.activityDuration(a)).append(newline);
        }
        return sb.toString();
    }

    /**
     * 把 AOE 网写入字符流;<b>流由调用方关闭</b>。
     *
     * @param network 待输出的 AOE 网,不能为 null
     * @param writer  目标字符流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(AOENetwork network, Writer writer) {
        if (writer == null) {
            throw new IllegalArgumentException("输出字符流不能为 null");
        }
        try {
            writer.write(format(network));
            writer.flush();
        }
        catch (IOException e) {
            throw new IllegalStateException("写出 AOE 网数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把 AOE 网写入字节流(UTF-8);<b>流由调用方关闭</b>。
     *
     * @param network 待输出的 AOE 网,不能为 null
     * @param out     目标字节流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(AOENetwork network, OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("输出流不能为 null");
        }
        write(network, new OutputStreamWriter(out, UTF_8));
    }

    /**
     * 把 AOE 网写入文件(UTF-8)。本类负责关闭文件流。
     *
     * @param network 待输出的 AOE 网,不能为 null
     * @param path    目标文件路径,不能为 null
     * @throws IllegalArgumentException 参数为 null;目标路径无法创建
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(AOENetwork network, String path) {
        if (network == null) {
            throw new IllegalArgumentException("待输出的 AOE 网不能为 null");
        }
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(path);
            write(network, out);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("无法写入文件: " + path, e);
        }
        finally {
            closeQuietly(out);
        }
    }

    // ------------------------------------------------------------------
    // 导出:Graphviz DOT
    // ------------------------------------------------------------------

    /**
     * 把图导出为 Graphviz DOT 文本({@code graph { ... }}),可直接用 {@code dot -Tsvg} 画图。
     * 每条边只输出一次,自环只输出一条(两个端点相同,画成一个环)。
     *
     * @param graph 待导出的图,不能为 null
     * @return DOT 文本
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public static String toDot(UndirectedGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("待导出的图不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("graph {").append(newline);
        sb.append("node[shape=circle, style=filled, fixedsize=true, width=0.3, fontsize=\"10pt\"]").append(newline);
        for (int[] edge : graph.edges()) {
            sb.append(edge[0]).append(" -- ").append(edge[1]).append(newline);
        }
        sb.append("}").append(newline);
        return sb.toString();
    }

    /**
     * 把加权图导出为 Graphviz DOT 文本,边带上权值标签({@code 0 -- 7 [label="0.16"]}),
     * 可直接用 {@code dot -Tsvg} 画图。
     *
     * @param graph 待导出的加权图,不能为 null
     * @return DOT 文本
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public static String toDot(EdgeWeightedGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("待导出的图不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("graph {").append(newline);
        sb.append("node[shape=circle, style=filled, fixedsize=true, width=0.3, fontsize=\"10pt\"]").append(newline);
        sb.append("edge[fontsize=\"9pt\"]").append(newline);
        for (Edge e : graph.edges()) {
            int v = e.either();
            int w = e.other(v);
            sb.append(v).append(" -- ").append(w)
                    .append(" [label=\"").append(e.weight()).append("\"]").append(newline);
        }
        sb.append("}").append(newline);
        return sb.toString();
    }

    /**
     * 把有向加权图导出为 Graphviz DOT 文本。有向边用 {@code ->} 连接并带权值标签
     * ({@code 0 -> 4 [label="0.38"]}),可直接用 {@code dot -Tsvg} 画图。
     *
     * @param graph 待导出的有向加权图,不能为 null
     * @return DOT 文本
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public static String toDot(EdgeWeightedDigraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("待导出的图不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("digraph {").append(newline);
        sb.append("node[shape=circle, style=filled, fixedsize=true, width=0.3, fontsize=\"10pt\"]").append(newline);
        sb.append("edge[fontsize=\"9pt\"]").append(newline);
        for (DirectedEdge e : graph.edges()) {
            sb.append(e.from()).append(" -> ").append(e.to())
                    .append(" [label=\"").append(e.weight()).append("\"]").append(newline);
        }
        sb.append("}").append(newline);
        return sb.toString();
    }

    /**
     * 把有向图导出为 Graphviz DOT 文本。有向边用 {@code ->} 连接,可直接用 {@code dot -Tsvg} 出图。
     *
     * @param graph 待导出的有向图,不能为 null
     * @return DOT 文本
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public static String toDot(Digraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("待导出的图不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("digraph {").append(newline);
        sb.append("node[shape=circle, style=filled, fixedsize=true, width=0.3, fontsize=\"10pt\"]").append(newline);
        for (int[] edge : graph.edges()) {
            sb.append(edge[0]).append(" -> ").append(edge[1]).append(newline);
        }
        sb.append("}").append(newline);
        return sb.toString();
    }

    /**
     * 把流网络导出为 Graphviz DOT 文本:每条弧标出"流量/容量"
     * (跑过最大流之后出图,一眼就能看出哪些管道被压满了)。
     *
     * @param network 待导出的流网络,不能为 null
     * @return DOT 文本
     * @throws IllegalArgumentException {@code network} 为 null
     */
    public static String toDot(FlowNetwork network) {
        if (network == null) {
            throw new IllegalArgumentException("待导出的流网络不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("digraph {").append(newline);
        sb.append("node[shape=circle, style=filled, fixedsize=true, width=0.3, fontsize=\"10pt\"]").append(newline);
        for (FlowEdge edge : network.edges()) {
            sb.append(edge.from()).append(" -> ").append(edge.to())
                    .append(" [label=\"").append(String.format("%.2f/%.2f", edge.flow(), edge.capacity()))
                    .append("\"]").append(newline);
        }
        sb.append("}").append(newline);
        return sb.toString();
    }

    /**
     * 把 AOV 网导出为 Graphviz DOT 文本:顶点是活动(用名字),弧是先后约束。
     *
     * @param network 待导出的 AOV 网,不能为 null
     * @return DOT 文本
     * @throws IllegalArgumentException {@code network} 为 null
     */
    public static String toDot(AOVNetwork network) {
        if (network == null) {
            throw new IllegalArgumentException("待导出的 AOV 网不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("digraph {").append(newline);
        sb.append("node[shape=box, fontsize=\"10pt\"]").append(newline);
        for (int v = 0; v < network.activityCount(); v++) {
            sb.append('"').append(network.nameOf(v)).append('"').append(newline);
        }
        for (int v = 0; v < network.activityCount(); v++) {
            for (int w : network.successors(v)) {
                sb.append('"').append(network.nameOf(v)).append("\" -> \"")
                        .append(network.nameOf(w)).append('"').append(newline);
            }
        }
        sb.append("}").append(newline);
        return sb.toString();
    }

    /**
     * 把 AOE 网导出为 Graphviz DOT 文本:顶点是事件,弧是活动(标签为"活动名/工期")。
     *
     * @param network 待导出的 AOE 网,不能为 null
     * @return DOT 文本
     * @throws IllegalArgumentException {@code network} 为 null
     */
    public static String toDot(AOENetwork network) {
        if (network == null) {
            throw new IllegalArgumentException("待导出的 AOE 网不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("digraph {").append(newline);
        sb.append("node[shape=circle, style=filled, fixedsize=true, width=0.3, fontsize=\"10pt\"]")
                .append(newline);
        for (int a = 0; a < network.activityCount(); a++) {
            sb.append(network.eventName(network.activityFrom(a))).append(" -> ")
                    .append(network.eventName(network.activityTo(a)))
                    .append(" [label=\"").append(network.activityName(a)).append('/');
            double duration = network.activityDuration(a);
            sb.append(duration == Math.rint(duration)
                    ? String.valueOf((long) duration) : String.valueOf(duration));
            sb.append("\"]").append(newline);
        }
        sb.append("}").append(newline);
        return sb.toString();
    }

    /**
     * 把符号图导出为 Graphviz DOT 文本:顶点标签是<b>名字</b>(机场代码、演员姓名……)。
     *
     * @param symbolGraph 待导出的符号图,不能为 null
     * @return DOT 文本
     * @throws IllegalArgumentException {@code symbolGraph} 为 null
     */
    public static String toDot(SymbolGraph symbolGraph) {
        if (symbolGraph == null) {
            throw new IllegalArgumentException("待导出的符号图不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("graph {").append(newline);
        sb.append("node[shape=circle, fontsize=\"10pt\"]").append(newline);
        for (String name : symbolGraph.vertices()) {
            sb.append('"').append(name).append('"').append(newline);
        }
        for (String[] edge : symbolGraph.edges()) {
            sb.append('"').append(edge[0]).append("\" -- \"").append(edge[1]).append('"').append(newline);
        }
        sb.append("}").append(newline);
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 写:邻接矩阵表示
    // ------------------------------------------------------------------

    /**
     * 把邻接矩阵表示写成与邻接表<b>完全相同</b>的文本格式(V、E、每条弧一行 {@code "from to weight"}),
     * 于是两种表示可以互相读、互相验证。
     *
     * <p>读回来请用 {@link #readWeightedDigraphFile(String)}(得到邻接表),
     * 再用 {@code new AdjMatrixEdgeWeightedDigraph(that)} 转成矩阵。</p>
     *
     * @param graph 待输出的矩阵表示,不能为 null
     * @return 文本
     * @throws IllegalArgumentException {@code graph} 为 null
     */
    public static String format(AdjMatrixEdgeWeightedDigraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("待输出的图不能为 null");
        }
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append(graph.V()).append(newline);
        sb.append(graph.E()).append(newline);
        for (DirectedEdge edge : graph.edges()) {
            sb.append(edge.from()).append(' ').append(edge.to()).append(' ')
                    .append(edge.weight()).append(newline);
        }
        return sb.toString();
    }

    /**
     * 把邻接矩阵表示写入字符流;<b>流由调用方关闭</b>。
     *
     * @param graph  待输出的矩阵表示,不能为 null
     * @param writer 目标字符流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(AdjMatrixEdgeWeightedDigraph graph, Writer writer) {
        if (writer == null) {
            throw new IllegalArgumentException("输出字符流不能为 null");
        }
        try {
            writer.write(format(graph));
            writer.flush();
        }
        catch (IOException e) {
            throw new IllegalStateException("写出图数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把邻接矩阵表示写入字节流(UTF-8);<b>流由调用方关闭</b>。
     *
     * @param graph 待输出的矩阵表示,不能为 null
     * @param out   目标字节流,不能为 null
     * @throws IllegalArgumentException 参数为 null
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(AdjMatrixEdgeWeightedDigraph graph, OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("输出流不能为 null");
        }
        write(graph, new OutputStreamWriter(out, UTF_8));
    }

    /**
     * 把邻接矩阵表示写入文件(UTF-8)。本类负责关闭文件流。
     *
     * @param graph 待输出的矩阵表示,不能为 null
     * @param path  目标文件路径,不能为 null
     * @throws IllegalArgumentException 参数为 null;目标路径无法创建
     * @throws IllegalStateException    写入过程中发生 I/O 错误
     */
    public static void write(AdjMatrixEdgeWeightedDigraph graph, String path) {
        if (graph == null) {
            throw new IllegalArgumentException("待输出的图不能为 null");
        }
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(path);
            write(graph, out);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("无法写入文件: " + path, e);
        }
        finally {
            closeQuietly(out);
        }
    }

    // ------------------------------------------------------------------
    // 内部:切分与装配
    // ------------------------------------------------------------------

    /**
     * 去掉注释后按空白切分:整行 {@code #} 注释与行尾 {@code #} 注释都忽略,
     * 空行不产生 token。这样 V/E/端点这类"扁平 token 流"格式也能像 AOV/AOE/符号图一样
     * 在文件里写注释。
     *
     * @return token 数组;全为空则返回长度 0 的数组(具体报错由 {@link #build} 等统一给出)
     */
    private static String[] tokenize(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            String body = stripComment(line);
            if (!body.isEmpty()) {
                sb.append(body).append(' ');
            }
        }
        String trimmed = sb.toString().trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("\\s+");
    }

    /**
     * 校验并消费 token 序列:V、E、然后 2E 个端点。
     *
     * @throws IllegalArgumentException 数目不对或端点越界(端点校验委托给 {@code addEdge})
     */
    private static UndirectedGraph build(String[] tokens) {
        if (tokens.length == 0) {
            throw new IllegalArgumentException("输入为空,至少需要给出顶点数");
        }
        int V = parseInt(tokens[0], "顶点数");
        if (tokens.length < 2) {
            throw new IllegalArgumentException("缺少边数:第二项应为边数 E");
        }
        int E = parseInt(tokens[1], "边数");
        if (E < 0) {
            throw new IllegalArgumentException("边数必须非负,当前为 " + E);
        }
        if (tokens.length - 2 != 2 * E) {
            throw new IllegalArgumentException("边端点数目与边数不符:声明 " + E
                    + " 条边,需要 " + (2 * E) + " 个端点,实际给出 " + (tokens.length - 2) + " 个");
        }
        UndirectedGraph graph = new UndirectedGraph(V);
        for (int i = 0; i < E; i++) {
            int v = parseInt(tokens[2 + 2 * i], "第 " + (i + 1) + " 条边的起点");
            int w = parseInt(tokens[3 + 2 * i], "第 " + (i + 1) + " 条边的终点");
            graph.addEdge(v, w);
        }
        return graph;
    }

    /**
     * 校验并消费加权图的 token 序列:V、E、然后 3E 个值(每三个是 from、to、weight)。
     *
     * <p>无向加权图与有向加权图的文本格式<b>完全一样</b>(都是三元组),区别只在装配时
     * 用 {@link Edge} 还是 {@link DirectedEdge}。所以这里先解析成中间结构
     * {@link WeightedTriples},再交给两个装配方法,避免把同一套校验写两遍。</p>
     *
     * @throws IllegalArgumentException 数目不对、权值非法或端点越界
     */
    private static WeightedTriples readWeightedTriples(String[] tokens) {
        if (tokens.length == 0) {
            throw new IllegalArgumentException("输入为空,至少需要给出顶点数");
        }
        int V = parseInt(tokens[0], "顶点数");
        if (tokens.length < 2) {
            throw new IllegalArgumentException("缺少边数:第二项应为边数 E");
        }
        int E = parseInt(tokens[1], "边数");
        if (E < 0) {
            throw new IllegalArgumentException("边数必须非负,当前为 " + E);
        }
        if (tokens.length - 2 != 3 * E) {
            throw new IllegalArgumentException("三元组数目与边数不符:声明 " + E
                    + " 条边,每条边需要 v、w、weight 三项,共需 " + (3 * E)
                    + " 项,实际给出 " + (tokens.length - 2) + " 项");
        }
        int[] from = new int[E];
        int[] to = new int[E];
        double[] weight = new double[E];
        for (int i = 0; i < E; i++) {
            from[i] = parseInt(tokens[2 + 3 * i], "第 " + (i + 1) + " 条边的起点");
            to[i] = parseInt(tokens[3 + 3 * i], "第 " + (i + 1) + " 条边的终点");
            weight[i] = parseDouble(tokens[4 + 3 * i], "第 " + (i + 1) + " 条边的权值");
        }
        return new WeightedTriples(V, E, from, to, weight);
    }

    /** 装配无向加权图 */
    private static EdgeWeightedGraph buildWeighted(String[] tokens) {
        WeightedTriples t = readWeightedTriples(tokens);
        EdgeWeightedGraph graph = new EdgeWeightedGraph(t.vertexCount);
        for (int i = 0; i < t.edgeCount; i++) {
            graph.addEdge(new Edge(t.from[i], t.to[i], t.weight[i]));
        }
        return graph;
    }

    /** 装配有向加权图 */
    private static EdgeWeightedDigraph buildWeightedDigraph(String[] tokens) {
        WeightedTriples t = readWeightedTriples(tokens);
        EdgeWeightedDigraph graph = new EdgeWeightedDigraph(t.vertexCount);
        for (int i = 0; i < t.edgeCount; i++) {
            graph.addEdge(new DirectedEdge(t.from[i], t.to[i], t.weight[i]));
        }
        return graph;
    }

    /**
     * 装配无权有向图:与 {@link #build(String[])} 同一套格式(V、E、2E 个端点),
     * 只是把边解释成有向的。
     *
     * @throws IllegalArgumentException 数目不对或端点越界(端点校验委托给 {@code addEdge})
     */
    private static Digraph buildDigraph(String[] tokens) {
        int V = readVertexCount(tokens);
        int E = readEdgeCount(tokens);
        if (tokens.length - 2 != 2 * E) {
            throw new IllegalArgumentException("边端点数目与边数不符:声明 " + E
                    + " 条边,需要 " + (2 * E) + " 个端点,实际给出 " + (tokens.length - 2) + " 个");
        }
        Digraph graph = new Digraph(V);
        for (int i = 0; i < E; i++) {
            int v = parseInt(tokens[2 + 2 * i], "第 " + (i + 1) + " 条边的起点");
            int w = parseInt(tokens[3 + 2 * i], "第 " + (i + 1) + " 条边的终点");
            graph.addEdge(v, w);
        }
        return graph;
    }

    /** 读出并校验前两项:顶点数、边数 */
    private static int readVertexCount(String[] tokens) {
        if (tokens.length == 0) {
            throw new IllegalArgumentException("输入为空,至少需要给出顶点数");
        }
        return parseInt(tokens[0], "顶点数");
    }

    /** 读出并校验边数 */
    private static int readEdgeCount(String[] tokens) {
        if (tokens.length < 2) {
            throw new IllegalArgumentException("缺少边数:第二项应为边数 E");
        }
        int E = parseInt(tokens[1], "边数");
        if (E < 0) {
            throw new IllegalArgumentException("边数必须非负,当前为 " + E);
        }
        return E;
    }

    /**
     * 装配流网络:与加权有向图同一套 token 布局(V、E、E 个三元组),权值解释为容量。
     *
     * @throws IllegalArgumentException 数目不对、容量为负或端点越界/自环
     */
    private static FlowNetwork buildFlowNetwork(String[] tokens) {
        WeightedTriples t = readWeightedTriples(tokens);
        FlowNetwork network = new FlowNetwork(t.vertexCount);
        for (int i = 0; i < t.edgeCount; i++) {
            try {
                network.addEdge(t.from[i], t.to[i], t.weight[i]);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 条边:" + e.getMessage(), e);
            }
        }
        return network;
    }

    /**
     * 装配符号图:每行一条边(两个名字),名字按首次出现顺序登记为编号。
     *
     * @throws IllegalArgumentException 某行不是两个名字
     */
    private static SymbolGraph buildSymbolGraph(String text) {
        SymbolGraph symbolGraph = new SymbolGraph();
        String[] lines = text.split("\\r?\\n");
        List<int[]> edgeIds = new ArrayList<int[]>();
        for (int index = 0; index < lines.length; index++) {
            String line = stripComment(lines[index]);
            if (line.isEmpty()) {
                continue;
            }
            String[] tokens = tokenize(line);
            if (tokens.length != 2) {
                throw new IllegalArgumentException("第 " + (index + 1)
                        + " 行:每条边需要两个顶点名(空白分隔),实际给出 " + tokens.length + " 项");
            }
            int a = symbolGraph.indexOf(tokens[0]);
            if (a < 0) {
                a = symbolGraph.addVertex(tokens[0]);
            }
            int b = symbolGraph.indexOf(tokens[1]);
            if (b < 0) {
                b = symbolGraph.addVertex(tokens[1]);
            }
            edgeIds.add(new int[]{a, b});
        }
        // 名字都登记完再连边(SymbolGraph 要求两端已存在)
        for (int[] edge : edgeIds) {
            symbolGraph.addEdge(symbolGraph.nameOf(edge[0]), symbolGraph.nameOf(edge[1]));
        }
        return symbolGraph;
    }

    /** 加权图文本解析出的中间结果:V、E 与 E 组三元组 */
    private static final class WeightedTriples {
        final int vertexCount;
        final int edgeCount;
        final int[] from;
        final int[] to;
        final double[] weight;

        WeightedTriples(int vertexCount, int edgeCount, int[] from, int[] to, double[] weight) {
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.from = from;
            this.to = to;
            this.weight = weight;
        }
    }

    /**
     * 解析 AOV 网文本。分两遍:先把每一行左边的活动名全部登记(于是活动编号 = 行序),
     * 再逐行连约束 —— 这样前置活动写在后面也没问题(支持前向引用)。
     *
     * @throws IllegalArgumentException 活动名为空/重复、前置活动不存在、自己先于自己或约束重复
     */
    private static AOVNetwork buildAov(String text) {
        AOVNetwork network = new AOVNetwork();
        String[] lines = text.split("\\r?\\n");
        List<String> activities = new ArrayList<String>();
        List<String> prerequisiteTexts = new ArrayList<String>();
        List<Integer> lineNumbers = new ArrayList<Integer>();

        // 第一遍:登记全部活动(顺序即编号)
        for (int index = 0; index < lines.length; index++) {
            String line = stripComment(lines[index]);
            if (line.isEmpty()) {
                continue;
            }
            int colon = indexOfSeparator(line);
            String name = (colon < 0 ? line : line.substring(0, colon)).trim();
            String prerequisites = colon < 0 ? "" : line.substring(colon + 1).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 行:活动名为空");
            }
            try {
                network.addActivity(name);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 行:" + e.getMessage(), e);
            }
            activities.add(name);
            prerequisiteTexts.add(prerequisites);
            lineNumbers.add(index + 1);
        }

        // 第二遍:连约束(此刻全部活动都已登记)
        for (int i = 0; i < activities.size(); i++) {
            String name = activities.get(i);
            for (String prerequisite : splitPrerequisites(prerequisiteTexts.get(i))) {
                try {
                    network.addPrecedence(prerequisite, name);
                }
                catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("第 " + lineNumbers.get(i) + " 行:" + e.getMessage(), e);
                }
            }
        }
        return network;
    }

    /** 找出行内分隔"活动名"与"前置列表"的冒号(半角与全角都认) */
    private static int indexOfSeparator(String line) {
        int ascii = line.indexOf(':');
        int fullWidth = line.indexOf('：');
        if (ascii < 0) {
            return fullWidth;
        }
        if (fullWidth < 0) {
            return ascii;
        }
        return Math.min(ascii, fullWidth);
    }

    /** 拆分前置列表:按半角/全角逗号切分;忽略空项与教材里表示"无前置"的破折号 */
    private static List<String> splitPrerequisites(String text) {
        List<String> result = new ArrayList<String>();
        if (text.isEmpty()) {
            return result;
        }
        String normalized = text.replace('，', ',');
        for (String token : normalized.split(",")) {
            String name = token.trim();
            if (name.isEmpty() || "—".equals(name) || "-".equals(name) || "–".equals(name)) {
                continue;
            }
            result.add(name);
        }
        return result;
    }

    /**
     * 解析 AOE 网文本:第一行有效内容是事件清单(空格分隔),其后每行是一个活动
     * {@code 名称 起点 终点 工期}。
     *
     * @throws IllegalArgumentException 缺少事件清单、活动行项数不对、事件不存在或工期非法
     */
    private static AOENetwork buildAoe(String text) {
        AOENetwork network = new AOENetwork();
        String[] lines = text.split("\\r?\\n");
        int eventLineIndex = -1;

        // 第一遍:找出事件清单并登记(先有事件,才能连活动)
        for (int index = 0; index < lines.length && eventLineIndex < 0; index++) {
            String line = stripComment(lines[index]);
            if (line.isEmpty()) {
                continue;
            }
            eventLineIndex = index;
            for (String event : tokenize(line)) {
                try {
                    network.addEvent(event);
                }
                catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("第 " + (index + 1) + " 行:" + e.getMessage(), e);
                }
            }
        }
        if (eventLineIndex < 0) {
            throw new IllegalArgumentException("缺少事件清单:第一行有效内容应为事件名列表(空格分隔)");
        }

        // 第二遍:连活动
        for (int index = eventLineIndex + 1; index < lines.length; index++) {
            String line = stripComment(lines[index]);
            if (line.isEmpty()) {
                continue;
            }
            String[] tokens = tokenize(line);
            int lineNumber = index + 1;
            if (tokens.length != 4) {
                throw new IllegalArgumentException("第 " + lineNumber
                        + " 行:活动行需要 4 项「活动名 起点事件 终点事件 工期」,实际给出 " + tokens.length + " 项");
            }
            double duration = parseDouble(tokens[3], "第 " + lineNumber + " 行的工期");
            try {
                network.addActivity(tokens[0], tokens[1], tokens[2], duration);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("第 " + lineNumber + " 行:" + e.getMessage(), e);
            }
        }
        return network;
    }

    /** 去掉行内注释(从第一个 # 开始)并 trim;全为空则返回空串 */
    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        String body = hash < 0 ? line : line.substring(0, hash);
        return body.trim();
    }

    /** 解析一个整数 token,失败时给出可定位的错误信息 */
    private static int parseInt(String token, String what) {
        try {
            return Integer.parseInt(token);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " 不是合法整数: \"" + token + "\"", e);
        }
    }

    /** 解析一个权值 token(有限实数),失败时给出可定位的错误信息 */
    private static double parseDouble(String token, String what) {
        double value;
        try {
            value = Double.parseDouble(token);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " 不是合法实数: \"" + token + "\"", e);
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(what + " 必须是有限实数: \"" + token + "\"");
        }
        return value;
    }

    /** 把读到的字符全部拼成字符串(JDK 8 无 Reader.readAllAsString) */
    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    /** 关闭流,忽略关闭失败(只读/只写场景下关闭失败没有补救价值) */
    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            }
            catch (IOException ignored) {
                // 关闭失败不影响已读/已写的数据
            }
        }
    }
}
