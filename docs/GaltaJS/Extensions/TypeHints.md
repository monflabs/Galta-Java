# Type Hints

With `supportTypeHints`, GaltaJS accepts TypeScript-style type annotations and type declarations and ignores them: parameters, return types, variable types, generics, `interface`, `type` and `declare` statements parse, produce nothing, and never influence execution. It lets a script be written, edited and type-checked with TypeScript tooling while the engine runs it directly, with no transpilation step. There is no type checking and no code generation for types; a `string` flowing into a `number` parameter is not an error.

## Scope

The goal of type hints is to deal with the **type system** only: to let a script carry TypeScript's static type information, so that it can be written and checked with TypeScript tooling and run as is. They are not a way to add language features: nothing that is not native to JavaScript is introduced through them. TypeScript constructs that exist at run time rather than in the type system - constructs that generate code or values, as opposed to annotations that are erased - are out of scope, and a script using type hints behaves exactly like the same script with its annotations removed.

**Status: experimental.** The parser support is recent and still being extended; the exact subset below is what the grammar accepts today. The flag is on in `enableGaltaJSExtensions()` (hence in `GaltaJSEnvironment`) and off in `JavaScriptEnvironment`.

## What is accepted

| Construct | Example |
|---|---|
| Variable annotations | `let count: number = 2;` |
| Parameter annotations, optional parameters, rest parameters | `function f(a: number, b?: string, ...rest: any[])` |
| Return type annotations, on functions, methods, getters, setters | `function f(): string {}` |
| Arrow functions with parameter and return types | `const shout = (s: string): string => s.toUpperCase();` |
| Function types in annotations | `let fn: (a: number) => string = a => a.toString();` |
| Generics on functions, methods, classes, `extends` clauses | `function id<T>(x: T): T`, `class Box<T> extends Base<number>` |
| `implements` on classes | `class C implements A, B {}` |
| Class field annotations | `name: string = 'box';` |
| Union, intersection, conditional, indexed, array and tuple types, `keyof`, `typeof`, `infer`, `readonly` inside types | `type Id = string \| number;` |
| `type` aliases, `interface` declarations, `declare` statements | Each is parsed and dropped |

Sample: `doc_examples/TypeHintsExamples.java` (`testAnnotationsAreParsedAndIgnored`)

```js
interface Named { name: string }
type Id = string | number;

function label(id: Id, named?: Named): string {
	return `${id}:${named?.name ?? 'anonymous'}`;
}
const shout = (s: string): string => s.toUpperCase();

class Box<T> implements Named {
	name: string = 'box';
	constructor() {}
	wrap<U>(value: U): U[] { return [value]; }
}
const b = new Box();
let count: number = 2;
count = count * 2;
[label(1), label('x', b), shout('ok'), b.wrap(count)[0]]   // => ['1:anonymous', 'x:box', 'OK', 4]
```

No checking takes place:

```java
JSEnvironment env = GaltaJSEnvironment.create();
assertEquals("11", env.evaluateScript("function twice(n: number): number { return n + n } twice('1')"));
```

The words `type`, `interface`, `declare`, `implements`, `keyof`, `infer` and `readonly` are not reserved: they stay valid identifiers outside a type position.

```java
assertEquals(11, env.evaluateScript("let type = 5; const interface = 6; type = type + interface; type"));
```

Sample: `doc_examples/TypeHintsExamples.java` (`testNoTypeChecking`, `testTypeWordsRemainValidIdentifiers`)

## What is not accepted

These TypeScript constructs are parse errors (`JSParseException`):

- Type assertions: `x as T`, `<T>x`, and `satisfies`.
- Non-null assertions: `value!`.
- `enum`, `namespace`, `module` declarations.
- Parameter properties: `constructor(private x: number)`.
- Member modifiers: `public`, `private`, `protected`, `abstract`, `override`.
- Type arguments at call and construction sites: `new Box<number>()`, `f<string>(x)`.
- `import type` / `export type`.
- A `?` after a destructuring pattern in a parameter list: `{a, b}?: T`.

```java
for(String script : new String[] {
		"let x = 5 as number;",            // type assertions
		"enum Color { Red }",              // enums
		"class C { private x = 1 }",       // member modifiers
		"new Box<number>()",               // type arguments at call sites
}) {
	try {
		env.evaluateScript(script);
		fail("expected a parse error for: " + script);
	} catch(JSParseException e) {
		// not part of the supported subset
	}
}
```

Sample: `doc_examples/TypeHintsExamples.java` (`testUnsupportedTypeScriptSyntax`)

## Enabling it

```java
try {
	JavaScriptEnvironment.create().evaluateScript("let x: number = 5;");
	fail();
} catch(JSParseException e) {
	// plain ECMAScript: the annotation is a syntax error
}
JSEnvironment env = JavaScriptEnvironment.newBuilder().supportTypeHints(true).build();
assertEquals(5, env.evaluateScript("let x: number = 5; x"));
```

Sample: `doc_examples/TypeHintsExamples.java` (`testDisabledByDefault`)

## How it works

The annotations are skipped in the JavaCC grammar with peek-only scanners (`scanTypeEnd`, `scanTypeParametersEnd`) and skip productions (`skipType`, `skipTypeParameters`, `skipInterfaceBody`, `skipDeclareSignature`) guarded by `LOOKAHEAD({ env.supportTypeHints() && ... })`. With the flag off the grammar is byte-for-byte the plain ECMAScript one. Type statements produce an empty node that `StatementList` drops, so nothing reaches the AST, the optimizer or the transpiler.

## Gotchas

- Type hints do not create bindings: `interface Named {}` followed by `Named` in an expression is a `ReferenceError`.
- Because the words are not reserved, `type = 1` assigns a variable rather than starting a type alias; a `type` alias must look like `type Name = ...`.
- Generic call syntax (`f<T>(x)`) is a parse error even though `function f<T>()` is accepted.
- Optional-parameter markers are accepted but have no runtime effect beyond what JavaScript already does (`undefined`).

## Source

`parser/JSParser.jj` (`FormalParameter`, `skipType`, `scanTypeEnd`, `TypeAliasStatement`, `InterfaceStatement`, `DeclareStatement`), `JSConfiguration.java` (`supportTypeHints`), `ConfigurationImpl.java`, tests `js/src/test/resources/tests/galtajs/typehints/`.
