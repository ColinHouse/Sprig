# 5. 集合

列表和映射，以及 Sprig 对"能改"和"不能改"的区分。

## 5.1 列表

<<< @/snippets/book/ch05_lists.spr

```text
[90, 72, 85, 60]
4
90
true
false
[ADA, BOB]
[90, 85]
[60, 72, 85, 90]
```

- `[90, 72, 85]` 这样的字面量，不写类型时是 `MutableList[Int]`：可以 `append`、`sort`、`set`、`insert`、`removeAt`。
- `scores[0]` 取元素，下标从 0 开始；越界在运行时报错。
- `x in xs` 和 `xs.contains(x)` 一样。
- `map`、`filter`、`forEach` 接收函数值（第 11 章），`fn(n: String) => ...` 是最简形式的 lambda。
- `sort()` 就地排序，没有返回值。

写错方法名时，报错会列出这个类型的全部方法，不用去翻文档：

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:2:7: Type MutableList[Int] has no method 'sorted'
  hint: MutableList[Int] methods: size, isEmpty, get, contains, indexOf, toMutableList, toList, map, filter, forEach, append, set, insert, removeAt, remove, clear, sort, toString.
```

## 5.2 List 和 MutableList

`names: List[String]` 写了类型，所以它是只读的 `List`。两种类型的关系是：

- `MutableList[T]` 可以当作 `List[T]` 使用：只读视角看的是**同一个**列表，不复制。
- 反过来不行。拿到一个 `List[T]`，要改就得 `toMutableList()` 复制一份。
- `toList()` 从 `MutableList` 复制出一个只读快照。

<<< @/snippets/book/ch05_readonly.spr

```text
6
10
4
5
```

`total` 只需要读，所以参数类型写 `List[Int]`；调用者手里的 `numbers` 是 `MutableList[Int]`，直接传进去。函数签名上写 `List` 就是在承诺"我不会改你的列表"，这个承诺由编译器保证：

### 故意写错：改只读列表

<<< @/snippets/book/ch05_immutable.spr

```text
SPR-COLLECTION-IMMUTABLE [TYPE] main.spr:2:1: Cannot call mutating method 'append' on an immutable collection; take an explicit snapshot with toMutableList()
```

::: tip 一条经验法则
函数参数和类字段写 `List`，除非确实要在里面改；局部变量用字面量，让它是 `MutableList`。这样"谁会改这个列表"一眼就能看出来。
:::

元素类型是不变的：`MutableList[MutableList[Int]]` 不能当作 `List[List[Int]]`，只有最外层可以切换到只读视角。

## 5.3 映射

<<< @/snippets/book/ch05_maps.spr

```text
3
12
null
13
tea
milk
rice
true
{tea: 12, rice: 20}
```

- `{"tea": 12, "milk": 3}` 是映射字面量；`Map[K, V]` 只读，`MutableMap[K, V]` 可改，关系和列表一样。
- `stock["rice"] = 20` 写入，`stock["tea"]` 读取。
- **读取的结果是 `Int?`**：键可能不存在，所以值可能是 `null`。打印 `stock["coffee"]` 得到 `null`；要对它做算术，先 `if tea != null:` 判断。第 8 章专门讲这件事。
- `keys()`、`values()`、`containsKey`、`remove`、`size`。`for key in stock:` 直接遍历映射时得到的也是键。映射保持插入顺序。

## 5.4 标准库里的列表工具

内置方法有意保持很少。排序出一个新列表、求和、查找这些，放在标准库模块 `@std/lists.spr` 里：

<<< @/snippets/book/ch05_std_lists.spr

```text
[banana, fig, pear]
[fig, pear, banana]
6
banana
true
[pear, fig, banana]
```

`import "@std/lists.spr" as lists` 把模块引进来，之后用 `lists.函数名` 调用。`sorted`、`sort_by`、`reversed` 都返回新列表，不改原来的。`find` 找不到时返回 `null`，所以它的返回类型是 `String?`。

标准库还有 `@std/text`（文本处理）、`@std/nulls`（可空值工具）、`@std/files`、`@std/json`、`@std/test` 等。`sprig api @std/lists.spr` 会列出一个模块的所有函数和注释。

## 小结

- 字面量是 `MutableList` / `MutableMap`；写类型 `List` / `Map` 得到只读版本。
- 可改的可以当只读的用（同一份数据），反过来要显式复制。
- 映射取值得到的是可空类型。
- 更多工具在 `@std/lists.spr` 里。

下一章进入类型系统部分：[类](/tutorial/ch06-classes)。
