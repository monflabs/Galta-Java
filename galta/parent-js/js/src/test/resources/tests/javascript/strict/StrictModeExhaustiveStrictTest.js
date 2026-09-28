// Intentionally NO "use strict" at the top level of this file, and env.isStrictMode() is
// false too: every strict-mode behavior asserted below comes from an explicit "use strict"
// directive on the individual function/eval'd code, not from the environment.

// arguments.callee poison-pill for genuinely strict functions is covered by
// tests.javascript.strict.ArgumentsCalleeStrictTest (interpreted-mode only -- the
// transpiler's arguments object never has a poison-pill callee).

(function testStrict_ArgumentsNotMapped() {
  // A function's own "use strict" directive disables arguments-object mapping,
  // even though the surrounding script/environment is not strict.
  function f(a) {
    "use strict";
    a = 7;
    return arguments[0];
  }
  assertEquals(1, f(1));

  function g(a) {
    "use strict";
    arguments[0] = 9;
    return a;
  }
  assertEquals(1, g(1));
})();

(function testStrict_EvalOwnDirectiveIsRecognized() {
  // The eval'd string's own "use strict" directive makes assigning to eval/arguments
  // inside it a SyntaxError, even though the calling code here is not strict.
  assertThrows(function() {
    eval('"use strict"; eval = 1;');
  });
  assertThrows(function() {
    eval('"use strict"; arguments = 1;');
  });
})();

(function testStrict_EvalInheritsCallerStrictness() {
  // A direct eval also inherits strictness from its caller, independently of
  // any directive in the eval'd string itself.
  (function() {
    "use strict";
    assertThrows(function() {
      eval('eval = 1;');
    });
  })();
})();

(function testStrict_EvalParseTimeInheritsCallerStrictness() {
  // A direct eval must force the eval'd text to PARSE as strict when the
  // calling context is strict, even when the eval'd text has no "use
  // strict" of its own - closing the parse-time half of strict-mode
  // inheritance (runtime semantics were already covered above).
  (function() {
    "use strict";

    // `with` is an early SyntaxError in strict code.
    assertThrows(function() { eval('with({}) {}'); }, SyntaxError);

    // A legacy octal literal is an early SyntaxError in strict code.
    assertThrows(function() { eval('var x = 010;'); }, SyntaxError);

    // "eval"/"arguments" as a declared binding name is an early SyntaxError
    // in strict code.
    assertThrows(function() { eval('var eval;'); }, SyntaxError);
    assertThrows(function() { eval('var arguments;'); }, SyntaxError);
  })();

  // Indirect eval must NEVER inherit the caller's strictness, regardless of
  // how the calling context threads its own strictness. (Deliberately not
  // "var eval;" here - a bare `var eval;` via sloppy indirect eval at global
  // scope clobbers the real global eval() with undefined, a separate,
  // pre-existing GaltaJS var-hoisting bug unrelated to strictness
  // inheritance - orthogonal to what this assertion is checking.)
  (function() {
    "use strict";
    var indirectEval = eval;
    try {
      indirectEval('var arguments;');
    } catch (e) {
      fail("indirect eval must not inherit caller strictness: " + e);
    }
  })();
})();

(function testStrict_OctalLiteralRejectedInStrictFunction() {
  // Direct (non-eval) baseline: a legacy octal literal is an early
  // SyntaxError in an ordinary strict function, via its own directive.
  assertThrows(function() { eval('(function(){"use strict"; return 010;})'); }, SyntaxError);
})();

(function testStrict_EvalArgumentsBindingNameRejectedDirectly() {
  // Direct (non-eval) baselines: "eval"/"arguments" as a declared binding
  // name is an early SyntaxError via an ordinary strict directive - as a
  // parameter name, and as a function's own declared name.
  assertThrows(function() { eval('(function(eval){"use strict";})'); }, SyntaxError);
  assertThrows(function() { eval('(function(arguments){"use strict";})'); }, SyntaxError);
  assertThrows(function() { eval('(function(){"use strict"; function eval(){}})'); }, SyntaxError);
})();

(function testStrict_DuplicateParameterNames() {
  // Sloppy-mode ordinary function with a SIMPLE parameter list: duplicates
  // tolerated, last one wins (unaffected by this fix).
  function sloppySimple(a, b, a) { return a; }
  assertEquals(2, sloppySimple(1, 9, 2));

  // Strict-mode ordinary function: duplicates are a SyntaxError, even with
  // a simple parameter list.
  assertThrows(function() { eval('(function(a, b, a){"use strict";})'); }, SyntaxError);

  // Sloppy-mode ordinary function with a NON-simple parameter list (a
  // default value here) - duplicates are a SyntaxError regardless of
  // strict-mode-ness, since UniqueFormalParameters applies.
  assertThrows(function() { eval('(function(a, b = 1, a){})'); }, SyntaxError);

  // Destructured/rest parameters introducing the same name are also
  // caught, even across different parameter positions/shapes.
  assertThrows(function() { eval('(function(a, {a}){})'); }, SyntaxError);
  assertThrows(function() { eval('(function(a, ...a){})'); }, SyntaxError);

  // Arrow/method/generator/async functions always require unique
  // parameter names, even in sloppy mode with a simple parameter list.
  assertThrows(function() { eval('(a, a) => a;'); }, SyntaxError);
  assertThrows(function() { eval('({m(a, a){}});'); }, SyntaxError);
  assertThrows(function() { eval('(function*(a, a){});'); }, SyntaxError);
  assertThrows(function() { eval('(async function(a, a){});'); }, SyntaxError);
})();

(function testStrict_CallerArgumentsPoisonPill() {
  // A genuinely strict function's "caller"/"arguments" own properties are
  // poisoned (throw on get/set) based on the FUNCTION's own strictness, not
  // the strictness of the code accessing it (this whole file's surrounding
  // code is sloppy).
  function strictFn() { "use strict"; }
  assertThrows(function() { void strictFn.caller; });
  assertThrows(function() { void strictFn.arguments; });
  assertThrows(function() { strictFn.caller = 1; });
  assertThrows(function() { strictFn.arguments = 1; });

  // A sloppy function accessed from this (sloppy) code must NOT be poisoned.
  function sloppyFn() {}
  try {
    void sloppyFn.caller;
    void sloppyFn.arguments;
  } catch (e) {
    fail();
  }
})();
