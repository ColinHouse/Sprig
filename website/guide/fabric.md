# Fabric / JVM 框架集成

本页把一次真实 Fabric/Loom mod dogfood 提炼成可复用的方法：**host 构建系统是依赖与
classpath 的权威，Sprig 负责业务语义，只有 Sprig 当前无法表达的 JVM 形状才交给一个
窄 Java adapter。**

Fabric 是已验证案例，方法本身不绑定 Fabric。Gradle 插件、Maven 库或自研 JVM 框架都可
套用同样的分工：导出 host classpath → `sprig api`/`sprig wrap` → 生成 Java 交还 host
构建 → `conform` 回调 → 打包校验。

## 已验证版本（dogfood 事实）

| 组件 | 实测版本 |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Fabric Loom | 1.18.2（构建声明 `1.18-SNAPSHOT`） |
| Gradle wrapper | 9.7.1 |
| Sprig | 0.4.0-alpha.1 源码修订 `ede71f7a` |
| 编译与运行 JDK | OpenJDK 26.0.1（`javac --release 25`，classfile major 69） |

明确未验证的内容：

- **JDK 25 runtime 没有实测**。是 JDK 26 运行了以 Java 25 为目标的 mod；这不等于
  独立验证过 JDK 25 runtime。mod 元数据要求 Java 25 或更新。
- Loom 版本在构建声明里写 `1.18-SNAPSHOT`，实测解析到 1.18.2。
- 这不是 Fabric 的普遍结论：下面的 gradle 接线、`fabric.mod.json` 入口是
  Fabric-specific；其余模式适用于其他 JVM 框架。

## 三层分工

```text
host build（Gradle/Loom/Maven）
  依赖解析、classpath、最终 artifact —— 权威来源
        │ 导出真实 classpath
Sprig 源码
  模型、状态、持久化、命令策略、回调实现 —— 公开语义
        │ 只在形状受限处
窄 Java adapter
  第三方 wildcard builder、不可表达的接口 —— 机械翻译，不放业务逻辑
```

先看 `sprig api`，再决定业务代码放哪一层。dogfood 里模型、tick 统计、JSON、文件路径、
生命周期回调全部在 Sprig；Java 只出现在 Brigadier 命令树这一个形状受限的边界。

## 1. 让 host 构建系统导出 classpath

不要手工维护 Minecraft/Fabric jar。让 host 构建把**它实际编译使用的** classpath 写成
文件，Sprig 命令直接消费这一份：

```groovy
// build.gradle：把 host 解析出的 compile classpath 交给 Sprig。
def sprigCompiler = file('<path-to-sprig>/bin/sprig')  // built or installed SDK launcher
def sprigSourceDir = file('src/main/sprig')
def sprigBridgeSourceDir = file('src/bridge/java')
def sprigGeneratedDir = layout.buildDirectory.dir('generated/sprig')
def sprigBridgeClassesDir = layout.buildDirectory.dir('classes/java/sprigBridge')
def sprigClasspathOutput = layout.buildDirectory.file('sprig/compile-classpath.txt')
def sprigMainCompileClasspath = sourceSets.main.compileClasspath + files(sprigBridgeClassesDir)

tasks.register('compileSprigBridge', JavaCompile) {
    inputs.files(fileTree(sprigBridgeSourceDir) { include '**/*.java' })
    classpath = configurations.compileClasspath
    destinationDirectory = sprigBridgeClassesDir
    options.release = 25
    source(fileTree(sprigBridgeSourceDir) { include '**/*.java' })
}

tasks.register('sprigClasspath') {
    dependsOn('compileSprigBridge')
    inputs.files(sprigMainCompileClasspath)
    outputs.file(sprigClasspathOutput)
    doLast {
        def output = sprigClasspathOutput.get().asFile
        output.parentFile.mkdirs()
        output.text = sprigMainCompileClasspath.asPath
    }
}
```

Loom 案例里，这个导出的 classpath 是 `sourceSets.main.compileClasspath` 加上已编译的
bridge classes。换到别的框架时原则相同：**导出 host 真正用来编译的那份，不要另配一份**。

## 2. 用真实 classpath 查询 API

```bash
CP="$(cat build/sprig/compile-classpath.txt)"
bin/sprig api net.fabricmc.api.ModInitializer --json --classpath "$CP"
```

