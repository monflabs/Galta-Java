// strict_mode_sloppy.js
// Intentionally NO "use strict" at top: this file tests sloppy-mode behavior.

// Helper to robustly get the global object in sloppy scripts.
function getGlobalObject() {
  // In sloppy code, Function("return this")() should be the global object.
  return Function("return this")();
}

(function testSloppy_Basics() {
  const g = getGlobalObject();
  assertTrue(typeof g === "object" && g !== null);

  // In sloppy mode, `this` in a plain function call is the global object.
  function f() { return this; }
  assertEqualsStrict(g, f());

  // In sloppy mode, primitives passed as `this` are boxed.
  function typeOfThis() { return typeof this; }
  assertEquals("object", typeOfThis.call(1)); // Number object wrapper

  // Undeclared assignment creates/sets a global property (sloppy).
  // Use a unique name to reduce collision risk.
  var name = "__sloppy_undeclared_" + Math.random().toString(16).slice(2);
  // Assign via eval so we can use a dynamic identifier.
  eval(name + " = 123;");
  assertEquals(123, g[name]);

  // Deleting a `var` binding is allowed in sloppy mode and returns false.
  var x = 1;
  var delResult = delete x;
  assertFalse(delResult);

  // `with` is allowed in sloppy mode.
  var withResult;
  var obj = { a: 10 };
  with (obj) {
    withResult = a + 1;
  }
  assertEquals(11, withResult);

  // An undeclared assignment inside `with` still creates a global (sloppy).
  var wname = "__sloppy_with_global_" + Math.random().toString(16).slice(2);
  with (obj) {
    eval(wname + " = 11;");
  }
  assertEquals(11, g[wname]);
})();

(function testSloppy_DuplicateParams_Octal() {
  // Duplicate parameters are allowed in sloppy mode.
  function dup(a, a) { return a; }
  assertEquals(2, dup(1, 2));

  // Legacy octal literal is allowed in sloppy script (Annex B).
  // Some environments may disable this; if your engine supports it, it should be 8.
  assertEquals(8, eval("010"));

  // Legacy octal escape in string is allowed in sloppy.
  assertEquals("A", eval("'\\101'")); // \101 == 'A'
})();

// Mapped-arguments linkage (arguments[i] aliasing named parameters both ways) is
// interpreted-mode only -- see tests.javascript.functions.ArgumentsMappingTest, which
// skips itself in transpiled mode instead of asserting behavior the transpiler doesn't
// implement.

(function testSloppy_ArgumentsCallee_Caller() {
  // arguments.callee is permitted in sloppy (non-strict).
  function self() { return arguments.callee; }
  assertEqualsStrict(self, self());

  // In sloppy, function.caller/function.arguments should not be poison-pill throwing.
  // (Value may be null or something implementation-defined-ish, but should not throw.)
  function nf() {}
  try {
    void nf.caller;
    void nf.arguments;
  } catch (e) {
    fail();
  }
})();

(function testSloppy_EvalLeaksBindings() {
  // In sloppy, direct eval may introduce var bindings into the surrounding scope.
  eval("var __sloppy_eval_var = 321;");
  assertEquals(321, __sloppy_eval_var);
})();

(function testSloppy_EvalArgumentsAsIdentifiers() {
  // In sloppy, `eval` and `arguments` can be used as identifiers (this is a SyntaxError in strict mode).
  // Note: this hoisted `var eval` shadows the builtin for the whole function, so it must be
  // in its own scope, separate from an actual eval(...) call.
  var eval = 1;
  var arguments = 2;
  assertEquals(1, eval);
  assertEquals(2, arguments);
})();

(function testSloppy_FunctionConstructorStrictness() {
  // Function constructor body is sloppy unless it contains "use strict".
  var f1 = Function("return this");
  var f2 = Function('"use strict"; return this');

  assertEqualsStrict(getGlobalObject(), f1());
  assertUndefined(f2());
})();