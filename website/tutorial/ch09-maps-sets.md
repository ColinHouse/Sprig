# 9. 映射和集合

这一章你会学到：

- 映射（map）：按键存取值；
- 读取不存在的键会得到 `null`，以及类型里的 `?`；
- 用映射计数的套路；
- `m[k] += 1` 在键不存在时的运行时陷阱；
- 怎么遍历键和值；
- `@std/sets.spr` 提供的集合。

## 9.1 为什么需要映射

列表用下标找元素，下标是 0、1、2 这样的编号。如果要"按名字"找，就得先记住名字排在第几个。映射把键直接对应到值：

<<< @/snippets/book/ch09_map_basics.spr

```text
{Ada: 90, Bob: 72}
2
90
{Ada: 95, Bob: 72, Cyd: 85}
[Ada, Bob, Cyd]
[95, 72, 85]
true
false
72
{Ada: 95, Cyd: 85}
null
false
```

逐行读：

- `{"Ada": 90, "Bob": 72}` 是映射字面量，写成 `键: 值`，逗号隔开。不写类型时它是 `MutableMap[String, Int]`；这里写出来做示范。
- `scores["Ada"]` 按键读值，得到 `90`。
- `scores["Cyd"] = 85` 加入新键；`scores["Ada"] = 95` 覆盖已有键的值。
- 打印格式是 `{Ada: 95, Bob: 72, Cyd: 85}`。**映射保持插入顺序**：`Ada` 早就在，覆盖后仍在第一位；`Cyd` 是新键，排在最后。
- `scores.keys()` 是键的列表 `[Ada, Bob, Cyd]`，`scores.values()` 按同样的顺序给值的列表 `[95, 72, 85]`。
- `scores.containsKey("Bob")` 问有没有这个键；`"Zoe" in scores` 是另一种写法。
- `scores.remove("Bob")` 删除键，返回**删除前的值** `72`；再删一次返回 `null`，因为键已经不在了。删除 `Bob` 后是 `{Ada: 95, Cyd: 85}`。
- `scores.isEmpty()` 为 `false`，映射还有两个键。

读写的两副写法：`scores[k]` 等于 `scores.get(k)`，`scores[k] = v` 等于 `scores.set(k, v)`。

| 方法 | 作用 |
|---|---|
| `size()` / `isEmpty()` | 键的个数 / 是否为空 |
| `get(k)` / `m[k]` | 按键读值（结果可空，见 9.2） |
| `set(k, v)` / `m[k] = v` | 写入或覆盖 |
| `containsKey(k)` / `k in m` | 是否有这个键 |
| `keys()` / `values()` | 键的列表 / 值的列表 |
| `remove(k)` | 删除键，返回旧值或 `null` |
| `clear()` | 清空 |

空映射字面量 `{}` 和空列表一样要写类型：`let counts: MutableMap[String, Int] = {}`。

## 9.2 读取可能落空

映射和列表最大的区别在这里：读一个键之前，没人保证这个键存在。所以读取的结果是**可空类型** `Int?`，读作"可能是 `Int`"：

<<< @/snippets/book/ch09_map_null.spr

```text
null
coffee: none
tea: 12
```

- `stock["coffee"]` 的静态类型是 `Int?`。键不存在，值就是 `null`，打印得到 `null`。
- `if coffee == null:` 成立，打印 `coffee: none`。
- `stock["tea"]` 存在，值是 12；`if tea != null:` 的块里 `tea` 被当作真正的 `Int` 使用，打印 `tea: 12`。

"判断过 `!= null` 之后就能当普通值用"叫做收窄，第 13 章会系统地讲可空值。这里先记住两条：读映射得到 `Int?`；`if x != null:` 之后再运算。

### 故意写错：对可能为空的值做运算

<<< @/snippets/book/ch09_map_null_arith.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:3:7: Operator '+' cannot use a value that may be null (expected non-null operands, actual Int? and Int)
  hint: coffee may be null (Int?): check it first with 'if coffee != null:', and inside that block it is Int, or give a fallback with or_else from @std/nulls.spr.
```

错误码 `SPR-TYPE-NULLABLE` 说的是"可空值不能直接用"。读报错的方法：

- `actual Int? and Int`：左边是 `Int?`（可能为空），右边是 `Int`。
- `hint` 给出两条路：先 `if coffee != null:` 判断；或者用 `or_else` 给一个默认值（`@std/nulls.spr` 里的工具，第 13 章讲）。

改法是：

```sprig
if coffee != null:
    print(coffee + 1)
```

## 9.3 计数套路

映射最经典的用法是数数：给一串值，数每个值出现多少次。列表的练习里数一个值的出现次数还行，数所有值就要映射：

<<< @/snippets/book/ch09_map_count.spr

```text
{tea: 3, fig: 1, pear: 1}
3
```

循环体是固定的三段：

1. `let old = counts[word]` 先读当前的计数（类型是 `Int?`）。
2. `old == null` 说明这个词第一次见，写 `1`。
3. 否则写回 `old + 1`。

跟着表走一遍：

| 第几步 | `word` | `old` | `counts` |
|---|---|---|---|
| 开始 | — | — | `{}` |
| 1 | tea | null | `{tea: 1}` |
| 2 | fig | null | `{tea: 1, fig: 1}` |
| 3 | tea | 1 | `{tea: 2, fig: 1}` |
| 4 | pear | null | `{tea: 2, fig: 1, pear: 1}` |
| 5 | tea | 2 | `{tea: 3, fig: 1, pear: 1}` |

循环结束后 `counts` 是 `{tea: 3, fig: 1, pear: 1}`，`counts["tea"]` 是 `3`。

## 9.4 `+=` 的运行时陷阱

看上面"先读、判断、写回"，你可能会想写成更短的：

```sprig
counts[word] += 1
```

只有当键**已经存在**时它才是计数。键不存在时，这一行会以运行时错误停下：

<<< @/snippets/book/ch09_map_add_trap.spr

把上面的程序存成 `main.spr` 运行：

```bash
sprig run main.spr
```

```text
2
SPR-RUNTIME-ERROR [RUNTIME] main.spr:4:1: Uncaught Error: compound assignment requires an existing map key
  hint: Catch it with try/catch or declare throws in the calling function. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

