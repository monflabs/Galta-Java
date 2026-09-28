// Function.prototype.toString() must return the exact original source text
// (including comments/whitespace), not a re-serialization of the AST -
// verified exhaustively by test262's built-ins/Function/prototype/toString
// suite, which runs in a single well-defined (non-transpiled,
// non-decompiled) execution mode. This file runs under GaltaJS's own
// 4-mode regression sweep (interpreted, interpreted+optimized, transpiled,
// decompiled+re-parsed): interpreted and transpiled mode both recover the
// exact original text (transpiled functions store their source [start,end)
// offsets and reslice the unit's retained source on demand - see
// BuiltinFunctionTranspiler.getOriginalSource()); decompiled mode
// re-serializes source (losing comments/original formatting). So an
// exact-text comparison isn't mode-agnostic, but the presence of the
// function's own body tokens (parameters, "return", etc.) is - and that
// also distinguishes real recovered source from the `[unavailable]`
// placeholder a source-less function would fall back to.

function namedFn(a, b) { return a + b; }
const anon = function (x) { return x; };
const arrow = (a, b) => a + b;
function* gen() { yield 1; }
async function asyncFn() { return 1; }

// A user-defined function's toString() is always a non-empty string.
assertTrue(typeof namedFn.toString() === "string" && namedFn.toString().length > 0);
assertTrue(typeof anon.toString() === "string" && anon.toString().length > 0);
assertTrue(typeof arrow.toString() === "string" && arrow.toString().length > 0);
assertTrue(typeof gen.toString() === "string" && gen.toString().length > 0);
assertTrue(typeof asyncFn.toString() === "string" && asyncFn.toString().length > 0);

// Real source text is recovered (not the `[unavailable]` fallback, which has
// empty "()" params and no body tokens) in every mode that retains source -
// i.e. all but the [native code] built-in case. Body tokens survive
// decompiled mode's re-serialization too, so these hold mode-agnostically.
assertTrue(namedFn.toString().includes("return"));
assertTrue(namedFn.toString().includes("b"));
assertTrue(anon.toString().includes("return"));
assertTrue(gen.toString().includes("yield"));
assertTrue(asyncFn.toString().includes("return"));
assertTrue(!namedFn.toString().includes("[unavailable]"));

// A native/builtin function always falls back to a generic stub (never real
// source), regardless of execution mode.
assertTrue(Array.prototype.map.toString().includes("[native code]"));

// %Function.prototype% itself
assertTrue(Function.prototype.toString().includes("[native code]"));

// Calling toString on a non-callable receiver throws TypeError.
assertThrows(TypeError, () => Function.prototype.toString.call({}));
