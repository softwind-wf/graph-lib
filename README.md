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

## 6. 发布到网上:可以,先看清单

技术上完全可以发布。可选渠道与前置条件:

| 渠道 | 适合 | 前置条件 |
|---|---|---|
| **Maven Central**(Sonatype Central Portal) | 想让任何人 `<dependency>` 就能用 | 见下方"中央仓库 5 项硬要求";免费,但要一次性配置 |
| **GitHub Packages** | 自己的项目/团队用 | GitHub 账号 + `settings.xml` 里配 token;别人用要配仓库地址与凭证 |
| **JitPack** | 已放在 GitHub/Gitee 上的开源项目 | 打 tag 即可,`com.github.<用户>:<仓库>:<tag>` |
| **Gitee / 阿里云效 / 自建 Nexus** | 国内团队内部 | 私服账号;不公开但可用 |

### 第 0 步:先把库推到 GitHub(顺序不能反)

**先建远程仓库,再 push** —— 顺序反了 Git 会报 `remote: Repository not found.`,
那**不是网络问题**,而是远程仓库还不存在(或私有且当前凭据无权访问)。

```bash
cd graph-lib

# 方式 1:网页建仓库后再推(fake 15 秒)
#   打开 https://github.com/new → Owner 选 softwind-wf,名字填 graph-lib
#   不要勾 "Add a README file" / .gitignore / license(否则远程会有提交,和本地冲突)
git remote add origin https://github.com/softwind-wf/graph-lib.git   # 已配置过可跳过
git push -u origin main

# 方式 2:一条命令(脚本会先调 API 建仓库,再推)
$env:GITHUB_TOKEN = "ghp_xxxxxxxx"        # 需要 repo 权限的 PAT
pwsh scripts/push-to-github.ps1

# 方式 3:用 SSH(免每次输令牌)
ssh-keygen -t ed25519 -C "softwind-wf@users.noreply.github.com"
#   把 ~/.ssh/id_ed25519.pub 贴到 https://github.com/settings/keys
pwsh scripts/push-to-github.ps1 -Ssh
```

`Repository not found` 的三种原因与处理:

| 原因 | 判断 | 处理 |
|---|---|---|
| 仓库还没建(最常见) | 打开 `https://github.com/softwind-wf?tab=repositories` 找不到 graph-lib | 按上面"方式 1"建仓库,或"方式 2"交给脚本 |
| 私有仓库 + 当前凭据无权限 | 仓库确实存在但你是用别的账号/过期令牌推 | `cmdkey /delete:git:https://github.com` 清掉旧凭据,重新推送并按提示登录 |
| 账号或仓库名拼错 | `git remote -v` 显示的名字和你账号对不上 | `git remote set-url origin <正确地址>`,或用脚本的 `-Owner` / `-Repo` 参数 |

> 顺带一提:如果你更想把库放在已有的 `algs-exercise` 仓库里当子目录,也可以 —— 但那样
> `pom.xml` 里的 `<url>` / `<scm>` 要改成那个仓库,而且库和练手代码混在一起不便引用。
> 建议单独建 `graph-lib` 仓库(本库的元数据已经按这个地址填好)。



| 要求 | 本库现状 |
|---|---|
| 1. **groupId 所有权** | ✅ `io.github.softwind-wf` —— 用 GitHub 账号 `softwind-wf` 即可验证命名空间 |
| 2. **GPG 签名** | ⚠️ 待你申请密钥;插件已配在 `release` profile 里(读环境变量 `GPG_KEYNAME` / `GPG_PASSPHRASE`) |
| 3. **`-sources.jar` 与 `-javadoc.jar`** | ✅ `-Prelease` 生成,`dist/` 里也已备好 |
| 4. **完整 POM 元数据** | ✅ name / description / url / licenses / developers / scm 均已指向 `github.com/softwind-wf/graph-lib` |
| 5. **明确许可证** | ✅ MIT(`LICENSE`,pom `<licenses>` 已同步) |

发布步骤:

```bash
# 0) 先把代码推到 GitHub:仓库名建议就是 graph-lib,地址 github.com/softwind-wf/graph-lib
#    并在 Sonatype Central Portal 用 GitHub 账号验证命名空间 io.github.softwind-wf
# 1) 申请 GPG 密钥并上传公钥
gpg --gen-key
gpg --keyserver keyserver.ubuntu.com --send-keys <你的密钥ID>
# 2) 一条命令发布
set GPG_KEYNAME=<你的密钥ID>
set GPG_PASSPHRASE=<密钥口令>
mvn -Prelease clean deploy ^
  "-DaltDeploymentRepository=ossrh::default::https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
# 3) 在 Central Portal 点 Publish,几分钟到几小时后全世界就能 <dependency> 到你
```

> 本机直连 GitHub 不通(见工程记忆),所以第 0 步的推送与 Sonatype 账号注册需要你在能访问 GitHub 的网络环境里完成。

### ⚠️ 许可证提醒(重要,别跳过)