`--classpath` 与 `check`/`build`/`run`/`api`/`wrap`/`doctor` 共用同一解析路径，也接受重复
传入或平台路径分隔符；输出包含 `interopLevel`、`interopReasonCodes`、`adaptation` 与
递归泛型形状。对 Fabric 回调，先用它确认签名是具体可调用的，再写 Sprig。

## 3. 需要时用 `sprig wrap` 导入生态类

小范围包装一个类，而不是整类抄进来：

```bash
CP="$(cat build/sprig/compile-classpath.txt)"
bin/sprig wrap <fully.qualified.JavaClass> --member <name> \
  --out src/main/sprig/wrapped.spr --classpath "$CP" --force --json
```

生成结果是普通可编辑 `.spr`，默认不覆盖已有文件（需要 `--force`），生成前会在同一
classpath 下检查；`--json` 报告 `generatedMembers`/`skippedMembers`，跳过项带稳定
`reasonCodes`。只保留真正需要的方法，其余删掉即可——它只是起点，不是运行时依赖。
细节见 [wrapper 生成器（英文契约）](/en/reference/WRAP)。

## 4. 把生成的 Java 交还 host 构建

`build --emit-java-only` 只做静态检查并输出 Java，不调用 `javac`；由 host 的编译任务
编译生成源码、Sprig runtime 与 bridge：

> 下面的片段沿用第 1 节同一份 `build.gradle` 中的变量。

```groovy
tasks.register('sprigGenerate', Exec) {
    dependsOn('compileSprigBridge')
    inputs.files(fileTree(sprigSourceDir) { include '**/*.spr' })
    inputs.files(sprigMainCompileClasspath)
    outputs.dir(sprigGeneratedDir)
    doFirst {
        commandLine([sprigCompiler, 'build', new File(sprigSourceDir, 'mod_entry.spr').absolutePath,
            '--emit-java-only', '-d', sprigGeneratedDir.get().asFile.absolutePath,
            '--classpath', sprigMainCompileClasspath.asPath])
    }
}

sourceSets.main.java.srcDir(sprigGeneratedDir.map { it.dir('java') })
sourceSets.main.output.dir(sprigBridgeClassesDir, builtBy: 'compileSprigBridge')

tasks.named('compileJava') { dependsOn('compileSprigBridge', 'sprigGenerate') }
tasks.matching { it.name == 'sourcesJar' }.configureEach {
    dependsOn('compileSprigBridge', 'sprigGenerate')
}
```

三个要点（dogfood 实测的接线）：

1. 生成的 Java 目录是 main source set 的 source dir，由 host 的 `compileJava` 编译。
2. bridge 的 classes 同时进入**编译 classpath** 和 **runtime output**（
   `sourceSets.main.output`）。dogfood 第一次 `runClient` 失败正是只做了前者：
   `NoClassDefFoundError` 指向 bridge 类在运行时缺失。**编译期可见 ≠ 打包时包含。**
3. Gradle 9 会校验隐式依赖：读取生成目录的 `sourcesJar` 必须显式依赖生成任务。

不要迷信第一次成功；用 clean 路径复核（见第 7 节）。

## 5. 用 `conform` 实现 Java 回调

具体签名的回调不需要 adapter。写一个具名 Sprig 类，方法签名与 Java 接口一致，然后
声明 conformance：

```sprig
import net.fabricmc.api.ModInitializer as ModInitializer

class ModEntry:
    func onInitialize() -> Unit:
        print("mod loaded")

conform ModEntry to ModInitializer
```

dogfood 中 mod 入口、server started/stopping、end-tick、player join/disconnect 全部在
Sprig 里直接 conform。注意：

- Java 引用结果保守可空，事件字段也必须在 `.register(...)` 前判空；
- 静态 enum 风格字段（例如 world resource 常量）同样按可空处理；
- `conform` v1 只支持 Java interface、非泛型源类/目标接口、无重载抽象方法、无改名或
  参数适配；`Short`/`Byte`/`Character` 槽位不可表达。见
  [conform 契约（英文）](/en/reference/JVM_CONFORMANCE)。

Fabric 入口类名是 `sprig.user.$ModEntry`，写在 `fabric.mod.json` 的 entrypoint 里。

## 6. 什么时候写窄 Java adapter

