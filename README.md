# graph-lib

一个**零运行时依赖**的 Java 图算法库(JDK 8 起可用):从图的表示、遍历、最小生成树、最短路,
一路做到有向图与强连通分量、网络流与匹配、全局最小割、指派问题、AOV/AOE 与关键路径,
外加符号图(名字当顶点)和统一的文本 / Graphviz 读写。

- **46 个主类**,**669 个测试**:默认构建跑其中 **494 个、零第三方依赖**;
  另有 175 个与 Princeton《Algorithms, 4th Edition》官方实现的**差分验证**,用 `-Pdifferential` 打开
- 运行时**不依赖任何第三方库**:只要 JDK 8+(库本身按 1.8 编译)
- 每个类都带中文 Javadoc、`main` 演示、以及"为什么这么做"的设计说明

---

## 1. 你能用它做什么

| 主题 | 类 |
|---|---|
| 图的表示 | `UndirectedGraph` `Digraph` `EdgeWeightedGraph` `EdgeWeightedDigraph` `AdjMatrixEdgeWeightedDigraph` `Edge` `DirectedEdge` |
| 遍历与路径 | `DepthFirstTraversal` `BreadthFirstTraversal` `DirectedPaths` `ConnectedComponents` `UndirectedCycle` `DirectedCycle` `Bipartite` |
| 最小生成树 | `PrimMST`(懒/稠密两模式)`KruskalMST` `BoruvkaMST` `UF` |
| 最短路 | `DijkstraSP` `DijkstraUndirectedSP` `AcyclicSP` `AcyclicLP` `BellmanFordSP`(SPFA/全弧扫描)`FloydWarshall` `Johnson` |
| 拓扑与工程 | `TopologicalSort`(Kahn/Kahn 字典序/DFS)`AOVNetwork` `AOENetwork` `CriticalPath` |
| 连通性 | `StronglyConnectedComponents`(Kosaraju/Tarjan)`TransitiveClosure` |
| 欧拉路 | `EulerianPath`(无向)`DirectedEulerianPath`(有向) |
| 网络流与匹配 | `FlowEdge` `FlowNetwork` `FordFulkerson` `EdmondsKarp` `Dinic` `MinCut` `GlobalMincut` `BipartiteMatching` `AssignmentProblem` |
| 名字/数据进出 | `SymbolGraph` `GraphIO`(文本读写 + Graphviz DOT) |
| 造测试数据 | `GraphGenerator` `DigraphGenerator` |

典型用途:依赖解析与构建顺序(AOV + 拓扑排序)、项目排期(AOE + 关键路径)、
路径规划(最短路)、网络容量与瓶颈(最大流/最小割、全局最小割)、
任务分配(二分图匹配 / 指派问题)、代码与包的循环依赖(强连通分量)。

---

## 2. 三种使用方式

### 方式 A:直接拿 jar(最省事)

```
graph-lib/dist/graph-lib-1.0.0.jar          ← 运行时只需要这一个
graph-lib/dist/graph-lib-1.0.0-sources.jar  ← 源码(IDE 里看实现)
graph-lib/dist/graph-lib-1.0.0-javadoc.jar  ← API 文档
```

把它们丢进项目的 `lib/` 目录、或加入 IDE 的依赖即可(纯 jar,无传递依赖)。

### 方式 B:装进本地仓库后用坐标依赖(推荐)

```bash
cd graph-lib
mvn install            # 装进 ~/.m2(或你配置的本地仓库)
```

然后在你的项目里:

```xml
<dependency>
  <groupId>io.github.softwind-wf</groupId>
  <artifactId>graph-lib</artifactId>
  <version>1.0.0</version>
</dependency>
```

Java 里导入的包名是 `io.github.softwindwf.graph`(去掉连字符,因为 Java 包名不允许 `-`)。

### 方式 C:不需要安装,直接把库内的 `repo/` 当文件仓库

`graph-lib/repo/` 是一个标准的 Maven 仓库目录(含本库产物与测试用的 algs4 参照物):

```xml
<repositories>
  <repository>
    <id>graph-lib-local</id>
    <url>file:///绝对路径/graph-lib/repo</url>
  </repository>
</repositories>
```

`graph-lib/samples/consumer/` 就是一个可直接运行的消费者示例:

```bash
cd graph-lib/samples/consumer
mvn compile exec:java -Dexec.mainClass=demo.Demo
```

---

## 3. 最小示例