- 本库的**代码是为这份工程独立编写的**,但它的**算法、术语与样例数据明显源自**
  《Algorithms, 4th Edition》(Princeton)**与中文教材《精讲数据结构(Java 语言实现)》**;
  **algs4 的官方实现是 GPLv3**。
- 所以:
  - 本库按"照着算法自己写实现"处理,**采用 MIT**(`LICENSE` 已按此写好);
    但**一旦你从 algs4 复制过任何代码/注释/数据结构细节**,整份作品就要按 GPLv3 走 —— 请自查一次。
  - 样例数据文件(`tinyEWDAG.txt` 等)**是本工程为测试自行整理/构造的**,随库分发没问题;
    但若你打算把 algs4 的原始数据文件一并分发,请先确认其许可。
  - 测试当参照物用的 algs4 jar **只在 `repo/` 里供本地差分验证,不要随发布产物上传**。

### 发布前的其它建议

- 包名已改为 `io.github.softwindwf.graph`(发布后很难再改);若以后要换,用一次全局替换 + 全量回归即可。
- 版本号从 `1.0.0` 起,之后遵循语义化版本:API 变化升主版本。
- 建议加 `CHANGELOG.md` 与 CI(`mvn -o test` 作为回归闸门)。
- 注意:本库是**从主工程 `graph` 包抽取出来的独立副本**,主工程内仍保留 `cn.exercise.algs4.datastructure.graph` 那一份
  (整个仓库都按 `cn.exercise.algs4.datastructure.*` 组织,不宜改名)。两边要同步时,把主工程的类复制过来、
  再把包名替换成 `io.github.softwindwf.graph` 即可。

---

## 7. 别人怎么用你的库?(仓库公开 ≠ 别人能当依赖用)

**先分清两件事**:

- **仓库公开**(现在已做到):别人能 clone、能读源码、能下载 `dist/` 里的 jar,但**不能**写一行
  `<dependency>` 就自动拉取 —— Maven 不认识 GitHub 仓库里的源码;
- **能被当依赖**:必须把**产物**发布到某个"仓库"里。按门槛从低到高有四档:

| 档位 | 别人写的坐标 | 别人还要做的配置 | 你的成本 | 现状 |
|---|---|---|---|---|
| A. 源码 clone 后自建 | 无 | clone + `mvn install` | 0 | ✅ 已可用 |
| B. **JitPack** | `com.github.softwind-wf:graph-lib:v1.0.0` | 项目里加一行 jitpack 仓库 | 0(打个 tag 触发) | ⏳ 差一个 tag |
| C. **Maven Central** | `io.github.softwind-wf:graph-lib:1.0.0` | **完全不用配置** | GPG 密钥 + Sonatype 账号 | ⏳ 差密钥与账号 |
| D. GitHub Packages | 同 C,但仓库指向 GitHub | 必须配 token(不能匿名) | 低 | 未配置 |

### B. JitPack —— 最快让别人"一行依赖"用上(建议先做)

只要打一个 tag 并推送(`v1.0.0` 我已经在本地打好):

```bash
cd graph-lib
git push origin v1.0.0
```

然后别人在项目里(仓库只需加一次)+ 依赖:

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

Gradle 版本:

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { implementation 'com.github.softwind-wf:graph-lib:v1.0.0' }
```

要点:

- 首次构建要排队,几分钟;进度与**准确坐标**看 <https://jitpack.io/#softwind-wf/graph-lib/v1.0.0>
  (页面上 "Get it" 给的坐标就是权威值,复制它最保险);
- 仓库里已放 `jitpack.yml`,构建命令就是 `mvn -B install`(默认档:494 个用例,不依赖任何第三方,
  所以 JitPack 上能直接构建成功);
- 国内访问 `jitpack.io` 偶尔较慢,重试即可;
- 每次发布新版本:改 pom 版本号 → 提交 → 打新 tag(`v1.1.0`…)→ 推送,JitPack 会各自构建。

### C. Maven Central —— 终极形态(别人零配置)

使用者**不需要任何 `<repositories>` 配置**,直接:

```xml
<dependency>
  <groupId>io.github.softwind-wf</groupId>
  <artifactId>graph-lib</artifactId>
  <version>1.0.0</version>
</dependency>
```

发布步骤见上一节的 5 项硬要求(现在只差 GPG 密钥与 Sonatype 账号)。发布后用这两条验证:

```bash
mvn dependency:get -Dartifact=io.github.softwind-wf:graph-lib:1.0.0
# 或打开 https://central.sonatype.com/artifact/io.github.softwind-wf/graph-lib
```

### 怎么确认"别人真的能用"?

1. 建一个**空项目**,只写坐标 + 几行调用代码(不要引用你的本地源码);
2. 在**干净的本地仓库**上构建(临时指定一个空目录即可,排除本机缓存干扰):

   ```bash
   mvn -Dmaven.repo.local=./tmp-repo test
   ```

   能编译、能运行,说明别人也没问题;
3. JitPack 看构建页是否 `Build successful`;Central 看 `central.sonatype.com` 能否搜到。

---

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
