# 18. 模块、项目和依赖

前面十七章的程序都装在一个文件里。真实程序会长到装不下，也会用到别人写好的代码。这一章讲三件事：

- **模块**：把代码拆进多个 `.spr` 文件，让它们互相引用；
- **项目**：用 `sprig.toml` 把一个入口、一组源码和一份依赖清单固定下来；
- **依赖**：使用放在本地目录、Git 仓库或包注册表里的 Sprig 包。

这一章你会学到：

- 一个 `.spr` 文件就是一个模块；`import "./math_module.spr" as math` 之后，用 `math.名字` 访问它的顶层函数、类和 `let` 绑定；
- 模块的顶层语句只在第一次被导入时执行一次，而且两个模块互相导入会被编译器直接拒绝；
- `export` 可以把一个模块做成门面，只转出想让别人用的名字；
- 项目 = `sprig.toml` + `src/` + `tests/` + 提交进版本库的 `sprig.lock`；没解析过锁文件，`sprig run` 会拒绝运行；
- `sprig add` 声明依赖，用 `@别名/模块.spr` 导入；依赖只允许导入自己 `exports` 里列出的模块。

## 18.1 一个文件就是一个模块

程序里有两件事常被混在一起：某些工具函数（面积、格式化、解析）和"用这些工具做一件事"的主流程。把工具留在自己的文件里，主流程的文件就能短到一眼看完，工具也能被别的程序复用。这个"一个文件"的单位就是模块。

最小的例子：一个模块，一个使用它的程序。两个文件放在同一个目录里。

`math_module.spr`：

<<< @/snippets/book/ch18_import/math_module.spr

`main.spr`：

<<< @/snippets/book/ch18_import/main.spr

```text
49
3.14159
```

逐行读：

- `import "./math_module.spr" as math`：导入**同目录**下的 `math_module.spr`，并给它起一个本地别名 `math`。路径以 `./` 开头表示"相对于当前文件"。
- `print(math.square(7))`：`math` 后面跟一个点，再跟模块里的名字，就能调用函数。`square(7)` 返回 `49`。
- `print(math.pi_approx)`：顶层 `let` 绑定也是一样的访问方式。名字前的 `math.` 是必须的；直接写 `pi_approx` 编译器不认识。
- 另外两行输出 `49` 和 `3.14159`，就是函数返回值和绑定值打印出来的样子。

模块里没有 `private`：被导入文件顶层的函数、类和 `let` 绑定，导入者全都能看到。要藏东西，就把它放进另一个不被导入的文件。

### 顶层语句只执行一次

模块的顶层语句在第一次被导入时执行。如果两个模块都依赖同一个模块，它也只初始化一次。三个文件：

`d.spr`：

<<< @/snippets/book/ch18_once/d.spr

`b.spr`：

<<< @/snippets/book/ch18_once/b.spr

`main.spr`：

<<< @/snippets/book/ch18_once/main.spr

```text
d initialized
b done
main done
```

`main.spr` 直接导入了 `d`，又通过 `b` 间接导入了 `d`。编译器先处理 `b`，`b` 第一次导入 `d` 时执行 `d.spr` 的顶层语句，打印 `d initialized`，然后 `b.spr` 自己的顶层语句打印 `b done`；回到 `main.spr`，它再导入 `d` 时模块已经初始化过了，什么都不重跑，最后打印 `main done`。这个规则保证了模块里的初始化代码（比如打开一次配置文件）不会重复执行。

### 互相导入会被拒绝

如果 `main.spr` 导入 `other.spr`，`other.spr` 又导入 `main.spr`，谁先初始化就说不清了。编译器不猜，直接报错：

`other.spr`：

<<< @/snippets/book/ch18_cycle/other.spr

`main.spr`：

<<< @/snippets/book/ch18_cycle/main.spr

```text
SPR-NAME-IMPORT-CYCLE [NAME] main.spr: Circular module import: main.spr -> other.spr -> main.spr
```

