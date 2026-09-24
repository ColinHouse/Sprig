# 示例

`examples/` 下的每个文件都会在每次运行 `scripts/test.sh` 时被编译并执行。下面的
清单是真实文件，不是重新录入的副本。

## Hello world

<<< @/../examples/hello.spr

```text
Hello, Ada!
```

## FizzBuzz

<<< @/../examples/fizzbuzz.spr

```text
1
2
Fizz
4
Buzz
Fizz
7
8
Fizz
Buzz
11
Fizz
13
14
FizzBuzz
```

程序遍历 `range(1, 16)`，每行打印一个值：3 的倍数替换为 `Fizz`，5 的倍数替换为
`Buzz`，15 的倍数替换为 `FizzBuzz`。

## Shapes：类、variant 与 match

<<< @/../examples/shapes.spr

```text
circle r=2.0cm
area=12.56636
rect 3.0x4.0cm
area=12.0
radius=2.0
drawn=2
```

这个例子组合了带默认值的类、sealed `variant`、`enum`、两个穷尽 `match`、可空返回
类型以及不可变集合。

## 词频统计

<<< @/../examples/word_count.spr

```text
the: 3
quick: 1
brown: 1
fox: 2
jumps: 1
over: 1
lazy: 1
dog: 1
sorted keys: [brown, dog, fox, jumps, lazy, over, quick, the]
```

## 数值精度策略

<<< @/../examples/numeric_science.spr

```text
2.0
0.3
true
```

均值使用 binary64 `Float`，金额式的值使用 `Decimal`，因此 `0.1 + 0.2` 精确等于
`0.3`；最后一行把浮点误差显式展示出来，而不是隐藏它。

## 测试套件中的更大程序

- `tests/runtime/`：18 个带 golden stdout 的端到端程序，覆盖算术、函数、控制流、
  类、variant、enum、可空性、错误、集合、lambda、字符串、模块、断言、格式化与
  JVM 互操作。
- `tests/visitor/ast_visitor.spr`：完全用 Sprig 编写的小型 AST 解释器，包含四个
  visitor 类（打印、求值、化简、节点计数）；给 variant 增加 case 会让漏掉它的
  visitor 编译失败。
- `tests/visitor/mini_pipeline.spr`：自举可行性切片实验。
- `tests/numeric/`：受检算术、转换与精度测试，并带有独立的 Python oracle。
