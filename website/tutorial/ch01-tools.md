# 1. 准备工具

这一章你会学到：

- 终端（terminal）是什么，怎么打开；
- 怎么装 JDK 21 或更新的版本；
- 怎么拿到这本书需要的那一个 Sprig 编译器，以及怎么确认版本；
- 怎么在 VS Code 里写 Sprig 代码；
- 怎么用 `sprig doctor` 检查自己的环境。

不要把这一章跳过去。书的后面每一章都假设这三样东西是好的：一个终端、一个 JDK、一个正确的 `sprig` 命令。

## 1.1 终端

程序是一串给计算机的指令。Sprig 编译器把 Sprig 程序翻译成 Java，JVM 再运行 Java，所以你的电脑上需要能跑 Java 的东西。输入这些指令的地方叫**终端**。

- macOS：按 `Command + 空格` 打开聚焦搜索，输入 `Terminal`，回车。
- Windows：打开开始菜单，输入 `PowerShell`，回车。
- Linux：一般在快捷键 `Ctrl + Alt + T`，或者应用列表里搜 `Terminal`。

打开的窗口里，`$`（Windows 上是 `>`）后面可以输入命令，回车执行。本书的命令行都写成这样：

```bash
java -version
```

其中 `java -version` 是你输入的内容，下面跟着的就是屏幕上出现的内容。`$` 本身不要输入。

三个最常用的导航命令，现在混个脸熟就行：

| 命令 | 作用 |
|---|---|
| `cd 目录` | 进入一个目录（change directory） |
| `pwd` | 显示当前所在目录（Windows 用 `cd`，不带参数） |
| `ls` | 列出当前目录里的文件（Windows 用 `dir`） |

## 1.2 装 JDK 21 或更新

Sprig 生成的是 Java 代码，所以需要 **JDK 21 或更新的版本**。注意是 JDK（Java Development Kit，开发工具包），不是只有 JRE（运行环境）：JRE 能运行 Java，但没有 `javac` 这个编译器，Sprig 用不了。

装好以后，在终端里检查：

```bash
java -version
javac -version
```

这台写书的机器上，输出是这样（你的版本号会不同）：

```text
openjdk version "26.0.1" 2026-04-21
OpenJDK Runtime Environment (build 26.0.1+8-34)
OpenJDK 64-Bit Server VM (build 26.0.1+8-34, mixed mode, sharing)
javac 26.0.1
```

两条命令都有输出，而且版本号的大数字是 21 或更大，就够了。看第一行的 `"26.0.1"` 或者 `javac` 后面的数字；只要 ≥ 21 就行。

