package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 二分图最大匹配(Bipartite Matching):左部 L 个顶点、右部 R 个顶点,边只连接左右两侧;
 * 挑出尽可能多的边,使<b>每个顶点最多被挑中一次</b>。
 *
 * <p><b>为什么值得单独做</b>:匹配是"分配问题"的基本模型 —— 人配任务、学生配宿舍、
 * 广告位配用户。它有两条通往同一答案的路,本类把两条都实现出来:</p>
 * <table border="1" summary="二分图匹配的两种实现">
 *   <tr><th>模式</th><th>做法</th><th>时间</th></tr>
 *   <tr><td>{@link Mode#KUHN}</td>
 *       <td>匈牙利算法:对每个左顶点找一条<b>交替路径</b>(未匹配边 → 匹配边 → …),
 *           找到就把路径上的匹配状态整体翻转</td>
 *       <td>Θ(V·E)</td></tr>
 *   <tr><td>{@link Mode#DINIC}</td>
 *       <td>归约到最大流:源点→左部(容量 1)、原有的边(容量 1)、右部→汇点(容量 1),
 *           跑 {@link Dinic};流量为 1 的边就是匹配</td>
 *       <td>Θ(E·√V)(单位容量网络上的 Dinic)</td></tr>
 * </table>
 *
 * <p><b>顺带拿到的两个结论</b>(都源自 König 定理,本类直接给出结果并可由测试独立验证):</p>
 * <ul>
 *   <li><b>最小点覆盖</b>:用最少的顶点把每条边都"盖住",其大小恰好等于最大匹配
 *       ({@link #minVertexCoverLeft()} / {@link #minVertexCoverRight()});
 *       两种模式用<b>不同构造</b>得出(最小割 vs 交替路径可达集合),可以互相对照;</li>
 *   <li><b>最大独立集</b>:顶点数 − 最大匹配({@link #maxIndependentSetSize()})。</li>
 * </ul>
 *
 * <p><b>求解时机</b>:加完边之后<b>第一次查询时自动求解</b>(惰性),再继续加边会让结果失效并在
 * 下次查询时重新求解 —— 所以"查到的答案永远对应当前这张图",不用记着调用什么 solve()。</p>
 *
 * <pre>
 * BipartiteMatching matching = new BipartiteMatching(3, 3);
 * matching.addEdge(0, 0);
 * matching.addEdge(1, 1);
 * matching.size();                 // 最大匹配数(此时才真正计算)
 * matching.matchedLeft(0);         // 0 匹配到的右侧顶点(-1 表示没匹配上)
 * matching.minVertexCoverLeft();   // König 定理给出的最小点覆盖
 * </pre>
 *
 * @see Dinic
 * @see FordFulkerson
 * @see MinCut
 * @see <a href="https://algs4.cs.princeton.edu/64maxflow">Algorithms, 4th Edition, Section 6.4</a>
 */
public final class BipartiteMatching {

    /** 求最大匹配的两种实现 */
    public enum Mode {

        /** 匈牙利算法:交替路径 + 翻转(直接在图上看匹配) */
        KUHN,

        /** 归约到最大流,用 Dinic 求解 */
        DINIC
    }

    /** 左部顶点数 */
    private final int leftCount;

    /** 右部顶点数 */
    private final int rightCount;

    /** 所用算法 */
    private final Mode mode;

    /** adjacency[left] = 与左部顶点 left 相邻的右部顶点 */
    private final List<List<Integer>> adjacency;

    /** matchedLeft[left] = 匹配到的右部顶点;-1 表示未匹配 */
    private final int[] matchedLeft;

    /** matchedRight[right] = 匹配到的左部顶点;-1 表示未匹配 */
    private final int[] matchedRight;

    /** 最小点覆盖的左部部分 */
    private final List<Integer> coverLeft;

    /** 最小点覆盖的右部部分 */
    private final List<Integer> coverRight;

    /** 已加入的边数 */
    private int edgeCount;

    /** 最大匹配数 */
    private int size;

    /** 当前结果是否对应当前这张图(加边后就失效) */
    private boolean solved;

    /**
     * 建一个空的二分图(左 {@code leftCount}、右 {@code rightCount} 个顶点,用 Dinic 求匹配)。
     *
     * @param leftCount  左部顶点数,非负
     * @param rightCount 右部顶点数,非负
     * @throws IllegalArgumentException 顶点数为负
     */
    public BipartiteMatching(int leftCount, int rightCount) {
        this(leftCount, rightCount, Mode.DINIC);
    }

    /**
     * 建一个空的二分图,边随后用 {@link #addEdge(int, int)} 加入。
     *
     * @param leftCount  左部顶点数,非负
     * @param rightCount 右部顶点数,非负
     * @param mode       算法,不能为 null
     * @throws IllegalArgumentException 参数非法
     */
    public BipartiteMatching(int leftCount, int rightCount, Mode mode) {
        if (leftCount < 0 || rightCount < 0) {
            throw new IllegalArgumentException("顶点数不能为负: " + leftCount + ", " + rightCount);
        }
        if (mode == null) {
            throw new IllegalArgumentException("算法不能为 null");
        }
        this.leftCount = leftCount;
        this.rightCount = rightCount;
        this.mode = mode;
        this.adjacency = new ArrayList<List<Integer>>(leftCount);
        for (int left = 0; left < leftCount; left++) {
            adjacency.add(new ArrayList<Integer>());
        }
        this.matchedLeft = new int[leftCount];
        this.matchedRight = new int[rightCount];
        this.coverLeft = new ArrayList<Integer>();
        this.coverRight = new ArrayList<Integer>();
        this.edgeCount = 0;
        this.size = 0;
        this.solved = false;
    }

    // ------------------------------------------------------------------
    // 建模
    // ------------------------------------------------------------------

    /**
     * 加入一条边(左部顶点与右部顶点之间)。
     *
     * <p>不允许重复边:重复边在"归约到最大流"的构造里会变成两条容量 1 的平行弧,
     * 可能被同时推流,于是同一对顶点被算作两个匹配 —— 那是错的。</p>
     *
     * @param left  左部顶点编号
     * @param right 右部顶点编号
     * @throws IllegalArgumentException 编号越界或这条边已存在
     */
    public void addEdge(int left, int right) {
        validateLeft(left);
        validateRight(right);
        if (adjacency.get(left).contains(right)) {
            throw new IllegalArgumentException("左 " + left + " 与右 " + right + " 之间已经有边了");
        }
        adjacency.get(left).add(right);
        edgeCount++;
        solved = false;                                    // 图变了,旧结果作废
    }

    /** 需要时求解(惰性) */
    private void solveIfNeeded() {
        if (solved) {
            return;
        }
        coverLeft.clear();
        coverRight.clear();
        size = mode == Mode.KUHN
                ? solveKuhn()
                : solveDinic();
        solved = true;
    }

    // ------------------------------------------------------------------
    // 匈牙利算法(KUHN)
    // ------------------------------------------------------------------

    /**
     * 对每个左部顶点尝试找一条增广路,找到就把匹配规模 +1。
     *
     * @return 最大匹配数
     */
    private int solveKuhn() {
        Arrays.fill(matchedLeft, -1);
        Arrays.fill(matchedRight, -1);
        int matching = 0;
        for (int left = 0; left < leftCount; left++) {
            boolean[] visitedRight = new boolean[rightCount];
            int[] parentRight = new int[rightCount];
            if (tryAugment(left, visitedRight, parentRight)) {
                matching++;
            }
        }
        computeCoverByAlternatingSearch();
        return matching;
    }

    /**
     * 迭代式寻找一条增广路(交替路径:未匹配边 → 匹配边 → …)并翻转匹配状态。
     *
     * <p>用显式栈模拟递归,避免左部顶点很多时递归过深;{@code visitedRight} 保证每个右部顶点
     * 只访问一次,所以一次搜索是 Θ(E)。</p>
     *
     * @param start        起点(左部顶点)
     * @param visitedRight 右部顶点的访问标记
     * @param parentRight  parentRight[r] = 交替路径上"发现 r"的那个左部顶点
     * @return 是否找到并翻转了一条增广路
     */
    private boolean tryAugment(int start, boolean[] visitedRight, int[] parentRight) {
        Deque<Frame> stack = new ArrayDeque<Frame>();
        stack.push(new Frame(start, 0));
        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            List<Integer> neighbors = adjacency.get(top.left);
            if (top.cursor >= neighbors.size()) {
                stack.pop();
                continue;
            }
            int right = neighbors.get(top.cursor++);
            if (visitedRight[right]) {
                continue;
            }
            visitedRight[right] = true;
            parentRight[right] = top.left;
            if (matchedRight[right] == -1) {
                // 找到增广路:沿交替路径把匹配状态整体翻转
                int r = right;
                while (true) {
                    int l = parentRight[r];
                    int previousRight = matchedLeft[l];
                    matchedLeft[l] = r;
                    matchedRight[r] = l;
                    if (l == start) {
                        break;
                    }
                    r = previousRight;
                }
                return true;
            }
            stack.push(new Frame(matchedRight[right], 0));
        }
        return false;
    }

    /** 一个"暂停中的递归调用":左部顶点 + 它邻接表的当前位置 */
    private static final class Frame {
        final int left;
        int cursor;

        Frame(int left, int cursor) {
            this.left = left;
            this.cursor = cursor;
        }
    }

    /**
     * König 定理的经典构造:从<b>未匹配的左部顶点</b>出发,沿
     * "未匹配边(左→右)、匹配边(右→左)"做交替搜索,记可达集合为 Z,则
     * <pre>
     *   最小点覆盖 = (左部 \ Z) ∪ (右部 ∩ Z)
     * </pre>
     * 本方法用于 KUHN 模式;DINIC 模式改用最小割得到同一个集合,两种构造可以互相对照。
     */
    private void computeCoverByAlternatingSearch() {
        boolean[] reachableLeft = new boolean[leftCount];
        boolean[] reachableRight = new boolean[rightCount];
        Deque<Integer> queue = new ArrayDeque<Integer>();
        for (int left = 0; left < leftCount; left++) {
            if (matchedLeft[left] == -1) {
                reachableLeft[left] = true;
                queue.add(left);
            }
        }
        while (!queue.isEmpty()) {
            int left = queue.poll();
            for (int right : adjacency.get(left)) {
                if (right == matchedLeft[left] || reachableRight[right]) {
                    continue;                              // 只沿未匹配边走
                }
                reachableRight[right] = true;
                int partner = matchedRight[right];
                if (partner != -1 && !reachableLeft[partner]) {
                    reachableLeft[partner] = true;          // 匹配边:右 → 左
                    queue.add(partner);
                }
            }
        }
        for (int left = 0; left < leftCount; left++) {
            if (!reachableLeft[left]) {
                coverLeft.add(left);
            }
        }
        for (int right = 0; right < rightCount; right++) {
            if (reachableRight[right]) {
                coverRight.add(right);
            }
        }
    }

    // ------------------------------------------------------------------
    // 归约到最大流(DINIC)
    // ------------------------------------------------------------------

    /**
     * 建出单位容量流网络(源点→左部→右部→汇点)跑 Dinic,再从最小割读最小点覆盖。
     *
     * @return 最大匹配数
     */
    private int solveDinic() {
        Arrays.fill(matchedLeft, -1);
        Arrays.fill(matchedRight, -1);
        int source = leftCount + rightCount;
        int sink = source + 1;
        FlowNetwork network = new FlowNetwork(sink + 1);
        for (int left = 0; left < leftCount; left++) {
            network.addEdge(source, left, 1.0);
        }
        for (int right = 0; right < rightCount; right++) {
            network.addEdge(leftCount + right, sink, 1.0);
        }
        for (int left = 0; left < leftCount; left++) {
            for (int right : adjacency.get(left)) {
                network.addEdge(left, leftCount + right, 1.0);
            }
        }

        Dinic maxFlow = new Dinic(network, source, sink);
        for (FlowEdge edge : network.edges()) {
            if (edge.from() < leftCount && edge.to() >= leftCount
                    && edge.to() < leftCount + rightCount && Math.abs(edge.flow() - 1.0) < 1e-9) {
                int left = edge.from();
                int right = edge.to() - leftCount;
                matchedLeft[left] = right;
                matchedRight[right] = left;
            }
        }

        // König:最小点覆盖 = (左部不在 S 侧) ∪ (右部在 S 侧)
        MinCut cut = maxFlow.minCut();
        for (int left = 0; left < leftCount; left++) {
            if (!cut.inSourceSide(left)) {
                coverLeft.add(left);
            }
        }
        for (int right = 0; right < rightCount; right++) {
            if (cut.inSourceSide(leftCount + right)) {
                coverRight.add(right);
            }
        }
        return (int) Math.round(maxFlow.value());
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * @return 左部顶点数
     */
    public int leftCount() {
        return leftCount;
    }

    /**
     * @return 右部顶点数
     */
    public int rightCount() {
        return rightCount;
    }

    /**
     * @return 边数
     */
    public int edgeCount() {
        return edgeCount;
    }

    /**
     * @return 本实例所用的算法
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @return 最大匹配的大小(匹配边的条数)
     */
    public int size() {
        solveIfNeeded();
        return size;
    }

    /**
     * @param left 左部顶点编号
     * @return 匹配到的右部顶点编号;未匹配返回 -1
     * @throws IllegalArgumentException 编号越界
     */
    public int matchedLeft(int left) {
        validateLeft(left);
        solveIfNeeded();
        return matchedLeft[left];
    }

    /**
     * @param right 右部顶点编号
     * @return 匹配到的左部顶点编号;未匹配返回 -1
     * @throws IllegalArgumentException 编号越界
     */
    public int matchedRight(int right) {
        validateRight(right);
        solveIfNeeded();
        return matchedRight[right];
    }

    /**
     * @param left 左部顶点编号
     * @return 是否已匹配
     * @throws IllegalArgumentException 编号越界
     */
    public boolean isMatchedLeft(int left) {
        return matchedLeft(left) != -1;
    }

    /**
     * @param right 右部顶点编号
     * @return 是否已匹配
     * @throws IllegalArgumentException 编号越界
     */
    public boolean isMatchedRight(int right) {
        return matchedRight(right) != -1;
    }

    /**
     * @return 匹配的边列表,每个元素是 {@code int[]{左, 右}},按左部顶点升序
     */
    public List<int[]> matching() {
        solveIfNeeded();
        List<int[]> result = new ArrayList<int[]>();
        for (int left = 0; left < leftCount; left++) {
            if (matchedLeft[left] != -1) {
                result.add(new int[]{left, matchedLeft[left]});
            }
        }
        return result;
    }

    /**
     * @return 是否完美匹配(左右顶点数相同,且所有顶点都被匹配);
     *         按定义,0×0 的空图返回 true(空匹配就是完美匹配)
     */
    public boolean isPerfect() {
        solveIfNeeded();
        if (leftCount != rightCount || size != leftCount) {
            return false;
        }
        for (int left = 0; left < leftCount; left++) {
            if (matchedLeft[left] == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return 最小点覆盖的左部顶点(升序)
     */
    public List<Integer> minVertexCoverLeft() {
        solveIfNeeded();
        return new ArrayList<Integer>(coverLeft);
    }

    /**
     * @return 最小点覆盖的右部顶点(升序)
     */
    public List<Integer> minVertexCoverRight() {
        solveIfNeeded();
        return new ArrayList<Integer>(coverRight);
    }

    /**
     * @return 最小点覆盖的大小(按 König 定理等于 {@link #size()})
     */
    public int minVertexCoverSize() {
        solveIfNeeded();
        return coverLeft.size() + coverRight.size();
    }

    /**
     * @return 最大独立集的大小 = 顶点总数 − 最大匹配
     */
    public int maxIndependentSetSize() {
        return leftCount + rightCount - size();
    }

    /**
     * 检查给定的顶点集合是否真的"盖住"了每一条边(调用方自查与测试都用得上)。
     *
     * @param leftCover  左部被选中的顶点
     * @param rightCover 右部被选中的顶点
     * @return 每条边至少有一个端点被选中
     */
    public boolean isVertexCover(Set<Integer> leftCover, Set<Integer> rightCover) {
        if (leftCover == null || rightCover == null) {
            throw new IllegalArgumentException("集合不能为 null");
        }
        for (int left = 0; left < leftCount; left++) {
            for (int right : adjacency.get(left)) {
                if (!leftCover.contains(left) && !rightCover.contains(right)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void validateLeft(int left) {
        if (left < 0 || left >= leftCount) {
            throw new IllegalArgumentException("左部顶点 " + left + " 不在 [0, " + (leftCount - 1) + "] 内");
        }
    }

    private void validateRight(int right) {
        if (right < 0 || right >= rightCount) {
            throw new IllegalArgumentException("右部顶点 " + right + " 不在 [0, " + (rightCount - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code BipartiteMatching(DINIC): 左 5 / 右 5,8 条边,最大匹配 5},随后列出匹配与最小点覆盖
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("BipartiteMatching(").append(mode).append("): 左 ").append(leftCount)
                .append(" / 右 ").append(rightCount).append(",").append(edgeCount)
                .append(" 条边,最大匹配 ").append(size());
        if (isPerfect()) {
            sb.append("(完美匹配)");
        }
        sb.append(newline);
        sb.append("  匹配: ").append(matchingText()).append(newline);
        sb.append("  最小点覆盖: 左 ").append(minVertexCoverLeft()).append(" + 右 ")
                .append(minVertexCoverRight()).append(" = ").append(minVertexCoverSize())
                .append("(König:等于最大匹配)").append(newline);
        sb.append("  最大独立集: ").append(maxIndependentSetSize());
        return sb.toString();
    }

    /** 匹配的可读形式:{@code "左-右, 左-右, …"} */
    private String matchingText() {
        StringBuilder sb = new StringBuilder();
        for (int[] pair : matching()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(pair[0]).append('-').append(pair[1]);
        }
        return sb.length() == 0 ? "(空)" : sb.toString();
    }

    /**
     * 演示:一个 5×5 的"人—任务"二分图,用两种算法各跑一遍并对照结果。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        int[][] edges = {{0, 0}, {0, 1}, {1, 1}, {2, 2}, {3, 2}, {3, 3}, {4, 3}, {4, 4}};
        for (Mode mode : Mode.values()) {
            BipartiteMatching matching = new BipartiteMatching(5, 5, mode);
            for (int[] edge : edges) {
                matching.addEdge(edge[0], edge[1]);
            }
            System.out.println(matching);
            System.out.println("  这是一个点覆盖吗: "
                    + matching.isVertexCover(new HashSet<Integer>(matching.minVertexCoverLeft()),
                    new HashSet<Integer>(matching.minVertexCoverRight())));
            System.out.println();
        }
    }
}
