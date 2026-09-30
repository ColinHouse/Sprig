# Canonical source formatting

`sprig fmt file.spr` formats a file in place. `sprig fmt .` formats the current
project's declared source tree (default `src/`), or the selected directory
when no project/source tree exists.
`sprig fmt --check file.spr` writes nothing and returns 1 if changes are needed.
Both commands accept `--json`, using the standard diagnostic envelope plus
`checkedFiles`, `changedFiles` and `checkOnly`.

The formatter uses four spaces per block, one space around binary operators,
commas followed by a space, and no spaces inside delimiters. Inline comments
are separated by two spaces; standalone comments retain their physical order
and indentation relative to the surrounding block. Repeated blank lines are
reduced to one. Strings and numeric literal spellings are preserved exactly.
It is deterministic and idempotent, with no configuration, style flags or
aggressive wrapping. Explicit invocation is the only way source is rewritten.

Source must parse first. Tabs remain invalid. All selected files are validated
before any writes, and each changed file is replaced atomically beside the
original, preserving POSIX permissions. Parse/format failure writes nothing;
I/O failure may occur after other files have been replaced, so this is not a
transaction spanning multiple files. Symbolic link replacement is refused.

Spaces and comments are lexer hidden-channel tokens. The layout adapter uses
only default-channel tokens; formatter trivia never enters semantic AST nodes.
Formatting is checked against the original parse-rule/terminal structure,
ignoring source locations. This prevents changes in operator grouping and
block structure. It does not execute or type-check a module to format it.

Continuation lines inside delimiters receive one extra indentation level.
There is no line wrapping or alignment algorithm. Ambiguous standalone comment
association follows its original column and surrounding indentation stack;
comments are never reordered or deleted.
