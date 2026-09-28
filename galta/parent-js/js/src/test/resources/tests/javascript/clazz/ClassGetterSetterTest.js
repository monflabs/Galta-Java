class A {
	a = 3
	get b() {
		return this.a;
	}
	set b(v) {
		this.a = v;
	}
}

const a = new A();

assertEquals(3,a.a)
assertEquals(3,a.b)
a.b = 5;
assertEquals(5,a.a)
assertEquals(5,a.b)


class B {
  b = 7
  get b() {
    return 8;
  }
}

const b = new B();
assertEquals(7,b.b)

// Private accessors: get #x()/set #x() must parse and dispatch like their
// public counterparts.
class C {
	#value = 1;
	get #accessor() {
		return this.#value;
	}
	set #accessor(v) {
		this.#value = v;
	}
	read() {
		return this.#accessor;
	}
	write(v) {
		this.#accessor = v;
	}
	static hasField(o) {
		return #value in o;
	}
}
const c = new C();
assertEquals(1, c.read());
c.write(9);
assertEquals(9, c.read());
assertEquals(true, C.hasField(c));
assertEquals(false, C.hasField({}));



//
// `accessor` auto-accessor class elements (decorators proposal): a getter/
// setter pair on the prototype (or the class, when static) over a private
// backing slot - never an own data property of the instance.
//
{
  class C { accessor x = 1; static accessor s = 10; accessor [Symbol.iterator] = 5; accessor noInit; }
  const d = Object.getOwnPropertyDescriptor(C.prototype, "x");
  assertEquals("function", typeof d.get);
  assertEquals("function", typeof d.set);
  assertFalse(d.enumerable);
  assertEquals("get x", d.get.name);
  assertEquals("set x", d.set.name);
  assertEquals(0, Object.getOwnPropertyNames(new C()).length);
  assertEquals(1, new C().x);
  const a = new C(), b = new C();
  a.x = 7;
  assertEquals(7, a.x);
  assertEquals(1, b.x);
  assertEquals(5, a[Symbol.iterator]);
  assertUndefined(a.noInit);
  assertEquals(10, C.s);
  C.s = 11;
  assertEquals(11, C.s);
  assertEquals("function", typeof Object.getOwnPropertyDescriptor(C, "s").get);
  assertThrows(TypeError, () => d.get.call({}));
  class D extends C {}
  const dd = new D();
  dd.x = 3;
  assertEquals(3, dd.x);
  assertEquals(1, new C().x);
  // "accessor" stays an ordinary identifier / field name elsewhere
  let accessor = 4;
  assertEquals(4, accessor);
  class F { accessor = 9; }
  assertEquals(9, new F().accessor);
}
