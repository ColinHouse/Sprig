# Gradle 集成

已经有一个用 Gradle 构建的 Java 项目？可以用官方的 `dev.sprig` 插件把 Sprig 代码加进去。插件会读取项目真实的编译 classpath，编译可选的 Java 桥接代码，运行 Sprig 的检查和测试，把生成的 Java 和运行时源码加进项目，并接入 Gradle 平常的构建流程。

::: warning 需要比 v0.5.0-beta.1 更新的版本
这个插件是在 v0.5.0-beta.1 发布之后才加入的，已发布的 SDK 里还没有。在下一个版本发布之前，可以克隆 Sprig 仓库，运行 `python3 scripts/build.py` 构建，然后把下面的 `SPRIG_HOME` 设成这个仓库的目录。
:::

## 接入插件

插件以源码的形式放在 SDK 的 `libraries/sprig-gradle/` 里，通过 Gradle 的 included build 机制使用，不需要 Gradle 插件门户的账号。先把环境变量 `SPRIG_HOME` 设成 SDK 的根目录（也可以用 `-PsprigHome=<路径>` 传给 Gradle），然后在 `settings.gradle` 里引入插件：

```groovy
pluginManagement {
    def home = providers.gradleProperty('sprigHome')
        .orElse(providers.environmentVariable('SPRIG_HOME')).get()
    includeBuild(new File(home, 'libraries/sprig-gradle'))
    repositories { gradlePluginPortal(); mavenCentral() }
}
```

再在 `build.gradle` 里启用它：

```groovy
plugins {
    id 'java'
    id 'dev.sprig'
}

sprig {
    targetSourceSet = 'main'
}
```

用 Loom 开发 Minecraft 模组时，把 `targetSourceSet` 设成 `client`，完整的项目见 [Fabric 模组](/guide/fabric)。

## 日常使用

照常使用 Gradle 命令：

```sh
./gradlew check
./gradlew build
```

- `check` 会运行 `sprigCheck` 和 `sprigTest`。
- `build` 会生成检查过的 Java 代码，连同运行时源码一起编译。
- Java 桥接代码默认放在 `src/sprigBridge/java/`。生成的 Java 在 `build/generated/sprig/<sourceSet>/java/`，可以打开查看。
- Sprig 的任务只读取 `sprig.lock`，不会联网。要更新依赖，需要明确运行 `./gradlew sprigResolve`。
- `./gradlew sprigInfo` 会列出当前用的编译器和运行时、目标 source set、桥接代码、生成目录、测试目录、锁文件状态和 classpath 的大小。

插件会按顺序查找 `sprig` 编译器：先看 `sprig { executable = ... }` 等显式设置，再看 `SPRIG_HOME`，然后是 `PATH`，最后是 `~/.sprig` 里安装的 SDK。

其他配置项、兼容性和各个任务的细节，见 [`dev.sprig` 插件说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-gradle/README.md)。这个插件只是省掉了每个项目都要重复写的构建配置，它不能代替 Gradle、Loom 或 Java，也不能说明 Sprig 的开发效率比 Java 高。
