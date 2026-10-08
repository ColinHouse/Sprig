# 8. 列表

这一章你会学到：

- 为什么需要一个装很多值的容器；
- 列表字面量，按下标读和写；
- `append`、`size`、`insert`、`remove`、`removeAt`、`clear` 等常用方法；
- 下标越界在运行时会怎样；
- 为什么空列表字面量要写类型；
- `List` 和 `MutableList` 的关系：只读视角、快照和复制；
- 两个列表怎么比较、怎么排序；
- `@std/lists.spr` 里不需要函数值的那部分工具。

## 8.1 为什么需要列表

一个学生的成绩，存一个变量就够了：

```sprig
let score_ada = 90
let score_bob = 72
```

40 个学生呢？你不想起 40 个变量名。列表把一串同类型的值装在一起，用一个名字拿着：

<<< @/snippets/book/ch08_list_basics.spr

```text
[90, 72, 85]
3
90
85
175
[60, 72, 85, 100]
```

逐行读这个程序：

- `let scores = [90, 72, 85]`：`[...]` 是列表字面量，元素用逗号隔开。没写类型时，它是 `MutableList[Int]`，元素类型从内容推断成 `Int`。
- `print(scores)` 打印 `[90, 72, 85]`：方括号和逗号是列表的打印格式。如果元素是字符串，打印时不带引号。
- `scores.size()` 是元素个数。`size` 是方法，要点号调用、带括号。
- `scores[0]` 是第一个元素，下标从 0 开始；所以 `scores[2]` 是第三个元素 `85`。
- `scores[0] + scores[2]` 把两个元素当普通 `Int` 相加，得到 `175`。
- `scores[0] = 60` 改写第一个元素。
- `scores.append(100)` 在末尾追加一个元素。`append` 没有返回值，所以不能写 `print(scores.append(100))`。

读和写各有两副写法，效果一样：`scores[0]` 等于 `scores.get(0)`，`scores[0] = 60` 等于 `scores.set(0, 60)`。

## 8.2 一批常用方法

往列表里放东西和拿东西之外，还要问"在不在"、"是第几个"、删除元素：

<<< @/snippets/book/ch08_list_methods.spr

```text
true
true
1
-1
false
[Ada, Eve, Bob, Cyd]
true
[Eve, Bob, Cyd]
Eve
[Bob, Cyd]
Bob
2
[]
true
```

逐行读：

- `names.contains("Bob")` 问列表里有没有等于 `"Bob"` 的元素，`"Cyd" in names` 是同一件事的另一种写法。
- `names.indexOf("Bob")` 是它的位置，从 0 数；`names.indexOf("Zoe")` 找不到，得到 `-1`。
- `names.isEmpty()` 问列表是不是空的；这里还有元素，所以 `false`。
- `names.insert(1, "Eve")` 在下标 1 的位置插入，原来的 `"Bob"`、`"Cyd"` 往后挪一格。
- `names.remove("Ada")` 按**值**删除第一个等于 `"Ada"` 的元素，返回 `Bool` 表示有没有删到；删除后是 `[Eve, Bob, Cyd]`。
- `names.removeAt(0)` 按**下标**删除，返回被删掉的那个元素 `"Eve"`；列表变成 `[Bob, Cyd]`。
- `names.get(0)` 就是 `names[0]`，得到 `"Bob"`；`names.size()` 是 2。
- `names.clear()` 清空列表，打印成 `[]`；`isEmpty()` 这才变成 `true`。

常用方法一张表（都在可改列表 `MutableList` 上）：

| 方法 | 作用 |
|---|---|
| `size()` / `isEmpty()` | 元素个数 / 是否为空 |
| `get(i)` / `xs[i]` | 读下标 `i` |
| `set(i, x)` / `xs[i] = x` | 写下标 `i` |
| `append(x)` | 末尾追加 |
| `insert(i, x)` | 在下标 `i` 处插入 |
| `remove(x)` | 按值删除第一个，返回 `Bool` |
| `removeAt(i)` | 按下标删除，返回被删的元素 |
| `contains(x)` / `x in xs` | 是否含有 |
| `indexOf(x)` | 第一个位置，没有是 `-1` |
| `clear()` | 清空 |
| `sort()` | 就地升序排序 |

## 8.3 用循环处理列表

实际程序很少只处理一个元素，而是把列表过一遍。下面的程序同时做累加和变换，请对照追踪表读：

<<< @/snippets/book/ch08_list_loop.spr

```text
8
[8, 2, 6]
0: 4
1: 1
2: 3
```

第一个循环每次拿到一个元素 `n`，做两件事：把 `n` 加到 `total`，把 `n * 2` 追加到 `doubled`。跟着值走：

| 第几步 | `n` | `total` | `doubled` |
|---|---|---|---|
| 开始 | — | 0 | `[]` |
| 1 | 4 | 4 | `[8]` |
| 2 | 1 | 5 | `[8, 2]` |
| 3 | 3 | 8 | `[8, 2, 6]` |