```java
// 0) 导入(包名去掉了 GitHub 用户名里的连字符)
import io.github.softwindwf.graph.*;

// 1) 建图:自己加边,或从文本/文件读进来
EdgeWeightedDigraph g = new EdgeWeightedDigraph(4);
g.addEdge(0, 1, 1.0);
g.addEdge(1, 2, 2.5);
g.addEdge(0, 2, 5.0);
g.addEdge(2, 3, 1.0);

// 2) 单源最短路(点编号即身份)
DijkstraSP sp = new DijkstraSP(g, 0);
sp.distTo(3);          // 4.5
sp.pathVertices(3);    // [0, 1, 2, 3]
sp.pathTo(3);          // [0->1 1.00, 1->2 2.50, 2->3 1.00]

// 3) 从文件读(所有文本 IO 集中在 GraphIO)
EdgeWeightedGraph tiny = GraphIO.readWeightedFile("tinyEWG.txt");
KruskalMST mst = new KruskalMST(tiny);
mst.weight();          // 1.81

// 4) 出图:Graphviz DOT,一行命令变 SVG
GraphIO.toDot(tiny);   // dot -Tsvg tiny.dot -o tiny.svg

// 5) 不指定源汇的全局最小割
new GlobalMincut(tiny).weight();
```

---

## 4. 设计约定(与"能长期维护"有关)

1. **点用整数编号,编号即身份**:不引入 `Vertex` 对象(实测内存与常数开销不划算);
   需要名字的场合用 `SymbolGraph` / `AOVNetwork` / `AOENetwork`,它们把"名字 ↔ 编号"这一层单独做在两个类里。
2. **数据结构不跑算法**:`UndirectedGraph` 只有结构;遍历、最短路、MST 各自独立成类,
   所以同一张图可以喂给任意算法,算法之间也能互相印证。
3. **所有文本进出都在 `GraphIO`**:数据结构的 `toString()` 只是给人看的调试视图,不保证可回读;
   无损格式一律由 `GraphIO.format/parse/read*` 负责,并在文档里写明是否无损(比如符号图的边表格式无法表达孤立顶点)。
4. **同一个算法给两种以上实现时用 `Mode` 枚举**:如 `PrimMST.LAZY/DENSE`、`BellmanFordSP.QUEUE/SWEEP`、
   `FloydWarshall.IN_PLACE/COPY`、`AssignmentProblem.JV/MCMF`。既能对照,也方便按场景选。
5. **不使用递归**:DFS/BFS/拓扑排序/强连通分量/Dinic 的路径搜索全部用显式栈,
   10 万~20 万顶点的深图不会栈溢出(测试里有对应的规模用例)。
6. **错误要么明确报错,要么在文档里说清语义**:例如存在可达负环时 `BellmanFordSP.distTo` 抛
   `IllegalStateException` 而不是返回错数;不可达距离用 `+∞`(`Dijkstra*`/`FloydWarshall`/`Johnson`)或 `-1`(`BFS`)。

---

## 5. 测试与验证

```bash
cd graph-lib
mvn test                 # 默认:494 个用例,零第三方依赖(全新克隆即可跑,JitPack 也走这条)
mvn -Pdifferential test  # 全量:669 个用例,含 175 个与 algs4 的差分验证(需要参照 jar,见下)
mvn -Prelease package    # 额外产出 -sources.jar / -javadoc.jar(需要联网解析两个插件)
```

这个库的测试不是"跑通就行",而是**用独立参照物交叉验证**:

| 手法 | 用在哪 |
|---|---|
| 暴力枚举(所有二划分 / 所有排列 / 所有源汇对 / 所有 s-t 路径) | 最小生成树、最短路、最大流、全局最小割、指派问题 |
| 另一个算法对拍(同一结论的两条技术路线) | Prim/Kruskal/Borůvka、Dijkstra/Bellman-Ford/Floyd-Warshall/Johnson、FF/EK/Dinic、Kuhn/Dinic 匹配、DFS/Warshall 传递闭包 |
| 结构判据 | 无向图有环 ⟺ `E > V − 分量数`;二分图 ⟺ 无奇环;欧拉路 ⟺ 奇度顶点数 ∈ {0,2}(有向看入出度差) |
| 最优性证书 | 最大流 = 最小割容量;势函数对偶可行 + 互补松弛 + 强对偶(指派问题);交换图无负环 |
| 与官方参考实现差分 | 17 个测试文件对拍 Princeton algs4 的同类实现(175 个用例) |
| 规模用例 | 10 万~20 万顶点(栈溢出、Θ(V²) 扫描的取舍) |

**为什么要把测试分成两档?** 因为 algs4 **不在 Maven 中央仓库**,也不随本仓库分发
(它是 GPLv3 构件)。所以默认构建用 `testExcludes` 把这 17 个文件排除在编译之外 ——
于是全新克隆、CI、JitPack 都能在**零外部依赖**下跑通 494 个用例;想要完整的 669 个时,
用 `-Pdifferential` 把依赖与这些文件一起加回来。

