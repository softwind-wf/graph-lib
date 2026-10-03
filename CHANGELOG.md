# 更新记录

遵循[语义化版本](https://semver.org/lang/zh-CN/)。

## 1.0.0 — 2026-10-04

首个版本。从 `algs4-master` 工程的 `graph` 包抽取为独立库,改名为 `graph-lib`,
坐标 `io.github.softwind-wf:graph-lib`,包名 `io.github.softwindwf.graph`。

**内容**

- **图的表示**(7):`UndirectedGraph` `Digraph` `EdgeWeightedGraph` `EdgeWeightedDigraph`
  `AdjMatrixEdgeWeightedDigraph` `Edge` `DirectedEdge`
- **遍历与路径**(7):`DepthFirstTraversal` `BreadthFirstTraversal` `DirectedPaths`
  `ConnectedComponents` `UndirectedCycle` `DirectedCycle` `Bipartite`
- **最小生成树**(4):`PrimMST` `KruskalMST` `BoruvkaMST` `UF`
- **最短路**(7):`DijkstraSP` `DijkstraUndirectedSP` `AcyclicSP` `AcyclicLP`
  `BellmanFordSP` `FloydWarshall` `Johnson`
- **拓扑与工程**(4):`TopologicalSort` `AOVNetwork` `AOENetwork` `CriticalPath`
- **连通性**(2):`StronglyConnectedComponents` `TransitiveClosure`
- **欧拉路**(2):`EulerianPath` `DirectedEulerianPath`
- **网络流与匹配**(9):`FlowEdge` `FlowNetwork` `FordFulkerson` `EdmondsKarp` `Dinic`
  `MinCut` `GlobalMincut` `BipartiteMatching` `AssignmentProblem`
- **名字与数据进出**(4):`SymbolGraph` `GraphIO` `GraphGenerator` `DigraphGenerator`

**质量**

- 669 个测试全部通过;`javac -Xlint:all` 无警告、`javadoc -Xdoclint:all` 无错误。
- 验证以"独立参照物"为主:暴力枚举(所有二划分 / 所有排列 / 所有源汇对 / 所有 s-t 路径)、
  同一结论的两种实现互拍、结构判据(环 ⟺ `E > V − 分量数`、二分图 ⟺ 无奇环、欧拉路 ⟺ 奇度顶点数)、
  最优性证书(最大流 = 最小割、对偶可行 + 互补松弛 + 强对偶、交换图无负环)。
- 16 个测试类与 Princeton algs4 官方实现做差分验证(仅测试作用域,不随库发布)。
- 规模用例覆盖 10 万~20 万顶点,验证迭代实现不栈溢出。

**已知限制**

- `GlobalMincut` 用 Θ(V²) 权重矩阵实现,顶点数上限 2048。
- `TransitiveClosure` 结果是 V² 个布尔值,顶点数上限 16384。
- `AssignmentProblem` 只处理 n×n 方阵;最大化收益请把代价取负。
