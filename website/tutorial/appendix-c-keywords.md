# 附录 C. 关键字和内置函数

关键字是语言里保留的词，不能拿来当变量或函数的名字；内置函数和内置方法不需要导入，任何 Sprig 程序都能直接用。本附录的清单与编译器自身的帮助一致，可以用 `sprig help language`、`sprig help types`、`sprig help numerics`、`sprig help strings` 和 `sprig help collections` 复核。

## C.1 关键字

Sprig 的 36 个关键字：

| 关键字 | 用途 | 最早出现 |
|---|---|---|
| `and` | 逻辑与，短路 | 第 3 章 |
| `as` | import 别名、match 绑定、`conform ... as 父类视图` | 第 12、16、18 章 |
| `break` | 跳出循环 | 第 6 章 |
| `case` | match 的一个分支 | 第 12 章 |
| `catch` | 捕获错误 | 第 14 章 |
| `class` | 声明类 | 第 11 章 |
| `conform` | 声明类满足契约 | 第 16 章 |
| `continue` | 跳过本轮循环 | 第 6 章 |
| `elif` | 条件链的下一支 | 第 5 章 |
| `else` | 条件链的兜底分支 | 第 5 章 |
| `enum` | 声明枚举 | 第 12 章 |
| `false` | `Bool` 字面量：假 | 第 3 章 |
| `finally` | try 结束时一定执行的块 | 第 14 章 |
| `fn` | 函数类型、lambda | 第 15 章 |
| `for` | 遍历循环 | 第 6 章 |
| `func` | 声明函数或方法 | 第 7 章 |
| `generic` | 泛型参数块 | 第 16 章 |
| `if` | 条件语句和条件表达式 | 第 5 章 |
| `import` | 导入模块或 Java 类 | 第 4、18 章 |
| `in` | 遍历的目标、成员判断运算符 | 第 4、6 章 |
| `let` | 只绑定一次的名字 | 第 3 章 |
| `match` | 匹配 enum 或 variant | 第 12 章 |
| `not` | 逻辑非 | 第 3 章 |
| `null` | 空值字面量，只属于 `T?` | 第 13 章 |
| `or` | 逻辑或，短路 | 第 3 章 |
| `pass` | 什么也不做的语句 | 第 6 章 |
| `requires` | 泛型函数的能力约束 | 第 16 章 |
| `return` | 从函数返回 | 第 7 章 |
| `rethrows` | 函数只抛它的函数参数抛出的错 | 第 15 章 |
| `throw` | 抛出错误 | 第 14 章 |
| `throws` | 声明函数可能抛出的错误 | 第 14 章 |
| `true` | `Bool` 字面量：真 | 第 3 章 |
| `try` | 可能抛错的块 | 第 14 章 |
| `var` | 可以重新赋值的名字 | 第 3 章 |
| `variant` | 声明 variant（封闭的类型集合） | 第 12 章 |
| `while` | 当型循环 | 第 6 章 |

两个看起来像关键字的词其实不是：`export` 和 `to`（`conform A to B` 里的 `to`）是"上下文词"，只在那个位置有特殊含义，可以被用作变量名：

<<< @/snippets/book/appendix_c_contextual.spr

```text
7
```

类型名（`Int`、`String`、`List`……）也不是关键字，只是预先声明好的名字。

## C.2 内置函数

只有三个：

| 函数 | 作用 | 备注 |
|---|---|---|
| `print(value)` | 打印一个值并换行 | 只收一个参数；`print(a, b)` 是 `SPR-CALL-ARITY`；`null` 也能打印 |
| `range(stop)`、`range(start, stop)`、`range(start, stop, step)` | 返回 `List[Int]` | `stop` 不含在内；`step` 可以是负数；直接写在 `for` 后面时不会真的建出列表 |
| `assert(condition)`、`assert(condition, message)` | 条件为 `false` 时抛出 `Error` | 在测试和排查时很有用；`message` 省略时是 `assertion failed` |

<<< @/snippets/book/appendix_c_builtins.spr

```text
hello
[0, 1, 2]
[1, 2, 3]
[0, 5]
assert passed
```

- `range(3)` 是 `[0, 1, 2]`，`range(1, 4)` 是 `[1, 2, 3]`：`stop` 不包含。
- `range(0, 10, 5)` 每步加 5，得到 `[0, 5]`；`step` 为负时从高往低数。
- `assert(2 > 1, ...)` 通过，程序继续。

## C.3 内置方法

方法用 `.` 调用。完整清单如下；括号里是参数个数或说明。

**数字类型**

| 类型 | 方法 |
|---|---|
| `Int` | `toFloatExact()`、`toFloatLossy()`、`toInt32Exact()`、`toDecimal()`、`divTrunc(b)`、`compareTo(b)`、`toString()` |
| `Int`（静态） | `Int.abs(n)`、`Int.min(a, b)`、`Int.max(a, b)` |
| `Int32` | `toInt()`、`toFloat()`、`toDecimal()`、`divTrunc(b)`、`compareTo(b)`、`toString()` |
| `Float` | `toIntExact()`（必须是整数）、`toIntTrunc()`、`toFloat32Exact()`、`toFloat32Lossy()`、`isNaN()`、`isInfinite()`、`isFinite()`、`approxEqual(other, tolerance)`、`compareTo(b)`、`toString()` |
| `Float`（静态） | `Float.sqrt(x)`、`Float.floor(x)`、`Float.ceil(x)`、`Float.abs(x)` |
| `Float32` | `toFloat()`、`isNaN()`、`isInfinite()`、`isFinite()`、`compareTo(b)`、`toString()` |
| `Decimal` | `divide(divisor, decimals, roundingMode)`、`toIntExact()`、`toFloatExact()`、`toFloatLossy()`、`toJava()`、`compareTo(b)`、`toString()` |
| `Decimal`（静态） | `Decimal.parse(text)`、`Decimal.fromJava(bigDecimal)` |
| `BigInt` | `divTrunc(b)`、`toIntExact()`、`toFloatExact()`、`toFloatLossy()`、`toDecimal()`、`toJava()`、`compareTo(b)`、`toString()` |
| `BigInt`（静态） | `BigInt.parse(text)`、`BigInt.fromInt(n)`、`BigInt.fromJava(bigInteger)` |
| `Bool` | `toString()` |

