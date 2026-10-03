package io.github.softwindwf.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UF} 并查集测试。
 *
 * <p>验证重点:</p>
 * <ol>
 *   <li>基本语义:初始每人一集合、合并后连通且集合数减一、重复合并返回 false;</li>
 *   <li>传递性:合并链上任意两点连通;</li>
 *   <li><b>与被测实现无关的参照物</b>:用朴素"重标记"分区模型跑 2000 次随机合并 + 抽查,逐操作比对;</li>
 *   <li><b>两个优化真的生效</b>:按大小合并保证树高 ≤ log2 n;调用 find 之后路径被压平(深度 ≤ 1);</li>
 *   <li>参数校验与规模(20 万元素)。</li>
 * </ol>
 */
@DisplayName("UF 并查集测试")
class UFTest {

    @Nested
    @DisplayName("构造与初始状态")
    class ConstructionTest {

        @Test
        @DisplayName("初始时每个元素自成一个集合")
        void initialState() {
            UF uf = new UF(5);
            assertEquals(5, uf.size());
            assertEquals(5, uf.count());
            for (int i = 0; i < 5; i++) {
                assertEquals(i, uf.find(i));
                assertTrue(uf.connected(i, i), "每个元素与自己连通");
                assertEquals(1, uf.componentSize(i));
                assertEquals(0, uf.depth(i));
                for (int j = 0; j < 5; j++) {
                    if (i != j) {
                        assertFalse(uf.connected(i, j));
                    }
                }
            }
        }

        @Test
        @DisplayName("0 个元素是合法输入;负数被拒绝")
        void degenerateSizes() {
            UF empty = new UF(0);
            assertEquals(0, empty.size());
            assertEquals(0, empty.count());
            assertThrows(IllegalArgumentException.class, () -> new UF(-1));
        }

        @Test
        @DisplayName("toString 含元素数与集合数")
        void toStringContent() {
            UF uf = new UF(10);
            uf.union(0, 1);
            String s = uf.toString();
            assertTrue(s.contains("n=10"), s);
            assertTrue(s.contains("集合数=9"), s);
        }
    }

    @Nested
    @DisplayName("合并与查询")
    class UnionTest {

        @Test
        @DisplayName("合并成功返回 true 且集合数减一;重复合并返回 false 且集合数不变")
        void mergeSemantics() {
            UF uf = new UF(4);
            assertTrue(uf.union(0, 1));
            assertEquals(3, uf.count());
            assertTrue(uf.connected(0, 1));
            assertFalse(uf.union(0, 1), "已在同一集合,不会真的合并");
            assertFalse(uf.union(1, 0), "方向无关");
            assertEquals(3, uf.count());
            assertEquals(2, uf.componentSize(0));
            assertEquals(2, uf.componentSize(1));
        }

        @Test
        @DisplayName("传递性:0-1、1-2 合并后 0 与 2 连通")
        void transitivity() {
            UF uf = new UF(5);
            uf.union(0, 1);
            uf.union(1, 2);
            assertTrue(uf.connected(0, 2));
            assertTrue(uf.connected(2, 0));
            assertEquals(3, uf.componentSize(2));
            assertEquals(3, uf.count(), "只剩下 {0,1,2}、{3}、{4} 三个集合");
        }

        @Test
        @DisplayName("大集合吃掉小集合:集合大小按并集累加")
        void componentSizeGrows() {
            UF uf = new UF(10);
            uf.union(0, 1);
            uf.union(2, 3);
            uf.union(0, 2);
            assertEquals(4, uf.componentSize(3));
            uf.union(4, 5);
            uf.union(6, 7);
            uf.union(4, 6);
            uf.union(0, 4);
            assertEquals(8, uf.componentSize(7));
            assertEquals(3, uf.count());
        }

        @Test
        @DisplayName("参数越界抛 IllegalArgumentException")
        void validation() {
            UF uf = new UF(3);
            assertThrows(IllegalArgumentException.class, () -> uf.find(-1));
            assertThrows(IllegalArgumentException.class, () -> uf.find(3));
            assertThrows(IllegalArgumentException.class, () -> uf.connected(0, 3));
            assertThrows(IllegalArgumentException.class, () -> uf.union(-1, 0));
            assertThrows(IllegalArgumentException.class, () -> uf.componentSize(3));
            assertThrows(IllegalArgumentException.class, () -> uf.depth(-1));
        }
    }

    @Nested
    @DisplayName("随机差分:对照朴素重标记分区模型")
    class DifferentialTest {

