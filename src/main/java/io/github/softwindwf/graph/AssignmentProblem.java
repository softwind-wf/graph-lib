package io.github.softwindwf.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * 指派问题(Assignment Problem):n 个工人配 n 项任务,一人一项、一项一人,
 * 求<b>总代价最小</b>的指派。它是 {@link BipartiteMatching}(无权匹配)的加权版。
 *
 * <p><b>两条实现路线(结果必须一致)</b>:</p>
 * <table border="1" summary="指派问题的两种实现">
 *   <tr><th>模式</th><th>做法</th><th>时间</th></tr>
 *   <tr><td>{@link Mode#JV}(默认)</td>
 *       <td>匈牙利 / Jonker–Volgenant 型:逐个工人做"最短增广路",用势函数把代价化成非负,
 *           每步 Θ(n²),总共 Θ(n³)</td>
 *       <td>Θ(n³)</td></tr>
 *   <tr><td>{@link Mode#MCMF}</td>
 *       <td>归约成<b>最小费用最大流</b>:源→工人(容量 1、费用 0)、工人→任务(容量 1、费用 = 代价)、
 *           任务→汇(容量 1、费用 0),然后用 SPFA 逐条找最小费用增广路</td>
 *       <td>Θ(n · E · V) 量级</td></tr>
 * </table>
 *
 * <p><b>与对偶的关系(最优性证书)</b>:设 {@code u(i)}、{@code v(j)} 是势函数,
 * 最优解满足</p>
 * <pre>
 *   ① 对偶可行:c(i,j) − u(i) − v(j) ≥ 0        对所有允许的 (i,j)
 *   ② 互补松弛:被选中的指派上取等号
 *   ③ 强对偶:Σu + Σv = 最小总代价
 * </pre>
 * <p>本类把势函数暴露成 {@link #dualRow(int)} / {@link #dualCol(int)},测试里就用这三条来验收最优性
 * (与"暴力枚举所有排列"互为独立参照物)。</p>
 *
 * <p><b>只有"最小化"</b>:要最大化收益,把代价取负后调用即可(总收益 = −最小总代价)——
 * 这是指派问题的标准技巧,本类不再重复提供 {@code maximize} 开关。</p>
 *
 * <p><b>允许负代价</b>(两种模式都支持);代价必须有限,{@code Double.POSITIVE_INFINITY}
 * 表示"这项任务不能派给这个工人"(也可以干脆不加这条边)。没有完美指派时
 * {@link #hasAssignment()} 为 false,而 {@link #cost()} / {@link #assignment()} 会抛
 * {@link IllegalStateException}(和 {@link BellmanFordSP} 处理负环的口径一致:
 * 无可返回的结果就明确报错)。</p>
 *
 * <pre>
 * AssignmentProblem problem = new AssignmentProblem(3);
 * problem.addEdge(0, 0, 4.0);
 * problem.addEdge(0, 1, 1.0);
 * …
 * problem.hasAssignment();
 * problem.cost();        // 最小总代价
 * problem.assignment();  // 每个工人派到的任务
 * </pre>
 *
 * @see BipartiteMatching
 * @see FordFulkerson
 * @see <a href="https://algs4.cs.princeton.edu/64maxflow">Algorithms, 4th Edition, Section 6.4</a>
 */
public final class AssignmentProblem {

    /** 求最小代价指派的两种实现 */
    public enum Mode {

        /** 匈牙利 / Jonker–Volgenant 势函数法(默认,Θ(n³)) */
        JV,

        /** 归约成最小费用最大流,用 SPFA 逐条增广 */
        MCMF
    }

    /** 表示"这条边不允许"的大数:用有限大数避免 Inf/NaN 参与运算 */
    private static final double FORBIDDEN = 1e12;

    /** 判定"是否用到了不允许的边"的阈值 */
    private static final double FORBIDDEN_THRESHOLD = 1e11;

    /** 浮点比较容差 */
    private static final double EPS = 1e-9;

    /** 工人数 = 任务数 */
    private final int n;

    /** 实现方式 */
    private final Mode mode;

    /** cost[i][j] = 工人 i 做任务 j 的代价;不允许为 FORBIDDEN */
    private final double[][] cost;

    /** 允许的边数 */
    private int edgeCount;

    /** 是否已求解(惰性:加边后作废) */
    private boolean solved;

    /** 是否存在完美指派 */
    private boolean hasAssignment;

    /** 最小总代价 */
    private double costValue;

    /** assignment[i] = 工人 i 派到的任务;-1 表示未派 */
    private final int[] assignment;

    /** 势函数(对偶变量) */
    private final double[] dualRow;

    /** 势函数(对偶变量) */
    private final double[] dualCol;

    /**
     * 建一个 n×n 的指派问题(只允许成对的工人与任务,边随后用 {@link #addEdge(int, int, double)} 加入)。
     *
     * @param n 工人数(等于任务数),非负
     * @throws IllegalArgumentException 顶点数为负
     */
    public AssignmentProblem(int n) {
        this(n, Mode.JV);
    }

    /**
     * 建一个 n×n 的指派问题。
     *
     * @param n    工人数(等于任务数),非负
     * @param mode 实现方式,不能为 null
     * @throws IllegalArgumentException 参数非法
     */
    public AssignmentProblem(int n, Mode mode) {
        if (n < 0) {
            throw new IllegalArgumentException("工人数不能为负: " + n);
        }
        if (mode == null) {
            throw new IllegalArgumentException("实现方式不能为 null");
        }
        this.n = n;
        this.mode = mode;
        this.cost = new double[n][n];
        for (double[] row : cost) {
            Arrays.fill(row, FORBIDDEN);
        }
        this.assignment = new int[n];
        Arrays.fill(assignment, -1);
        this.dualRow = new double[n];
        this.dualCol = new double[n];
        this.edgeCount = 0;
        this.solved = false;
    }

    /**
     * 从稠密代价矩阵建问题:有限值表示允许,{@link Double#POSITIVE_INFINITY} 或 NaN 表示不允许。
     *
     * @param costMatrix n×n 代价矩阵(必须是方阵),不能为 null
     * @throws IllegalArgumentException 矩阵为 null、不是方阵或含非法值
     */
    public AssignmentProblem(double[][] costMatrix) {
        this(checkSquare(costMatrix).length, Mode.JV);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double value = costMatrix[i][j];
                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    addEdge(i, j, value);
                }
            }
        }
    }

    /** 校验并返回方阵本身 */
    private static double[][] checkSquare(double[][] costMatrix) {
        if (costMatrix == null) {
            throw new IllegalArgumentException("代价矩阵不能为 null");
        }
        for (int i = 0; i < costMatrix.length; i++) {
            if (costMatrix[i] == null || costMatrix[i].length != costMatrix.length) {
                throw new IllegalArgumentException("代价矩阵必须是方阵");
            }
        }
        return costMatrix;
    }

    /**
     * 允许"工人 i 做任务 j",并给出代价(可以重复调用;同一对之间不允许重复加边)。
     *
     * @param worker 工人编号
     * @param job    任务编号
     * @param cost   代价,有限实数(可以是负数)
     * @throws IllegalArgumentException 编号越界、代价非有限或这条边已存在
     */
    public void addEdge(int worker, int job, double cost) {
        validate(worker, job);
        if (Double.isNaN(cost) || Double.isInfinite(cost)) {
            throw new IllegalArgumentException("代价必须是有限实数,当前为 " + cost);
        }
        if (this.cost[worker][job] != FORBIDDEN) {
            throw new IllegalArgumentException("工人 " + worker + " 与任务 " + job + " 之间已经有代价了");
        }
        this.cost[worker][job] = cost;
        edgeCount++;
        solved = false;
    }

    /** 需要时求解(惰性) */
    private void solveIfNeeded() {
        if (solved) {
            return;
        }
        Arrays.fill(assignment, -1);
        if (n == 0) {
            hasAssignment = true;
            costValue = 0.0;
            solved = true;
            return;
        }
        if (mode == Mode.JV) {
            solveWithPotentials();
        }
        else {
            solveWithMinCostFlow();
        }
        solved = true;
    }

    // ------------------------------------------------------------------
    // 匈牙利 / Jonker–Volgenant:逐个工人找最短增广路,用势函数保持非负费用
    // ------------------------------------------------------------------

    /**
     * 经典的 Θ(n³) 实现(1-indexed 的教科书写法):
     * {@code p[j]} = 任务 j 分配给的工人,{@code way[j]} 记录增广路径,{@code u/v} 是势函数。
     * 最终总代价等于 {@code −v[0]}。
     */
    private void solveWithPotentials() {
        double[] u = new double[n + 1];
        double[] v = new double[n + 1];
        int[] p = new int[n + 1];
        int[] way = new int[n + 1];
        double[][] a = new double[n + 1][n + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= n; j++) {
                a[i][j] = cost[i - 1][j - 1];
            }
        }

        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[n + 1];
            boolean[] used = new boolean[n + 1];
            Arrays.fill(minv, Double.POSITIVE_INFINITY);
            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = Double.POSITIVE_INFINITY;
                int j1 = -1;
                for (int j = 1; j <= n; j++) {
                    if (used[j]) {
                        continue;
                    }
                    double current = a[i0][j] - u[i0] - v[j];
                    if (current < minv[j]) {
                        minv[j] = current;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                if (Double.isInfinite(delta)) {
                    hasAssignment = false;                 // 剩余任务全都不可用 → 无完美指派
                    return;
                }
                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    }
                    else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            }
            while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            }
            while (j0 != 0);
        }

        for (int j = 1; j <= n; j++) {
            int worker = p[j];
            if (worker <= 0 || cost[worker - 1][j - 1] > FORBIDDEN_THRESHOLD) {
                hasAssignment = false;
                return;
            }
            assignment[worker - 1] = j - 1;
        }
        hasAssignment = true;
        costValue = -v[0];
        for (int i = 1; i <= n; i++) {
            dualRow[i - 1] = u[i];
            dualCol[i - 1] = v[i];
        }
    }

    // ------------------------------------------------------------------
    // 最小费用最大流(SPFA 逐条增广)
    // ------------------------------------------------------------------

    /** 网络里的一条边(含反向边索引,便于退流) */
    private static final class Arc {
        final int to;
        final int cost;
        int capacity;
        int reverse;

        Arc(int to, int capacity, int cost) {
            this.to = to;
            this.capacity = capacity;
            this.cost = cost;
        }
    }

    /** 把代价乘以 SCALE 转成整数费用,便于最短路比较(代价是实数,乘大后取整仍然精确到 1e-6) */
    private static final double SCALE = 1e6;

    private void solveWithMinCostFlow() {
        // 节点:0..n-1 工人,n..2n-1 任务,2n 源点,2n+1 汇点
        int source = 2 * n;
        int sink = 2 * n + 1;
        int vertices = 2 * n + 2;
        List<List<Arc>> adjacency = new ArrayList<List<Arc>>(vertices);
        for (int v = 0; v < vertices; v++) {
            adjacency.add(new ArrayList<Arc>());
        }
        for (int i = 0; i < n; i++) {
            addArc(adjacency, source, i, 1, 0);
            addArc(adjacency, n + i, sink, 1, 0);
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (cost[i][j] <= FORBIDDEN_THRESHOLD) {
                    addArc(adjacency, i, n + j, 1, (int) Math.round(cost[i][j] * SCALE));
                }
            }
        }

        int flow = 0;
        long totalCost = 0;
        int[] previousVertex = new int[vertices];
        int[] previousArc = new int[vertices];
        while (flow < n) {
            // SPFA:残量图上的最小费用路(允许负费用边)
            long[] distance = new long[vertices];
            Arrays.fill(distance, Long.MAX_VALUE / 4);
            boolean[] inQueue = new boolean[vertices];
            Deque<Integer> queue = new ArrayDeque<Integer>();
            distance[source] = 0;
            queue.add(source);
            inQueue[source] = true;
            while (!queue.isEmpty()) {
                int v = queue.poll();
                inQueue[v] = false;
                List<Arc> arcs = adjacency.get(v);
                for (int k = 0; k < arcs.size(); k++) {
                    Arc arc = arcs.get(k);
                    if (arc.capacity <= 0 || distance[v] + arc.cost >= distance[arc.to]) {
                        continue;
                    }
                    distance[arc.to] = distance[v] + arc.cost;
                    previousVertex[arc.to] = v;
                    previousArc[arc.to] = k;
                    if (!inQueue[arc.to]) {
                        queue.add(arc.to);
                        inQueue[arc.to] = true;
                    }
                }
            }
            if (distance[sink] >= Long.MAX_VALUE / 8) {
                hasAssignment = false;                     // 增广不到汇点 → 无完美指派
                return;
            }
            int pushed = n;
            for (int v = sink; v != source; v = previousVertex[v]) {
                pushed = Math.min(pushed, adjacency.get(previousVertex[v]).get(previousArc[v]).capacity);
            }
            for (int v = sink; v != source; v = previousVertex[v]) {
                Arc arc = adjacency.get(previousVertex[v]).get(previousArc[v]);
                arc.capacity -= pushed;
                adjacency.get(v).get(arc.reverse).capacity += pushed;
            }
            flow += pushed;
            totalCost += distance[sink] * pushed;
        }

        hasAssignment = true;
        costValue = totalCost / SCALE;
        for (int i = 0; i < n; i++) {
            for (Arc arc : adjacency.get(i)) {
                if (arc.to >= n && arc.to < 2 * n && arc.capacity == 0) {
                    assignment[i] = arc.to - n;            // 这条工人→任务的弧用满了 = 匹配
                }
            }
        }
    }

    /** 加一条弧及其反向弧 */
    private static void addArc(List<List<Arc>> adjacency, int from, int to, int capacity, int cost) {
        Arc forward = new Arc(to, capacity, cost);
        Arc backward = new Arc(from, 0, -cost);
        forward.reverse = adjacency.get(to).size();
        backward.reverse = adjacency.get(from).size();
        adjacency.get(from).add(forward);
        adjacency.get(to).add(backward);
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * @return 工人数(等于任务数)
     */
    public int n() {
        return n;
    }

    /**
     * @return 允许的"工人—任务"对数
     */
    public int edgeCount() {
        return edgeCount;
    }

    /**
     * @return 本实例所用的实现方式
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @return 是否存在完美指派(每个工人都有任务、每个任务都有人做)
     */
    public boolean hasAssignment() {
        solveIfNeeded();
        return hasAssignment;
    }

    /**
     * 最小总代价。
     *
     * @return 最小总代价
     * @throws IllegalStateException 不存在完美指派
     */
    public double cost() {
        solveIfNeeded();
        if (!hasAssignment) {
            throw new IllegalStateException("不存在完美指派:有工人或任务无法配对"
                    + "(可先调用 hasAssignment() 判断)");
        }
        return costValue;
    }

    /**
     * @return 每个工人派到的任务编号({@code -1} 表示不存在完美指派时未派)
     * @throws IllegalStateException 不存在完美指派
     */
    public int[] assignment() {
        solveIfNeeded();
        if (!hasAssignment) {
            throw new IllegalStateException("不存在完美指派:有工人或任务无法配对");
        }
        return Arrays.copyOf(assignment, n);
    }

    /**
     * @param worker 工人编号
     * @return 该工人派到的任务;-1 表示未派
     * @throws IllegalArgumentException 编号越界
     */
    public int matched(int worker) {
        solveIfNeeded();
        if (worker < 0 || worker >= n) {
            throw new IllegalArgumentException("工人编号 " + worker + " 不在 [0, " + (n - 1) + "] 内");
        }
        return hasAssignment ? assignment[worker] : -1;
    }

    /**
     * 指派结果({@code int[]{工人, 任务}} 列表,按工人升序)。
     *
     * @return 指派列表
     * @throws IllegalStateException 不存在完美指派
     */
    public List<int[]> pairs() {
        int[] solution = assignment();
        List<int[]> pairs = new ArrayList<int[]>(n);
        for (int i = 0; i < n; i++) {
            pairs.add(new int[]{i, solution[i]});
        }
        return pairs;
    }

    /**
     * 势函数(对偶变量)u(i):{@code c(i,j) − u(i) − v(j) ≥ 0} 且被选中的指派上取等号,
     * 于是 {@code Σu + Σv = 最小总代价}。
     *
     * @param worker 工人编号
     * @return u(worker)
     * @throws IllegalArgumentException 编号越界
     * @throws IllegalStateException    不存在完美指派
     */
    public double dualRow(int worker) {
        solveIfNeeded();
        checkDualsAvailable();
        if (worker < 0 || worker >= n) {
            throw new IllegalArgumentException("工人编号 " + worker + " 不在 [0, " + (n - 1) + "] 内");
        }
        return dualRow[worker];
    }

    /**
     * 势函数(对偶变量)v(j)。
     *
     * @param job 任务编号
     * @return v(job)
     * @throws IllegalArgumentException 编号越界
     * @throws IllegalStateException    不存在完美指派
     */
    public double dualCol(int job) {
        solveIfNeeded();
        checkDualsAvailable();
        if (job < 0 || job >= n) {
            throw new IllegalArgumentException("任务编号 " + job + " 不在 [0, " + (n - 1) + "] 内");
        }
        return dualCol[job];
    }

    /** 势函数只在 JV 模式有意义(与 {@link Johnson#potential(int)} 的口径一致) */
    private void checkDualsAvailable() {
        if (!hasAssignment) {
            throw new IllegalStateException("不存在完美指派,没有对偶解");
        }
        if (mode != Mode.JV) {
            throw new IllegalStateException("当前模式(" + mode + ")不维护势函数,请用 Mode.JV");
        }
    }

    /**
     * @param worker 工人编号
     * @param job    任务编号
     * @return 该配对的代价;不允许时返回 {@code null}
     * @throws IllegalArgumentException 编号越界
     */
    public Double costOf(int worker, int job) {
        validate(worker, job);
        return cost[worker][job] > FORBIDDEN_THRESHOLD ? null : cost[worker][job];
    }

    private void validate(int worker, int job) {
        if (worker < 0 || worker >= n) {
            throw new IllegalArgumentException("工人编号 " + worker + " 不在 [0, " + (n - 1) + "] 内");
        }
        if (job < 0 || job >= n) {
            throw new IllegalArgumentException("任务编号 " + job + " 不在 [0, " + (n - 1) + "] 内");
        }
    }

    /**
     * @return 形如 {@code AssignmentProblem(JV): n=3, 允许 5 对, 最小总代价 5.00},随后列出指派与势函数
     */
    @Override
    public String toString() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("AssignmentProblem(").append(mode).append("): n=").append(n)
                .append(",允许 ").append(edgeCount).append(" 对");
        if (!hasAssignment()) {
            sb.append(",不存在完美指派");
            return sb.toString();
        }
        sb.append(String.format(",最小总代价 %.2f", cost()));
        sb.append(newline);
        sb.append("  指派: ").append(assignmentText());
        if (mode == Mode.JV) {
            sb.append(newline);
            sb.append("  势函数: u=").append(Arrays.toString(Arrays.copyOf(dualRow, n)))
                    .append(", v=").append(Arrays.toString(Arrays.copyOf(dualCol, n)))
                    .append("(Σu+Σv = ").append(String.format("%.2f", sumDuals())).append(")");
        }
        return sb.toString();
    }

    /** 指派的可读形式:{@code "工人0→任务1, 工人1→任务0, …"} */
    private String assignmentText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('工').append(i).append('→').append('任').append(assignment[i])
                    .append('(').append(String.format("%.2f", cost[i][assignment[i]])).append(')');
        }
        return sb.length() == 0 ? "(空)" : sb.toString();
    }

    /** Σu + Σv(按强对偶,它等于最小总代价) */
    private double sumDuals() {
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += dualRow[i] + dualCol[i];
        }
        return sum;
    }

    /**
     * 演示:3 个工人 3 项任务的最小代价指派,并与另一模式对照。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        double[][] matrix = {
            {4.0, 1.0, 3.0},
            {2.0, 0.0, 5.0},
            {3.0, 2.0, 2.0}
        };
        for (Mode mode : Mode.values()) {
            AssignmentProblem problem = new AssignmentProblem(3, mode);
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    problem.addEdge(i, j, matrix[i][j]);
                }
            }
            System.out.println(problem);
            System.out.println();
        }

        AssignmentProblem jv = new AssignmentProblem(matrix);
        System.out.println("直接传矩阵: " + jv);
        System.out.println("对照:暴力枚举 3! = 6 种排列的最小代价 = " + bruteForceSmallest(matrix));
    }

    /** 演示用:暴力枚举所有排列取最小(仅适合极小 n) */
    private static double bruteForceSmallest(double[][] matrix) {
        int n = matrix.length;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        return permute(matrix, order, 0);
    }

    private static double permute(double[][] matrix, int[] order, int index) {
        if (index == order.length) {
            double sum = 0.0;
            for (int i = 0; i < order.length; i++) {
                sum += matrix[i][order[i]];
            }
            return sum;
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = index; i < order.length; i++) {
            int swap = order[index];
            order[index] = order[i];
            order[i] = swap;
            best = Math.min(best, permute(matrix, order, index + 1));
            swap = order[index];
            order[index] = order[i];
            order[i] = swap;
        }
        return best;
    }
}
