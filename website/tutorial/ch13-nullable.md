# 13. 可空值

这一章你会学到：

- `Int` 和 `Int?` 的区别：后者“可能有值，也可能是 `null`”；
- “收窄”：编译器在哪几处会相信你已经检查过 `null`；
- 只有 `let`、参数、循环变量会收窄；`var` 和字段怎么办；
- `continue` 和 `break` 也能触发收窄；
- `@std/nulls` 的 `or_else` 给默认值，以及它“先把两个参数都算出来”的含义；
- 三个故意写错：不检查就用、对 `var` 收窄、把 `null` 放进 `Int`。

## 13.1 可能没有值：问号类型

“可能没有值”是程序里最常见的错误来源。很多语言用一个特殊的 `null` 表示它，然后在运行时才发现自己忘了判断。Sprig 把它写进类型：`Int` 一定有值，`Int?` 可能有一个 `Int`，也可能是 `null`。两者不是同一个类型，编译器不允许把后者当前者用。

下面的 `find` 在一个字符串列表里找目标，找到了返回位置，找不到返回 `null`：

<<< @/snippets/book/ch08_nullable.spr

```text
found at 1
true
null
```

- `-> Int?` 是返回类型，问号表示这个函数可能返回 `null`：按名字找，不一定找得到。
- `return null` 是合法的，因为返回类型带问号。反过来，`let n: Int = null` 不能通过编译（13.5 会看到）。
- `let index = find(words, "beta")` 里 `index` 是 `Int?`。`words` 是 `["alpha", "beta"]`，`"beta"` 在第 1 个位置，所以函数返回 1。
- `if index != null:` 判断过以后，在那个分支里 `index` 就是 `Int`，可以直接拼进字符串：`"found at " + index` 输出 `found at 1`。这种“从可能为 `null` 变成一定有值”的变化叫**收窄**（narrowing）。
- `find(words, "gamma")` 找不到，返回 `null`；`other == null` 是 `true`。
- `print(other)` 输出 `null`：可空值可以直接打印，和 `null` 或别的值比较也都行。

`index` 的类型在每一步的变化：

| 位置 | `index` 的类型 | 为什么 |
|---|---|---|
| `let index = find(words, "beta")` | `Int?` | 函数签名是 `-> Int?` |
| `if index != null:` 的分支内 | `Int` | 刚判断过不是 `null` |
| 分支外再使用 | `Int?` | 判断只在那一个分支里有效 |

你已经在别处见过可空类型：第 9 章映射取值 `stock["tea"]` 返回 `Int?`，第 4 章的 `"12".toIntOrNull()` 返回 `Int?`。用法都一样。

## 13.2 故意写错：不检查就用

<<< @/snippets/book/ch08_deref.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:2:7: Operator '+' cannot use a value that may be null (expected non-null operands, actual Int? and Int)
  hint: count may be null (Int?): check it first with 'if count != null:', and inside that block it is Int, or give a fallback with or_else from @std/nulls.spr.
```

读法：

- `count` 是 `count.toIntOrNull()` 的结果，类型 `Int?`；
- 位置第 2 行第 7 列是 `+`；
- 消息说 `+` 不能用在可能为 `null` 的值上，实际类型 `Int?` 和 `Int`；
- 提示给了两条路：先用 `if count != null:` 判断，或者用 `@std/nulls` 的 `or_else` 给默认值。两种都在本章后面。

字符串拼接同理：`"count " + count` 里 `count` 是 `Int?` 就会报同一类错，判断过之后才行。

## 13.3 收窄的四种位置

编译器不是只在 `if != null` 里收窄，它认识四种常见写法：

<<< @/snippets/book/ch08_narrowing.spr

```text
count 3
none
true
false
empty
hi
42
```

1. **提前返回。** `if count == null: return "none"`，之后的代码里 `count` 不再是可空的。`describe` 和 `double` 都是这样：`describe(3, true)` 先经过 `count == null` 的检查，没返回，所以打印 `count 3`。
2. **`if x != null:` 的分支里，包括后面的 `elif` 和 `else`。** `describe(none, false)` 传入 `null`，走 `return "none"`，输出 `none`。
3. **`and` 的右边。** `value != null and value > 0`：右边只在左边为真时求值，所以那里 `value` 一定有值。`first_positive(5)` 是 `true`，`first_positive(none)` 是 `false`。
4. **`or` 的右边。** `text == null or text.length() == 0`：右边只在 `text` 不为 `null` 时求值。`label("")` 输出 `empty`，`label("hi")` 输出 `hi`。

这些规则只有一个前提：被判断的名字是**不可改的**——`let` 绑定、参数或循环变量。`describe` 的 `count` 是参数，`double` 的 `value` 也是。

## 13.4 故意写错：`var` 不收窄

同样的判断，如果名字是 `var`，编译器拒绝收窄：

<<< @/snippets/book/ch08_var_narrowing.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:3:11: Operator '+' cannot use a value that may be null (expected non-null operands, actual Int? and Int)
  hint: count may be null (Int?), and a var never narrows: copy it into a let (let current = count), check 'if current != null:', and use current inside that block.
```

为什么？因为 `var` 在判断之后可能已经被别处改掉（比如判断和使用之间调用了一个会改它的函数）。编译器不对它做任何假设。修法就是提示说的：先 `let current = count`，再判断 `current`。

