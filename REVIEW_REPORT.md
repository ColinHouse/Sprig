# Sprig v0.8 / compiler v0.2.0-alpha.1 — 独立审查与发布验收报告

**判定：NOT READY — RELEASE BLOCKERS REMAIN**

审查日期：2026-09-26。该报告统合当前实现、独立补充测试、SDK 盲测和发布条件；
旧实现报告和 alpha.2 报告保留为历史记录，不能替代本次验收。
本轮没有创建 tag、GitHub prerelease 或发布资产，也没有将语言元数据改为正式 `0.8`。

## 1. 范围、来源与版本基线

| 对象 | 精确身份 / 证据用途 |
|---|---|
| v0.7 / alpha.2 对照基线 | `e354969`（前一轮独立审查后的报告提交；正确性修复在 `d1ca3f5`） |
| 初始候选 | `283702d9cc338112429b02b84d3c6fdb2bff6455` |
| 初始候选对应 main | `cb2b4e7e27c44e47b834b9866f4ae8a72f75a538`，两者 tree 均为 `c9cf3e97160f726f17ad688ba3ab6ac17687f725` |
| 本轮最终修复代码提交 | `6732029f484081c4d1eb82820767e8d9dbe5588a`（泛型/项目修复 `9f3d266`，init 边界 `d4869e2`，诊断文案 `6732029`） |
| 当前元数据 | compiler `0.2.0-alpha.1`，language `0.8-dev`；最近已发布版本仍为 `v0.1.0-alpha.1` |

已读取 frozen v0.7 spec、当前 grammar、泛型契约、数值契约、JVM 文档、项目说明、
capability catalog、Agent guide、release draft、实现报告及相关源码。区分：

- **语言规则**：`spec/` 与明确的实现契约；不是 implementation report 的“完成”声明。
- **实现能力**：从源码和实际 check/build/javac/run 判断。
- **测试证据**：本轮重新执行；语法测试不证明类型检查或运行正确。
- **发布要求**：所有者此次要求的依赖解析、锁定及 stage-1 集成比现有实现契约更广。
  能诚实声明“不支持”，不代表已经满足这些发布要求。

修复没有改变数字语法、整数/浮点/Decimal 运行时、JVM metadata 实现或 v0.7
集合可变性契约。grammar 中 v0.8 增量为 generic/requires、扩展 variant payload 和
带类型候选的 postfix brackets；本轮修复不需要新增语法。

## 2. 发布阻塞项

| 阻塞项 | 实际情况 | 未通过的要求 |
|---|---|---|
| 依赖解析与锁定 | 没有 `resolve`、Git pinning、`sprig.lock` 校验、Maven resolver、transitive graph、checksum/cache/offline resolver | Git A→B 固定、stale/corrupt lock、Maven 冲突/传递依赖、严格 offline 均不能验收 |
| Sprig package 边界 | local/Git 声明只记录元数据；没有 `@package/...`、exports enforcement、依赖周期/越界防护 | dependency security/reproducibility 不具备可测试实现 |
| stage-1 项目迁移 | 仍是单文件 Sprig frontend probe；generic Stack/Table 是分离的实验 | 未形成要求的 multi-module project，也没有 stage-1 中的 3+ 参数实际集成 |
| 发布身份 | language 是 `0.8-dev` | 未达到正式 language `0.8` 的验收范围；不能为通过版本检查而仅改字符串 |

以上是已确认的未实现能力，不是已经证明存在“跟随移动 Git 分支”或“安全边界绕过”。
没有 resolver 就没有可声称通过的 resolver 安全测试。本轮只修复合理范围的正确性问题，
没有用临时 resolver 或空 lockfile 冒充功能。

## 3. 独立构建与执行

起始候选在 clean worktree 中重新通过 JDK 17.0.19 和 26.0.1：
ANTLR 生成、Java 源码编译、完整官方测试 **117/117**、grammar harness **24**、
SDK packaging 和 archive smoke。docs gate 通过 **18** 个 runnable snippets、
元数据一致性检查及 VitePress。实现报告的“grammar 21”是旧计数，实际当前是 24。

官方绿灯之后，独立严格回归在修复前实际失败：

