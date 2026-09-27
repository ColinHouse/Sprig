# Expression match

Statement match remains the multi-statement control-flow form. Expression match
maps one enum/variant value to a result, with exactly one expression per case:

```sprig
let value = match result:
    case Result.Ok as ok:
        ok.value
    case Result.Error:
        0
```

Every reachable case must be listed. No default/wildcard is supported. Case
ownership and binders obey the statement match rules. Binders exist only in
that branch. Expressions propagate throws from all branches conservatively.
The scrutinee executes once; only the selected branch expression executes.

An expected type (an annotation, parameter or return type) checks every branch
using ordinary Sprig assignability, including contextual numeric literals.
Without context, the first non-null successful branch establishes the type;
other non-null branches must be assignable to it. Null branches make that type
nullable under ordinary `T?` rules. All-null results require an annotation.
There is no common-supertype/union inference or new numeric promotion. Unit
results are rejected: use statement match for side-effect-only branching.

Branches cannot contain declarations, return statements or multiple statements.
Use statement match for that work. This is not a general block expression or a
block lambda. Direct initializer and return positions, nested branch expressions
and expression-lambda bodies are supported. Indentation blocks inside grouping
delimiters (calls, lists, parentheses) are not supported by the current layout
adapter. Bind the match result to a local before composing it in those positions.

Java generation uses a Java 17 switch expression containing an explicitly typed
scrutinee temporary and conditional `yield` branches. No closure captures are
introduced, erased payloads use the existing boxing/unboxing rules, and Sprig
result typing is checked before Java is generated.