**类的字段同理**：字段也不收窄，编译器的提示和 `var` 一样，让你先复制到 `let` 再判断。这也是第 3 章建议默认用 `let` 的原因之一。

## 13.5 故意写错：把 `null` 放进 `Int`

<<< @/snippets/book/ch13_null_assign.spr

```text
SPR-TYPE-NULL [TYPE] main.spr:1:14: null is not assignable to Int (initializer); use Int? (expected Int, actual null)
```

- 错误码 `SPR-TYPE-NULL`，位置是 `= null` 里的 `null`；
- 消息说 `null` 不能赋给 `Int`，并直接给出修法：`use Int?`。

`let n: Int? = null` 就通过了。想装 `null`，类型必须带问号。

集合类型也一样，注意区分两个问号的位置：

- `List[Int?]`：列表本身一定有，里面的**元素**可能为 `null`；
- `List[Int]?`：列表本身**可能为 `null`**，但里面的元素都不是。

## 13.6 continue 和 break 也收窄

循环里的 `continue` 和 `break` 结束当前路径，效果和提前 `return` 一样。下面是两个常用套路：

<<< @/snippets/book/ch13_loop_narrow.spr

```text
Ada
none
3
```

- `first_seen` 找第一个不是 `null` 的名字。循环变量 `name` 是 `String?`；`if name == null: continue` 跳过 `null`，之后的 `return name` 里 `name` 已经收窄成 `String`。
- `sum_until_missing` 累加直到遇见 `null`：`if value == null: break` 结束循环，`total += value` 只能在收窄之后写。
- `first_seen([null, "Ada", "Bo"])` 第一轮 `null` 被跳过，第二轮返回 `"Ada"`。
- `first_seen([null])` 全是 `null`，返回 `"none"`。
- `sum_until_missing([1, 2, null, 4])` 加到 1 + 2 = 3 时遇见 `null` 退出，输出 `3`。

跟踪 `first_seen([null, "Ada", "Bo"])`：

| 轮次 | `name` | 判断 | 之后 |
|---|---|---|---|
| 1 | `null` | `name == null` 为真 | `continue`，下一轮 |
| 2 | `"Ada"` | 不是 `null` | `return "Ada"` |

## 13.7 用 or_else 给默认值

不想写 `if` 时，`@std/nulls` 的 `or_else(value, fallback)` 有值就用它，为 `null` 就用第二个参数：

<<< @/snippets/book/ch13_or_else.spr

```text
12
0
computing the fallback
5
```

- `stock["tea"]` 有值 12，`or_else` 返回它；`stock["milk"]` 是 `null`，返回默认的 0。
- 后两行是一个要点：`fallback()` **在调用 `or_else` 之前就被算出来了**，不管第一个参数是不是 `null`。所以 `nulls.or_else(5, fallback())` 明明有值 5，`computing the fallback` 还是打印了，然后才打印 `5`。

这个“提前求值”（eager）意味着：第二个参数如果开销大，或者有副作用（打印、读文件），即使第一个参数有值它也会执行。想要“只在需要时才算”，就老老实实写 `if value != null:`。

`@std/nulls` 里还有 `require(value, message)`：有值就返回，为 `null` 就抛出一个带消息的错误。它的签名里有 `throws`，属于下一章的内容。

## 本章小结

- `T?` 可能为 `null`，`T` 一定有值，两者不通用；`null` 不能放进不带问号的类型。
- 四个收窄位置：提前返回、`if != null` 的分支（含 `elif`/`else`）、`and` 右边、`or` 右边；`continue` 和 `break` 也收窄。
- 只有不可改的名字（`let`、参数、循环变量）会收窄；`var` 和字段先复制到 `let` 再判断。
- 没有 `?.` 和 `??`；默认值用 `nulls.or_else`（注意它的第二个参数总会求值），“必须有值”用 `nulls.require`。

## 动手练习

**练习 1（返回可空值）。** 写 `first_long(words: List[String], min: Int) -> String?`，返回第一个长度至少为 `min` 的词，没有就返回 `null`。调用方判断后打印。

::: details 参考答案
<<< @/snippets/book/ch13_ex1.spr

```text
hello
```
:::

**练习 2（给映射取值兜底）。** 有一个 `Map[String, Int]`，用 `or_else` 打印已有键的值，再给一个不存在的键打印 0。

::: details 参考答案
<<< @/snippets/book/ch13_ex2.spr

```text
36
0
```
:::

**练习 3（跳过 null）。** 写 `sum_known(values: List[Int?]) -> Int`，把非 `null` 的值加起来，`null` 用 `continue` 跳过。用 `[1, null, 2, null, 3]` 验证结果是 6。

::: details 参考答案
<<< @/snippets/book/ch13_ex3.spr

```text
6
```
:::

**练习 4（修好 var 的不收窄）。** 下面这段编译不过，把它改对，让它打印 `40`：

```sprig
var maybe: Int? = 4
if maybe != null:
    print(maybe * 10)
```

::: details 参考答案
<<< @/snippets/book/ch13_ex4.spr

```text
40
```

先把 `maybe` 读进 `let value = maybe`，再判断 `value != null`。
:::

下一章：[错误处理](/tutorial/ch14-errors)。
