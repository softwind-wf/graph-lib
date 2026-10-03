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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BipartiteMatching} 二分图匹配测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li><b>匹配的合法性</b>:每个顶点最多用一次、每条匹配边都真实存在
 *       (两种模式都要检查,因为最大匹配可能不唯一);</li>
 *   <li><b>最优性</b>:与"暴力回溯枚举最大匹配"逐图对照(小图);</li>
 *   <li>两种模式(匈牙利 / 归约最大流 + Dinic)结果一致;</li>
 *   <li><b>König 定理</b>:最小点覆盖的大小等于最大匹配,且确实覆盖每条边;
 *       两种模式用不同构造得到覆盖,分别验证;</li>
 *   <li><b>Hall 定理</b>:匹配能饱和整个左部 ⟺ 左部任意子集 S 都有 |N(S)| ≥ |S|;</li>
 *   <li>惰性求解:加边后结果自动更新;重复边被拒绝;规模测试。</li>
 * </ol>
 */
@DisplayName("BipartiteMatching 二分图匹配测试")
class BipartiteMatchingTest {

    @Nested
    @DisplayName("已知小例子")
    class KnownCaseTest {

        @Test
        @DisplayName("完美匹配:5 个人 5 个任务,最大匹配 5")
        void perfectMatching() {
            int[][] edges = {{0, 0}, {0, 1}, {1, 1}, {2, 2}, {3, 2}, {3, 3}, {4, 3}, {4, 4}};
            for (BipartiteMatching.Mode mode : BipartiteMatching.Mode.values()) {
                BipartiteMatching matching = build(5, 5, mode, edges);
                assertEquals(5, matching.size(), mode + " 的最大匹配");
                assertTrue(matching.isPerfect(), mode + " 应判定为完美匹配");
                assertValidMatching(matching, edges);
            }
        }

        @Test
        @DisplayName("左右不对称:2 个左顶点、4 个右顶点,最多匹配 2")
        void unbalanced() {
            int[][] edges = {{0, 0}, {0, 1}, {1, 1}, {1, 2}, {1, 3}};
            for (BipartiteMatching.Mode mode : BipartiteMatching.Mode.values()) {
                BipartiteMatching matching = build(2, 4, mode, edges);
                assertEquals(2, matching.size());
                assertFalse(matching.isPerfect(), "两侧顶点数不同,谈不完美匹配");
                assertValidMatching(matching, edges);
                assertEquals(2, matching.minVertexCoverSize(), "König:覆盖大小 = 匹配大小");
            }
        }

        @Test
        @DisplayName("三个左顶点抢同一个右顶点:最多匹配 1")
        void contention() {
            int[][] edges = {{0, 0}, {1, 0}, {2, 0}};
            for (BipartiteMatching.Mode mode : BipartiteMatching.Mode.values()) {
                BipartiteMatching matching = build(3, 1, mode, edges);
                assertEquals(1, matching.size());
                assertEquals(1, matching.matchedRight(0) >= 0 ? 1 : 0);
                assertValidMatching(matching, edges);
            }
        }

        @Test
        @DisplayName("没有边、孤立顶点:匹配为 0,覆盖为 0,独立集 = 全部顶点")
        void noEdges() {
            for (BipartiteMatching.Mode mode : BipartiteMatching.Mode.values()) {
                BipartiteMatching matching = new BipartiteMatching(3, 4, mode);
                assertEquals(0, matching.size());
                assertTrue(matching.matching().isEmpty());
                assertEquals(0, matching.minVertexCoverSize());
                assertEquals(7, matching.maxIndependentSetSize());
                assertEquals(-1, matching.matchedLeft(0));
                assertFalse(matching.isMatchedLeft(0));
                assertFalse(matching.isMatchedRight(3));
            }
        }

        @Test
        @DisplayName("空图(0 个顶点):按定义空匹配就是完美匹配")
        void empty() {
            BipartiteMatching matching = new BipartiteMatching(0, 0);
            assertEquals(0, matching.size());
            assertTrue(matching.isPerfect(), "空图两侧都是 0 个顶点,空匹配满足完美匹配的定义");
            assertEquals(0, matching.maxIndependentSetSize());
            assertTrue(matching.matching().isEmpty());
        }
    }

