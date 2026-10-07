# 12. 模块与项目

前面十一章的程序都是一个文件。这一章讲代码多起来以后怎么组织：拆成模块，建成项目，写测试，用别人的包。

## 12.1 模块

一个 `.spr` 文件就是一个模块。另一个文件用 `import` 引用它：

<<< @/snippets/modules/main.spr

<<< @/snippets/modules/math_module.spr

```text
49
3.14159
```

- `import "./file.spr" as alias` 导入同目录下的文件，之后用 `alias.名字` 访问它顶层的函数、类、`let` 绑定。
- `import` 必须写在文件最前面，所有声明和语句之前。
- 每个模块只初始化一次，顶层语句在第一次被导入时执行。两个模块互相导入会报 `SPR-NAME-IMPORT-CYCLE`。
- 标准库模块以 `@std/` 开头：`import "@std/lists.spr" as lists`。Java 类也是同一个 `import` 关键字，第 13 章讲。

模块里没有 `private`：顶层的一切都能被导入者看到。要控制可见范围，就把不想暴露的东西放到另一个不被导入的文件里，或者（对发布的包）用下面的 `exports`。

## 12.2 项目

第 1 章用过 `sprig init`。回顾一下生成的东西：

```text
my-tool/
├── sprig.toml        # 项目配置
├── sprig.lock        # sprig resolve 生成的锁文件
├── src/
│   └── main.spr      # 程序入口
└── tests/            # sprig test 运行的测试（自己建）
```

`sprig.toml`：

```toml
[project]
name = "my-tool"
version = "0.1.0"
language = "0.8"
```

- 源码目录默认 `src`，入口默认 `src/main.spr`，可以用 `source` 和 `entry` 改。
- 在项目的任何子目录里运行 `sprig check`、`sprig run`、`sprig build`，都会向上找到 `sprig.toml`，检查入口和它导入的所有文件。
- `sprig project` 打印项目信息，加 `--json` 给脚本用。
- 一个项目可以有多个入口，用 `[[bin]]` 声明并用 `--bin 名字` 运行，见[项目指南](/guide/projects#多个入口)。

## 12.3 测试

`sprig test` 运行 `tests/` 下的每个 `.spr` 文件。测试就是普通程序，没有专门的语法：正常结束就算通过，`assert` 失败或抛出未接住的 `Error` 就算失败。

`tests/sum.spr`：

```sprig
import "@std/test.spr" as testing

testing.equal_int(2 + 2, 4, "sum")
assert(1 < 2)
```

`tests/compile_fail/` 下的文件则**应该编译失败**，旁边同名的 `.expect.toml` 写明预期的错误码。这是给"编译器应该拒绝这种写法"的保证写测试的办法：

`tests/compile_fail/null_int.spr`：

```sprig
let n: Int = null
```

`tests/compile_fail/null_int.expect.toml`：

```toml
codes = ["SPR-TYPE-NULL"]
```

运行：

```text
$ sprig test
PASS compile_fail/null_int.spr
PASS sum.spr

2 passed, 0 failed
```

- 每个运行时测试在独立的 JVM 里执行，30 秒超时。
- `@std/test.spr` 提供 `equal_int`、`equal_text`、`equal_bool`（失败时报出两边的值）、`temp_dir()`（测试专用临时目录）和 `run_process`（运行子进程）。
- `sprig test --filter 文本` 只跑名字包含该文本的测试。

更多细节见[项目测试（英文）](/en/reference/tooling/testing)。

## 12.4 依赖

三种依赖，都用 `sprig add` 加，它会改 `sprig.toml` 并更新锁文件：

```bash
sprig add math --path ../math                                     # 本地目录里的 Sprig 包
sprig add math --git https://example.com/math.git --branch main   # Git 仓库里的 Sprig 包
sprig add --jvm org.apache.commons:commons-text:1.12.0            # Maven 上的 Java 库
```

加好以后，用 `@依赖名/模块.spr` 导入：

```sprig
import "@math/vector.spr" as vector
```

被依赖的包要在自己的 `sprig.toml` 里用 `exports = ["vector.spr"]` 列出允许导入的模块，没列出的导不进来。

关于锁文件 `sprig.lock`，记住三件事：

- **提交它。** 别人拿到同样的锁文件，构建出来的东西和你完全一样。
- **只有 `resolve`、`add`、`remove` 会改它。** `check`、`run`、`build` 只读；锁文件和 `sprig.toml` 对不上时直接报错，不会自己去解析。
- **Git 依赖锁定到具体提交。** 想更新就重新 `sprig resolve`。

## 12.5 包注册表

不知道一个包在哪个 Git 仓库里时，查注册表：

```text
$ sprig search json
json-codec  0.7.1-beta.1  https://github.com/ColinHouse/Sprig.git libraries/sprig-json-codec  Apache-2.0
    Path-aware JSON decoding and encoding over @std/json
1 package(s) in 1 registry(ies); add one with: sprig add NAME [--version V]
```

```bash
sprig add json-codec                      # 最新版本
sprig add json-codec --version 0.7.1-beta.1
```

注册表只是一份索引：一个目录，每个包一个 `packages/名字.toml`，写着它的 Git 仓库、子目录、许可证、维护者和各版本对应的 tag 及提交。`add` 查到以后，在 `sprig.toml` 里写下的是一个**普通的 Git 依赖**，之后 `check`、`run` 不再碰注册表。没有中心服务器、没有账号、没有上传：发布一个包就是向 Sprig 仓库开一个只改 `registry/packages/名字.toml` 的 pull request（`sprig publish --tag v1.0.0 --license ... --owner ...` 帮你生成条目），CI 会核对 tag、提交和编译。已发布的版本不可更改，只能撤回（`--yank`）。

不声明任何注册表时，默认用 Sprig 仓库里的 `registry/` 目录，里面列着第一方库（`cli`、`json-codec`、`http`、`web`、`sqlite`）。团队自己的注册表用 `[[registry]]` 声明：

```toml
[[registry]]
name = "team"
path = "../registry"
```

查不到的包或版本报 `SPR-DEP-REGISTRY`。细节见[依赖契约（英文）](/en/reference/projects/dependencies)。

## 12.6 离线

`--offline` 让所有命令只用本地缓存（`~/.sprig/git` 等）。缓存里没有需要的东西时报 `SPR-DEP-OFFLINE`，而不是悄悄联网。

## 小结

- 一个文件一个模块，`import "./x.spr" as x`；`@std/` 是标准库，`@包名/` 是依赖。
- 项目 = `sprig.toml` + `src/` + `tests/`；锁文件要提交，只有 `resolve`/`add`/`remove` 改它。
- 测试是普通程序；`compile_fail/` 测编译器该拒绝什么。
- 注册表是索引，`search` 查、`add` 写成 Git 依赖。

下一章：[调用 Java](/tutorial/ch13-java)。
