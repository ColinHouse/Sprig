# 4. 函数

## 4.1 定义和调用

<<< @/snippets/book/ch04_functions.spr

```text
12
Hello, Ada!
3628800
```

- `func 名字(参数: 类型, ...) -> 返回类型:`，函数体缩进。
- **每个参数和返回类型都要写。** 局部变量的类型可以推断，签名不行：签名是给读代码的人和调用者看的契约。
- 不返回值的函数写 `-> Unit`。
- 函数可以递归调用自己。
- 参数在函数体内不可改，和 `let` 一样。

调用时参数按位置传，不能写名字：`area(3, 4)`，而不是 `area(width=3, height=4)`。写名字的调用只用于构造类的实例（第 6 章）。没有默认参数值，也没有可变参数。

函数只能定义在文件的顶层，不能嵌套在另一个函数里；需要"局部的小函数"时用第 11 章的 lambda。

## 4.2 每条路径都要返回

### 故意写错：漏了一个分支

<<< @/snippets/book/ch04_missing_return.spr

```text
SPR-FLOW-MISSING-RETURN [FLOW] main.spr:1:1: Function 'sign' must return String on every path
```

`n == 0` 时这个函数会走到末尾而没有返回值。编译器做的是流程分析（错误类别写着 `FLOW`）：它检查每一条执行路径是否都以 `return`（或 `throw`）结束。补上 `else:` 分支或在末尾加一个 `return` 就好。

`-> Unit` 的函数不需要 `return`；写 `return` 不带值也可以，用来提前结束。

## 4.3 入口在哪里

<<< @/snippets/book/ch04_main.spr

```text
from main
```

Sprig 没有特殊地位的 `main`。想要一个 `main` 函数是可以的（`sprig init` 生成的模板就是这样写的），但要记得在顶层调用它。顶层语句按出现顺序执行，函数、类的定义可以放在任何位置，调用它们的顶层语句之前或之后都行。

## 4.4 Unit 不是值

一个返回 `Unit` 的函数，它的"结果"不能被绑定：

```sprig
let r = greet("Ada")   # SPR-TYPE-UNIT: Cannot bind a Unit result to a variable
```

这避免了"把一个打印函数的返回值存起来"这类几乎总是写错了的代码。

## 小结

- 签名上的类型必须写，函数体里可以推断。
- 参数按位置传；无默认值、无可变参数、无嵌套函数。
- 返回值不是 `Unit` 的函数，每条路径都要 `return`。
- 没有特殊的 `main`，顶层语句就是入口。

下一章：[集合](/tutorial/ch05-collections)。
