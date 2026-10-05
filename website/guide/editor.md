# VS Code 插件

插件提供 `.spr` 语法高亮、保存时检查、一键运行、查看生成的 Java，以及错误码解释。检查和运行都是调用你装好的 `sprig` 命令完成的，插件自己不做类型检查。

插件还没有发布到 VS Code 插件市场，也没有放进 SDK 的压缩包，需要自己从源码打包安装。

## 安装

在 Sprig 仓库的 `editors/vscode/` 目录里运行：

```sh
npm ci
npm run package
```

会生成 `dist/sprig-language-0.1.0.vsix`。在 VS Code 的扩展面板里点 `…` → **Install from VSIX…** 选中它，或者在命令行运行：

```sh
code --install-extension dist/sprig-language-0.1.0.vsix
```

装好以后，语法高亮马上就能用，不需要 Java 和编译器。

要检查和运行代码，还需要装好 JDK 17+ 和 [Sprig SDK](/guide/getting-started)，然后在设置里把 **Sprig: Compiler Path**（`sprig.compilerPath`）设成 SDK 里的 `bin/sprig`。不设置的话，插件会先在 `PATH` 里找 `sprig`，再沿着上级目录查找源码构建出来的 `bin/sprig`。

## 能做什么

打开 `.spr` 文件，在命令面板或编辑器右键菜单里使用这些命令：

| 命令 | 作用 |
|---|---|
| **Sprig: Check** | 检查当前文件，错误显示在「问题」面板里 |
| **Sprig: Run** | 编译并运行当前文件，运行结束后在输出面板显示结果 |
| **Sprig: Run in Terminal** | 在终端里运行，适合服务器和需要输入的程序 |
| **Sprig: Build** | 生成 Java 并用 javac 编译 |
| **Sprig: Show Generated Java** | 检查后生成 Java，在旁边打开，不调用 javac |
| **Sprig: Show Capabilities** | 显示当前编译器的功能清单 |
| **Sprig: Explain Diagnostic** | 解释一个错误码，也可以从错误旁边的灯泡进入 |

- **保存时检查**：默认开启，可以用 `sprig.checkOnSave` 关掉。在带 `sprig.toml` 的项目里，会检查项目入口导入到的所有文件和当前文件，所以没被导入的模块也能得到检查。
- **只处理已保存的文件**：命令针对的是已保存的当前文件，并以最近的项目根目录作为工作目录。运行前请先保存项目里改过的文件。
- **不自动下载依赖**：锁文件缺失或过期时，插件只会提示你，需要你在命令行运行 `sprig resolve`。
- **Run 有运行限制**：**Sprig: Run** 适合很快就能跑完、不需要输入的程序。它不提供标准输入，程序结束后才显示输出，默认最多运行 120 秒、输出不超过 8 MB。时间上限可以用 `sprig.commandTimeoutSeconds` 修改。服务器和需要输入的程序请用 **Sprig: Run in Terminal**，它没有这些限制，用 Ctrl+C 或关掉终端来停止。

## 限制

- 没有自动补全、重命名、跳转、格式化、调试，也没有语言服务器（LSP）。
- 高亮只看词法：首字母大写的名字会被当成类型来着色，不做真正的名字解析。
- 在受限模式（未信任的工作区）下只保留高亮，检查和运行需要信任这个工作区。
- 支持 Linux 和 macOS。Windows 只是预览，取消运行时可能停不掉子进程。
- 用 Remote SSH 或容器开发时，SDK 和 JDK 要装在远程那一端。不支持浏览器版 VS Code 和虚拟文件系统。

完整的配置、开发和限制说明见[插件 README（英文）](https://github.com/ColinHouse/Sprig/blob/main/editors/vscode/README.md)。
