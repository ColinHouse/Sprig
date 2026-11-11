# Gradle 集成

已经有一个用 Gradle 构建的 Java 项目？可以用官方的 `dev.sprig` 插件把 Sprig 代码加进去。插件会读取项目真实的编译 classpath，编译可选的 Java 桥接代码，运行 Sprig 的检查和测试，把生成的 Java 连同项目自己的一份运行时加进项目，并接入 Gradle 平常的构建流程。

::: tip 需要 v0.6.0-beta.1 或更新的版本
这个插件从 v0.6.0-beta.1 开始随 SDK 一起发布。如果 `sprig version` 显示的版本更旧，先运行 `sprig upgrade`。
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

用 Loom 开发 Minecraft 模组时，物品、方块这类两端都要用的内容放进 `main`，只在客户端运行的模组才设成 `client`。完整的项目见 [Fabric 模组](/guide/fabric)。

## 日常使用

照常使用 Gradle 命令：

```sh
./gradlew check
./gradlew build
```

- `check` 会运行 `sprigCheck` 和 `sprigTest`。
- `build` 会生成检查过的 Java 代码和项目自己的一份运行时，一起编译。
- Java 桥接代码默认放在 `src/sprigBridge/java/`。生成的 Java 在 `build/generated/sprig/<sourceSet>/java/`，可以打开查看。
- Sprig 的任务只读取 `sprig.lock`，不会联网。要更新依赖，需要明确运行 `./gradlew sprigResolve`。
- `./gradlew sprigInfo` 会列出当前用的编译器和运行时、目标 source set、桥接代码、生成目录、生成的包名、测试目录、锁文件状态和 classpath 的大小。

插件会按顺序查找 `sprig` 编译器：先看 `sprig { executable = ... }` 等显式设置，再看 `SPRIG_HOME`，然后是 `PATH`，最后是 `~/.sprig` 里安装的 SDK。

## 在 Java 里用 Sprig 写的类

Sprig 生成的类都在以项目名命名的包里。`sprig.toml` 里写的是 `name = "ordinary-project"`，包就是 `sprig.user.ordinary_project`（Java 包名里不能出现的字符都换成 `_`）。项目里的 Java 代码带着这个包名引用它们：

```java
import sprig.user.ordinary_project.$Counter;

System.out.println(new $Counter().current());
```

记不住包名就运行 `./gradlew sprigInfo`，输出里有一行 `Generated package`。

项目用到的 Sprig 运行时也在这个包下面（`sprig.user.ordinary_project.runtime`），每个项目各带一份。所以两个用 Sprig 构建的 jar 没有任何同名的类，放进同一个 JVM 也互不影响，比如同一个游戏里的两个 Fabric 模组。

有两件事要记住：

- 改了项目名，包名就跟着变。`javac` 会指出每一处还在用旧包名的 Java 代码。
- 两个这样的 jar 之间要用 Java 的类型传值（`String`、数字、你自己写的接口）。它们各有一份运行时，一个项目的 Sprig `List` 不是另一个项目的 `List`。

::: tip 这是 `main` 分支上的行为
v0.8.0-beta.1 和更早的 SDK 把所有项目的类都生成到 `sprig.user`，运行时在 `sprig.runtime`。从这些版本升级时，把 Java 代码里的 `sprig.user.` 改成 `sprig.user.<项目名>.`。
:::

## 从 Java 调用时，顶层的 `let` 何时初始化 {#calling-from-java}

用 `sprig run` 运行程序时，模块顶层的语句（包括顶层 `let`、`var` 的初始值）在程序一启动就运行。Java 项目里没有这一步：Java 是直接调用某个函数，或者直接 `new` 一个对象。比如 `src/store.spr` 是这样一个模块：

<<< @/snippets/guide/java_caller_store.spr

Java 直接这样用它，前面不用先调用什么“初始化”方法：

```java
sprig.user.ordinary_project.$M_store.fn$remember("x");
System.out.println(sprig.user.ordinary_project.$M_store.fn$seen());   // 1
```

Java 第一次调用一个模块的函数，或者第一次 `new` 一个带方法的类，这个模块的顶层语句就会先运行：先是它导入的模块的，再是它自己的，而且只运行一次。所以不管从哪里进来，Sprig 代码读到的顶层变量都已经有值了。

有三件事要留意：

- **顶层语句出错，失败的就是那第一次调用。** 抛出的是 Sprig 的 `Error` 这类非受检异常时，Java 收到的就是它本身；抛出的是 Java 的受检异常时，Java 收到的是一个 `IllegalStateException`，原来的异常是它的 cause。顶层语句不会再运行第二遍，所以把它当作致命错误处理。
- **第一次调用要发生在其他线程用到这些代码之前。** 顶层语句在发起第一次调用的线程上运行，只运行一次。这期间别的线程调进来不会等它运行完，可能读到还没有值的变量。框架的初始化入口（比如 Fabric 的 `ModInitializer`）就是做第一次调用的合适位置。
- **别在 Java 里直接读顶层变量对应的字段**，比如 `$M_store.names`。读字段不会让顶层语句运行，在第一次调用之前它还是 `null`。要读就通过函数读。

::: tip 这是 `main` 分支上的行为
v0.9.0-beta.1 和更早的 SDK 不会这样做：Java 直接调用函数时，顶层语句还没运行过，顶层变量是 `null` 或 `0`。用这些版本时，在第一次调用之前先调用一次模块类的 `$init()`，比如 `sprig.user.ordinary_project.$M_store.$init()`（它声明了 `throws Exception`）。
:::

其他配置项、兼容性和各个任务的细节，见 [`dev.sprig` 插件说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-gradle/README.md)。这个插件只是省掉了每个项目都要重复写的构建配置，它不能代替 Gradle、Loom 或 Java，也不能说明 Sprig 的开发效率比 Java 高。
