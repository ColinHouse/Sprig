# 11. 函数作为值

第 5 章的 `map`、`filter` 已经用到了 `fn(...) => ...`。这一章正式讲它。

## 11.1 lambda 和函数类型

<<< @/snippets/book/ch11_lambdas.spr

```text
42
20
7
45
15
[30, 80, 50]
3
8
5
```

- `fn(n: Int) => n * 2` 是一个函数值：参数要写类型，`=>` 后面是**一个表达式**，就是返回值。没有多行的 lambda 体；逻辑复杂就写一个具名函数，再在 lambda 里调用它。
- 函数值的类型写成 `fn(Int) -> Int`：参数类型列表和返回类型。`apply_twice` 的第二个参数就是这个类型。
- 函数值可以存进 `let`、作为参数传递、作为返回值。
- lambda 可以**捕获**外面的 `let` 绑定（`base`）。

用 `func` 定义的具名函数不加括号就是一个函数值：`apply_twice(5, triple)` 和 `apply_twice(5, fn(n: Int) => triple(n))` 完全一样。模块里的函数（`lists.sum`）和对象的方法（`counter.bump`）也可以这样引用；方法引用在创建时就记住了接收者。`print` 比较特殊：它能接受任何类型，所以只有在编译器知道参数类型的位置才能当值，比如 `scores.forEach(print)`；单独 `let p = print` 会被拒绝，提示写 lambda。泛型函数要写出类型参数（`identity[Int]`），因为 Sprig 从不从"值要去的地方"推断类型。

两条相关的限制：Java 方法和内置方法（`names.add`、`text.length`）不能直接引用，写 lambda；函数值不能用 `==` 比较（同一个名字的两次引用是两个值，比较只会让人困惑）。

### 故意写错：在调用里直接写 if 表达式

<<< @/snippets/book/ch11_if_in_call.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] ch11_if_in_call.spr:1:42: An if expression cannot be written inside parentheses, brackets or braces
  hint: Line breaks are ignored there, so the branches cannot go on their own lines. Bind the if expression, or the lambda that holds it, to a let first, then use the name.
```

圆括号里的换行会被忽略，而 if 表达式的每个分支都要单独占一行，所以它没法写进调用的括号里，写在 lambda 体里也不行。有两种改法：把逻辑写成具名函数，直接传函数名；或者先把整个 lambda 绑定到 `let`，再传名字：

<<< @/snippets/book/ch11_if_lambda.spr

```text
[small, big, big]
[odd, even, odd]
```

逻辑只用一次时，绑定 lambda 更省事；好几个地方都要用，就写成具名函数。

### 故意写错：捕获 var

<<< @/snippets/book/ch11_capture.spr

```text
SPR-TYPE-CAPTURE [TYPE] main.spr:3:16: Lambda captures mutable local 'counter'
  hint: Copy it into a 'let' binding before the lambda, or use a class field.
```

lambda 可能在任何时候、任何线程上被调用，那时 `counter` 是什么值？Sprig 直接不允许这种写法：lambda 只能看到不会变的东西。需要可变状态就放进类的字段（第 14 章的并发库也因此能保证任务体里没有数据竞争）。

## 11.2 会失败的函数值

<<< @/snippets/book/ch11_throws.spr

```text
[1, 2]
not a number: x
[1, 3]
```

- 一个 lambda 调用了 `throws Error` 的函数，它自己的类型就带上 `throws Error`：`parser` 的类型是 `fn(String) -> Int throws Error`。
- 接收这种函数值的参数要在类型里写出来：`convert: fn(String) -> T throws Error`。
- `map_all` 自己会不会抛出，取决于传进来的 `convert` 会不会，所以它的签名写 `rethrows`：传一个会抛出的函数值，调用处就要 `try`；传一个不会抛出的（`fn(s: String) => s.length()`），调用处就不用。最后一行 `map_all(["a", "bbb"], ...)` 不在 `try` 里，就是这个意思。

`rethrows` 让 `map`、`filter` 这类高阶函数既能接受会失败的函数，又不强迫所有调用者都写 `try`。

## 11.3 和 Java 的回调

Java 库里大量方法接收函数式接口（`Comparator`、`Runnable`、`Function`）。Sprig 的函数值可以直接传过去，第 13 章有例子。

## 小结

- `fn(参数: 类型) => 表达式`，类型写 `fn(T) -> R`；具名函数、模块函数、方法不加括号就是值，`print` 只在参数类型已知的位置是值。
- lambda 只能捕获 `let`，不能捕获 `var`。
- 调用会抛出的函数的 lambda 类型带 `throws`；高阶函数用 `rethrows` 把这个性质传给调用者。

至此类型系统部分结束。下一章进入工程部分：[模块与项目](/tutorial/ch12-modules-projects)。
