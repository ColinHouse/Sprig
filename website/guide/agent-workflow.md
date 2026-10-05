# 面向 Agent 的工作流

Sprig 面向人和编码 Agent。目标是让实现依据、失败位置和修复决定可以检查，而不是承诺 Agent 总能写对程序。**Agent 友好也应当方便审查。**

## 先查询本机编译器

版本和 SDK 能力会变化。先用安装中的工具回答问题：

```sh
sprig version
sprig capabilities --json
sprig help language --json
sprig api java.time.LocalDate --json
```

`capabilities` 是该 SDK 的 feature inventory。语法能解析不代表静态语义、Java 生成和 JVM 运行都可用；用实际 `check`、`build`、`run` 验证你依赖的阶段。

## 从最小失败开始

1. 读 `AGENTS.md`、相关参考页和同目录的现有测试。
2. 编写一个小的正向程序，运行 `sprig check --json path/to/file.spr`。
3. 发生错误时，读取稳定 code、源码范围、expected/actual 类型及 repair 说明；再运行 `sprig explain SPR-CODE --json`。
4. 只在理解修复语义后改源码。编译器不会替 Agent 决定有损转换或扩大 API 支持范围。
5. 运行 `sprig test --json`（若当前 SDK 声明支持）、相关独立测试和完整贡献 gate；最后审查 diff 与生成文件状态。

JSON 诊断结构和命令版本边界见[Agent 工具参考](/en/reference/tooling/agent-guide)；稳定诊断码见[诊断码表](/en/reference/tooling/diagnostic-codes)。

## 修复类型错误示例

如果把字符串赋给整数，编译器报 `SPR-TYPE-ASSIGN`，并给出 `expectedType: Int` 与 `actualType: String`。保留这个机器可读证据；根据意图将值改成整数，或把变量类型改成 `String`。不要仅为通过构建而转换。

本教程的[预期错误片段](/tutorial#_1-值和类型)由文档门禁实际检查。新语义应同时添加正向和反向程序，防止测试只证明它“能过”而没有验证拒绝路径。

## 早期 dogfood 的边界

维护者报告曾用一个较低成本的编码模型尝试 Sprig 工作流。记录性质是早期轶事：没有受控 benchmark、等价 Java 对照程序、公开任务集或 productivity measurement。我们不据此声称 Sprig 优于 Java、Agent 输出更正确或开发速度有定量提升。

目前可陈述的产品假设较窄：稳定的类型错误、Java API 查询与 capability inventory 能给修复提供可核对的信息。外部用户可按上面的流程复现；提交反馈时请附 SDK 版本、最小源文件、命令及完整诊断。

## 延伸阅读

- [从零开始的教程](/tutorial)
- [已发布与源码功能状态](/en/reference/language/feature-status)
- [项目测试](/en/reference/tooling/testing)
- [Sprig contribution guide（英文）](/en/project/contributing)
