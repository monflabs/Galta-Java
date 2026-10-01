// Proper tail calls: far deeper than the Java stack allows without them
const N = 100000;

function countDown(n) {
    if (n === 0) {
        return "done";
    }
    return countDown(n - 1);
}
assertEquals("done", countDown(N));

// Tail positions: conditional branches, logical and comma operators, blocks,
// loops, switch, catch and finally, arrow bodies, member calls
function viaConditional(n) { return n === 0 ? "c" : viaConditional(n - 1); }
assertEquals("c", viaConditional(N));
function viaLogical(n) { return n === 0 || viaLogical(n - 1); }
assertEquals(true, viaLogical(N));
function viaComma(n) { if (n === 0) return "comma"; return (0, viaComma(n - 1)); }
assertEquals("comma", viaComma(N));
function viaLoop(n) { while (true) { if (n === 0) return "loop"; return viaLoop(n - 1); } }
assertEquals("loop", viaLoop(N));
function viaSwitch(n) { switch (n) { case 0: return "switch"; default: return viaSwitch(n - 1); } }
assertEquals("switch", viaSwitch(N));
function viaCatch(n) { if (n === 0) return "catch"; try { throw null; } catch (e) { return viaCatch(n - 1); } }
assertEquals("catch", viaCatch(N));
function viaFinally(n) { if (n === 0) return "finally"; try { } finally { return viaFinally(n - 1); } }
assertEquals("finally", viaFinally(N));
const viaArrow = n => n === 0 ? "arrow" : viaArrow(n - 1);
assertEquals("arrow", viaArrow(N));
const o = { m(n) { return n === 0 ? this : this.m(n - 1); } };
assertEquals(o, o.m(N));

// Mutual recursion
function isEven(n) { return n === 0 ? true : isOdd(n - 1); }
function isOdd(n) { return n === 0 ? false : isEven(n - 1); }
assertEquals(true, isEven(N));

// Not a tail call: the result is used, so the value must be computed normally
function sum(n) { return n === 0 ? 0 : n + sum(n - 1); }
assertEquals(5050, sum(100));

// A tail call to a function used as a constructor, and to a native function
function Point(x) { this.x = x; }
function make(x) { return new Point(x); }
assertEquals(3, make(3).x);
function max(a, b) { return Math.max(a, b); }
assertEquals(7, max(3, 7));