17 个文件(175 个用例)的清单就在 `pom.xml` 的 `<testExcludes>` 里,
它们之间的引用闭包已核对,不会出现"排除了 A、却留下引用 A 的 B"这类断裂。

> 想跑差分测试:把 algs4 的 jar 放到 `repo/edu/princeton/cs/algs4/1.0.0.0/`
> (该目录已在 `.gitignore` 中,不会进公开仓库),或装进你自己的本地仓库,然后 `mvn -Pdifferential test`。

测试数据(`tinyG.txt`、`tinyEWG.txt`、`tinyEWD*.txt`、`tinyDG.txt`、`tinyFN.txt`、`routes.txt`、
`coursesAOV.txt`、`projectAOE.txt`)放在模块根目录,测试以相对文件名读取。

---

## 6. 发布到 GitHub(本项目采用的方式)

**结论:只走 GitHub 就够了**,不必发布到 Maven 中央仓库(那要 GPG 签名 + Sonatype 账号,对个人项目
属于额外负担)。GitHub 路线已经能满足两种需求:

- **你以后自己的项目用**:clone 后 `mvn install`,然后按坐标依赖(第 2 节方式 B);
- **别人用**:clone 自己构建、用 JitPack 一行依赖(打个 tag 即触发,零成本)、或从 GitHub Releases
  直接下载 jar。

### 6.1 推代码与打 tag(三步)

```bash
cd graph-lib

# 1) 首次:建好远程仓库后关联并推送
#    网页 https://github.com/new → Owner: softwind-wf,名字: graph-lib,不要勾任何初始化文件
git remote add origin https://github.com/softwind-wf/graph-lib.git   # 已配置过可跳过
git push -u origin main

# 2) 打版本 tag 并推送(推 tag 会让 JitPack 能构建,也方便做 Release)
git tag -a v1.0.0 -m "graph-lib 1.0.0"
git push origin v1.0.0

# 也可以一键(建仓库 + 推送 + 失败诊断)
pwsh scripts/push-to-github.ps1          # 想用 SSH 就加 -Ssh
```

### 6.2 以后发新版本

```bash
# 1. 改 pom.xml 的 <version>(例如 1.1.0),同步 CHANGELOG.md
git commit -am "release 1.1.0" && git push origin main
# 2. 打新 tag 并推送(JitPack 会为每个 tag 单独构建)
git tag -a v1.1.0 -m "graph-lib 1.1.0" && git push origin v1.1.0
```

### 6.3 可选:在 GitHub Releases 挂 jar(给不用 Maven 的人)

网页 → **Releases → Draft a new release** → 选 tag `v1.0.0` → 把 `dist/` 下三个文件拖进去:

```
graph-lib-1.0.0.jar           # 运行时只需要这个
graph-lib-1.0.0-sources.jar   # IDE 里看源码
graph-lib-1.0.0-javadoc.jar   # API 文档
```

这样不会用 Maven 的人也能直接下载使用。

### 6.4 `Repository not found` 的排查

| 原因 | 判断 | 处理 |
|---|---|---|
| 仓库还没建(最常见) | `https://github.com/softwind-wf?tab=repositories` 里找不到 graph-lib | 先在网页建仓库,或用 `scripts/push-to-github.ps1` |
| 私有仓库 + 凭据无权 | 仓库确实存在,但你用的是别的账号或过期令牌 | `cmdkey /delete:git:https://github.com` 清凭据后重试 |
| 账号或仓库名拼错 | `git remote -v` 与实际不符 | `git remote set-url origin <正确地址>` |

> 这**不是网络问题**:能收到 `remote:` 开头的回复,就说明已经连上 GitHub 了。

---

## 7. 别人怎么用你的库?

**先分清两件事**:

- **仓库公开**:别人能 clone、能读源码、能下载 `dist/` 里的 jar,但**不能**写一行 `<dependency>`
  就自动拉取 —— Maven 不认识 GitHub 仓库里的源码;
- **能被当依赖**:必须把产物放到某个 Maven 仓库,或让对方手动安装/下载。

| 档位 | 别人写的坐标 | 别人还要做的配置 | 你的成本 | 现状 |
|---|---|---|---|---|
| A. clone 后自建 | 无 | clone + `mvn install` | 0 | ✅ 已可用 |
| B. **JitPack**(想让别人一行依赖用上) | `com.github.softwind-wf:graph-lib:v1.0.0` | 加一行 jitpack 仓库 | 打个 tag | ⏳ 推 tag 即生效 |
| C. GitHub Releases 下载 jar | 无 | 手动下载 | 拖三个文件 | ⏳ 可选 |
| D. Maven Central | `io.github.softwind-wf:graph-lib:1.0.0` | 零配置 | GPG + Sonatype 账号 | 未做(也不需要,见附录) |