读这行报错：`SPR-NAME-IMPORT-CYCLE` 是错误码，`main.spr` 是说"问题从 main.spr 看起"，冒号后面 `main.spr -> other.spr -> main.spr` 画出了这条绕回自己的导入链。修法是打断循环，通常把两个文件都需要的代码抽到第三个模块里。

### 故意写错：忘了用别名

导入起了别名，访问名字时却直接写名字，是初学时最常见的错：

<<< @/snippets/book/ch18_alias/main.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:4:7: Unresolved name 'square'
```

`SPR-NAME-UNRESOLVED` 的意思是"这个名字我找不到"。`main.spr:4:7` 指出第 4 行第 7 列，也就是 `square` 的开头。编译器不会猜你想找 `math.square`；把 `square(7)` 改成 `math.square(7)` 就好了。

::: tip 学过其他语言？

- Python 的 `from math import square` 会把名字直接搬进当前文件；Sprig 没有这种写法，只有 `import ... as ...`，调用时别名不能省。Sprig 也没有 `import *`。
- Java 的包名和目录结构绑定；Sprig 的模块就是文件本身，`import` 里写的是相对路径，别名随你起。

:::

## 18.2 export：给别人一个门面

一个模块可能有很多顶层名字，但只有一部分是给外人用的。Sprig 的做法不是把它藏起来，而是再写一个**门面模块**，只把要公开的名字用 `export` 转出去。别人只导入门面，就只会看到这些名字。

`math_module.spr`：

<<< @/snippets/book/ch18_facade/math_module.spr

`facade.spr`：

<<< @/snippets/book/ch18_facade/facade.spr

`main.spr`：

<<< @/snippets/book/ch18_facade/main.spr

```text
25
```

- `facade.spr` 先正常导入 `math_module.spr`，然后 `export math.square`：把导入进来的 `square` 再转出去。`facade.spr` 自己就成了一个提供 `square` 的模块。
- `main.spr` 不知道也不关心 `square` 来自哪里，它只跟 `facade` 打交道。
- 转出去的名字保留原来声明的身份，所以门面不会复制一份函数，只是加了一个对外的入口。
- `import` 写在最前，`export` 在 `import` 之后、普通声明之前；每个 `export` 写一个名字，没有通配符。

### 故意写错：门面没转出的名字

门面只转出 `square`，使用方却想用 `pi_approx`：

<<< @/snippets/book/ch18_facade/limited.spr

```text
SPR-NAME-UNRESOLVED [NAME] limited.spr:4:7: Module 'facade' has no member 'pi_approx'
```

这次 `SPR-NAME-UNRESOLVED` 后面多了一句 `Module 'facade' has no member 'pi_approx'`：名字没错，是 `facade` 这个模块里没有它。要么在门面里补上 `export math.pi_approx`，要么直接导入 `math_module.spr`。

::: tip 学过其他语言？

Java 的 `public` 修饰符是在成员上开一扇窗；Sprig 反过来，默认全公开，要收口就额外写一个只列出可用名字的门面文件。这跟有些项目的 `__init__.py` 或 `index.js` 转出子模块的用法很像。

:::

## 18.3 项目：sprig.toml

一个目录里放一组模块、一个入口和一份依赖清单，就是 Sprig 项目。`sprig init` 帮你起头：

```text
$ sprig init
Created /private/tmp/sprig-book/my-tool/sprig.toml
Created /private/tmp/sprig-book/my-tool/src/main.spr
Run `sprig resolve` to create sprig.lock.
```

（输出里的路径是运行目录的绝对路径，在你自己机器上会不一样。）目录长这样：

```text
my-tool/
├── sprig.toml        # 项目配置
├── sprig.lock        # sprig resolve 生成的锁文件
└── src/
    └── main.spr      # 程序入口
```

`sprig.toml`：

```toml
[project]
name = "my-tool"
version = "0.1.0"
language = "0.8"
```

生成的 `src/main.spr`：

```sprig
# my-tool entry point.

func main() -> Unit:
    print("Hello, Sprig!")

