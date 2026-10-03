package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AssignmentProblem} 指派问题测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li><b>暴力对拍</b>:小问题上枚举 n! 种排列取最小总代价;</li>
 *   <li><b>两种实现一致</b>:JV(势函数法)与 MCMF(最小费用流)在随机代价矩阵上给出同样的总代价;</li>
 *   <li><b>最优性证书(对偶三条件)</b>:对偶可行 {@code c − u − v ≥ 0}、被选中的指派上取等号、
 *       {@code Σu + Σv = 最小总代价}(强对偶);</li>
 *   <li><b>交换图无负环</b>:把指派看成匹配后,若存在负代价的交替环就可以改进 ——
 *       用 {@link FloydWarshall} 在交换图上验证"没有负环"(与对偶条件互为独立参照物);</li>
 *   <li>负代价、不允许的边({@code +∞})、无完美指派、稠密矩阵构造、规模;</li>
 *   <li>最大化收益 = 代价取负后最小化(标准技巧)。</li>
 * </ol>
 */
@DisplayName("AssignmentProblem 指派问题测试")
class AssignmentProblemTest {

    private static final double EPS = 1e-6;

    /** 已知样例的代价矩阵(放在外层:内部类里不允许 static 字段) */
    private static final double[][] MATRIX = {
        {4.0, 1.0, 3.0},
        {2.0, 0.0, 5.0},
        {3.0, 2.0, 2.0}
    };