### A. 你自己的下一个项目(最常用)

```bash
cd path/to/graph-lib
mvn install          # 装进本地仓库
```

```xml
<dependency>
  <groupId>io.github.softwind-wf</groupId>
  <artifactId>graph-lib</artifactId>
  <version>1.0.0</version>
</dependency>
```

不需要任何 `<repositories>` 配置,整个过程都在本机,对个人复用来讲完全够。

### B. JitPack —— 让别人"一行依赖"用上

推了 tag 之后(见 6.1),别人项目里加一次仓库 + 一条依赖:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.softwind-wf</groupId>
  <artifactId>graph-lib</artifactId>
  <version>v1.0.0</version>
</dependency>
```

Gradle:

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { implementation 'com.github.softwind-wf:graph-lib:v1.0.0' }
```

要点:

- 首次构建要排队几分钟;**准确坐标**与构建状态看 <https://jitpack.io/#softwind-wf/graph-lib/v1.0.0>
  (页面 "Get it" 给的坐标就是权威值,复制它最稳);
- 仓库里已放 `jitpack.yml`,命令是 `mvn -B install`(默认档:494 个用例、零第三方依赖,所以能构建成功);
- 国内访问 `jitpack.io` 偶尔慢,重试即可;
- 以后每发一个版本就推一个新 tag,JitPack 会各自构建。

### 怎么确认"别人真的能用"?

1. 建一个**空项目**,只写坐标 + 几行调用代码(**不要引用你的本地源码**);
2. 用一个**空目录**当本地仓库构建,排除你机器上的缓存干扰:

   ```bash
   mvn -Dmaven.repo.local=./tmp-repo test
   ```

3. JitPack 看构建页是否 `Build successful`。

### 附录:以后若真要发 Maven Central

本库的 POM 元数据(name / description / url / licenses / developers / scm)已按 Central 要求填好,
许可证是 MIT;`-Prelease package` 也能产出 `-sources.jar` 与 `-javadoc.jar`。真要做的时候,
只差三件外部事项(所以现在不必做):

1. 在 <https://central.sonatype.com> 用 GitHub 账号验证命名空间 `io.github.softwind-wf`;
2. `gpg --gen-key` 生成密钥,把公钥发到 keyserver;
3. 在 pom 的 `release` profile 里加回 `maven-gpg-plugin`(为减负已移除),然后:

   ```bash
   mvn -Prelease clean deploy \
     "-DaltDeploymentRepository=ossrh::default::https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
   ```

   再到 Central Portal 点 Publish。官方文档:<https://central.sonatype.org/publish/>

### 许可证与来源(读一次)

- 本库代码为独立编写,采用 **MIT**(`LICENSE`);
- 算法与术语参考了《Algorithms, 4th Edition》(Princeton,官方实现为 GPLv3)与中文教材
  《精讲数据结构(Java 语言实现)》;**若你日后从 algs4 复制过任何代码/注释,整份作品需改为 GPLv3**;
- 测试用的 algs4 参照 jar 不随仓库分发(`repo/edu/` 已在 `.gitignore` 中),详见 [NOTICE.md](NOTICE.md);
- 本库是**从主工程 `graph` 包抽取出来的独立副本**,主工程内仍保留
  `cn.exercise.algs4.datastructure.graph` 那一份(整个仓库按 `cn.exercise.algs4.datastructure.*` 组织,
  不宜改名);两边同步时,把主工程的类复制过来、再把包名替换成 `io.github.softwindwf.graph` 即可。

## 8. 目录结构

```
graph-lib/
├── pom.xml                     # 库的 Maven 工程(坐标 io.github.softwind-wf:graph-lib:1.0.0)
├── README.md                   # 本文
├── LICENSE                     # MIT(纯 MIT 正文,GitHub 才能识别出许可证)
├── NOTICE.md                   # 第三方来源与 algs4/GPL 说明
├── CHANGELOG.md                # 版本记录
├── jitpack.yml                 # 让 JitPack 能构建本库
├── scripts/push-to-github.ps1  # 建仓库 + 推送 + 失败诊断
├── src/main/java/io/github/softwindwf/graph/      # 46 个主类
├── src/test/java/io/github/softwindwf/graph/      # 47 个测试文件:默认 494 例,-Pdifferential 669 例
├── tiny*.txt routes.txt ...    # 测试数据
├── repo/                       # 库内 file:// 仓库:本库产物 + algs4 参照物
├── dist/                       # 现成可用的 jar(主/源码/Javadoc)
└── samples/consumer/           # 消费者示例(可直接运行)
```

## 9. 环境

- **JDK 8+**(源码/字节码目标 1.8;更高版本 JDK 也可运行)
- 构建:**Maven 3.6+**;运行时:**无需任何依赖**
