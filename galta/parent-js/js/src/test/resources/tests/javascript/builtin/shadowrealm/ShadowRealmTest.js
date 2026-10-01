// ShadowRealm: a separate realm, reachable through primitives and wrapped functions

const r = new ShadowRealm();
assertEquals(3, r.evaluate("1 + 2"));
assertEquals("string", r.evaluate("typeof 'x'"));

// Lexical declarations don't outlive an evaluation, var declarations do
r.evaluate("const c = 1; var v = 2;");
assertEquals(2, r.evaluate("v"));
assertEquals("undefined", r.evaluate("typeof c"));

// The realm has its own intrinsics and global object
r.evaluate("globalThis.shared = 42; Array.prototype.extra = 1;");
assertEquals(42, r.evaluate("shared"));
assertEquals(undefined, globalThis.shared);
assertEquals(undefined, [].extra);

// Functions cross the boundary wrapped, in both directions
const add = r.evaluate("(a, b) => a + b");
assertEquals(5, add(2, 3));
assertEquals(2, add.length);
assertEquals(Function.prototype, Object.getPrototypeOf(add));
const apply = r.evaluate("(f, x) => f(x) * 2");
assertEquals(14, apply(x => x + 2, 5));

// Objects don't cross, errors become TypeErrors of the caller realm
assertThrows(TypeError, () => r.evaluate("({})"));
assertThrows(TypeError, () => add({}, 1));
assertThrows(TypeError, () => r.evaluate("throw new Error('inside')"));
assertThrows(SyntaxError, () => r.evaluate("let let"));
assertThrows(TypeError, () => r.evaluate(1));
assertThrows(TypeError, () => ShadowRealm());