- `counts["tea"] += 1` 能工作：键存在，先打印出 `2`。
- `counts["fig"] += 1` 失败：`+=` 要读旧值再加法，而 `counts["fig"]` 是 `null`，加不了。提示写得很直接：`compound assignment requires an existing map key`（复合赋值要求键已存在）。

所以数数就用 9.3 的套路：不知道键在不在，就 `let old = ...` 然后判断 `null`。

## 9.5 遍历键和值

<<< @/snippets/book/ch09_map_iterate.spr

```text
tea
milk
rice
12
3
20
tea=12
milk=3
rice=20
```

- `for key in stock:` 直接遍历映射，得到的是**键**。
- `stock.values()` 是值的列表，可以直接 `for`。
- `stock.keys()` 是键的列表；在循环里用 `stock[key]` 读值，结果仍然是 `Int?`，所以还是 `if value != null:` 之后再用。
- `key + "=" + value` 里，`value` 已经收窄成 `Int`，字符串和 `Int` 可以直接用 `+` 拼接。

## 9.6 集合：只问"有没有"

如果你只关心"这些东西里出现过哪些"，不关心顺序和次数，用集合（set）。Sprig 的集合在 `@std/sets.spr`：

<<< @/snippets/book/ch09_sets.spr

```text
2
true
true
true
true
[apple, fig]
[apple, fig, kiwi]
[fig]
[apple]
```

- `sets.of(["apple", "pear", "apple"])` 从列表造集合：重复的 `apple` 只留一次，成员按**第一次出现的顺序**排列，`size()` 是 2。
- `fruits.has("apple")` 问是不是成员。
- `fruits.add("fig")` 加入，返回 `Bool`：刚加入返回 `true`，已经在里面就返回 `false`。
- `fruits.remove("pear")` 删除成员，返回 `Bool`：删到了 `true`，再删同一个返回 `false`。
- `fruits.to_list()` 按插入顺序给成员的列表 `[apple, fig]`。
- `union`（并集）、`intersection`（交集）、`difference`（差集）返回新集合，不改动输入。

空集合要写元素类型：`sets.of[String]([])`。

::: tip 学过其他语言？
Python 的 `set` 不保证顺序，遍历顺序随实现变化；Sprig 的集合保留首次插入的顺序。Java 的 `HashSet`/`HashMap` 同样不保证顺序，而 Sprig 的 `Map` 和 `Set` 都按插入顺序排列，输出可复现。
:::

## 9.7 想一次装两个值？

列表装一种元素，映射是一个键对一个值。想把"名字、分数、出生地"打包成一个整体，需要自己定义类——第 11 章会讲怎么用一行定义一个类。在那之前，可以先用映射，把字段名当键。

## 本章小结

- `{键: 值}` 是映射字面量，默认是 `MutableMap`；映射保持插入顺序。
- 读映射得到可空值 `V?`；直接参与运算会得到 `SPR-TYPE-NULLABLE`，先 `if x != null:` 再使用。
- 计数用"读出来、判断 `null`、写回"三步；`m[k] += 1` 要求键已存在，否则是运行时 `SPR-RUNTIME-ERROR`。
- `for key in m:` 遍历键；`m.keys()` 和 `m.values()` 给列表。
- `@std/sets.spr` 提供集合：`of`、`add`、`has`、`remove`、`size`、`to_list`、`union`、`intersection`、`difference`。

## 动手练习

**练习 1（热身）** 给定 `{"tea": 12, "milk": 3, "rice": 20}`，遍历 `values()` 求和并打印。

提示：`for value in stock.values():`，`total += value`。

::: details 参考答案
<<< @/snippets/book/ch09_ex1_answer.spr

```text
35
```
:::

**练习 2** 统计 `["b", "a", "b", "c", "b"]` 里每个字母出现的次数，打印整个映射和 `"b"` 的次数。

提示：套用 9.3 的计数套路。

::: details 参考答案
<<< @/snippets/book/ch09_ex2_answer.spr

```text
{b: 3, a: 1, c: 1}
3
```
:::

**练习 3** 把两个映射相加：`{"a": 1, "b": 2}` 和 `{"b": 3, "c": 4}` 合并成 `{"a": 1, "b": 5, "c": 4}`——两边都有的键，值相加。

提示：遍历右边 `keys()`；`left[key]` 是 `Int?`，先收窄再决定写 `other` 还是 `old + other`。

::: details 参考答案
<<< @/snippets/book/ch09_ex3_answer.spr

```text
{a: 1, b: 5, c: 4}
```
:::

**练习 4** 用 `@std/sets` 处理 `["a", "b", "a", "c"]`，打印不同值的个数和列表。

提示：`sets.of(values)`，然后 `size()` 和 `to_list()`。

::: details 参考答案
<<< @/snippets/book/ch09_ex4_answer.spr

```text
3
[a, b, c]
```
:::

下一章把这一章和前面所有东西拼起来，写一个能玩的小程序：[小项目：猜数字](/tutorial/ch10-project-guess)。
