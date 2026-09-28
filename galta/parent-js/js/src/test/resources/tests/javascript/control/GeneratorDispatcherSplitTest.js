// Dispatcher-mode codegen (ASTVarContainer.transpilerDeclareFunctionClasses'
// shared-switch-class branch, functionsSize>DEFAULT_MAX_FUNCTIONS_BEFORE_DISPATCHER
// (50)) puts every sibling function of a container into ONE Java class as
// f_N methods. A generator needing eager parameter binding used to emit a
// fixed-name `initGeneratorParams` override regardless - a second sibling
// generator needing the same split collided (duplicate method, transpile
// compile failure). Padded with plain functions past the 50-function
// dispatcher threshold, then three generators with declared params/defaults
// sharing that same dispatcher class - each must bind ITS OWN default eagerly
// (before next()), not another sibling's.

let calls = [];
function pad0() { return 0; }
function pad1() { return 1; }
function pad2() { return 2; }
function pad3() { return 3; }
function pad4() { return 4; }
function pad5() { return 5; }
function pad6() { return 6; }
function pad7() { return 7; }
function pad8() { return 8; }
function pad9() { return 9; }
function pad10() { return 10; }
function pad11() { return 11; }
function pad12() { return 12; }
function pad13() { return 13; }
function pad14() { return 14; }
function pad15() { return 15; }
function pad16() { return 16; }
function pad17() { return 17; }
function pad18() { return 18; }
function pad19() { return 19; }
function pad20() { return 20; }
function pad21() { return 21; }
function pad22() { return 22; }
function pad23() { return 23; }
function pad24() { return 24; }
function pad25() { return 25; }
function pad26() { return 26; }
function pad27() { return 27; }
function pad28() { return 28; }
function pad29() { return 29; }
function pad30() { return 30; }
function pad31() { return 31; }
function pad32() { return 32; }
function pad33() { return 33; }
function pad34() { return 34; }
function pad35() { return 35; }
function pad36() { return 36; }
function pad37() { return 37; }
function pad38() { return 38; }
function pad39() { return 39; }
function pad40() { return 40; }
function pad41() { return 41; }
function pad42() { return 42; }
function pad43() { return 43; }
function pad44() { return 44; }
function pad45() { return 45; }
function pad46() { return 46; }
function pad47() { return 47; }
function pad48() { return 48; }
function pad49() { return 49; }
function pad50() { return 50; }
function pad51() { return 51; }

function* ga(a = (calls.push('a'), 100)) { yield a; }
function* gb(b = (calls.push('b'), 200)) { yield b; }
function* gc(c = (calls.push('c'), 300)) { yield c; }

// Padding functions still callable/correct (dispatcher switch reaches them).
let padSum = pad0() + pad1() + pad2() + pad3() + pad4() + pad5() + pad6() + pad7() + pad8() + pad9() + pad10() + pad11() + pad12() + pad13() + pad14() + pad15() + pad16() + pad17() + pad18() + pad19() + pad20() + pad21() + pad22() + pad23() + pad24() + pad25() + pad26() + pad27() + pad28() + pad29() + pad30() + pad31() + pad32() + pad33() + pad34() + pad35() + pad36() + pad37() + pad38() + pad39() + pad40() + pad41() + pad42() + pad43() + pad44() + pad45() + pad46() + pad47() + pad48() + pad49() + pad50() + pad51();
assertEquals((51*52)/2, padSum);

// Each generator's default must bind synchronously at call time (before
// next()), and only ITS OWN helper must run - not a sibling's.
let ia = ga();
assertEquals(1, calls.length);
assertEquals('a', calls[0]);
let ib = gb();
assertEquals(2, calls.length);
assertEquals('b', calls[1]);
let ic = gc();
assertEquals(3, calls.length);
assertEquals('c', calls[2]);

assertEquals(100, ia.next().value);
assertEquals(200, ib.next().value);
assertEquals(300, ic.next().value);

// Re-run in a different order to catch any index mixup (e.g. always
// dispatching to the first/last registered helper).
calls = [];
let ic2 = gc();
let ia2 = ga();
let ib2 = gb();
assertEquals(['c','a','b'].join(','), calls.join(','));
assertEquals(300, ic2.next().value);
assertEquals(100, ia2.next().value);
assertEquals(200, ib2.next().value);
