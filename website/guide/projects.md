# 项目

项目就是一个包含 `sprig.toml` 的目录。单文件模式永久保留：
`sprig run hello.spr` 不需要任何 manifest。

## 目录与默认值

```text
project/
├── sprig.toml
├── src/
│   └── main.spr
└── build/
```

```toml
[project]
name = "hello"
version = "0.1.0"
language = "0.8"
```

`source` 默认为 `src`，`entry` 默认为 `src/main.spr`，都可以在 `[project]`
中覆盖。包名来自 manifest——`.spr` 源码不写 `package`。

## 创建与查看

```bash
sprig init          # 创建 sprig.toml 与 src/main.spr，绝不覆盖已有文件
sprig project       # 人类可读摘要
sprig project --json
```

`project --json` 输出 root、name、version、language、源码根、入口、binaries、
exports、manifest 与 lockfile 路径、lock 状态与声明的依赖，Agent 无需自己解析
TOML。

## 运行项目

```bash
sprig check         # 检查项目入口
sprig build
sprig run
sprig run --bin server
sprig run path/to/file.spr   # 显式文件优先于项目发现
```

项目发现会从当前目录向上查找。`[[bin]]` 声明命名入口：

```toml
[[bin]]
name = "server"
entry = "src/server.spr"
```

## 依赖

本地路径与 Git 的 Sprig 依赖已经可以真正解析：编辑 `sprig.toml` 后运行
`sprig resolve`。

```toml
[[dependency]]
name = "math"
path = "../math"
```

```toml
[[dependency]]
name = "math"
git = "https://example.com/math.git"
branch = "main"
```

- `name` 是包内导入别名；依赖自己的 `[project] name` 是独立的身份元数据。
- `sprig resolve` 生成确定性的 `sprig.lock`（建议提交）。`check`/`build`/`run`
  拒绝缺失或过期的 lock，并且绝不会自己移动 Git 分支——只有 `resolve` 会。
- Git 依赖锁定到精确 commit SHA；分支后续移动不会改变已锁定的构建。
- 用 `import "@math/vector.spr" as vector` 导入被 export 的模块；只有
  `exports` 列出的模块可被外部导入，路径会 canonicalize，无法逃出依赖源码根。
- `--offline` 只使用 Git 缓存（`~/.sprig/git`），缓存缺 revision 时以
  `SPR-DEP-OFFLINE` 明确失败。
- 依赖环与重复别名都会被结构化诊断拒绝。

**Maven/JVM 依赖尚未实现**：声明 `[[jvm]]` 会以 `SPR-DEP-MAVEN` 失败，第三方
jar 目前仍需显式 `--classpath`。见[已知限制](/reference/known-limitations)。
