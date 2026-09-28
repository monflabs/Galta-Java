// TypeScript-style type hints: parsed for syntax only, then discarded.
// Every annotation below must have zero effect on runtime behavior -
// the assertions verify the underlying program executes exactly as it
// would with all annotations stripped.

// Variable declarations
{
	let x: number = 5;
	assertEquals(5, x);

	let u: number | string = 5;
	assertEquals(5, u);

	let arr: Array<number> = [1, 2, 3];
	assertEquals(3, arr.length);

	let nested: Map<string, Array<number>> = null;
	assertEquals(null, nested);

	let obj: { a: number, b: string } = { a: 1, b: 'x' };
	assertEquals(1, obj.a);

	let fn: (a: number) => string = (a) => a.toString();
	assertEquals('5', fn(5));

	let tuple: [number, string] = [1, 'a'];
	assertEquals(1, tuple[0]);

	let suffix: number[] = [1, 2, 3];
	assertEquals(3, suffix.length);

	let k: keyof any = 'a';
	assertEquals('a', k);

	const c: number = 42;
	assertEquals(42, c);
}

// Function parameters and return types
{
	function add(a: number, b: string): number {
		return a;
	}
	assertEquals(1, add(1, 'x'));

	function optional(a?: number) {
		return a;
	}
	assertUndefined(optional());

	function optionalSpaced(a ?: number) {
		return a;
	}
	assertUndefined(optionalSpaced());

	function rest(...args: number[]) {
		return args.length;
	}
	assertEquals(3, rest(1, 2, 3));

	function identity<T>(x: T): T {
		return x;
	}
	assertEquals(5, identity(5));

	function withDefault(a: number = 5) {
		return a;
	}
	assertEquals(5, withDefault());

	function destructure({ a, b }: { a: number, b: number }) {
		return a + b;
	}
	assertEquals(3, destructure({ a: 1, b: 2 }));
}

// Arrow functions
{
	const f: (a: number) => string = (a: number): string => a.toString();
	assertEquals('5', f(5));

	const opt = (a?: number) => a;
	assertUndefined(opt());

	const curried = (a: number): (b: number) => number => (b: number) => a + b;
	assertEquals(3, curried(1)(2));
}

// Classes
{
	class Point {
		x: number = 1;
		y: number;
		m(a: number): number {
			return a;
		}
	}
	assertEquals(1, new Point().x);
	assertEquals(5, new Point().m(5));

	class Box<T> {
		value: T;
		label?: string;
		constructor(v: T, label?: string) {
			this.value = v;
			this.label = label;
		}
		map<R>(f: (v: T) => R): Box<R> {
			return new Box(f(this.value), this.label);
		}
	}
	assertEquals(5, new Box(5).value);
	const boxed: Box<number> = new Box(21, "life");
	const mapped: Box<number> = boxed.map((v: number): number => v * 2);
	assertEquals(42, mapped.value);
	assertEquals("life", mapped.label);

	class Base<T> {}
	class Sub extends Base<number> {}
	assertNotNull(new Sub());

	interface Shape {}
	class Circle implements Shape {}
	assertNotNull(new Circle());

	// A computed method name ("[Symbol.iterator]") must still be recognized
	// as a method (not misparsed as a field) once generic methods are
	// supported alongside it.
	class Range {
		[Symbol.iterator]() {
			let i: number = 0;
			return { next: () => i < 3 ? { value: i++, done: false } : { value: undefined, done: true } };
		}
	}
	assertArrayEquals([0, 1, 2], [...new Range()]);

	// Object spread of a called expression must not be misparsed as a
	// method definition either (the spread's own leading "..." must never
	// be treated as a one-token PropertyName).
	const spread: { a: string, b: string } = { a: "N", ...(() => ({ b: "M" }))() };
	assertEquals("N", spread.a);
	assertEquals("M", spread.b);
}

// type / interface / declare statements - produce no runtime effect at all
{
	type MyNumber = number;
	let typed: MyNumber = 5;
	assertEquals(5, typed);

	type Box<T> = { value: T };

	interface Named {
		name: string;
		greet(x: number): void;
	}

	interface Base2 {
		a: number;
	}
	interface Extended extends Base2 {
		b: number;
	}

	declare let ambientVar: number;
	declare function ambientFn(a: number): void;
	declare class AmbientClass {
		x: number;
	}

	type Conditional<T> = T extends string ? true : false;
	type Indexed = { a: number }['a'];

	assertTrue(true);
}

// "type"/"interface"/"declare"/"implements" must still work as ordinary
// identifiers everywhere outside a declaration-starting position.
{
	let type = 5;
	type = type + 1;
	assertEquals(6, type);

	let interface = 5;
	assertEquals(5, interface);

	let declare = 5;
	assertEquals(5, declare);

	let implements = 5;
	assertEquals(5, implements);
}