    @Nested
    @DisplayName("暴力对拍与两种模式一致")
    class OracleTest {

        @Test
        @DisplayName("40 张随机二分图:与暴力回溯枚举的最大匹配一致")
        void matchesBruteForce() {
            Random rnd = new Random(20261004L);
            for (int trial = 0; trial < 40; trial++) {
                int left = 1 + rnd.nextInt(6);
                int right = 1 + rnd.nextInt(6);
                List<int[]> edges = new ArrayList<int[]>();
                for (int l = 0; l < left; l++) {
                    for (int r = 0; r < right; r++) {
                        if (rnd.nextDouble() < 0.4) {
                            edges.add(new int[]{l, r});
                        }
                    }
                }
                int expected = bruteForceMaxMatching(left, right, edges);
                for (BipartiteMatching.Mode mode : BipartiteMatching.Mode.values()) {
                    BipartiteMatching matching = build(left, right, mode, edges);
                    assertEquals(expected, matching.size(),
                            "第 " + trial + " 张图(" + mode + ")");
                    assertValidMatching(matching, edges);
                    assertKonig(matching, edges);
                }
            }
        }

        @Test
        @DisplayName("两种模式在 30 张随机图上给出相同的匹配规模")
        void modesAgree() {
            Random rnd = new Random(1234L);
            for (int trial = 0; trial < 30; trial++) {
                int left = 1 + rnd.nextInt(8);
                int right = 1 + rnd.nextInt(8);
                List<int[]> edges = new ArrayList<int[]>();
                for (int l = 0; l < left; l++) {
                    for (int r = 0; r < right; r++) {
                        if (rnd.nextDouble() < 0.35) {
                            edges.add(new int[]{l, r});
                        }
                    }
                }
                BipartiteMatching kuhn = build(left, right, BipartiteMatching.Mode.KUHN, edges);
                BipartiteMatching dinic = build(left, right, BipartiteMatching.Mode.DINIC, edges);
                assertEquals(kuhn.size(), dinic.size(), "第 " + trial + " 张图");
                assertEquals(kuhn.minVertexCoverSize(), dinic.minVertexCoverSize(),
                        "两种构造给出的最小点覆盖大小也应相同");
                assertEquals(kuhn.maxIndependentSetSize(), dinic.maxIndependentSetSize());
            }
        }

        @Test
        @DisplayName("Hall 定理:能饱和整个左部 ⟺ 左部每个子集 S 都有 |N(S)| ≥ |S|")
        void hallCondition() {
            Random rnd = new Random(555L);
            for (int trial = 0; trial < 30; trial++) {
                int left = 1 + rnd.nextInt(7);
                int right = 1 + rnd.nextInt(6);
                List<int[]> edges = new ArrayList<int[]>();
                for (int l = 0; l < left; l++) {
                    for (int r = 0; r < right; r++) {
                        if (rnd.nextDouble() < 0.45) {
                            edges.add(new int[]{l, r});
                        }
                    }
                }
                boolean hallHolds = hallConditionHolds(left, right, edges);
                BipartiteMatching matching = build(left, right, BipartiteMatching.Mode.DINIC, edges);
                boolean saturatesLeft = matching.size() == left;
                for (int l = 0; l < left; l++) {
                    saturatesLeft &= matching.isMatchedLeft(l);
                }
                assertEquals(hallHolds, saturatesLeft,
                        "第 " + trial + " 张图:Hall 条件与'左部全饱和'必须一致");
            }
        }

        @Test
        @DisplayName("König 定理:最小点覆盖大小 = 最大匹配,且覆盖每条边")
        void konigTheorem() {
            Random rnd = new Random(8888L);
            for (int trial = 0; trial < 20; trial++) {
                int left = 2 + rnd.nextInt(7);
                int right = 2 + rnd.nextInt(7);
                List<int[]> edges = new ArrayList<int[]>();
                for (int l = 0; l < left; l++) {
                    for (int r = 0; r < right; r++) {
                        if (rnd.nextDouble() < 0.5) {
                            edges.add(new int[]{l, r});
                        }
                    }
                }
                for (BipartiteMatching.Mode mode : BipartiteMatching.Mode.values()) {
                    BipartiteMatching matching = build(left, right, mode, edges);
                    assertKonig(matching, edges);
                    assertTrue(matching.minVertexCoverSize() <= left + right);
                    assertEquals(left + right - matching.size(), matching.maxIndependentSetSize());
                }
            }
        }
    }

