// A private auto-accessor mints a real private getter/setter pair over a
// hidden backing slot, rather than being stored as a plain private field.

// --- instance -------------------------------------------------------------
class Inst {
	accessor #x = 5;
	accessor #undef;
	get x() { return this.#x; }
	set x(v) { this.#x = v; }
	getUndef() { return this.#undef; }
	static has(o) { return #x in o; }
}
var i = new Inst();
assertEquals(5, i.x);
i.x = 42;
assertEquals(42, i.x);
assertEquals(undefined, i.getUndef());
// The brand check sees the accessor's own private name.
assertTrue(Inst.has(i));
assertFalse(Inst.has({}));

// Two instances have independent backing slots.
var i2 = new Inst();
assertEquals(5, i2.x);
i.x = 1;
assertEquals(5, i2.x);

// --- static ---------------------------------------------------------------
class Stat {
	static accessor #x = 5;
	static get x() { return Stat.#x; }
	static set x(v) { Stat.#x = v; }
}
assertEquals(5, Stat.x);
Stat.x = 42;
assertEquals(42, Stat.x);

// --- several private accessors coexist ------------------------------------
class Many {
	accessor #a = 1;
	accessor #b = 2;
	sum() { return this.#a + this.#b; }
}
assertEquals(3, new Many().sum());

// A private accessor of one class is not reachable on another class's
// instance, even with an identically-spelled name.
class OtherA { accessor #x = 1; static read(o) { return o.#x; } }
class OtherB { accessor #x = 2; }
assertEquals(1, OtherA.read(new OtherA()));
assertThrows(TypeError, () => OtherA.read(new OtherB()));

// --- ClassBody early errors: duplicate private names ----------------------
// Spec 15.7.1: a private name may appear once, EXCEPT a getter/setter pair
// of matching static-ness.
assertParseError("class C { #x = 5; #x = 42; }");
assertParseError("class C { #x = 5; get #x() {} }");
assertParseError("class C { #x = 5; #x() {} }");
assertParseError("class C { get #x(){} get #x(){} }");
assertParseError("class C { accessor #x = 5; accessor #x = 42; }");
assertParseError("class C { accessor #x = 5; #x = 42; }");
assertParseError("class C { accessor #x = 5; get #x() {} }");
assertParseError("class C { accessor #x = 5; set #x(v) {} }");
assertParseError("class C { accessor #x = 5; #x() {} }");
assertParseError("class C { accessor #x = 5; static #x = 42; }");
assertParseError("class C { static accessor #x = 5; #x = 42; }");
assertParseError("class C { static accessor #x = 5; static #x = 42; }");
// A get/set pair must agree on static-ness.
assertParseError("class C { get #x(){} static set #x(v){} }");
assertParseError("class C { static get #x(){} set #x(v){} }");

// The legal forms must still parse.
class Pair { get #x() { return 1; } set #x(v) {} read() { return this.#x; } }
assertEquals(1, new Pair().read());
class PairRev { set #x(v) {} get #x() { return 2; } read() { return this.#x; } }
assertEquals(2, new PairRev().read());
class PairStatic { static get #x() { return 3; } static set #x(v) {} static read() { return PairStatic.#x; } }
assertEquals(3, PairStatic.read());
// Same private name in two different (and in nested) classes is fine.
class N1 { #x = 1; read() { return this.#x; } }
class N2 { #x = 2; read() { return this.#x; } }
assertEquals(1, new N1().read());
assertEquals(2, new N2().read());

// --- public auto-accessors: a repeated name is legal, the last one wins ----
class Dup { accessor x = 0; accessor x = 1; }
assertEquals(1, new Dup().x);

// Distinct symbols that share a description must NOT share a backing slot.
var s1 = Symbol("k"), s2 = Symbol("k"), s3 = Symbol(), s4 = Symbol();
class Syms { accessor [s1] = 1; accessor [s2] = 2; accessor [s3] = 3; accessor [s4] = 4; }
var sy = new Syms();
assertEquals(1, sy[s1]);
assertEquals(2, sy[s2]);
assertEquals(3, sy[s3]);
assertEquals(4, sy[s4]);

// A `static {}` initialization block binds no private name, so several of them
// coexist with private members (they report as "private" internally, and once
// tripped the duplicate check above).
class Blocks {
	#x = 1;
	static #y = 2;
	static seen = 0;
	static { Blocks.seen++; }
	static { Blocks.seen++; }
	read() { return this.#x; }
	static readY() { return Blocks.#y; }
}
assertEquals(2, Blocks.seen);
assertEquals(1, new Blocks().read());
assertEquals(2, Blocks.readY());