- 泛型首轮 **40** 程序、**209** 截断前缀，**16** 失败，测试进程 exit 1。
- 清单首轮 **23** 场景，**23** 失败，测试进程 exit 1。
- 普通跨模块 variant 构造由独立 SDK Agent 发现并由审查侧复现。

### 可复现命令

使用对应 JDK 的 `JAVA_HOME` 并将其 `bin` 放在 PATH 首位。每个 JDK 使用独立 clean checkout，
同一目录的 build/test/package 顺序执行；不并行修改同一 build 目录。

```bash
./scripts/build.sh
./scripts/test.sh
ANTLR_JAR="$PWD/tools/antlr-4.13.2-complete.jar" ./tools/test-grammar.sh
./scripts/package-alpha.sh
python3 tools/check-sdk-archive.py
(cd website && npm ci)
./scripts/check-docs.sh
```

单独补充回归：

```bash
python3 tests/adversarial/v08/check_generics.py
python3 tests/adversarial/v08/check_projects.py
bin/sprig check tests/adversarial/v08/fixtures/generic_case_corruption.spr --json
bin/sprig build tests/adversarial/v08/fixtures/nested_list.spr -d build/repro --json
bin/sprig run tests/adversarial/v08/fixtures/generic_recursive_visitor.spr --json
```

### 最终修复提交的实际结果

以下结果均对应代码 SHA `6732029f484081c4d1eb82820767e8d9dbe5588a`。
本地两个 worktree 构建结束后 `git status --porcelain` 均为空。

| Gate | JDK 17.0.19 | JDK 26.0.1 |
|---|---|---|
| ANTLR 生成 + javac seed/compiler/runtime | 通过 | 通过 |
| 完整 `scripts/test.sh` | 119/119 | 119/119 |
| Static semantics | 54/54 | 54/54 |
| Numeric / correctness | 66/66、22/22 | 66/66、22/22 |
| Recovery | 467/467（含 435 个 prefixes） | 同样通过 |
| Acceptance / JSON / consistency | 52/52、13/13、10/10 | 同样通过 |
| Agent tooling | 89/89 | 89/89 |
| CLI contract | 19 个 rejected-option 案例及端到端契约通过 | 同样通过 |
| Stage-1 frontend probe | check/javac/JVM golden、malformed ranges、case evolution 通过 | 同样通过 |
| Project 原套件 | 14/14 | 14/14 |
| 新泛型对抗测试 | 46 个程序、209 个 prefixes，0 failure | 同样通过 |
| 新清单/项目/init 对抗测试 | 31 个场景，0 failure | 同样通过 |
| 独立 grammar harness | 24/24，syntax-only | 24/24，syntax-only |
| Clean package + SDK smoke | 通过 | 通过 |

