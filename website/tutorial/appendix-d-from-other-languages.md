# 附录 D. 从 Python、JavaScript、Java 过来

这份附录不重讲语言，而是把你熟悉的写法逐条映射到 Sprig：左边是你原来的习惯，右边是 Sprig 的写法。每一条 Sprig 片段都在编译器上跑过；每节末尾有一个完整程序，包含了最常见的几个差异。

如果你已经会编程，可以略读第 1–4 章，但请认真看第 3 章（数字）、第 13 章（可空值）和第 14 章（错误）——那三处和大多数语言不一样。

## D.1 从 Python 过来

| Python 的习惯 | Sprig 的写法 |
|---|---|
| `print(a, b)` | `print` 只收一个值：`print(a)`；先拼好再打印 |
| `1 / 2` 得 `0.5` | `Int` 的 `/` 被拒绝：`7.divTrunc(2)` 或 `7.toFloatExact() / 2.toFloatExact()` |
| `//` 整除 | `a.divTrunc(b)` |
| `-7 % 3` 得 `2` | `-7 % 3` 得 `-1`：`%` 的符号跟着被除数（和 Java 一样） |
| `if x:` 真值判断 | 条件必须是 `Bool`：`if x != 0:`、`if text != "":` |
| `len(s)`、`len(xs)` | `s.length()`（`String`）、`xs.size()`（集合） |
| f-string `f"n = {n}"` | `"n = " + n`，`+` 会把右侧的值变成文字 |
| `None` | `null`，只属于 `T?`；用之前先 `!= null` |
| `try`/`except` | 错误写进签名：`throws Error` + `try`/`catch problem: Error:` |
| list 默认可变 | `List` 只读、`MutableList` 可变；`xs[0] = v` 写成 `xs.set(0, v)` |
| `for i in range(n)` | 一样写 `for i in range(n):` |
| `pass` | 一样有 `pass` |
| 元组 `(a, b)` | 没有元组：一行类 `class Pair(first: Int, second: Int)` |
| `and`、`or`、`not` | 一样 |
| `d.get(k)` 返回 `None` | `m.get(k)` 返回 `V?`；`m[k]` 也是 `V?`，但 `m[k] += 1` 要求键已存在 |

<<< @/snippets/book/appendix_d_python.spr

```text
total: 7.5
found nut
[a, b, c]
3
-1
3.5
```

逐行说明：

- `"total: " + total` 拼接文字和 `Float`，不需要 f-string，也不需要 `str()`。
- `find` 返回 `String?`；`if found != null:` 之后，`found` 在分支里就是非空的字符串。
- `items` 声明为 `MutableList[String]` 才能 `append`；如果写 `List[String]`，`append` 会被拒绝。
- `7.divTrunc(2)` 是有意的截断；`-7 % 3` 得 `-1`，因为符号跟被除数。
- `7.toFloatExact() / 2.toFloatExact()` 才是小数除法，得到 `3.5`。

## D.2 从 JavaScript 过来

| JavaScript 的习惯 | Sprig 的写法 |
|---|---|
| `const` / `let` | `let` 只绑定一次、`var` 可重新赋值（注意和 JS 正好相反） |
| `===` / `!==` | `==` / `!=`，本来就是严格比较，没有隐式转换 |
| `undefined` | 不存在；缺失只有 `null`，而且只在 `T?` 里合法 |
| 模板字符串 `` `n = ${n}` `` | `"n = " + n` |
| `array.length` | `xs.size()` |
| `array.push(v)` | `xs.append(v)` |
| `array.map(f)` | `xs.map(f)`；`f` 是带类型的 lambda 或命名函数 |
| `{}` 对象字面量 | `{"k": v}` 是 `Map`；字段固定的记录用一行类 |
| `x?.y`、`x ?? y` | 没有可选链和 `??`：先 `if x != null:`，或用 `@std/nulls` 的 `or_else` |
| `async`/`await`、Promise | `@std/concurrent` 的虚拟线程与任务（第 22 章） |
| `function f(x) {}` | `func f(x: Int) -> Int:`，参数和返回值都要写明类型 |
| `Math.floor(x)` | `Float.floor(x)`；还有 `sqrt`、`ceil`、`abs` |
| `parseInt(s)` | `s.toIntOrNull()`（缺失返回 `null`）或 `s.toInt()`（失败抛错） |
| `NaN === NaN` 是 `false` | 用 `Float.isNaN(x)` 判断；近似相等用 `x.approxEqual(y, 误差)` |
| `for (const x of xs)` | `for x in xs:` |
| 数字都是 double | `Int` 是 64 位整数、`Float` 是 binary64，两者不隐式混用 |

