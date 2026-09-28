// Breaking/continuing out of a loop, or out of a labeled block, must keep the
// last completion value produced by the body (UpdateEmpty semantics) - the
// break/continue signal itself carries no value of its own and must not reset
// it to undefined.

assertEquals( undefined, eval("do { break; } while (false)") );
assertEquals( 3, eval("do { 3; break; } while (false)") );
assertEquals( 3, eval("while (true) { 3; break; }") );
assertEquals( 3, eval("for (;;) { 3; break; }") );
assertEquals( 3, eval("for (const x in {a:1}) { 3; break; }") );
assertEquals( 3, eval("for (const x of [1]) { 3; break; }") );
assertEquals( 5, eval("test262id: { 5; break test262id; 9; }") );

// A `continue` back to the loop condition must also preserve the value from
// the completed iteration, for when the loop naturally exits afterwards.
assertEquals( 2, eval("for (let i = 0; i < 3; i++) { i; continue; }") );

// A var/let/const declaration's own completion is always empty (UpdateEmpty
// semantics): evaluating the initializer sets the shared completion-value
// slot as a side effect, so the declaration must restore the PRIOR value
// rather than leaking the initializer's value or forcing it to undefined.
assertEquals( 4, eval("4; const test262id5 = 5;") );
assertEquals( 6, eval("6; let test262id7 = 7, test262id8 = 8;") );
assertEquals( 9, eval("9; var test262id10 = 10;") );
assertEquals( undefined, eval("var test262id11 = 11;") );

// An `if` statement's own test-expression evaluation must not pollute the
// completion value: when the taken branch's own completion is empty (a bare
// `break`/`continue`), the enclosing loop's accumulated value must show
// through instead of the test expression's own (e.g. boolean) result.
assertEquals( 3, eval("for (let i = 0; i < 5; i++) { if (i === 3) { 3; break; } }") );

// But an `if` statement's OWN completion also applies UpdateEmpty(stmtCompletion,
// undefined) to whichever branch it takes (IfStatement Evaluation, last step) -
// so a bare `break` (empty completion) inside the taken branch resolves to the
// if-statement's own completion value undefined right there, BEFORE the
// enclosing loop ever gets a chance to substitute its own accumulated value:
// undefined is a real completion value (not "empty"), so it blocks the loop's
// V from showing through, unlike the case above where the taken branch's own
// completion already has a real value (3) by the time the if-statement's
// UpdateEmpty runs. Confirmed against test262's
// language/statements/for/head-init-{expr,var}-check-empty-inc-empty-completion.js,
// which assert `undefined` for this exact if/break/else-with-a-value shape.
assertEquals( undefined, eval("for (let i = 0; i < 5; i++) { if (i === 3) break; else i; }") );
