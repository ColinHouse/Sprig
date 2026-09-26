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

## 依赖（尚未实现）

manifest 接受依赖声明，`sprig deps --json` 会如实列出：

```toml
[[dependency]]
name = "math"
path = "../math"

[[jvm]]
group = "com.fasterxml.jackson.core"
artifact = "jackson-databind"
version = "2.18.4"
```

依赖解析、`sprig.lock`、离线缓存和 `@name/...` 导入都**尚未实现**。
`sprig deps` 会以 `resolved: false` 报 `SPR-PROJECT-UNSUPPORTED`；目前 JVM
jar 仍需 `--classpath`。见[已知限制](/reference/known-limitations)。