main()
```

项目里的命令会向上找到最近的一个 `sprig.toml`，默认用 `src/main.spr` 当入口。先解析依赖，再运行：

```text
$ sprig resolve
Resolved 0 Sprig dependency entries and 0 Maven artifacts/models into /private/tmp/sprig-book/my-tool/sprig.lock

$ sprig run
Hello, Sprig!
```

`sprig resolve` 生成 `sprig.lock`，把每个依赖锁到确切的版本或提交。三个习惯要记住：

- **提交 `sprig.lock`。** 别人拿到同样的清单一跑，得到和你完全一样的构建；
- **只有 `resolve`、`add`、`remove` 会改它。** `check`、`run`、`build` 只读；
- **清单和锁对不上时，命令直接拒绝执行。** 改过 `sprig.toml` 之后要重新 `sprig resolve`。

### 故意写错：没 resolve 就跑

刚 `init` 完、还没 `resolve` 时运行：

```text
$ sprig run
SPR-PROJECT-LOCK-MISSING [CLI] Project 'my-tool' has no sprig.lock
  hint: Run `sprig resolve`.
```

`SPR-PROJECT-LOCK-MISSING` 说"项目没有锁文件"，`hint` 直接给出下一步。手动改过清单、锁过期时是相近的 `SPR-PROJECT-LOCK-STALE`：

```text
$ sprig run
SPR-PROJECT-LOCK-STALE [CLI] sprig.toml and sprig.lock do not match
  hint: Run `sprig resolve`.
```

### 一个项目，多个入口

一个项目可以有多个可执行入口，在清单里用 `[[bin]]` 声明。

```toml
[project]
name = "tools"
version = "0.1.0"
language = "0.8"

[[bin]]
name = "hello"
entry = "src/hello.spr"

[[bin]]
name = "bye"
entry = "src/bye.spr"
```

有多个 `[[bin]]` 时，`sprig run` 必须用 `--bin 名字` 挑一个：

```text
$ sprig run
SPR-PROJECT-ENTRY [CLI] sprig.toml: Multiple binaries require --bin or an explicit [project] entry
  hint: Name one: --bin hello, --bin bye. sprig check with no file checks every bin.

$ sprig run --bin hello
hello
```

`sprig project` 打印项目信息，`sprig project --json` 给脚本或编辑器用。清单里能写的键不多：`name`、`version`、`language`、`source`（默认 `src`）、`entry`（默认 `<source>/main.spr`），外加 `[[bin]]`、下面要讲的 `[[dependency]]`、`[[jvm]]` 和 `[[registry]]`；写错键名会被拒绝。

## 18.4 依赖

想用别的项目里的模块，先声明依赖。假设同事把数学工具放在 `../math`：

```text
$ sprig add math --path ../math
Added sprig-path dependency math and updated /private/tmp/sprig-book/dep/consumer/sprig.lock
```

`sprig add` 一边改 `sprig.toml`，一边立刻更新 `sprig.lock`。清单里多出来的是：

```toml
[[dependency]]
name = "math"
path = "../math"
```

然后在源码里用 `@` 加依赖名导入：

```sprig
import "@math/vector.spr" as vector
```

```text
$ sprig run
25
```

依赖包在自己的 `sprig.toml` 里决定别人能导入哪些模块：

```toml
[project]
name = "math"
version = "0.1.0"
language = "0.8"
exports = ["vector.spr"]
```

只有 `exports` 里列出的模块能被导入。同事新写了一个 `hidden.spr` 但没往 `exports` 里加，你导入它会得到：

```text
SPR-PROJECT-NOT-EXPORTED [NAME] main.spr:1:1: Dependency 'math' does not export 'hidden.spr'
  hint: Only modules listed in the dependency's [project] exports are importable.