    @Nested
    @DisplayName("接口行为、惰性求解与规模")
    class BehaviourTest {

        @Test
        @DisplayName("惰性求解:加边后结果自动更新")
        void lazyResolve() {
            BipartiteMatching matching = new BipartiteMatching(3, 3);
            matching.addEdge(0, 0);
            assertEquals(1, matching.size());
            matching.addEdge(1, 1);
            assertEquals(2, matching.size(), "加边后应重新求解");
            matching.addEdge(2, 2);
            assertEquals(3, matching.size());
            assertTrue(matching.isPerfect());
        }

        @Test
        @DisplayName("重复边被拒绝(否则同一对顶点会被算成两个匹配)")
        void duplicateEdgeRejected() {
            BipartiteMatching matching = new BipartiteMatching(2, 2);
            matching.addEdge(0, 1);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> matching.addEdge(0, 1));
            assertTrue(e.getMessage().contains("已经有边"), e.getMessage());
            assertEquals(1, matching.edgeCount());
        }

        @Test
        @DisplayName("参数校验:负顶点数、null 模式、越界编号、null 集合")
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new BipartiteMatching(-1, 1));
            assertThrows(IllegalArgumentException.class, () -> new BipartiteMatching(1, -1));
            assertThrows(IllegalArgumentException.class,
                    () -> new BipartiteMatching(1, 1, (BipartiteMatching.Mode) null));
            BipartiteMatching matching = new BipartiteMatching(2, 2);
            assertThrows(IllegalArgumentException.class, () -> matching.addEdge(2, 0));
            assertThrows(IllegalArgumentException.class, () -> matching.addEdge(0, 2));
            assertThrows(IllegalArgumentException.class, () -> matching.addEdge(-1, 0));
            assertThrows(IllegalArgumentException.class, () -> matching.matchedLeft(2));
            assertThrows(IllegalArgumentException.class, () -> matching.matchedRight(2));
            assertThrows(IllegalArgumentException.class,
                    () -> matching.isVertexCover(null, new HashSet<Integer>()));
        }

        @Test
        @DisplayName("toString 含规模、匹配与最小点覆盖(可读形式)")
        void toStringContent() {
            String text = build(5, 5, BipartiteMatching.Mode.DINIC,
                    new int[][]{{0, 0}, {1, 1}, {2, 2}, {3, 3}, {4, 4}}).toString();
            assertTrue(text.contains("BipartiteMatching(DINIC)"), text);
            assertTrue(text.contains("最大匹配 5"), text);
            assertTrue(text.contains("0-0"), text);
            assertTrue(text.contains("König"), text);
        }

        @Test
        @DisplayName("规模:2000×2000 的随机二分图,两种模式都能在合理时间内求出")
        void largeInstance() {
            int n = 2000;
            Random rnd = new Random(20261004L);
            List<int[]> edges = new ArrayList<int[]>();
            for (int l = 0; l < n; l++) {
                edges.add(new int[]{l, l});                       // 保证有完美匹配
                for (int k = 0; k < 2; k++) {
                    int r = rnd.nextInt(n);
                    if (r != l) {
                        edges.add(new int[]{l, r});
                    }
                }
            }
            for (BipartiteMatching.Mode mode : BipartiteMatching.Mode.values()) {
                BipartiteMatching matching = build(n, n, mode, edges);
                assertEquals(n, matching.size(), mode + " 应找到那个完美匹配");
                assertTrue(matching.isPerfect());
                assertValidMatching(matching, edges);
                assertEquals(n, matching.minVertexCoverSize(), "König");
            }
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private static BipartiteMatching build(int left, int right, BipartiteMatching.Mode mode,
                                           List<int[]> edges) {
        BipartiteMatching matching = new BipartiteMatching(left, right, mode);
        for (int[] edge : edges) {
            matching.addEdge(edge[0], edge[1]);
        }
        return matching;
    }

    private static BipartiteMatching build(int left, int right, BipartiteMatching.Mode mode,
                                           int[][] edges) {
        return build(left, right, mode, toList(edges));
    }

    /** 独立检查:匹配里的每个顶点最多出现一次,且每条匹配边都真实存在 */
    private static void assertValidMatching(BipartiteMatching matching, int[][] edges) {
        assertValidMatching(matching, toList(edges));
    }

    private static List<int[]> toList(int[][] edges) {
        List<int[]> list = new ArrayList<int[]>();
        for (int[] edge : edges) {
            list.add(edge);
        }
        return list;
    }

    /** 独立检查:匹配里的每个顶点最多出现一次,且每条匹配边都真实存在 */
    private static void assertValidMatching(BipartiteMatching matching, List<int[]> edges) {
        Set<Integer> usedLeft = new HashSet<Integer>();
        Set<Integer> usedRight = new HashSet<Integer>();
        Set<String> edgeSet = new HashSet<String>();
        for (int[] edge : edges) {
            edgeSet.add(edge[0] + "-" + edge[1]);
        }
        for (int[] pair : matching.matching()) {
            assertTrue(usedLeft.add(pair[0]), "左部顶点 " + pair[0] + " 被用了两次");
            assertTrue(usedRight.add(pair[1]), "右部顶点 " + pair[1] + " 被用了两次");
            assertTrue(edgeSet.contains(pair[0] + "-" + pair[1]),
                    "匹配边 " + Arrays.toString(pair) + " 在原图里不存在");
            assertEquals(pair[1], matching.matchedLeft(pair[0]));
            assertEquals(pair[0], matching.matchedRight(pair[1]));
        }
        assertEquals(matching.size(), usedLeft.size(), "匹配大小应等于匹配边数");
        assertEquals(matching.size(), usedRight.size());
    }

    /** 独立检查 König 结论:覆盖大小 = 匹配大小,且覆盖每条边 */
    private static void assertKonig(BipartiteMatching matching, List<int[]> edges) {
        Set<Integer> leftCover = new HashSet<Integer>(matching.minVertexCoverLeft());
        Set<Integer> rightCover = new HashSet<Integer>(matching.minVertexCoverRight());
        assertEquals(matching.size(), matching.minVertexCoverSize(),
                "König 定理:最小点覆盖大小应等于最大匹配大小");
        assertTrue(matching.isVertexCover(leftCover, rightCover), "给出的点覆盖必须覆盖每条边");
        for (int[] edge : edges) {
            assertTrue(leftCover.contains(edge[0]) || rightCover.contains(edge[1]),
                    "边 " + edge[0] + "-" + edge[1] + " 没有被覆盖");
        }
    }

    /** 暴力回溯枚举最大匹配(只适合小图) */
    private static int bruteForceMaxMatching(int left, int right, List<int[]> edges) {
        List<List<Integer>> adjacency = new ArrayList<List<Integer>>();
        for (int l = 0; l < left; l++) {
            adjacency.add(new ArrayList<Integer>());
        }
        for (int[] edge : edges) {
            adjacency.get(edge[0]).add(edge[1]);
        }
        return search(adjacency, 0, new boolean[right], 0);
    }

    private static int search(List<List<Integer>> adjacency, int leftIndex,
                              boolean[] usedRight, int matched) {
        if (leftIndex == adjacency.size()) {
            return matched;
        }
        int best = search(adjacency, leftIndex + 1, usedRight, matched);   // 这个左顶点不匹配
        for (int right : adjacency.get(leftIndex)) {
            if (!usedRight[right]) {
                usedRight[right] = true;
                best = Math.max(best, search(adjacency, leftIndex + 1, usedRight, matched + 1));
                usedRight[right] = false;
            }
        }
        return best;
    }

    /** Hall 条件:左部每个非空子集 S 的邻居集合大小 ≥ |S| */
    private static boolean hallConditionHolds(int left, int right, List<int[]> edges) {
        for (int mask = 1; mask < (1 << left); mask++) {
            Set<Integer> neighbors = new HashSet<Integer>();
            int size = 0;
            for (int l = 0; l < left; l++) {
                if ((mask & (1 << l)) == 0) {
                    continue;
                }
                size++;
                for (int[] edge : edges) {
                    if (edge[0] == l) {
                        neighbors.add(edge[1]);
                    }
                }
            }
            if (neighbors.size() < size) {
                return false;
            }
        }
        return true;
    }
}