        @Test
        @DisplayName("2000 次随机合并逐次比对:返回值、集合数、抽查连通性")
        void randomMergesAgainstNaiveModel() {
            final int n = 40;
            UF uf = new UF(n);
            int[] label = new int[n];
            for (int i = 0; i < n; i++) {
                label[i] = i;
            }
            Random rnd = new Random(20261003L);

            for (int step = 0; step < 2000; step++) {
                int p = rnd.nextInt(n);
                int q = rnd.nextInt(n);
                boolean expectedMerged = label[p] != label[q];
                boolean merged = uf.union(p, q);
                if (expectedMerged) {
                    int from = label[q];
                    int to = label[p];
                    for (int i = 0; i < n; i++) {
                        if (label[i] == from) {
                            label[i] = to;
                        }
                    }
                }
                assertEquals(expectedMerged, merged, "第 " + step + " 步 union(" + p + "," + q + ") 返回值不符");
                assertEquals(distinctLabels(label), uf.count(), "第 " + step + " 步集合数不符");
                assertEquals(countOf(label, label[p]), uf.componentSize(p), "第 " + step + " 步集合大小不符");

                int x = rnd.nextInt(n);
                int y = rnd.nextInt(n);
                assertEquals(label[x] == label[y], uf.connected(x, y),
                        "第 " + step + " 步 connected(" + x + "," + y + ") 不符");
            }
            assertEquals(distinctLabels(label), uf.count());
        }

        @Test
        @DisplayName("随机合并后 find 的结果与参照物标签一一对应")
        void findsMatchModel() {
            final int n = 60;
            UF uf = new UF(n);
            int[] label = new int[n];
            for (int i = 0; i < n; i++) {
                label[i] = i;
            }
            Random rnd = new Random(5L);
            for (int step = 0; step < 300; step++) {
                int p = rnd.nextInt(n);
                int q = rnd.nextInt(n);
                if (label[p] != label[q]) {
                    int from = label[q];
                    int to = label[p];
                    for (int i = 0; i < n; i++) {
                        if (label[i] == from) {
                            label[i] = to;
                        }
                    }
                }
                uf.union(p, q);
            }
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    assertEquals(label[i] == label[j], uf.find(i) == uf.find(j),
                            "顶点 " + i + " 与 " + j + " 的根关系不符");
                }
            }
        }

        private int distinctLabels(int[] label) {
            Set<Integer> set = new HashSet<Integer>();
            for (int value : label) {
                set.add(value);
            }
            return set.size();
        }

        private int countOf(int[] label, int value) {
            int count = 0;
            for (int x : label) {
                if (x == value) {
                    count++;
                }
            }
            return count;
        }
    }

    @Nested
    @DisplayName("两个优化的实际效果")
    class OptimizationTest {

        @Test
        @DisplayName("按大小合并:链式合并 1000 次后树高仍 ≤ log2 n")
        void unionBySizeKeepsTreeShallow() {
            final int n = 1000;
            UF uf = new UF(n);
            for (int i = 0; i + 1 < n; i++) {
                uf.union(i, i + 1);          // 若不做按大小合并,这会退化成一条 999 层的链
            }
            int maxDepth = 0;
            for (int i = 0; i < n; i++) {
                maxDepth = Math.max(maxDepth, uf.depth(i));
            }
            assertTrue(maxDepth <= 10,
                    "按大小合并后树高应不超过 log2(1000) ≈ 10,实际 " + maxDepth);
            assertEquals(1, uf.count());
            assertEquals(n, uf.componentSize(0));
        }

        @Test
        @DisplayName("路径压缩:对所有元素 find 一遍后,每个结点都直连根(深度 ≤ 1)")
        void pathCompressionFlattens() {
            final int n = 500;
            UF uf = new UF(n);
            for (int i = 0; i + 1 < n; i++) {
                uf.union(i, i + 1);
            }
            for (int i = 0; i < n; i++) {
                uf.find(i);
            }
            for (int i = 0; i < n; i++) {
                assertTrue(uf.depth(i) <= 1, "路径压缩后深度应 ≤ 1,顶点 " + i + " 实际 " + uf.depth(i));
            }
        }

        @Test
        @DisplayName("20 万元素的链式合并与查询能正常完成")
        void largeScale() {
            final int n = 200_000;
            UF uf = new UF(n);
            for (int i = 0; i + 1 < n; i++) {
                uf.union(i, i + 1);
            }
            assertEquals(1, uf.count());
            assertEquals(n, uf.componentSize(0));
            assertTrue(uf.connected(0, n - 1));
            int maxDepth = 0;
            for (int i = 0; i < n; i += 1_000) {
                maxDepth = Math.max(maxDepth, uf.depth(i));
            }
            assertTrue(maxDepth <= 20, "20 万元素下树高应远小于 n,实际 " + maxDepth);
        }
    }
}
