// Early errors are reported when the code is parsed, before anything runs
// (an indirect eval parses the code as global code)
function parseError(code) {
	assertThrows(function() { (0, eval)(code); }, SyntaxError);
}

// Invalid assignment targets
parseError('function g() { 1 = 2; }');
parseError('function g() { x++ = 1; }');
parseError('function g() { a?.b = 1; }');
parseError('function g() { f() &&= 1; }');
parseError('function g() { "use strict"; f() = 1; }');
parseError('function g() { new.target = 1; }');
parseError('function g() { ({a}) = 1; }');
parseError('function g() { [a] += 1; }');
// Annex B: a call is still a (runtime ReferenceError) target in sloppy code
eval('function g() { f() = 1; }');

// Destructuring patterns and parameters
parseError('function f([...x, y]) {}');
parseError('function f(...a,) {}');
parseError('function f(...a = 1) {}');
parseError('var [...x,] = [];');
parseError('var {...r, a} = {};');
parseError('let [a.b] = [];');
parseError('function f(a = 0) { "use strict"; }');

// Regular expression literals
parseError('function g() { return /(?<a>.)\\k<>/u; }');
parseError('function g() { return /a/gg; }');

// Declarations
parseError('{ var f; let f; }');
parseError('{ let f; { var f; } }');
parseError('{ function f() {} async function f() {} }');
parseError('switch (0) { case 1: let f; default: var f; }');
parseError('do let x; while (false)');
parseError('while (false) class C {}');
parseError('if (true) function* g() {}');

// Class bodies
parseError('var C = class { constructor() { super(); } };');
parseError('function f() { super.x; }');
parseError('var C = class { x = arguments; };');
parseError('var C = class { constructor; };');
parseError('var C = class { get a(p) {} };');
parseError('class C { static { var await; } }');
parseError('var C = class let {};');

// Operators, literals and jumps
parseError('-3 ** 2');
parseError('0 && 0 ?? true');
parseError('"use strict"; var x; delete x;');
parseError('0_1');
parseError('1__0');
parseError('07n');
parseError('"\\u1"');
parseError('"\\x4"');
parseError('function f() { "\\1"; "use strict"; }');
parseError('continue;');
parseError('while (0) { break Q; }');
parseError('L: x = 1; while (0) { continue L; }');
parseError('new.target');
parseError('function f() { export default 1; }');
assertEquals(9, (-3) ** 2);
assertEquals(1_000, 1000);
var total = 0;
outer: for (var i = 0; i < 3; i++) { for (var j = 0; j < 3; j++) { if (j == 1) continue outer; if (i == 2) break outer; total++; } }
assertEquals(2, total);

// Still valid
{ function h() { return 1; } function h() { return 2; } }
assertEquals(2, h());
if (true) function k() { return 7; }
assertEquals(7, k());
var a, b, o = {};
[a, ...o.rest] = [1, 2, 3];
({x: o.x, ...b} = {x: 9, y: 8});
assertEquals('1,2,9,8', [a, o.rest.length, o.x, b.y].join());
class Base { m() { return 1; } }
class Derived extends Base { f = super.m(); constructor() { super(); this.v = (() => super.m())(); } }
assertEquals(2, new Derived().v + new Derived().f);