当 `sprig api` 明确说需要的成员不可用时，才加 adapter，而且只加那一小块。dogfood 的
唯一边界是 Brigadier：`ArgumentBuilder.then(ArgumentBuilder<S, ?>)` 含 Java wildcard，
`api` 标记为 `wildcard-unsupported`，Sprig 无法表达嵌套 builder 树。处理方式：

- Java 侧：一个小的命令树 builder + 一个非泛型 action 接口；
- Sprig 侧：命令决策、状态、文案全部留在 Sprig 类里，`conform` 到该接口；
- Java 只把 Brigadier 回调翻译成接口调用，并投递返回的文本。

**不要**因为一个形状受限就整段用 Java 重写业务逻辑。如果 adapter 开始变大，先回到
`sprig api` 看是否有更窄的边界；宽 wildcard 支持是未来语言级问题，不属于集成工作。

## 7. 打包与验收清单

任何 host 框架都适用：

1. host 构建导出它真正编译用的 classpath；
2. 生成 Java 由 host 编译，生成目录是 source dir；
3. 最终 artifact 同时包含：生成的模块类（如 `sprig/user/**`）、`sprig/runtime/**`、
   bridge 类；
4. `clean` 后从零重建成功；
5. 真实 run/package 路径被跑过一次（不是一个 `javac` 通过就宣布完成）。

Fabric/Loom 额外步骤：

```bash
./gradlew clean
./gradlew compileJava          # 从零重新生成并编译
./gradlew runClient            # 进入单人世界验证回调与命令
./gradlew build                # remap 出最终 mod jar
jar tf build/libs/<artifact>.jar | grep -E "sprig/user|sprig/runtime|your/bridge/"
```

dogfood 的最终 jar 中确认包含 `sprig/user/$ModEntry.class`、生成的模块类、bridge 类与
`sprig/runtime/**`。

## 8. Dogfood 证据（单个项目的局部测量）

一次真实单人存档纵向切片（创建/进入/自动保存/退出/重进/正常关闭/构建）通过：

| 指标 | 数值 | 说明 |
|---|---:|---|
| 手写 Sprig 功能代码 | 440 行 | 模型、持久化、入口 |
| 手写 Java glue | 52 行 | 一个 Brigadier builder + 一个 action 接口 |
| 直接 `conform` 的回调 | 生命周期、tick、join/disconnect | 无 adapter |
| 窄 wildcard adapter | 1 处 | Brigadier `then` |
| `conform` 的 mod 入口与命令注册 | 直接使用 | — |

这些是**该项目的局部测量，不是普遍比例**。更大的、wildcard 密集的第三方 API 会提高
Java 占比；把这组数字当作"这种形状可行"的证据，而不是对任意 mod 的预测。

## 9. Fabric-specific 与通用模式

| 内容 | 归类 |
|---|---|
| `fabric.mod.json` entrypoint、Loom `runClient`/`remapJar` | Fabric-specific |
| Minecraft/Fabric 版本与 `--release 25` 元数据 | Fabric/该 dogfood specific |
| host 构建导出 compile classpath 给 `sprig api`/`wrap` | 通用 |
| `build --emit-java-only` + host 编译生成源码/runtime | 通用 |
| bridge classes 必须进入 runtime output 与 artifact | 通用（Gradle/source set 语言） |
| `conform` 具体回调接口 | 通用 |
| wildcard builder 形状用薄 adapter + 非泛型 action 接口 | 通用模式（本例是 Brigadier） |
| `sourcesJar` 需要显式依赖生成任务 | Gradle 9 通用 |

## 10. 保留的限制

- **Java wildcard / Brigadier builder**：当前 interop profile 不支持 wildcard
  形状，需要薄 adapter 或未来独立设计的 typed helper；`sprig api` 会先告诉你。
- **Java nullable boundary**：Java 引用结果与 static 字段一律保守可空，必须显式判空；
  这是安全边界，不是缺陷。
- **host build 必须包含 generated/runtime/bridge outputs**：只放进编译 classpath
  不够，artifact 与 run 路径都要包含。
- **JDK 事实**：该 dogfood 用 JDK 26.0.1 编译/运行，`javac --release 25`；**JDK 25
  runtime 未验证**。

下一步：读 [JVM 互操作](/guide/jvm-interop) 了解类型映射与 adapter/数组/具体泛型，
读 [工具与 JSON](/guide/tooling) 了解 `api`/`wrap`/`test` 的 JSON 输出。
