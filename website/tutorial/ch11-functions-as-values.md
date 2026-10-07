# 11. 函数作为值

第 5 章的 `map`、`filter` 已经用到了 `fn(...) => ...`。这一章正式讲它。

## 11.1 lambda 和函数类型

<<< @/snippets/book/ch11_lambdas.spr

```text
42
20
7
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

用 `func` 定义的具名函数**不是**值：`apply_twice(5, factorial)` 会报 `SPR-TYPE-NOT-CALLABLE`，要写成 `fn(n: Int) => factorial(n)`。这样所有能当值传的函数在代码里长得都一样。

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

- `fn(参数: 类型) => 表达式`，类型写 `fn(T) -> R`；具名函数要包一层才能当值。
- lambda 只能捕获 `let`，不能捕获 `var`。
- 调用会抛出的函数的 lambda 类型带 `throws`；高阶函数用 `rethrows` 把这个性质传给调用者。

至此类型系统部分结束。下一章进入工程部分：[模块与项目](/tutorial/ch12-modules-projects)。
