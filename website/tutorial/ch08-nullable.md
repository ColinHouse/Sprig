# 8. 可空值

"可能没有值"是程序里最常见的错误来源。Sprig 把它写进类型：`Int` 一定有值，`Int?` 可能是 `null`。两者不是同一个类型，编译器不允许把后者当前者用。

## 8.1 问号类型

<<< @/snippets/book/ch08_nullable.spr

```text
found at 1
true
null
```

- `-> Int?` 表示这个函数可能返回 `null`：按名字找，不一定找得到。
- 没有问号的类型里不能放 `null`：`let n: Int = null` 不能通过。
- `index != null` 判断过以后，在那个分支里 `index` 就是 `Int`，可以直接拼进字符串。这叫**收窄**（narrowing）。
- 可以直接 `print` 一个可空值，也可以用 `==` 和 `null` 或别的值比较。

上一章的映射取值 `stock["tea"]`、第 2 章的 `"12".toIntOrNull()`，返回的都是可空类型，用法一样。

### 故意写错：不判断就用

<<< @/snippets/book/ch08_deref.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:2:7: Operator '+' cannot use a value that may be null (expected non-null operands, actual Int? and Int)
  hint: count may be null (Int?): check it first with 'if count != null:', and inside that block it is Int, or give a fallback with or_else from @std/nulls.spr.
```

提示给了两条路：先判断，或者用标准库的 `or_else` 给个默认值。下面分别看。

## 8.2 收窄发生在哪些位置

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

四种写法，都是编译器能看懂的：

1. **`if x == null: return ...`，提前返回。** 之后的代码里 `x` 不再可空。`double` 和 `describe` 都是这样。
2. **`if x != null:` 的分支里。** 包括后面的 `elif` 和 `else`：`describe` 里 `count == null` 已经返回了，所以 `elif` 和 `else` 里 `count` 是 `Int`。
3. **`and` 的右边。** `value != null and value > 0`：右边只在左边为真时求值，所以那里 `value` 有值。
4. **`or` 的右边。** `text == null or text.length() == 0`：右边只在 `text` 不为 `null` 时求值。

这些规则只有一个前提：被判断的名字是 **`let` 绑定、参数或循环变量**，也就是不可改的。

### 故意写错：对 var 收窄

<<< @/snippets/book/ch08_var_narrowing.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:3:11: Operator '+' cannot use a value that may be null (expected non-null operands, actual Int? and Int)
  hint: count may be null (Int?), and a var never narrows: copy it into a let (let current = count), check 'if current != null:', and use current inside that block.
```

一个 `var` 在判断之后可能已经被别处改掉了（比如判断和使用之间调用了一个会改它的函数，或者在 lambda 里），所以编译器不对它做任何假设。修法就是提示说的：先 `let current = count`，再判断 `current`。这也是第 2 章建议默认用 `let` 的原因。

类的字段同样不收窄，因为任何方法调用都可能改它。读到局部的 `let` 里再判断。

## 8.3 没有 `?.` 和 `??`

很多语言有安全调用 `a?.b` 和默认值运算符 `a ?? b`。Sprig 没有这两个运算符，可空值的处理只有上面的 `if`，以及标准库 `@std/nulls.spr` 里的几个函数：

<<< @/snippets/book/ch08_nulls.spr

```text
12
0
8080
port must be a number: eighty
```

- `or_else(value, fallback)`：有值就用它，否则用默认值。
- `require(value, message)`：有值就返回它，否则抛出带这条消息的 `Error`。它的签名有 `throws Error`，所以 `port` 也得声明。下一章讲这个。

这两个函数在读代码时比运算符更显眼，这是有意的：每个 `null` 被吞掉的地方都应该能一眼看到。

## 8.4 可空值可以放进集合

`List[Int?]` 是"元素可能为 null 的列表"，`Map[String, Int?]` 同理。但注意区分 `List[Int]?`（列表本身可能为 null）和 `List[Int?]`。Java 方法返回的对象在 Sprig 里一律是可空的，第 13 章再说。

## 小结

- `T?` 可能为 `null`，`T` 一定有值，两者不通用。
- 四个收窄位置：提前返回、`if != null` 的分支（含 elif/else）、`and` 右边、`or` 右边。
- 只有不可改的名字（`let`、参数、循环变量）能收窄；`var` 和字段先复制到 `let`。
- 没有 `?.`、`??`；默认值用 `nulls.or_else`，"必须有"用 `nulls.require`。

下一章：[错误处理](/tutorial/ch09-errors)。
