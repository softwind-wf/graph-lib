# NOTICE / 第三方与许可说明

本库(`graph-lib`)的代码为独立编写,按 [MIT License](LICENSE) 发布。

## 参考来源

算法与术语参考了:

- **《Algorithms, 4th Edition》**(Robert Sedgewick / Kevin Wayne,Princeton University)
  —— 其官方实现 **algs4 以 GPLv3 发布**;
- **《精讲数据结构(Java 语言实现)》** —— 工程中 AOV/AOE、关键路径等章节的算法来源。

本库**没有复制 algs4 的代码**。但请注意:

- 如果你(或后续维护者)从 algs4 复制了任何代码、注释或数据结构细节,**整份作品需改为按 GPLv3 分发**;
- 如需再分发 algs4 的任何内容,请先核对其许可证。

## 测试中的 algs4 参照物

`src/test` 里有 16 个测试类会与 algs4 的同类实现做**差分验证**。algs4 仅以 `test` 作用域、
`<optional>` 方式依赖:

- 它**不会**成为本库的运行时依赖,也不会传递给使用方;
- 它**不随本仓库分发**(`repo/edu/` 已在 `.gitignore` 中);
- 想跑差分测试:把 algs4 的 jar 放到 `repo/edu/princeton/cs/algs4/1.0.0.0/`,或用
  `mvn -Dmaven.test.skip=true install` 跳过测试构建。

## 测试数据

`tiny*.txt`、`routes.txt`、`coursesAOV.txt`、`projectAOE.txt` 是为本库测试整理或构造的数据文件,
可随库分发。
