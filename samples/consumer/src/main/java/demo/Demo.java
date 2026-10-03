package demo;

import io.github.softwindwf.graph.AcyclicSP;
import io.github.softwindwf.graph.BipartiteMatching;
import io.github.softwindwf.graph.DijkstraSP;
import io.github.softwindwf.graph.Dinic;
import io.github.softwindwf.graph.EdgeWeightedDigraph;
import io.github.softwindwf.graph.FlowNetwork;
import io.github.softwindwf.graph.GlobalMincut;
import io.github.softwindwf.graph.GraphGenerator;
import io.github.softwindwf.graph.KruskalMST;
import io.github.softwindwf.graph.SymbolGraph;
import io.github.softwindwf.graph.UndirectedGraph;

import java.util.Arrays;
import java.util.Random;

/**
 * 消费者示例:把 graph-lib 当作普通第三方依赖使用,覆盖几类典型场景。
 *
 * <p>运行:{@code mvn -o compile exec:java -Dexec.mainClass=demo.Demo}</p>
 */
public final class Demo {

    private Demo() {
    }

    public static void main(String[] args) {
        System.out.println("== 1. 无向图与最小生成树(自造一个 12 顶点 30 边的图)==");
        Random rnd = new Random(20261004L);
        io.github.softwindwf.graph.EdgeWeightedGraph weighted =
                GraphGenerator.edgeWeightedPositive(rnd, 12, 30);
        KruskalMST mst = new KruskalMST(weighted);
        System.out.println("  V=" + weighted.V() + ", E=" + weighted.E()
                + " → 最小生成树 " + mst.edges().size() + " 条边,总权值 "
                + String.format("%.2f", mst.weight()));

        System.out.println("== 2. 有向图最短路(含负权的 DAG)===");
        EdgeWeightedDigraph dag = new EdgeWeightedDigraph(5);
        dag.addEdge(0, 1, 1.0);
        dag.addEdge(0, 2, 4.0);
        dag.addEdge(1, 2, -2.0);
        dag.addEdge(2, 3, 2.0);
        dag.addEdge(1, 3, 5.0);
        AcyclicSP shortest = new AcyclicSP(dag, 0);
        System.out.println("  0→3 最短距离 " + shortest.distTo(3)
                + ",路径 " + shortest.pathVertices(3));

        System.out.println("== 3. 最大流与最小割 ==");
        FlowNetwork flow = new FlowNetwork(4);
        flow.addEdge(0, 1, 3.0);
        flow.addEdge(0, 2, 2.0);
        flow.addEdge(1, 3, 2.0);
        flow.addEdge(2, 3, 3.0);
        Dinic maxFlow = new Dinic(flow, 0, 3);
        System.out.println("  最大流 " + maxFlow.value() + ",最小割 S=" + maxFlow.minCut().sourceSide()
                + " → " + maxFlow.minCut().capacity());

        System.out.println("== 4. 全局最小割(无需指定源汇)==");
        UndirectedGraph star = new UndirectedGraph(4);
        star.addEdge(0, 1);
        star.addEdge(0, 2);
        star.addEdge(0, 3);
        io.github.softwindwf.graph.EdgeWeightedGraph weightedStar =
                new io.github.softwindwf.graph.EdgeWeightedGraph(4);
        weightedStar.addEdge(new io.github.softwindwf.graph.Edge(0, 1, 1.0));
        weightedStar.addEdge(new io.github.softwindwf.graph.Edge(0, 2, 2.0));
        weightedStar.addEdge(new io.github.softwindwf.graph.Edge(0, 3, 3.0));
        System.out.println("  全局最小割 = " + new GlobalMincut(weightedStar).weight()
                + "(把顶点 0 与其余分开)");

        System.out.println("== 5. 二分图最大匹配 ==");
        BipartiteMatching matching = new BipartiteMatching(3, 3);
        matching.addEdge(0, 0);
        matching.addEdge(0, 1);
        matching.addEdge(1, 1);
        matching.addEdge(2, 2);
        System.out.println("  最大匹配 " + matching.size() + ",配对 "
                + Arrays.deepToString(matching.matching().toArray())
                + ",最小点覆盖大小 " + matching.minVertexCoverSize());

        System.out.println("== 6. 符号图:名字当顶点 ==");
        SymbolGraph routes = new SymbolGraph(new String[]{"JFK", "ORD", "LAX"});
        routes.addEdge("JFK", "ORD");
        routes.addEdge("ORD", "LAX");
        System.out.println("  顶点 " + routes.vertices() + ",JFK 的邻居 " + routes.adjacent("JFK"));

        System.out.println("== 7. 运行时依赖检查 ==");
        System.out.println("  graph-lib jar 位置: "
                + DijkstraSP.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("  (该 jar 的依赖列表里没有第三方库,只要有 JDK 8 就能跑)");    }
}
