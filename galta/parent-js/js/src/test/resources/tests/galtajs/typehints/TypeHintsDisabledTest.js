// supportTypeHints() defaults to false - none of TypeScript's erased type
// syntax is tolerated, byte-for-byte the same as before this feature
// existed.

assertParseError("let x: number = 5;");
assertParseError("function f(a: number) {}");
assertParseError("function f(a?: number) {}");
assertParseError("function f(): number { return 1; }");
assertParseError("const f = (a: number): string => a.toString();");
assertParseError("class C { x: number = 1; }");
assertParseError("class C implements Foo {}");
assertParseError("type X = number;");
assertParseError("interface X { a: number; }");
assertParseError("declare let x: number;");

// "type"/"interface"/"declare"/"implements" still parse as ordinary
// identifiers - unaffected either way by the flag.
let type = 5;
type = type + 1;
assertEquals(6, type);

let interface = 5;
assertEquals(5, interface);

let declare = 5;
assertEquals(5, declare);

let implements = 5;
assertEquals(5, implements);

// Ordinary, unannotated code is completely unaffected.
let plain = 5;
assertEquals(5, plain);
