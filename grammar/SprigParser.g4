// Sprig v0.7 syntax grammar. ANTLR4 + separate Java layout token source.
// This grammar recognizes syntax, NOT static types, nullability, exhaustiveness,
// read-only collection rules, constructor call categories or JVM API validity.
parser grammar SprigParser;
options { tokenVocab=SprigLexer; }

// Imports have one canonical location: at the start of a module.
program
    : NEWLINE* (importStatement NEWLINE NEWLINE*)*
      (NEWLINE | classDefinition | enumDefinition | variantDefinition
      | functionDefinition | statement)* EOF
    ;

importStatement: IMPORT (qualifiedName | STRING) (AS IDENT)?;
qualifiedName: IDENT (DOT IDENT)*;

classDefinition: CLASS IDENT COLON classSuite;
classSuite
    : NEWLINE INDENT (NEWLINE | fieldDeclaration NEWLINE | functionDefinition | PASS NEWLINE)+ DEDENT
    ;
fieldDeclaration: (VAR | LET) IDENT COLON typeRef (ASSIGN expression)?;

enumDefinition: ENUM IDENT COLON enumSuite;
enumSuite: NEWLINE INDENT (NEWLINE | IDENT NEWLINE)+ DEDENT;

// A sealed sum type: each variant case has immutable named fields.
// The semantic checker verifies unique cases, recursive type references,
// constructor field types, and exhaustive match coverage.
variantDefinition: VARIANT IDENT COLON variantSuite;
variantSuite: NEWLINE INDENT (NEWLINE | variantCase)+ DEDENT;
variantCase: IDENT (LPAREN variantFields RPAREN)? NEWLINE;
variantFields: variantField (COMMA variantField)* COMMA?;
variantField: IDENT COLON typeRef;

functionDefinition
    : FUNC IDENT LPAREN parameters? RPAREN ARROW typeRef
      (THROWS typeRef (COMMA typeRef)*)? COLON suite
    ;
parameters: parameter (COMMA parameter)* COMMA?;
parameter: IDENT COLON typeRef;

// Named functions/methods have explicit parameter and return types.
// Local bindings can infer their type from their initializer.
typeRef: qualifiedName (LBRACK typeRef (COMMA typeRef)* RBRACK)? QUESTION?;
variableDeclaration: (VAR | LET) IDENT typeAnnotation? ASSIGN expression;
typeAnnotation: COLON typeRef;

statement
    : simpleStatement NEWLINE
    | ifStatement | whileStatement | forStatement | tryStatement | matchStatement
    ;
simpleStatement
    : variableDeclaration | assignment | RETURN expression? | BREAK
    | CONTINUE | PASS | THROW expression | expression
    ;
assignment: assignmentTarget assignmentOperator expression;
assignmentTarget: IDENT (DOT IDENT | LBRACK expression RBRACK)*;
assignmentOperator: ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | STAR_ASSIGN | SLASH_ASSIGN;

ifStatement: IF expression COLON suite
    (ELIF expression COLON suite)* (ELSE COLON suite)?;
whileStatement: WHILE expression COLON suite;
forStatement: FOR IDENT IN expression COLON suite;
tryStatement
    : TRY COLON suite catchClause+ (FINALLY COLON suite)?
    | TRY COLON suite FINALLY COLON suite
    ;
catchClause: CATCH IDENT COLON typeRef COLON suite;
suite: NEWLINE INDENT (NEWLINE | statement)+ DEDENT;

// Match is a STATEMENT, not also an expression. A branch matches exactly one
// enum case or variant case, with an optional binding of the entire payload.
// The type checker rejects non-variant/non-enum scrutinees, duplicate branches,
// missing cases, and bindings on payloadless enum cases. No wildcard/default.
matchStatement: MATCH expression COLON matchSuite;
matchSuite: NEWLINE INDENT (NEWLINE | matchBranch)+ DEDENT;
matchBranch: CASE qualifiedName (AS IDENT)? COLON suite;

expression: orExpression;
orExpression: andExpression (OR andExpression)*;
andExpression: notExpression (AND notExpression)*;
notExpression: NOT notExpression | comparisonExpression;
// No chained comparisons, assignment expressions or pipe operators.
comparisonExpression: additiveExpression (comparisonOperator additiveExpression)?;
comparisonOperator: EQEQ | NEQ | LT | LE | GT | GE | IN;
additiveExpression: multiplicativeExpression ((PLUS | MINUS) multiplicativeExpression)*;
multiplicativeExpression: unaryExpression ((STAR | SLASH | PERCENT) unaryExpression)*;
unaryExpression: (PLUS | MINUS) unaryExpression | postfixExpression;
postfixExpression
    : primaryExpression (DOT IDENT | LPAREN arguments? RPAREN
      | LBRACK expression RBRACK)*
    ;
// Agent-friendly: positional and named arguments cannot mix syntactically.
// The checker ALSO enforces category: Sprig class/variant constructors are
// named-only; ordinary functions and JVM calls use positional arguments.
arguments: positionalArguments | namedArguments;
positionalArguments: expression (COMMA expression)* COMMA?;
namedArguments: namedArgument (COMMA namedArgument)* COMMA?;
namedArgument: IDENT ASSIGN expression;
primaryExpression
    : literal | IDENT | LPAREN expression RPAREN
    | listLiteral | mapLiteral | lambdaExpression
    ;
listLiteral: LBRACK (expression (COMMA expression)* COMMA?)? RBRACK;
mapLiteral: LBRACE (mapEntry (COMMA mapEntry)* COMMA?)? RBRACE;
mapEntry: expression COLON expression;
lambdaExpression: FN LPAREN parameters? RPAREN FAT_ARROW expression;
literal: INT | FLOAT | STRING | TRUE | FALSE | NULL;
