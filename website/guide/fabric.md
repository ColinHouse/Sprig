# Fabric 模组：用 Sprig 写 Minecraft 模组逻辑

Sprig 可以负责一个模组里的数据模型、状态、存档和业务逻辑，跟 Minecraft、Fabric 打交道的部分则留给 Java。新项目最好直接从 SDK 自带的 Fabric 模板开始，模板已经配好了官方的 `dev.sprig` Gradle 插件。

::: tip 需要 v0.6.0-beta.1 或更新的版本
模板和 Gradle 插件从 v0.6.0-beta.1 开始随 SDK 一起发布。如果 `sprig version` 显示的版本更旧，先运行 `sprig upgrade`。
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
    targetSourceSet = 'main'
}
```

Fabric 依赖、拆分的 source set、模组入口和 Java 版本，都还是普通的 Loom 配置。Sprig 插件负责编译桥接代码、提供真实的 classpath、生成源码、加入模组自己的一份运行时，并把这些接进 Gradle 的构建流程。

## 各部分的分工

| 路径 | 放什么 |
|---|---|
| `src/main.spr` | 用 Sprig 写的状态和逻辑：tick 计数器，以及一个继承 `Item` 的 `Wand` 物品类 |
| `tests/*.spr` | 项目测试，由 `sprigTest` 运行 |
| `src/sprigBridge/java/` | 一层很薄的 Java 接口，供 Sprig 调用；Sprig 类也可以用 `conform` 直接实现 Java 接口或继承 Java 类（见 [conformance 参考（英文）](/en/reference/jvm/conformance)） |
| `src/main/java/` | Fabric 主入口，负责注册用 Sprig 写的法杖物品 |
| `src/client/java/` | Fabric 客户端的入口和回调注册 |
| `build/generated/sprig/main/java/` | 生成的 Java，可以打开查看 |

Gradle 和 Loom 负责依赖、source set、javac 和打包 jar；Sprig 负责类型检查、生成代码、校验锁文件和运行 Sprig 测试；插件把两边接起来。依赖不会自动更新：`check` 和 `build` 只读当前的 `sprig.lock`，只有明确运行 `./gradlew sprigResolve` 才会更新它。

## 改项目名，以及和别的 Sprig 模组共存

Fabric 把所有模组放在同一个类加载器里，所以两个模组不能有同名的类。Sprig 往你的 jar 里放的东西因此都在以项目名命名的包里：生成的类在 `sprig.user.<项目名>`，模组自己的一份运行时在 `sprig.user.<项目名>.runtime`。模板的项目叫 `sprig-fabric-counter`，它的 Java 入口就这样调用 Sprig：

```java
CounterActions counter = sprig.user.sprig_fabric_counter.$M_main.fn$new_counter();
```

- **改了 `sprig.toml` 里的 `name`，Java 里的包名也要改。** `javac` 会指出 `SprigItems.java` 和 `SprigCounterClient.java` 里还在用旧包名的两处调用，`./gradlew sprigInfo` 会打印新的包名。
- **每个模组用自己的项目名。** 两个模组的 `name` 相同，包名就相同。
- **模组之间用 Java 的类型传值。** 每个模组各有一份运行时，一个模组的 Sprig `List` 不是另一个模组的。传 `String`、数字，或者双方都认识的 Java 接口。

::: tip 这是 `main` 分支上的行为
v0.8.0-beta.1 和更早的 SDK 把所有模组的类都生成到 `sprig.user`，运行时在 `sprig.runtime`，所以两个 Sprig 模组装进同一个游戏时会有同名的类。
:::

## 在模组里用顶层的 `let` 和 `var`

游戏里没有“运行 `main.spr`”这一步。Fabric 调用 Java 入口，Java 入口调用 `$M_main.fn$new_wand(...)`，之后 Minecraft 再调用法杖的 `use`。Java 第一次调进 Sprig 时，`main.spr` 和它导入的模块的顶层语句会先运行，而且只运行一次。所以在模块顶层写下这一行：

```sprig
let cooldown_ticks = 20
```

模组里的每个函数和方法读到的都是 `20`，和在 `sprig test` 里一样。

- **第一次调用放在模组的初始化入口里**，模板就是这么做的。这样游戏的其他线程调进来之前，顶层语句已经运行完了；它们还在运行的时候，别的线程调进来是不会等的。
- **顶层语句抛出错误，失败的是触发它的那次调用**，Fabric 会把它报在对应的入口上。顶层语句不会再运行第二遍。
- **顶层变量通过 Sprig 函数来读**，别在 Java 里直接读 `$M_main.cooldown_ticks` 这样的字段：读字段不会让顶层语句运行。

更完整的说明见 [Gradle 集成](/guide/gradle#calling-from-java)。

::: tip 这是 `main` 分支上的行为
v0.9.0-beta.1 和更早的 SDK 里，Java 入口直接调用函数时顶层语句不会运行，顶层变量是 `null` 或 `0`。用这些版本时，先在入口里调用一次 `$M_main.$init()`（它声明了 `throws Exception`）。
:::

## 测试过的版本

模板在这组版本上测试过：Minecraft 26.3、Fabric Loader 0.19.5、Fabric API 0.161.0+26.3、Fabric Loom 1.18.2、Gradle 9.7.1，以及 OpenJDK 26.0.1（编译目标为 Java 25）。这只是实际测过的一组组合，不代表所有 Fabric 或 Loom 版本都能用。

## 这个模板是怎么来的

之前用一个任务板（Quest Board）模组做过一次对比：同样的功能，一版只用 Java 写，另一版用 Java 加 Sprig。两版都能构建运行。纯 Java 版的产品代码有 707 行；混合版有 760 行，其中大约 470 行是 Sprig 写的应用逻辑。

那次真正反复费功夫的是 Gradle 和 Loom 的配置：编译 Java 桥接代码、导出客户端的 classpath、生成并注册 Java 代码、加入运行时、接上 `check`。这些都不是 Sprig 语言本身缺了什么，所以后来做成了插件和这个模板。

这只是一个项目的经验，没有统计时间或 token，不是严格的效率对比。而且在当时需要手工配置的条件下，最后选的是纯 Java 版。插件去掉的是那次看到的配置负担，**并不能证明 Sprig 比 Java 效率更高**。它只是让以后的对比少一些和语言无关的干扰。

更多细节和限制见 [`libraries/sprig-fabric/README.md`（英文）](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-fabric/README.md) 和 [`libraries/sprig-gradle/README.md`（英文）](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-gradle/README.md)。