还没装的话，从 [Adoptium](https://adoptium.net/) 下载 Temurin 21（选自己的系统，Windows 下载 `.msi` 或 `.zip`），一路点下去即可。也可以用自己的系统包管理器，比如 macOS 上的 Homebrew。装完**重新打开一个终端**，再运行上面两条命令。

常见问题：

- `java -version` 有输出，`javac -version` 报“找不到命令”：装的是 JRE，换成 JDK。
- 两条都找不到：JDK 没有装好，或者它的 `bin` 目录不在 `PATH` 里。先重开终端；还不行就重新装一遍，安装器一般会自动配置 `PATH`。

## 1.3 装 Sprig，并且确认版本

这一节最重要：**这本书跟着 Sprig 源码 `main` 分支上的 0.8 语言写**，书里的例子用到了 0.8 才有的一些语法。官方发布页和安装脚本现在给你的是 `v0.7.1-beta.1`，它没有这些语法，后面几章的程序在它上面跑不起来。0.8 正式发布之前，请从源码构建。

### 从源码构建（Linux、macOS、Windows 都可以）

需要 Git、JDK 21+ 和 Python 3.12+。在终端里：

```bash
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
python3 scripts/build.py
```

Windows 上把最后一条换成：

```powershell
py -3 scripts/build.py
```

第一次构建会下载固定版本的构建工具，需要联网，也要等几分钟。构建完成后，仓库里的 `bin/sprig`（Windows 是 `bin\sprig.cmd`）就是编译器。

### 让 `sprig` 可以直接输入（可选）

本书后面的命令都写作 `sprig`。如果不想每次都写完整路径，可以把仓库的 `bin` 目录加进 `PATH`。macOS 默认终端是 zsh，在终端里运行：

```bash
echo 'export PATH="/把这里换成仓库的完整路径/Sprig/bin:$PATH"' >> ~/.zshrc
```

然后重开终端。用 bash 的话改成 `~/.bashrc`；Linux 同理。Windows 可以在“设置”里搜索“环境变量”，把 `...\Sprig\bin` 加到 Path 里。

不配置也行：书里每条 `sprig ...` 都可以换成 `/完整路径/Sprig/bin/sprig ...`，Windows 上换成 `...\Sprig\bin\sprig.cmd ...`。

### 确认版本：`sprig version` 不够用

先看版本命令的输出：

```bash
sprig version
```

```text
sprig-compiler 0.7.1-beta.1
```

**这条信息区分不了新旧编译器**：官方发布的旧 SDK 也自称 `0.7.1-beta.1`，`main` 上构建出来的 0.8 编译器同样如此。`sprig capabilities` 的第一行也一样，两边都写“language 0.8-dev”。所以不要靠版本号，靠功能清单：

```bash
sprig capabilities
```

```text
Sprig compiler 0.7.1-beta.1 / language 0.8-dev
JDK minimum: 21
Commands: help, version, check, build, run, test, codes, explain, capabilities, api, wrap, doctor, init, resolve, add, remove, search, publish, project, deps, upgrade, fmt, lsp
Types: Int, Int32, BigInt, Float, Float32, Decimal, Bool, String, Unit
Implemented: typed functions, one-line class declarations, contract classes (methods without bodies) with conform, function references (named, module and method functions, and print, as values), source function types fn(A) -> R, function types that throw Error, rethrows functions, classes, enums, variants, match statements, match expressions, if expressions, explicit declaration reexports, nullable types, typed catch, local modules, lambdas up to three parameters, generic blocks with one or more type parameters, explicit Type[Arg] generic uses, type arguments inferred from call arguments, Equatable capability, Comparable capability, Sprig function values passed as Java functional interfaces with up to three parameters, Java varargs calls, declared foreign JVM conformance (conform Class to ImportedInterface), Java class conformance with a parent view, error classes (conform C to Error(message))
Unsupported: inference from the expected type, variance, inheritance, Java functional interfaces with more than three parameters, Java type-variable varargs (T...), varargs declarations in Sprig, annotations, decorators, macros, reflection-based schemas, block lambdas, tuples, destructuring, string interpolation, Char type, async/await, wildcard match, pipeline, operator overloading, arrays
Use 'sprig help <topic>' for syntax and rules.
```

`Implemented:` 这一行很长，里面必须出现 **`if expressions`**。旧 SDK 的清单里没有这一项。可以只筛选这一项来检查：

```bash
sprig capabilities | grep -o "if expressions"
```

```text
if expressions
```

Windows PowerShell 上：

```powershell
sprig capabilities | Select-String "if expressions"
```

有 `if expressions` 输出，编译器就对。没有输出，说明你手里的是旧发布版，回到上面“从源码构建”。

还有一个佐证：等会儿要运行的 `sprig doctor`，在正确的编译器上会打印 `jdkMinimum: 21`；旧发布版是 `jdkMinimum: 17`。

::: tip 学过其他语言？
静态类型语言、编译器等概念你可能都熟。这一章唯一反直觉的点是：**版本号不可信，功能清单才可信**。这也是 Sprig 工具链的一贯风格——`sprig capabilities --json`、`sprig doctor --json` 给的是机器可读的当前事实，编辑器插件和 AI 助手都读它们，不猜版本。
:::

## 1.4 编辑器：VS Code

虽然用记事本也能写，但编辑器能帮你省很多事。推荐 [VS Code](https://code.visualstudio.com/)：

1. 安装 VS Code。
2. 安装 Sprig 插件。插件目前没有发布到插件市场，需要从源码打包，步骤见[插件页面](/guide/editor)。装好以后，语法高亮、保存时检查、一键运行这些功能都会用上你刚构建的编译器。如果插件找不到编译器，在设置里把 **Sprig: Compiler Path** 指向 `bin/sprig`。
3. 把代码文件保存成 `.spr` 后缀，例如 `hello.spr`。

一个必须知道的规矩：**Sprig 的缩进只能用空格，不能用 Tab**。用 Tab 缩进的文件连检查都过不了，编译器会说：

```text
SPR-LEX-TAB [LEX] main.spr:2:1: Tabs are not allowed for indentation or inline whitespace
  hint: Sprig code blocks use spaces only; replace the tab with spaces.
```

在 VS Code 里看一眼窗口右下角的状态栏：显示 `Spaces: 4` 就对了（数字是几都行，一个文件里保持一致即可）。如果显示 `Tab Size`，点它，选择用空格缩进。第 5 章会讲缩进和代码块，现在只要记住“空格，不是 Tab”。

## 1.5 检查环境：`sprig doctor`

最后跑一次体检命令：

```bash
sprig doctor
```

这台机器上的开头几行是：

```text
schemaVersion: 1
compilerVersion: 0.7.1-beta.1
languageVersion: 0.8-dev
jdkMinimum: 21
javaVersion: 26.0.1
```

后面还有很多行，列出 `javaHome`、类路径、缓存位置等。重点看：

- `jdkMinimum: 21`：这本书需要的编译器；如果是 17，见 1.3。
- `javaVersion`：你的 Java 版本，≥ 21 即可。
- `javacAvailable: true`：能找到 Java 编译器；`false` 说明 JDK 没装对。

## 本章小结

- 终端是输入命令的地方；`cd`、`pwd`、`ls` 是三个最常用的导航命令。
- 需要 JDK 21+，`java -version` 和 `javac -version` 都要有输出。
- 这本书需要 `main` 上的 0.8 编译器；官方 SDK 的 `v0.7.1-beta.1` 不够，0.8 发布前从源码构建：`python3 scripts/build.py`，然后使用 `bin/sprig`。
- `sprig version` 和 `capabilities` 的第一行区分不了新旧版本；用 `sprig capabilities` 的 `Implemented:` 行里有没有 `if expressions` 来判断。
- `sprig doctor` 做环境体检，`jdkMinimum` 应为 21。
- 缩进只用空格，不用 Tab。

## 动手练习

1. 打开终端，运行 `java -version` 和 `javac -version`，读出两个版本号。
   提示：两条命令都要有输出；大版本 ≥ 21 就合格。
   ::: details 参考答案
   写书的这台机器上是：

   ```text
   openjdk version "26.0.1" 2026-04-21
   ...
   javac 26.0.1
   ```

   你的数字可能不同（21、22、23……都行），只要两个大版本都 ≥ 21。如果 `javac` 找不到，回到 1.2。
   :::

2. 运行 `sprig capabilities | grep -o "if expressions"`（Windows 用 `Select-String`），确认屏幕上有 `if expressions`。
   提示：`grep -o` 只留下匹配到的那一小段文字；Windows 上换成 `sprig capabilities | Select-String "if expressions"`。
   ::: details 参考答案
   正确编译器的输出就是：

   ```text
   if expressions
   ```

   没有输出说明编译器是旧发布版，回到 1.3 从源码构建。
   :::

3. 运行 `sprig doctor`，找出 `jdkMinimum` 和 `javacAvailable` 两行，说出它们应该是什么。
   提示：输出有好几行，从开头往下找这两个名字。
   ::: details 参考答案
   写书的这台机器上：

   ```text
   jdkMinimum: 21
   ...
   javacAvailable: true
   ```

   `jdkMinimum` 应为 21（旧发布版是 17），`javacAvailable` 必须是 `true`。
   :::

准备好了吗？下一章写第一个真正的程序：[第 2 章：第一个程序与读懂报错](/tutorial/ch02-first-program)。