```

`@std/...` 是唯一的例外：标准库模块来自编译器安装，不需要声明依赖，也不写进锁文件。

除了本地目录，`sprig add` 还接受 Git 和 Maven 两种来源：

```bash
sprig add math --path ../math                                     # 本地目录
sprig add format --git https://example.com/format.git --tag v1.0  # Git 仓库
sprig add --jvm org.apache.commons:commons-text:1.12.0            # Maven 上的 Java 库
```

Git 依赖可以用 `--branch`、`--tag` 或 `--rev` 指定版本，`resolve` 会把结果锁到具体提交。Maven 坐标是给 Java 库用的，第 21 章再展开。`check`、`run`、`resolve`、`add`、`remove` 和 `test` 都接受 `--offline`：只用本地缓存，缺东西时报 `SPR-DEP-OFFLINE`，绝不偷偷联网。

### 从注册表找包

记不住包的 Git 地址时查注册表：

```text
$ sprig search json
json-codec  0.7.1-beta.1  https://github.com/ColinHouse/Sprig.git libraries/sprig-json-codec  Apache-2.0
    Path-aware JSON decoding and encoding over @std/json
1 package(s) in 1 registry(ies); add one with: sprig add NAME [--version V]
```

`sprig add json-codec` 会在注册表里查到它，并在你的清单里写下一个**普通的 Git 依赖**；之后 `check`、`run` 不再碰注册表。注册表只是一份索引，没有中心服务器：默认索引是 Sprig 仓库里的 `registry/` 目录，团队自己的索引用 `[[registry]]` 声明。发布一个包就是向这份索引提交一条记录，`sprig publish --registry 目录 --tag v1.0.0` 帮你把当前包写进去（默认注册表还要求 `--license` 和 `--owner`），然后按命令提示开 pull request。

## 本章小结

- 一个 `.spr` 文件就是一个模块。`import "./x.spr" as x`，之后用 `x.名字` 访问；顶层名字默认全可见。
- 模块只初始化一次；互相导入是 `SPR-NAME-IMPORT-CYCLE`。
- `export x.名字` 做门面；门面没转出的名字，使用方会得到 `SPR-NAME-UNRESOLVED`。
- 项目 = `sprig.toml` + `src/` + `tests/`；`sprig resolve` 生成锁文件，锁文件要提交；`check`/`run`/`build` 拒绝缺失或过期的锁。
- `sprig add` 声明依赖，`@别名/模块.spr` 导入；依赖只有 `exports` 列出的模块可导入；`--offline` 只用缓存。
- `@std/` 是编译器自带的标准库，不是依赖。

## 动手练习

### 练习 1：抽出第一个模块

把下面的程序拆成两个文件：`double.spr` 里放函数 `double`，`main.spr` 导入并打印 `double(21)`。

```sprig
func double(x: Int) -> Int:
    return x * 2

print(double(21))
```

提示：导入之后，`double` 这个名字在 `main.spr` 里要通过别名访问。

::: details 参考答案

`double.spr`：

<<< @/snippets/book/ch18_ex1/double.spr

`main.spr`：

<<< @/snippets/book/ch18_ex1/main.spr

```text
42
```

:::

### 练习 2：加一个门面

在练习 1 的基础上加 `facade.spr`，它导入 `double.spr`，只把 `double` 转出去；让 `main.spr` 改成从门面导入。

提示：`export 别名.名字`。

::: details 参考答案

`double.spr`：

<<< @/snippets/book/ch18_ex2/double.spr

`facade.spr`：

<<< @/snippets/book/ch18_ex2/facade.spr

`main.spr`：

<<< @/snippets/book/ch18_ex2/main.spr

```text
8
```

:::

### 练习 3：它打印几次

一个模块被两个别名各导入一次，它的顶层语句执行几次？先猜，再运行答案里的两个文件验证。

提示：回忆 18.1 的"只执行一次"。

::: details 参考答案

`base.spr`：

<<< @/snippets/book/ch18_ex3/base.spr

`main.spr`：

<<< @/snippets/book/ch18_ex3/main.spr

```text
base initialized
main done
```

`base.spr` 的顶层语句只执行一次，尽管 `main.spr` 用 `first` 和 `second` 两个别名导入了它。第二次导入只是又加了一个别名，不会重新初始化模块。

:::

下一章：[测试](/tutorial/ch19-testing)。
