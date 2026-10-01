# Sprig + Fabric/Loom：把构建接线放回工具层

一次 Quest Board dogfood 表明，Sprig 能承担模型、状态、持久化和应用逻辑；当时 hybrid 方案真正反复增加的成本在 Gradle/Loom：编译 Java bridge、导出 client classpath、生成和注册 Java、加入 runtime、接入 `check`。这不是 Sprig 语法缺失，所以新项目应从 first-party `dev.sprig` 插件和 Fabric starter 开始。

它们把重复的 build wiring 放到可复用工具中，让项目只保留清晰的 Java host boundary 和 Sprig 应用逻辑。完整插件契约见[Gradle 集成指南](/guide/gradle)，starter 文件见 [`libraries/sprig-fabric`](https://github.com/ColinHouse/Sprig/tree/main/libraries/sprig-fabric)。

## 从 SDK 模板开始

Sprig SDK 随附模板和 `libraries/sprig-gradle` included build。设置 `SPRIG_HOME` 指向解压后的 SDK 根目录：

```sh
cp -R "$SPRIG_HOME/libraries/sprig-fabric/template" ./my-mod
cd my-mod
./gradlew check
./gradlew build
./gradlew runClient
```

模板的 settings 会从 SDK 加载插件，不需要 clone Sprig 仓库或配置个人绝对路径。`check` 会运行 Sprig 静态检查、Sprig tests 和正常 Java checks；`build` 生成并编译 Sprig Java、runtime 和 bridge；`runClient` 使用同一份生成输出。

消费者的 Sprig 配置只有目标 source set：

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'dev.sprig'
}

sprig {
    targetSourceSet = 'client'
}
```

Fabric dependencies、split source sets、mod entrypoint 和 Java release 仍是普通 Loom 配置；Sprig 插件负责 bridge 编译、真实 classpath、generated source、runtime source 和 Gradle lifecycle。

## 项目边界

| 路径 | 职责 |
|---|---|
| `src/main.spr` | Sprig 领域状态与逻辑 |
| `tests/*.spr` | `sprigTest` 执行的项目测试 |
| `src/sprigBridge/java/` | Sprig 调用的窄 Java interface |
| `src/client/java/` | Fabric client initializer 与回调注册 |
| `build/generated/sprig/client/java/` | 可检查的生成 Java |

Gradle/Loom 拥有依赖、source sets、javac 和 jar；Sprig 拥有类型检查、生成、lock 验证与 Sprig tests；插件只负责两边的集成。没有自动 dependency resolution：`check`/`build` 消费当前 `sprig.lock`，只有显式 `./gradlew sprigResolve` 会更新它。

## 验证范围

Starter 当前实测组合：Minecraft 26.3、Fabric Loader 0.19.5、Fabric API 0.161.0+26.3、Fabric Loom 1.18.2、Gradle 9.7.1、OpenJDK 26.0.1（`javac --release 25`）。这是一组经过构建测试的组合，不是对所有 Fabric 或 Loom 版本的兼容承诺。

早期 Java-only / Sprig hybrid Quest Board 比较中，两版都能构建运行；Java-only 版 production 为 707 LOC，hybrid 为 760 LOC，其中 Sprig 应用代码约 470 LOC。该比较只观察了一个项目，没有测量时间或 token，也不是受控生产力基准；在手工接线的条件下选择了 Java-only。插件移除的是已观察到的 Gradle/Loom 接线负担，**不证明 Sprig 比 Java 更高效**。它让后续比较能在较少 integration confounder 的情况下进行。

细节与边界见 [`libraries/sprig-fabric/README.md`](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-fabric/README.md) 和 [`libraries/sprig-gradle/README.md`](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-gradle/README.md)。