最终代码的 docs gate：18/18 snippets、tooling/version consistency、VitePress 全部通过。
同 SHA 的 [hosted CI #36231451322](https://github.com/ColinHouse/Sprig/actions/runs/36231451322)
中 JDK 17、JDK 26、Documentation site 全部 success；两项 JDK job 都包含完整测试、
grammar harness、package 和 SDK archive smoke，已经核对 job logs 的真实计数。

### 开发归档身份

SDK-only 最终盲测使用独立 clean JDK17 checkout 打包的 archive：

- source revision：`6732029`，BUILD_INFO 明确 `Working tree clean: yes`。
- ZIP SHA-256：`9a4a8a4ed827321308cf6a2a3339d542786ec427d274d01a3b3c022287f7d7a4`。
- compiler JAR SHA-256：`5f147ea558fd0c86cc115e1c4bedebb151abe03f4124580f2a55f38799da1ce5`。
- ANTLR JAR SHA-256：`eae2dfa119a64327444672aff63e9ec35a20180dc5b8090b7a6ab85125df4d76`。

已实际核验 generic/project/dependency docs、README/AGENT_GUIDE、license/notice、examples、
compiler/runtime。文本条目没有开发者 `/Users/` 绝对路径；没有打入审查/盲测日志、
acceptance 临时结果或本报告。不同构建的 ZIP/JAR 含时间及 build 信息，不能把不同归档
的 checksum 混用，未宣称字节级可复现。该 archive 未发布为 release asset。

`scripts/test.sh` 的总数是顶层断言/聚合检查数，不能与各子套件条目数相加宣称为独立程序数。
CLI、JSON 和 consistency matrix 分别验证不同阶段；JVM 执行采用真实 golden stdout。

本地修复迭代中曾在另一进程运行测试时重建同一 jar，造成短暂 `ClassNotFoundException`；
这些迭代日志不是验收结果。最终双 JDK 重跑使用分离的 clean worktree，避免共享构建竞态。

## 4. 已修复的重要发现

所有位置是 `compiler/src/main/java/sprig/compiler/` 下的相关方法；最小用例在 `tests/adversarial/v08/fixtures/`，
预期诊断与输出在 `cases.json`，未删除或放宽原测试断言。关键位置可直接查看
[assignability](compiler/src/main/java/sprig/compiler/sem/Semantics.java#L42)、
[common type](compiler/src/main/java/sprig/compiler/sem/TypeChecker.java#L1568)、
[generic instantiation](compiler/src/main/java/sprig/compiler/sem/TypeRefResolver.java#L226)、
[Java lowering](compiler/src/main/java/sprig/compiler/gen/JavaGenerator.java#L1334) 和
[project CLI](compiler/src/main/java/sprig/compiler/cli/Main.java#L408)。

| 级别 | 发现与修复 | 最小复现 / 原失败阶段 | 源码定位 |
|---|---|---|---|
| P0 | variant case→variant assignability 仅比较声明，遗漏所有类型实参；现按声明和有序实参共同判断 | `generic_case_corruption.spr`；原 check 通过，运行 String→Long cast 崩溃；现静态 `SPR-TYPE-ASSIGN` | `sem/Semantics.java:isAssignable` |
| P0 | variant 分支的 common type 丢失实参，混合实例化被接受，正确的 case 列表也失去类型；现保留并比较实参 | `generic_mixed_variant_list.spr` / `generic_infer_variant_list.spr`；静态推断 | `sem/TypeChecker.java:commonType` |
| P0 | generic nested List/Map 的 raw get、foreach 与集合结果没有恢复已检查类型；合法程序 check 通过但 javac 失败 | `nested_list.spr`、`nested_map.spr`、`generic_loop_nested.spr`、`generic_loop_box.spr`、`generic_collection_chaining.spr` | `gen/JavaGenerator.java:emitBuiltinMethod/emitIndexOn/emitFor` |
| P0 | generic 返回容器的 Java cast 分组错误，索引时 cast 落到元素上 | `call_index.spr`；原运行 Long→SprigList `ClassCastException`；现输出 `2` | `gen/JavaGenerator.java:unboxGeneric` |
| P0 | 泛型函数内 `size[T](...)` 被 name resolver 当作索引，把 T 当运行时变量 | `generic_recursive_visitor.spr`；原 `SPR-NAME-UNRESOLVED`；现递归遍历输出 `2` | `sem/NameResolver.java:resolveExpr(Subscript)` |
| P0 | erased result cast 在被丢弃的表达式位置不是合法 Java statement；现以局部临时值保留一次求值 | `generic_discard.spr`；codegen/javac。完整重跑也防止临时变量使用不可访问 JDK bridge 返回类型，采用 Java `var` | `gen/JavaGenerator.java:emitStmt` |
| P1 | Practical Strict nullable rule 只扫描已经解析的顶层 signature，漏掉 forward、nested、local、indirect use | `generic_nullable_forward/nested/local/indirect/call.spr`；原静态接受，现 `SPR-TYPE-GENERIC-NULLABLE` | `sem/TypeRefResolver.java:resolveArguments/declaredRefs` |
| P1 | substitution 后的 nested `Map[K,V]` 未重新验证 numeric key 禁令 | `generic_map_float_nested.spr`；原静态接受，现数值诊断 | `sem/TypeRefResolver.java:resolveArguments` |
| P1 | late/nested `requires` 授予能力，违背 leading-clause 契约 | `constraint_late/nested.spr`；现 `SPR-GENERIC-CONSTRAINT`，不授予 equality | `sem/TypeChecker.java:checkFunction/checkRequires` |
| P1 | 普通 `model.Choice.Replace(...)` 将模块类型当值解析 | `module_variant.spr` 与 `module_variant_model.spr`；原 check/build/run 同时拒绝；现普通及泛型构造输出 `42/7` | `sem/TypeChecker.java:resolveFieldAccess` |
| P1 | TOML parser 接受重复表、拼错字段、错误类型、非法 string/array；project model 接受重复名称及非 exact Maven version | `check_projects.py`；原 `project --json` exit 0；现 `SPR-PROJECT-MANIFEST` | `project/Toml.java`、`project/Project.java` |
| P1 | project check/build/run 静默忽略 unresolved dependencies，多个 bins 缺少明确 default 也不报歧义 | `check_projects.py`；现拒绝依赖并要求 `--bin` 或明确 entry；explicit file 仍正常执行 | `cli/Main.java:resolveProjectSource` |
| P1 | init 将目录名直接插入源文件注释，换行可注入额外程序语句；现将注释中的 CR/LF 转为空格，manifest 名称保持转义 | `check_projects.py`；原特殊目录 init→run 输出 `injected/Hello`，现只能输出 Hello | `cli/Main.java:init` |
| P1 | catalog/explain/SDK/website/release draft 存在单参数、无 project、alpha.2 等过期声明，SDK 缺少完整泛型/项目/依赖文档 | SDK 盲测触发多次 source repair；现同步说明真实边界、补归档文档 | `catalog.properties`、`CodeDocs.java`、`scripts/package-alpha.sh`、相关 docs |

### 最严重问题的完整最小示例

```sprig
generic T, E:
    variant Result:
        Ok:
            value: T
        Err:
            error: E
let x: Result[Int, String] = Result[String, Int].Ok(value="x")
match x:
    case Result.Ok as p:
        print(p.value + 1)
    case Result.Err as p:
        print(p.error)
```

预期：在赋值阶段拒绝；原行为：check 通过，生成的 Java 执行时将 String cast 为 Long。
现在 `bin/sprig check <file> --json` 与 build 均在前端拒绝。该问题是实际类型不健全，
不是 JVM 环境问题。

另一例：

```sprig
generic T:
    func head(xs: List[List[T]]) -> T:
        return xs.get(0).get(0)
print(head[Int]([[42]]))
```

原行为：check 成功，javac 报 Object 没有 get；现在 check/build/run 均通过，输出 `42`。
恢复类型使用已检查的节点类型，不进行反射式运行时类型猜测。

一项补充测试的初稿将推断出的 MutableList 直接传给 List 参数。它违反已有集合契约，
不能当作 compiler bug。最终源程序显式 `.toList()`，继续检查 generic variant 的实参推断；
未放宽 List/MutableList 规则。

## 5. 语言一致性、数值与 JVM 边界

- explicit typed function signatures、named class/variant constructors、let rebinding 拒绝、
  typed payload binder、match 穷尽性、无 wildcard/pipeline、不同集合可变性仍由前端处理。
- 多参数、3+ 参数、嵌套应用、每个参数位置的 substitute、无 hidden inference、重复/未知
  parameter、非 canonical aliases 和 truncation 均有真实 parser/static/runtime 证据。
- primitive boxing 覆盖 Int、Int32、Float、Float32、Bool、BigInt、Decimal、String、String?；
  本轮 fixture 分别经过 check、javac、JVM。泛型 erased T 的 equality 仍使用 Sprig value
  equality；浮点 NaN 和 signed zero 案例通过，不把 boxed Java equality 当作数值契约。
- Int 固定 64 位、Int32 固定 32 位、checked overflow、禁止整数 `/`、显式 `divTrunc`、
  checked/lossy conversions、IEEE Float/Float32 和 Decimal 规则保持原实现。
  66 项 numeric suite 是实际重跑结果；类型安全并不证明算法稳定性、物理单位或任意科学结果。
- Java generic collection 仍是 erased JVM boundary，不会自动伪造 `List[String]`；没有隐式
  collection adapter。本轮没有把手动下载 JAR 说成 Maven dependency resolution。
- 既有 metadata safety 和实际 third-party classpath suite 重跑；JVM indexing 不初始化被查询类。
  这不能证明尚不存在的 Maven resolver 不执行 plugin/hooks。

## 6. 独立 SDK-only Agent 结果

测试 Agent 未接触仓库源码、网站、前轮报告或 memory，只读取解压 SDK 的 docs/examples、
CLI help/capabilities/diagnostics。首轮保留 **93** 条命令的完整 argv、退出码和输出。
无论初轮或复测，SDK 均未由该 Agent 修改。

| 任务 | 首轮实际结果 |
|---|---|
| Generic class / function / 2 parameters | check/build/run 通过，正确输出 |
| Generic variant | 组合已文档化写法后两个分支均运行；缺分支静态拒绝 |
| Deliberate generic error | 缺 `[Int]`，explain 后一次修复；诊断 range 可定位 |
| Project | init、向上发现、explicit file、named bin 通过；bin 字段文档不足导致一次修复 |
| Local / Git dependency | 实际声明与 deps 调用证明 unresolved；任务未完成 |
| Maven | manifest 不解析 classpath；实际 Commons Lang JAR 由 Agent 用 curl 明确下载，同一 --classpath 下 api/check/build/run 通过 |
| JVM API | LocalDate metadata、Int32 映射、nullable narrowing、运行通过 |
| Multi-file | 普通 module variant 构造失败，模块内 wrapper workaround 后通过；失败源未丢弃 |

盲测脚本初稿曾把 unresolved 项目编译的退出码预期设为 2；依据 SDK 明确的
compile exit 1 契约纠正后重跑九条命令，保留原记录。这是测试预期修正，未改变编译器
或放宽依赖拒绝诊断。

初轮把 `Option[Int]` 写入 match case 导致语法错误，又发现 None 的 declaration 写法
缺例子。现在 SDK 收录完整 GENERICS、PROJECTS、DEPENDENCIES，明确 case owner 不带实参。
`explain` 与 catalog 的多参数描述同步修正。late requires 的次级 operand 文案不再错误地声称所有 capability 尚未实现。
manifest parse errors 补 URI/range；manifest
跨记录语义错误目前仍指向 line 1，这是剩余 P2 诊断质量限制。

最终复测针对上述 `6732029` clean SDK，由同一独立 Agent 重新执行：

- **108** 条完整日志命令：89 exit 0、16 exit 1、3 exit 2。
- 原有 **13** 个程序各自 check/build/run；新增 **4** 种特殊目录名 init 后各自
  check/build/run，合计 **17** 个程序的 **51** 个 phase 成功。
- **5** 个静态反例及 **14** 个 dependency/API/classpath 预期非零结果保持拒绝。
- 含 metadata/manifest/输出检查共 **93/93** 最终 verdict 匹配预期。
- 普通跨模块 variant 不再需要 wrapper；Some/None/match、三参数 class/function、
  leading Equatable、nested generic collections 均实际执行成功。
- newline、quote、backslash 和组合目录名精确 roundtrip；生成的可执行行与 Hello 模板
  一致，运行只输出 Hello，没有目录名中的测试 sentinel 语句执行。
- ZIP 和两个 JAR hash 由该 Agent 独立计算并核对 BUILD_INFO。SDK 未修改。

特殊目录名测试初稿误把注释中保留 sentinel 文本也判为失败，产生两个 oracle false。
最终检查所有可执行行与模板完全相同并保持原 runtime 输出期望，保留初稿记录；
不是修 SDK 或放宽实际执行行为。local/Git/Maven 的 unsupported 结果继续保留。

该盲测结论不是“全任务通过”：local/Git/Maven resolution 仍不可用。

## 7. 50 项发布要求的覆盖和未完成项

| 要求编号 | 本轮证据 / 限制 |
|---|---|
| 1–4 | 独立源码审查、基线比较、双 JDK clean build/test/docs/package |
| 5–17 | 官方及新增 generic corpus；positive 运行，negative 在前端拒绝，209 新 truncation prefixes |
| 18–19 | api/third-party JVM boundary suite；adapter 未实现，未声称 copy/view 安全 |
| 20–22 | 项目发现、explicit-file 优先、nearest manifest、strict parser、bins；不以 source metadata 冒充 sandbox |
| 23–31 | 声明及 unsupported CLI 实测；resolver/lock/security/offline 场景因功能不存在未执行，不记 pass |
| 32–33 | named bins/default ambiguity 已回归；dependency exports 尚无 enforcement |
| 34–35 | help/capabilities/project/deps/doctor/api/check/explain 实测及 SDK；false flags 保持诚实；genericConstraints 仅指 Equatable |
| 36–37 | 阅读并运行原 stage-1 frontend probe 和 Stack/Table 实验；递归 generic Visitor 实测；项目集成缺口仍阻塞 |
| 38–40 | 初轮与最终 SDK 盲测、clean packaging、内容/版本审计；language 仍 0.8-dev，禁止只改 metadata 过关 |
| 41–43 | P0/P1 修复与严格 regressions；原测试断言保留；最终完整重跑 |
| 44 | 最终代码 SHA 的 [CI](https://github.com/ColinHouse/Sprig/actions/runs/36231451322) 三项全部 success；交付报告提交的 exact HEAD checks 见 [PR #9 Checks](https://github.com/ColinHouse/Sprig/pull/9/checks)，需另行核对，不能用父提交绿灯代替 |
| 45–50 | 本报告判定 NOT READY；发布条件不满足，没有 tag/prerelease，也没有 post-release verification 的成功声明 |

## 8. 架构与自举判断

ANTLR 负责词法/语法，grammar 没有塞入核心语义 action。前端生成 AST 后由名称解析和
类型检查附加 symbols/types/resolved calls；JavaGenerator 消费这些信息。当前“Typed IR”
实质上是已检查 AST，并没有独立完整的 IR。已有节点类型/位置可供未来 tooling 复用，
但没有 LSP，不能仅据模块命名宣称 IDE integration 完成。

重复的类型解析应继续归并到 TypeRefResolver/Substitution 的规则入口；这轮已把 forward、
nested 和 instantiated annotations 放到同一校验入口，并在副本上解析，避免覆盖模板类型。
Java raw erasure 仍使 result adaptation 容易遗漏；下一轮应继续审查更多 composition，
不能把现有 46 案例视为所有 generic 程序的完备证明。

Sprig 有递归函数、closed variants/match、typed collections、字符串/字符相关 API、JVM
文件及进程服务，具备逐步写 lexer/parser/AST/symbol-table 的表达能力。单文件 frontend
probe 和本轮递归 Tree Visitor 是真实起点；Stack[T]/Table[K,V] 能减少不同元素类型的数据
结构重复。这些证据不证明完整 self-hosting，也未展示 stage-1 三参数抽象的实际收益。
主要距离来自模块化 stage-1、resolver/标准库分发、诊断与 frontend coverage 的工程工作；
没有依据认定必须引入新复杂类型系统。缺 module integration 是未完成工程，缺 resolver
是未实现基础设施；类型安全和数值稳定性仍属不同保证。

## 9. 下一阶段建议与保留限制

1. 保持本轮 soundness/codegen regressions 为 required gate；继续将 check/javac/runtime
   不一致缩成最小程序，先修前端或 lowering，不放宽断言。
2. 用明确契约实现 resolve→lock→cache→compile：固定 Git SHA、manifest digest、checksums、
   stale/corrupt lock 拒绝、严格 offline、exports/路径/symlink 边界；随后真实复测 A→B 场景。
3. 选择并审计真实 Maven resolver，实现 deterministic transitive/conflict 策略及四命令共享
   classpath，验证无 plugin/hook 执行和不初始化 metadata 类。
4. 将 Sprig frontend probe 拆成真实 stage-1 项目，用一/二/三参数抽象只解决实际重复，
   保留 Java host 的 filesystem/JVM/platform 服务边界。
5. 补 manifest record-level 诊断位置和 SDK composition examples，再进行 SDK-only 盲测。
   必须重新确认 exact final SHA 的双 JDK/docs/package hosted gates 后才可发布。

无 inference、variance、Comparable、interfaces/traits、registry、LSP 或 implicit Java
collection conversion 属公开的当前限制；本轮不强行添加。未验证的平台、完整 TOML、
完整泛型正确性证明、依赖安全性和完整 self-hosting 均不在通过结论内。

交付入口：[草稿 PR #9](https://github.com/ColinHouse/Sprig/pull/9)。本报告和索引说明
的后续记录提交不改变上述编译器、测试或 SDK 文档；最终交付 SHA 与其 exact-SHA CI
通过 PR 最新 Checks 和交付消息记录。默认分支未改动，未合并草稿 PR。

本轮代码、测试和文档由 AI 辅助完成；审查结论来自实际独立执行、源码定位和 SDK-only
Agent 的可复现输入。证据日志留在本地审查目录，不打入 SDK，不提交开发者绝对路径。
