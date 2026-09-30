# VS Code 插件

本地预览插件提供 `.spr` 语法高亮、保存检查、错误解释，以及 **Check / Run /
Build / Show Generated Java** 命令。静态语义由现有 Sprig CLI 负责。

## 安装

在仓库的 `editors/vscode/` 目录运行：

```sh
npm ci
npm run package
```

使用 VS Code 扩展面板的 **Install from VSIX…** 安装生成的
`dist/sprig-language-0.1.0.vsix`。预览插件尚未发布到 Marketplace，也不在
Beta SDK ZIP 中。

语法高亮不需要编译器。检查和运行需要另外安装
[SDK](https://github.com/ColinHouse/Sprig/releases/tag/v0.5.0-beta.1) 和 JDK17+，
并把 `sprig.compilerPath` 设置为 SDK 的 `bin/sprig`。默认先搜索 PATH，再搜索
祖先目录中的源码构建 `bin/`。

## 使用

保存 `.spr` 文件，从命令面板执行 **Sprig: Check**。Problems 显示对应文件、
位置、错误代码和期望/实际类型。默认保存后检查；项目同时检查入口图和当前文件。
锁文件缺失或过期时，请显式运行 CLI resolve，插件不会自动下载依赖。

**Sprig: Run** 编译并运行当前已保存文件，完成后显示输出。第一版支持有限的
非交互程序，默认上限为 120 秒和 8 MB。**Show Generated Java** 静态检查后
生成 Java，在旁边打开源码，不执行 javac。

未信任工作区保留高亮，编译器命令需要工作区信任。Linux/macOS 是支持目标，
Windows 为预览。Remote SSH/容器需要在工作区宿主安装 SDK/JDK；不支持纯浏览器
VS Code 或虚拟文件系统。尚未提供补全、跳转、格式化或 LSP。

[完整配置、开发与限制说明](https://github.com/ColinHouse/Sprig/blob/main/editors/vscode/README.md)。