输出 `8` 和 `[8, 2, 6]` 就是循环结束时的 `total` 和 `doubled`。

- `let doubled: MutableList[Int] = []` 先建一个空列表接结果。空字面量必须写元素类型，8.5 会看到不写会怎样。
- `total += n` 是 `total = total + n` 的简写。`total` 必须声明成 `var` 才能改。
- 第二个循环用 `range(0, numbers.size())` 生成下标 `0, 1, 2`；`i` 是 `Int`。
- `i.toString()` 把 `Int` 变成 `String`，再和别的字符串拼接。直接拼也行：`"n=" + i` 会得到 `n=2`，`+` 遇到字符串就做拼接；`i.toString()` 只是把"这里要文本"写得更明确。

## 8.4 下标越界的后果

下标必须落在 `0` 到 `size() - 1` 之间。超出范围不是编译错误：编译器不知道运行时列表有多长。它是运行时错误。

把 `ch08_oob.spr` 的内容存成 `main.spr` 再运行：

```bash
sprig run main.spr
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:2:1: List index 5 is out of bounds; size is 3
  hint: Check the list length before indexing. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

程序在这里停下，以非零状态退出，后面的语句不会执行。读法：错误码 `SPR-RUNTIME-EXCEPTION`、位置 `main.spr:2:1`、"下标 5 越界，长度是 3"，然后 `hint` 告诉你先检查长度再取下标。这类错误是你程序的 bug，正确的做法是让下标不越界，而不是"接住"它。

::: tip 学过其他语言？
很多语言越界会给出 `IndexOutOfBoundsException`/`IndexError` 之类的原始异常。Sprig 把它翻译成一条带错误码和提示的诊断，程序同样会停。下标从 0 开始、结束下标不含（`range(0, xs.size())` 正好覆盖所有下标）和 Python 一致，和 Java 的数组一致。
:::

## 8.5 空列表字面量要写类型（故意写错）

下面这段通不过检查：

<<< @/snippets/book/ch08_empty_list.spr

```text
SPR-TYPE-INFER [TYPE] main.spr:1:10: Cannot infer the type of an empty list literal; add a type annotation
```

`[]` 里一个元素都没有，编译器没有线索判断元素是什么类型，所以要求你写出来：

```sprig
let a: MutableList[Int] = []   # 以后要 append、set
let b: List[String] = []       # 只读
```

写了类型，之后往里放东西时元素类型就受检查；放错类型会得到类型错误，而不是运行时惊喜。

## 8.6 List 和 MutableList

Sprig 把"能改"和"不能改"写进类型：`MutableList[T]` 可改，`List[T]` 只读。很多函数只需要读，就在签名上写 `List`，向调用者承诺"我不会改你的列表"。`MutableList` 可以当 `List` 传——同一份数据，只是对方拿到只读视角。

<<< @/snippets/book/ch05_readonly.spr

```text
6
10
4
5
```

逐行读：

- `func total(xs: List[Int])` 遍历求和。调用者手里的 `numbers` 是 `MutableList`，直接传进去，不用复制。
- 前两行 6 和 10：`numbers.append(4)` 之后 `total(numbers)` 从 6 变成 10，说明函数看到的就是调用者那个列表。
- `let snapshot = numbers.toList()` 做一份只读快照；随后 `numbers.append(5)`，快照还是 4 个元素，原列表是 5 个。

`toList()` 和 `toMutableList()` 的复制行为值得单独记。看这个程序：

<<< @/snippets/book/ch08_list_views.spr

```text
[90, 72, 85]
[90, 72, 85]
[90, 72, 85, 60]
[1, 2]
[1, 2, 3]
[90, 72, 85, 60, 100]
[90, 72, 85, 60, 100]
```

对照输出的七行：

- `let view: List[Int] = grades` 是只读**视角**，同一份数据。`grades.append(85)` 之后 `view` 里也有 85（第 1 行）。
- `toList()` 复制一份只读快照。之后 `grades.append(60)`，快照还是三个元素（第 2 行），原列表四个（第 3 行）。
- `let frozen: List[Int] = [1, 2]` 是只读列表；`frozen.toMutableList()` 复制出可改副本 `copy`。改 `copy` 不影响 `frozen`（第 4、5 行）。
- `grades.toMutableList()`：`grades` 本来就是可改的，所以不复制，返回的是**同一个**列表（第 6、7 行都多了 100）。`toMutableList()` 只在只读列表上才是"复制"。

::: tip 学过其他语言？
`List` 不是 Java 的 `Collections.unmodifiableList` 包装，也不是把数据冻住：它只是同一个集合的只读视角，元素本身（如果元素是可改的集合）不受保护。要独立的一份，用 `toList()` 快照或 `toMutableList()` 复制。
:::

### 故意写错：改只读列表

<<< @/snippets/book/ch05_immutable.spr

```text
SPR-COLLECTION-IMMUTABLE [TYPE] main.spr:2:1: Cannot call mutating method 'append' on an immutable collection; take an explicit snapshot with toMutableList()
```

`append`、`set`、`insert`、`remove`、`removeAt`、`clear`、`sort` 这些改内容的方法只存在于 `MutableList`。变量是 `List` 时用它们，编译器直接拒绝，并提示用 `toMutableList()` 换成可改的。

## 8.7 比较和排序

列表可以用 `==` 比较内容，用 `sort()` 排序：

<<< @/snippets/book/ch08_list_compare.spr

```text
true
false
true
[banana, fig, pear]
[72, 85, 90]
```

- `a == b` 为 `true`：两个列表长度相同，每个位置的元素按 `==` 相等。
- `a == c` 为 `false`：元素一样但顺序不同，也不相等。列表比较看内容，不看是不是同一个列表。
- `words.sort()` 就地按升序排好字符串，打印得到 `[banana, fig, pear]`。`sort()` 没有返回值，所以它自己单独占一行，不能写进 `print`。数字列表同理。

## 8.8 @std/lists：不用函数值的工具

内置方法有意保持少；批量操作放在标准库模块 `@std/lists.spr` 里：

<<< @/snippets/book/ch08_std_lists.spr

```text
[banana, fig, fig, pear]
[pear, fig, banana, fig]
[fig, banana, fig, pear]
[pear, fig, banana]
1
6
[pear, fig]
[fig]
```

- `import "@std/lists.spr" as lists` 导入模块，之后用 `lists.函数名` 调用。
- `lists.sorted(words.toList())` 返回排好序的新列表，原来的 `words` 不变（第 2 行还是原顺序）。这里先 `toList()` 做只读快照：把一个可改列表直接传给 `lists.sorted`，当前实现会连原列表一起排序（模块注释说不会改输入，这是实现和注释不一致的地方）。
- `lists.reversed(words)` 返回倒序的新列表，`lists.distinct(words)` 每个值只保留第一次出现的位置。
- `lists.index_of(words, "fig")` 是 `"fig"` 第一次出现的下标；`lists.sum([1, 2, 3])` 是求和。
- `lists.take(words, 2)` 取前两个，`lists.drop(words, 3)` 丢掉前三个。
- 这里选的是不需要函数值的函数。`map`、`filter`、`sort_by`、`find` 这些要接收函数值，第 15 章讲完函数作为值再用。

## 本章小结

- `[1, 2, 3]` 是列表字面量，默认是 `MutableList[Int]`；`let xs: List[Int] = [...]` 得到只读列表。
- 下标从 0 开始；越界是运行时错误 `SPR-RUNTIME-EXCEPTION`，不是编译错误。
- 空字面量 `[]` 必须写元素类型，否则 `SPR-TYPE-INFER`。
- `MutableList` 可以当 `List` 传，是同一份数据的只读视角；`toList()` 做只读快照；`toMutableList()` 是只读列表的复制，对可改列表返回它自己。
- 改内容的方法只在 `MutableList` 上；写在 `List` 上是 `SPR-COLLECTION-IMMUTABLE`。
- `==` 按内容和顺序比较；`sort()` 就地升序排序。
- `@std/lists.spr` 提供 `sorted`、`reversed`、`distinct`、`index_of`、`sum`、`take`、`drop` 等；需要函数值的函数留到第 15 章。

## 动手练习

**练习 1（热身）** 把 `[3, 1, 4, 1, 5]` 里的每个元素加 10，放进一个新列表并打印。

提示：先 `let out: MutableList[Int] = []`，用 `for n in numbers:` 遍历，循环里 `out.append(n + 10)`。

::: details 参考答案
<<< @/snippets/book/ch08_ex1_answer.spr

```text
[13, 11, 14, 11, 15]
```
:::

**练习 2** 写一个函数 `largest(xs: List[Int]) -> Int`，返回列表里最大的元素。假设列表非空。

提示：先把 `xs[0]` 当作临时最大值；循环里遇到更大的就替换它。

::: details 参考答案
<<< @/snippets/book/ch08_ex2_answer.spr

```text
9
```
:::

**练习 3** 给名字列表 `["Ada", "Bob", "Cyd"]` 打印带编号的名单：`1. Ada`、`2. Bob`、`3. Cyd`。

提示：`for i in range(0, names.size())`，编号是 `i + 1`；数字和字符串可以直接用 `+` 拼，参考答案显式写了 `.toString()`。

::: details 参考答案
<<< @/snippets/book/ch08_ex3_answer.spr

```text
1. Ada
2. Bob
3. Cyd
```
:::

**练习 4** 用 `@std/lists` 处理 `[3, 1, 3, 2, 1]`：打印去重后的列表和倒序列表。

提示：`import "@std/lists.spr" as lists`，然后用 `lists.distinct(raw)` 和 `lists.reversed(raw)`。

::: details 参考答案
<<< @/snippets/book/ch08_ex4_answer.spr

```text
[3, 1, 2]
[1, 2, 3, 1, 3]
```
:::

下一章把单列的列表扩展成"按键找值"的映射，以及不许重复的集合：[映射和集合](/tutorial/ch09-maps-sets)。