<<< @/snippets/book/appendix_d_javascript.spr

```text
Ada has 1
missing
3
3
true
Grace
true
```

逐行说明：

- `var count` 可以 `count += 1`；`let name` 之后再赋值会报 `SPR-NAME-LET-ASSIGN`。
- `lookup` 返回 `Int?`，`{"ada": 36}` 是 `Map[String, Int]`；查不到时 `get` 返回 `null`。
- `numbers.size()` 是 3，`numbers[0]` 是 3；数组方法是 `size()` 不是 `.length`。
- `person.get("name")` 从 map 里取值；`"city" in person` 判断键。

## D.3 从 Java 过来

| Java 的习惯 | Sprig 的写法 |
|---|---|
| `public static void main(String[] args)` | 顶层语句就是程序，按源码顺序执行；叫 `main` 的函数不会自动调用 |
| `System.out.println(x)` | `print(x)` |
| `final` 局部变量 | `let`；可重新赋值的局部用 `var` |
| `int` 溢出回绕 | `Int` 检查溢出，溢出是运行时错误 |
| 整数 `/` 截断 | 拒绝 `/`：有意截断写 `a.divTrunc(b)` |
| `int` 是 32 位、`long` 是 64 位 | `Int` 是 64 位；要 32 位写 `Int32` |
| `double`、`float` | `Float`（binary64）、`Float32` |
| `a.equals(b)` | `==` 就是按值比较（数字、`Bool`、`String`、列表、map、enum、variant） |
| 类对象 `==` 比引用 | 一样是按同一性比较；字段相同不代表相等 |
| 接口 | 契约类（方法没有函数体）+ `conform`（第 16 章） |
| `extends` | 没有继承；用契约、组合或 variant |
| 泛型 `<T>` | `generic T:` 块（第 16 章） |
| 受检异常 | 可恢复错误是 `throws Error` 或自己定义的错误类；Java 受检异常也会被检查 |
| Java 方法返回的对象可能为 `null` | Sprig 里这类结果默认是 `T?`，用前必须检查 |
| 注解、反射 | 没有注解，也没有基于反射的结构描述；用显式的字段和函数 |
| `int[]` 数组 | `List[Int]`；Java 数组作为不透明值传递（附录 F） |

<<< @/snippets/book/appendix_d_java.spr

```text
Ada: 100
through the contract
3
```

逐行说明：

- `Account(owner="Ada")` 用命名参数构造对象；`deposit` 改动的是 `var balance`。
- `Sink` 的方法没有函数体，是契约类；`conform Console to Sink` 之后，`Console` 的值可以当作 `Sink` 使用。
- `7.divTrunc(2)` 是 3：整数除法必须写出"截断"的意图。
- Java 的 `main` 不会自动执行，所以上面的程序从头到尾都是顶层语句。

## 相关章节

- [第 3 章：值、变量和算术](/tutorial/ch03-values)：数字家族和显式转换。
- [第 4 章：文字](/tutorial/ch04-text)：字符串方法和码点。
- [第 8 章：列表](/tutorial/ch08-lists)、[第 9 章：映射和集合](/tutorial/ch09-maps-sets)：`List`/`MutableList`、`Map`。
- [第 11 章：类和对象](/tutorial/ch11-classes)：一行类、字段、方法。
- [第 13 章：可空值](/tutorial/ch13-nullable)：`T?` 和收窄。
- [第 14 章：错误处理](/tutorial/ch14-errors)：`throws`、`try`/`catch`。
- [第 16 章：泛型与契约类](/tutorial/ch16-generics-contracts)：`generic`、契约和 `conform`。
- [第 22 章：并发](/tutorial/ch22-concurrency)：`@std/concurrent` 代替 async/await。
