# Fabric 模组：用 Sprig 写 Minecraft 模组逻辑

Sprig 可以负责一个模组里的数据模型、状态、存档和业务逻辑，跟 Minecraft、Fabric 打交道的部分则留给 Java。新项目最好直接从 SDK 自带的 Fabric 模板开始，模板已经配好了官方的 `dev.sprig` Gradle 插件。

::: warning 需要比 v0.5.0-beta.1 更新的版本
模板和 Gradle 插件都是在 v0.5.0-beta.1 发布之后才加入的，已发布的 SDK 里还没有。在下一个版本发布之前，可以克隆 Sprig 仓库，运行 `python3 scripts/build.py` 构建，然后把下面的 `SPRIG_HOME` 设成这个仓库的目录。
:::

## 从模板开始

SDK 里带着这个模板，以及它用到的 `libraries/sprig-gradle` 插件。把 `SPRIG_HOME` 设成 SDK 的根目录，然后运行：

```sh
cp -R "$SPRIG_HOME/libraries/sprig-fabric/template" ./my-mod
cd my-mod
./gradlew check
./gradlew build
./gradlew runClient
```

模板会从 SDK 里加载插件，不需要克隆 Sprig 仓库，也不用写死你电脑上的绝对路径。

- `check` 运行 Sprig 的静态检查、Sprig 测试和普通的 Java 检查。
- `build` 生成并编译 Sprig 对应的 Java 代码、运行时和桥接代码。
- `runClient` 用同一份生成结果启动游戏客户端。

`build.gradle` 里和 Sprig 有关的配置只有一项，就是指定 source set：

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'dev.sprig'
}

sprig {
    targetSourceSet = 'client'
}
```

Fabric 依赖、拆分的 source set、模组入口和 Java 版本，都还是普通的 Loom 配置。Sprig 插件负责编译桥接代码、提供真实的 classpath、生成源码、加入运行时源码，并把这些接进 Gradle 的构建流程。

## 各部分的分工

| 路径 | 放什么 |
|---|---|
| `src/main.spr` | 用 Sprig 写的状态和逻辑 |
| `tests/*.spr` | 项目测试，由 `sprigTest` 运行 |
| `src/sprigBridge/java/` | 一层很薄的 Java 接口，供 Sprig 调用 |
| `src/client/java/` | Fabric 客户端的入口和回调注册 |
| `build/generated/sprig/client/java/` | 生成的 Java，可以打开查看 |

Gradle 和 Loom 负责依赖、source set、javac 和打包 jar；Sprig 负责类型检查、生成代码、校验锁文件和运行 Sprig 测试；插件把两边接起来。依赖不会自动更新：`check` 和 `build` 只读当前的 `sprig.lock`，只有明确运行 `./gradlew sprigResolve` 才会更新它。

## 测试过的版本

模板在这组版本上测试过：Minecraft 26.3、Fabric Loader 0.19.5、Fabric API 0.161.0+26.3、Fabric Loom 1.18.2、Gradle 9.7.1，以及 OpenJDK 26.0.1（编译目标为 Java 25）。这只是实际测过的一组组合，不代表所有 Fabric 或 Loom 版本都能用。

## 这个模板是怎么来的

之前用一个任务板（Quest Board）模组做过一次对比：同样的功能，一版只用 Java 写，另一版用 Java 加 Sprig。两版都能构建运行。纯 Java 版的产品代码有 707 行；混合版有 760 行，其中大约 470 行是 Sprig 写的应用逻辑。

那次真正反复费功夫的是 Gradle 和 Loom 的配置：编译 Java 桥接代码、导出客户端的 classpath、生成并注册 Java 代码、加入运行时、接上 `check`。这些都不是 Sprig 语言本身缺了什么，所以后来做成了插件和这个模板。

这只是一个项目的经验，没有统计时间或 token，不是严格的效率对比。而且在当时需要手工配置的条件下，最后选的是纯 Java 版。插件去掉的是那次看到的配置负担，**并不能证明 Sprig 比 Java 效率更高**。它只是让以后的对比少一些和语言无关的干扰。

更多细节和限制见 [`libraries/sprig-fabric/README.md`（英文）](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-fabric/README.md) 和 [`libraries/sprig-gradle/README.md`（英文）](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-gradle/README.md)。