`compareTo` 返回 `Int32`，负数、零或正数，和 `<` 的顺序一致；`a.compareTo(b)` 就是 Java 的 `Comparator`。

**`String`**

| 类型 | 方法 |
|---|---|
| `String` | `length()`、`isEmpty()`、`charAt(i)`、`codeAt(i)`、`substring(start)`、`substring(start, end)`、`indexOf(text)`、`lastIndexOf(text)`、`contains(text)`、`startsWith(text)`、`endsWith(text)`、`compareTo(other)`、`toUpperCase()`、`toLowerCase()`、`trim()`、`split(separator)`、`replace(old, new)`、`repeat(n)`、`toInt()`、`toIntOrNull()`、`toFloat()`、`toString()` |
| `String`（静态） | `String.join(parts, separator)`、`String.fromCode(codePoint)` |

- 位置都按 Unicode 码点算，不是 UTF-16 码元（第 4 章）。
- `substring(start, end)` 的 `end` 不含在内。
- `toInt()` 在不是整数时失败并抛错；`toIntOrNull()` 返回 `Int?`。
- `String.join` 接收"列表在前、分隔符在后"：`String.join(["a", "b"], ",")`。

**集合**

| 类型 | 方法 |
|---|---|
| `List[T]` | `size()`、`isEmpty()`、`get(i)`、`contains(v)`、`indexOf(v)`、`toMutableList()`、`toList()`、`map(f)`、`filter(f)`、`forEach(f)`、`toString()` |
| `MutableList[T]` | `List` 的全部方法，再加 `append(v)`、`set(i, v)`、`insert(i, v)`、`removeAt(i)`、`remove(v)`、`clear()`、`sort()` |
| `Map[K, V]` | `size()`、`isEmpty()`、`get(k)`（返回 `V?`）、`containsKey(k)`、`keys()`、`values()`、`toMutableMap()`、`toMap()`、`toString()` |
| `MutableMap[K, V]` | `Map` 的全部方法，再加 `set(k, v)`、`remove(k)`、`clear()` |

`List` 和 `Map` 是只读的；要改就先用 `toMutableList()`/`toMutableMap()` 转成可变版本（第 8、9 章）。

下面把这些方法各挑几个跑一遍：

<<< @/snippets/book/appendix_c_methods.spr

```text
7
3
3.0
7.0
2
2
Sprig
SPRIG
[a, b, c]
abab
x,y
4
null
[1, 2, 3, 4]
true
3
36
true
[ada]
```

- `Int.abs(-7)` 是 7，`Int.min(3, 9)` 是 3：静态方法用类型名调用。
- `Float.sqrt(9.0)` 是 `3.0`；`7.toFloatExact()` 是 `7.0`；`2.5.toIntTrunc()` 是 2；`7.divTrunc(3)` 是 2。
- `trim()` 去掉两端空白，`toUpperCase()` 变大写；`split("-")` 返回列表；`repeat` 重复字符串；`String.join` 拼出 `x,y`。
- `"3".toInt() + 1` 是 4；`"x".toIntOrNull()` 打印 `null`。
- `xs.append(4)` 再 `xs.sort()` 得到 `[1, 2, 3, 4]`；`contains`、`indexOf` 按值查找。
- `ages.get("ada")` 是 36；`"ada" in ages` 判断键；`keys()` 返回 `[ada]`。

标准库的 `@std` 模块里还有更多函数（列表工具、文本、文件、JSON……），那些要 `import` 才能用，不在本附录的"内置"之列。查看某个模块的完整签名用 `sprig api @std/<模块>.spr`。

## 相关章节

- [第 3 章：值、变量和算术](/tutorial/ch03-values)：`let`/`var`、`Bool`、数值运算。
- [第 4 章：文字](/tutorial/ch04-text)：字符串方法。
- [第 5 章：做决定：if](/tutorial/ch05-if)、[第 6 章：重复：循环](/tutorial/ch06-loops)：`if`/`elif`/`else`、`while`/`for`/`in`。
- [第 7 章：函数](/tutorial/ch07-functions)：`func`、`return`。
- [第 8 章：列表](/tutorial/ch08-lists)、[第 9 章：映射和集合](/tutorial/ch09-maps-sets)：集合方法。
- [第 12 章：枚举、variant 和 match](/tutorial/ch12-enums-variants)：`enum`、`variant`、`match`、`case`。
- [第 14 章：错误处理](/tutorial/ch14-errors)：`try`/`catch`/`finally`、`throw`/`throws`。
- [第 15 章：函数作为值](/tutorial/ch15-functions-as-values)：`fn`、`rethrows`。
- [第 16 章：泛型与契约类](/tutorial/ch16-generics-contracts)：`generic`、`requires`、`conform`。