    private static AssignmentProblem of(double[][] matrix, AssignmentProblem.Mode mode) {
        AssignmentProblem problem = new AssignmentProblem(matrix.length, mode);
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix.length; j++) {
                if (Double.isFinite(matrix[i][j])) {
                    problem.addEdge(i, j, matrix[i][j]);
                }
            }
        }
        return problem;
    }

    @Nested
    @DisplayName("已知样例")
    class KnownDataTest {

        @Test
        @DisplayName("3×3:最小总代价 5,指派是 0→1、1→0、2→2")
        void knownMatrix() {
            for (AssignmentProblem.Mode mode : AssignmentProblem.Mode.values()) {
                AssignmentProblem problem = of(MATRIX, mode);
                assertTrue(problem.hasAssignment());
                assertEquals(5.0, problem.cost(), EPS, mode + " 的总代价");
                assertArrayEquals(new int[]{1, 0, 2}, problem.assignment(), mode + " 的指派");
                assertEquals(1, problem.matched(0));
                assertEquals(3, problem.pairs().size());
                assertEquals(3, problem.n());
                assertEquals(9, problem.edgeCount());
            }
        }

        @Test
        @DisplayName("稠密矩阵构造 + 用 +∞ 表示不允许")
        void denseMatrixConstructor() {
            double[][] matrix = {
                {1.0, Double.POSITIVE_INFINITY},
                {2.0, 3.0}
            };
            AssignmentProblem problem = new AssignmentProblem(matrix);
            assertEquals(2, problem.n());
            assertEquals(3, problem.edgeCount(), "只有 3 对允许");
            assertTrue(problem.hasAssignment());
            assertEquals(4.0, problem.cost(), EPS, "0→0 (1) + 1→1 (3)");
            assertArrayEquals(new int[]{0, 1}, problem.assignment());
            assertNull(problem.costOf(0, 1));
            assertEquals(1.0, problem.costOf(0, 0), EPS);
        }

        @Test
        @DisplayName("负代价也能处理(取负就是最大化收益)")
        void negativeCosts() {
            double[][] matrix = {
                {-5.0, 2.0},
                {1.0, -3.0}
            };
            AssignmentProblem problem = new AssignmentProblem(matrix);
            assertEquals(-8.0, problem.cost(), EPS);
            assertArrayEquals(new int[]{0, 1}, problem.assignment());

            double[][] profit = {
                {5.0, -2.0},
                {-1.0, 3.0}
            };
            double[][] negated = new double[2][2];
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    negated[i][j] = -profit[i][j];
                }
            }
            assertEquals(-8.0, new AssignmentProblem(negated).cost(), EPS,
                    "最大收益 8 = 取负后的最小代价 −8");
        }

        @Test
        @DisplayName("无完美指派:hasAssignment 为 false,cost/assignment 明确报错")
        void infeasible() {
            double[][] matrix = {
                {1.0, Double.POSITIVE_INFINITY},
                {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY}
            };
            for (AssignmentProblem.Mode mode : AssignmentProblem.Mode.values()) {
                AssignmentProblem problem = of(matrix, mode);
                assertFalse(problem.hasAssignment(), mode + " 应当判为无完美指派");
                assertThrows(IllegalStateException.class, problem::cost);
                assertThrows(IllegalStateException.class, problem::assignment);
                assertThrows(IllegalStateException.class, problem::pairs);
                assertEquals(-1, problem.matched(0), "未求解时返回 −1");
                assertTrue(problem.toString().contains("不存在完美指派"));
            }
        }

        @Test
        @DisplayName("空问题(n = 0):平凡可解,代价 0")
        void emptyProblem() {
            AssignmentProblem problem = new AssignmentProblem(0);
            assertTrue(problem.hasAssignment());
            assertEquals(0.0, problem.cost(), EPS);
            assertEquals(0, problem.assignment().length);
        }

        @Test
        @DisplayName("toString 含模式、规模、代价与指派")
        void toStringContent() {
            String text = new AssignmentProblem(MATRIX).toString();
            assertTrue(text.contains("AssignmentProblem(JV)"), text);
            assertTrue(text.contains("最小总代价 5.00"), text);
            assertTrue(text.contains("工0→任1"), text);
            assertTrue(text.contains("势函数"), text);
        }
    }

    @Nested
    @DisplayName("对拍:暴力 / 双模式 / 最优性证书")
    class OracleTest {

        @Test
        @DisplayName("40 个随机小矩阵:与暴力枚举 n! 排列一致")
        void agreesWithBruteForce() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 40; trial++) {
                int n = 1 + rnd.nextInt(6);
                double[][] matrix = new double[n][n];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        matrix[i][j] = Math.rint((rnd.nextDouble() * 20 - 6) * 10) / 10;
                    }
                }
                double expected = bruteForcePermutation(matrix);
                for (AssignmentProblem.Mode mode : AssignmentProblem.Mode.values()) {
                    AssignmentProblem problem = of(matrix, mode);
                    assertEquals(expected, problem.cost(), 1e-5,
                            "第 " + trial + " 个矩阵(" + mode + ", n=" + n + ")");
                }
            }
        }

        @Test
        @DisplayName("30 个随机矩阵:JV 与 MCMF 两种实现给出同样的总代价")
        void modesAgree() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 30; trial++) {
                int n = 1 + rnd.nextInt(12);
                double[][] matrix = new double[n][n];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        matrix[i][j] = Math.rint((rnd.nextDouble() * 40 - 15) * 10) / 10;
                    }
                }
                AssignmentProblem jv = of(matrix, AssignmentProblem.Mode.JV);
                AssignmentProblem mcmf = of(matrix, AssignmentProblem.Mode.MCMF);
                assertEquals(jv.cost(), mcmf.cost(), 1e-4, "第 " + trial + " 个矩阵(n=" + n + ")");
            }
        }

        @Test
        @DisplayName("对偶三条件(对偶可行 + 互补松弛 + 强对偶)")
        void dualCertificate() {
            Random rnd = new Random(31415L);
            for (int trial = 0; trial < 20; trial++) {
                int n = 1 + rnd.nextInt(8);
                double[][] matrix = new double[n][n];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        matrix[i][j] = Math.rint((rnd.nextDouble() * 30 - 10) * 10) / 10;
                    }
                }
                AssignmentProblem problem = of(matrix, AssignmentProblem.Mode.JV);
                int[] solution = problem.assignment();
                double sumU = 0.0;
                double sumV = 0.0;
                for (int i = 0; i < n; i++) {
                    sumU += problem.dualRow(i);
                    sumV += problem.dualCol(i);
                }
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        double reduced = matrix[i][j] - problem.dualRow(i) - problem.dualCol(j);
                        assertTrue(reduced >= -1e-6, "对偶可行被破坏:第 " + trial + " 个矩阵 ("
                                + i + "," + j + ") reduced=" + reduced);
                        if (solution[i] == j) {
                            assertEquals(0.0, reduced, 1e-6, "被选中的指派上应取等号");
                        }
                    }
                }
                assertEquals(problem.cost(), sumU + sumV, 1e-6,
                        "强对偶:Σu+Σv 应等于最小总代价(第 " + trial + " 个矩阵)");
            }
        }

        @Test
        @DisplayName("交换图无负环(最优性的另一种证书,用 FloydWarshall 检测)")
        void noNegativeExchangeCycle() {
            Random rnd = new Random(1618L);
            for (int trial = 0; trial < 20; trial++) {
                int n = 2 + rnd.nextInt(7);
                double[][] matrix = new double[n][n];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        matrix[i][j] = Math.rint((rnd.nextDouble() * 20 - 5) * 10) / 10;
                    }
                }
                AssignmentProblem problem = of(matrix, AssignmentProblem.Mode.JV);
                int[] solution = problem.assignment();
                // 交换图:工人 i → 工人 k 的权值 = c(i, σ(k)) − c(k, σ(k))
                // "没有负环" ⟺ 当前指派无法通过交替环改进 ⟺ 最优
                EdgeWeightedDigraph exchange = new EdgeWeightedDigraph(n);
                for (int i = 0; i < n; i++) {
                    for (int k = 0; k < n; k++) {
                        if (i == k) {
                            continue;
                        }
                        exchange.addEdge(i, k, matrix[i][solution[k]] - matrix[k][solution[k]]);
                    }
                }
                assertFalse(new FloydWarshall(exchange).hasNegativeCycle(),
                        "第 " + trial + " 个矩阵:最优指派不应留下可改进的负环");
            }
        }
    }

    @Nested
    @DisplayName("接口行为、校验与规模")
    class BehaviourTest {

        @Test
        @DisplayName("惰性求解:加边后结果自动更新;重复边被拒绝")
        void lazyResolve() {
            AssignmentProblem problem = new AssignmentProblem(2);
            problem.addEdge(0, 0, 1.0);
            problem.addEdge(1, 1, 1.0);
            assertEquals(2.0, problem.cost(), EPS);
            problem.addEdge(0, 1, 0.5);
            problem.addEdge(1, 0, 0.5);
            assertEquals(1.0, problem.cost(), EPS, "加边后应重新求解");
            assertThrows(IllegalArgumentException.class, () -> problem.addEdge(0, 0, 9.0));
        }

        @Test
        @DisplayName("参数校验:负 n、null 模式、越界编号、非有限代价、非方阵")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new AssignmentProblem(-1));
            assertThrows(IllegalArgumentException.class,
                    () -> new AssignmentProblem(2, (AssignmentProblem.Mode) null));
            assertThrows(IllegalArgumentException.class, () -> new AssignmentProblem((double[][]) null));
            assertThrows(IllegalArgumentException.class, () -> new AssignmentProblem(new double[][]{{1.0, 2.0}}));
            AssignmentProblem problem = new AssignmentProblem(2);
            assertThrows(IllegalArgumentException.class, () -> problem.addEdge(2, 0, 1.0));
            assertThrows(IllegalArgumentException.class, () -> problem.addEdge(0, -1, 1.0));
            assertThrows(IllegalArgumentException.class, () -> problem.addEdge(0, 0, Double.NaN));
            assertThrows(IllegalArgumentException.class,
                    () -> problem.addEdge(0, 0, Double.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class, () -> problem.matched(2));
            assertThrows(IllegalArgumentException.class, () -> problem.costOf(0, 5));
            problem.addEdge(0, 0, 1.0);
            problem.addEdge(1, 1, 1.0);
            assertEquals(2.0, problem.cost(), EPS);
            assertThrows(IllegalArgumentException.class, () -> problem.dualRow(2));
            assertThrows(IllegalArgumentException.class, () -> problem.dualCol(-1));
        }

        @Test
        @DisplayName("MCMF 模式不维护势函数(与 Johnson 的口径一致)")
        void dualsOnlyInJvMode() {
            double[][] matrix = {{1.0, 2.0}, {3.0, 4.0}};
            AssignmentProblem mcmf = of(matrix, AssignmentProblem.Mode.MCMF);
            assertTrue(mcmf.hasAssignment());
            assertThrows(IllegalStateException.class, () -> mcmf.dualRow(0));
            assertThrows(IllegalStateException.class, () -> mcmf.dualCol(0));
            assertFalse(mcmf.toString().contains("势函数"));
        }

        @Test
        @DisplayName("规模:300×300 随机矩阵(JV Θ(n³))")
        void scale() {
            Random rnd = new Random(99L);
            int n = 300;
            double[][] matrix = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    matrix[i][j] = rnd.nextInt(1000);
                }
            }
            AssignmentProblem problem = new AssignmentProblem(matrix);
            assertTrue(problem.hasAssignment());
            int[] solution = problem.assignment();
            Arrays.sort(solution);
            for (int i = 0; i < n; i++) {
                assertEquals(i, solution[i], "指派必须是 0..n−1 的一个排列");
            }
            // 独立核对:总代价等于按指派取出的代价之和
            double sum = 0.0;
            int[] actual = problem.assignment();
            for (int i = 0; i < n; i++) {
                sum += matrix[i][actual[i]];
            }
            assertEquals(problem.cost(), sum, 1e-6);
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /** 暴力:枚举所有排列取最小总代价(只适合 n ≤ 8) */
    static double bruteForcePermutation(double[][] matrix) {
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
