# VS Code 插件

插件让你在 VS Code 里写 Sprig 时有这些帮助：

- 语法高亮
- 边写边报错
- 快速修复
- 格式化
- 文件大纲
- 悬停提示
- 自动补全
- 函数参数提示和语义着色（需要提供这两项能力的新编译器）
- 跳转到定义、查找引用和重命名
- 代码片段
- 测试面板
- 一键运行，以及查看生成的 Java

类型、签名和报错都来自你装好的 `sprig` 命令，插件自己不做类型检查。如果这个编译器带有[语言服务器](/guide/tooling#语言服务器) `sprig lsp`，插件会自动启动它，上面这些功能都由它提供。

插件还没有发布到 VS Code 插件市场，也没有放进 SDK 的压缩包，需要自己从源码打包安装。

## 安装

在 Sprig 仓库的 `editors/vscode/` 目录里运行：

```sh
npm ci
npm run package
```

会生成 `dist/sprig-language-0.3.1.vsix`。在 VS Code 的扩展面板里点 `…` → **Install from VSIX…** 选中它，或者在命令行运行：

```sh
code --install-extension dist/sprig-language-0.3.1.vsix
```

装好以后，语法高亮、大纲和代码片段马上就能用，不需要 Java 和编译器。

其他功能还需要装好 JDK 21+ 和 [Sprig SDK](/guide/getting-started)，然后在设置里把 **Sprig: Compiler Path**（`sprig.compilerPath`）设成 SDK 里的 `bin/sprig`。不设置的话，插件会先在 `PATH` 里找 `sprig`，再沿着上级目录查找源码构建出来的 `bin/sprig`。测试面板需要 v0.5.0-beta.1 或更新的编译器。

语言服务器是 v0.6.0-beta.1 新加的。用 v0.6.0-beta.1 或更新的编译器时，插件会自动用上它；用 v0.5.0-beta.1 时，插件改为每次单独调用编译器命令，区别见下表。想关掉语言服务器，把 `sprig.languageServer.enabled` 设成 `false`。

## 写代码时

| 功能 | 有语言服务器时 | 用 v0.5.0-beta.1 时 |
|---|---|---|
| 报错 | 边写边报，显示在「问题」面板里。被导入的文件里有错，会标在对应的 `import` 那一行 | 保存时才报 |
| 快速修复 | 错误的改法是一处机械改写时，比如把 `else if` 改成 `elif`、补上漏写的 `@std` 导入，错误旁边的灯泡里就有这处修改，点一下就改好。需要比 v0.7.1-beta.1 新的编译器 | 没有 |
| 悬停提示 | 任何名字都能显示声明和类型，包括局部变量和参数，还有写在声明上方的注释 | 关键字、`Java类.方法`、`模块.函数` 和你写的顶层声明，用的是已保存文件的信息 |
| 自动补全 | 输入点号后补全任何值的成员，包括局部变量；其他位置补全作用域里的名字和关键字 | 关键字、本文件的声明和导入的名字；输入 `Java类.`、`模块.`、enum 名加点或顶层变量加点之后，补全它的成员 |
| 跳转到定义（F12） | 任何名字，可以跨文件 | import 的路径、`模块.成员` 和本文件的声明 |
| 查找引用（Shift+F12） | 查找声明的用法；200 个文件的分析上限导致遗漏时，会明确提示结果不完整 | 没有 |
| 参数提示 | 编译器提供这项能力时，输入 `(` 或 `,` 显示 Sprig 函数、方法、命名构造参数和 Java 重载候选，并标出当前参数；没写右括号也能提示 | 没有 |
| 语义着色 | 编译器提供这项能力时，按解析后的声明区分类型、函数、方法、字段、参数和变量，颜色遵循当前主题 | 只有词法高亮 |
| 重命名（F2） | 局部变量和参数 | 没有 |
| 格式化 | 右键 **Format Document**（或 Shift+Alt+F），用的是 `sprig fmt`。想保存时自动格式化，就打开 VS Code 的 `editor.formatOnSave` | 一样 |
| 大纲 | 「大纲」视图和顶部的面包屑会列出函数、类、variant、enum、字段、方法和顶层变量 | 一样 |

两种情况下，停在关键字上都会显示 `sprig help` 的说明，**Go to Symbol in Workspace** 可以在整个工作区里搜，点错误码可以打开错误码文档。代码片段：输入 `func`、`class`、`variant`、`match`、`ifnn`、`try`、`importj` 等前缀，按 Tab 展开。

参数提示和语义着色由更新后的编译器提供，插件 0.3.1 随它一起发布，会自动注册这两项功能。嵌套调用和字符串里的逗号不会弄错当前参数；命名构造参数可以按任意顺序填写。Java 没保留参数名时显示 `arg0`、`arg1` 等占位名。Java 重载暂不按实参类型排序，Sprig 泛型显示声明里的类型参数；内置函数和原生字符串、集合方法暂不提供参数签名。调用之外的语法错误仍可能让提示失效。语法或名字解析失败时会清空语义着色，保留词法高亮。

## 命令

打开 `.spr` 文件，从命令面板、编辑器右键菜单，或者 **Sprig** 状态项里使用：

| 命令 | 作用 |
|---|---|
| **Sprig: Check** | 检查当前文件 |
| **Sprig: Run** | 编译并运行当前文件，运行结束后在输出面板显示结果 |
| **Sprig: Run in Terminal** | 在终端里运行，适合服务器和需要输入的程序 |
| **Sprig: Run Tests** | 运行当前项目的全部测试，结果显示在测试面板 |
| **Sprig: Build** | 生成 Java 并用 javac 编译 |
| **Sprig: Show Generated Java** | 检查后生成 Java，在旁边打开，不调用 javac |
| **Sprig: Resolve Dependencies** | 为当前项目运行 `sprig resolve` |
| **Sprig: New Project** | 选一个目录、起个名字，创建好项目并生成锁文件 |
| **Sprig: Explain Diagnostic** | 用排好版的页面解释一个错误码，也可以从错误旁边的灯泡进入 |
| **Sprig: Show Help Topic** | 用排好版的页面显示 `sprig help` 的某个主题 |
| **Sprig: Show Capabilities** | 显示当前编译器的功能清单 |
| **Sprig: Restart Language Server** | 重新启动语言服务器，比如重新构建编译器之后 |
| **Sprig: Open Documentation** | 打开 Sprig 网站 |
| **Sprig: Show Actions** | 从列表里挑上面任何一个命令 |

**Sprig** 状态项在状态栏右侧的语言状态区（`{}` 图标）里，显示编译器版本、语言服务器是否在运行；在项目里，还会显示锁文件是最新的、过期的，还是缺失的。

- **只处理已保存的文件**：命令针对的是已保存的当前文件，并以最近的项目根目录作为工作目录。运行前请先保存项目里改过的文件。
- **不会自己下载依赖**：锁文件缺失或过期时，插件只会提示你。只有你主动执行 **Resolve Dependencies** 或 **New Project** 时，才会运行 `sprig resolve`。
- **Run 有运行限制**：**Sprig: Run** 适合很快就能跑完、不需要输入的程序。它不提供标准输入，程序结束后才显示输出，默认最多运行 120 秒、输出不超过 8 MB。时间上限可以用 `sprig.commandTimeoutSeconds` 修改。服务器和需要输入的程序请用 **Sprig: Run in Terminal**，它没有这些限制，用 Ctrl+C 或关掉终端来停止。

## 测试面板

带 `sprig.toml` 的项目里，`tests/` 下的每个 `.spr` 文件都会出现在 VS Code 的测试面板里。可以运行单个文件，也可以运行整个项目。

- 失败的测试会显示原因、出错的源码位置和程序输出。
- `tests/compile_fail/` 下的文件应该编译失败，同名的 `.expect.toml` 里写着预期的错误码。
- 每次运行的时间上限是 `sprig.testTimeoutSeconds`，默认 600 秒。

## 限制

- 重命名只支持局部变量和参数。没有调试器。
- 没有语言服务器时，大纲和跳转只按声明的名字查找，局部变量和参数的成员不会补全，悬停提示用的是已保存文件的信息。
- 代码有语法错误时不能格式化。
- 在受限模式（未信任的工作区）下，只保留高亮、大纲、代码片段和关键字补全。语言服务器、检查、运行、格式化、悬停提示和测试都需要信任这个工作区。
- 支持 Linux 和 macOS。Windows 只是预览。
- 用 Remote SSH 或容器开发时，SDK 和 JDK 要装在远程那一端。不支持浏览器版 VS Code 和虚拟文件系统。

完整的配置、开发和限制说明见[插件 README（英文）](https://github.com/ColinHouse/Sprig/blob/main/editors/vscode/README.md)。
