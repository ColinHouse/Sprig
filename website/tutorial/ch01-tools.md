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

本书使用的语法和标准模块已包含在 [v0.8.0-beta.1 SDK](https://github.com/ColinHouse/Sprig/releases/tag/v0.8.0-beta.1) 中。安装发布的 SDK 就能开始，不需要先构建编译器。

### Linux / macOS：安装 SDK

装好 JDK 21+ 后，在终端运行：

```bash
curl -fsSL https://raw.githubusercontent.com/ColinHouse/Sprig/main/scripts/install-sprig.sh | sh
export PATH="$HOME/.local/bin:$PATH"
sprig version
```

安装脚本选择最新已发布的版本（包括 Beta），核对 ZIP 的 SHA-256，并将 SDK 安装到 `~/.sprig/versions/`，启动器放到 `~/.local/bin/sprig`。它不修改终端配置；上面的 `export` 只在当前终端生效。想让新终端也能找到 `sprig`，可以把这行 `export` 加到 zsh 的 `~/.zshrc` 或 bash 的 `~/.bashrc`。

也可以手动下载、校验和解压 ZIP，详见[安装说明](/guide/getting-started)。使用 SDK 不需要 Python 或 Git；第一次解析 Maven 依赖仍可能需要联网。

### Windows：解压 SDK

Windows 目前是实验性预览，没有上述安装脚本。下载发布页的 ZIP 和同名 `.sha256` 文件，核对校验值后解压，使用其中的 `bin\sprig.cmd`。把解压目录的 `bin` 加到 `Path` 后，本书的 `sprig ...` 命令就能直接运行。详细步骤见 [Windows 安装说明（英文）](/en/reference/projects/install#windows)。

### 确认版本

```bash
sprig version
```

当前 SDK 的输出是：

```text
sprig-compiler 0.8.0-beta.1
```

这表示 **SDK/编译器发布版本**。如果显示旧版本，在托管安装中运行 `sprig upgrade`；手动解压的 SDK 则重新下载。若仍显示旧版本，检查 `PATH` 和编辑器的 **Sprig: Compiler Path** 是否指向另一份编译器。

再运行：

```bash
sprig capabilities
```

它会列出编译器版本、`language 0.8-dev`、最低 JDK、命令、类型和已实现功能。`0.8-dev` 是**语言版本**，表示语言尚未冻结；它不表示你没有安装发布的 SDK。需要完整、可供编辑器或 AI 助手读取的清单时，用 `sprig capabilities --json`。

::: tip 两种版本各有什么用？
`sprig version` 帮你确认安装的是哪一版 SDK；`sprig capabilities` 帮你确认它有哪些功能。网站随 `main` 分支维护，后续可能增加功能，因此排查问题时应同时提供版本和功能信息，见[发布状态](/project/release-status)。
:::

### 从源码构建（可选）

想修改编译器或试用尚未发布的改动时，需要 Git、JDK 21+ 和 Python 3.12+：

```bash
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
python3 scripts/build.py
```

Windows 把最后一条换成 `py -3 scripts/build.py`。第一次构建会下载固定版本的构建工具。构建完成后使用仓库里的 `bin/sprig`（Windows 是 `bin\sprig.cmd`），或将仓库的 `bin` 加到 `PATH`。本书的 `sprig ...` 也可以直接替换为该启动器的完整路径。

## 1.4 编辑器：VS Code

虽然用记事本也能写，但编辑器能帮你省很多事。推荐 [VS Code](https://code.visualstudio.com/)：

1. 安装 VS Code。
2. 安装 [Marketplace 上的 Sprig 插件](https://marketplace.visualstudio.com/items?itemName=ColinHouse.sprig-language)，确认发布者是 **ColinHouse**。插件使用已安装的 SDK 提供报错、参数提示和运行功能。如果找不到编译器，将 **Sprig: Compiler Path** 指向 SDK 启动器（`bin/sprig`，Windows 为 `bin\sprig.cmd`），详见[插件说明](/guide/editor)。
3. 把代码文件保存成 `.spr` 后缀，例如 `hello.spr`。

### 故意写错：用 Tab 缩进

一个必须知道的规矩：**Sprig 的缩进只能用空格，不能用 Tab**。用 Tab 缩进的文件连检查都过不了，编译器会说：

```text
SPR-LEX-TAB [LEX] main.spr:2:1: Tabs are not allowed for indentation or inline whitespace
  hint: Sprig code blocks use spaces only; replace the tab with spaces.
```

`[LEX]` 说明错误出在读字符的阶段，`2:1` 指向第二行开头那个 Tab，`hint` 告诉你怎么改。在 VS Code 里看一眼窗口右下角的状态栏：显示 `Spaces: 4` 就对了（数字是几都行，一个文件里保持一致即可）。如果显示 `Tab Size`，点它，选择用空格缩进。第 5 章会讲缩进和代码块，现在只要记住“空格，不是 Tab”。

## 1.5 检查环境：`sprig doctor`

最后跑一次体检命令：

```bash
sprig doctor
```

这台机器上的开头几行是：

```text
schemaVersion: 1
compilerVersion: 0.8.0-beta.1
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
- 安装已发布的 v0.8.0-beta.1 SDK 即可；从源码构建是可选步骤。
- `sprig version` 确认 SDK 发布版本；`sprig capabilities` 列出功能和独立的语言版本。
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

2. 运行 `sprig version`，再运行 `sprig capabilities`，找出编译器版本和语言版本。
   提示：SDK 发布版本和语言版本描述的是两件事。
   ::: details 参考答案
   当前发布的 SDK 输出：

   ```text
   sprig-compiler 0.8.0-beta.1
   ```

   功能清单显示编译器 `0.8.0-beta.1`、语言 `0.8-dev`。新 SDK 的数字可能不同；若版本较旧，按 1.3 升级。
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
