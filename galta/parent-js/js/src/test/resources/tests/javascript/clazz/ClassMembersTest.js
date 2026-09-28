//
// Private fields and methods (accessed via this.#x / obj?.#x)
//
class Counter {
  #count = 0;

  increment() {
    this.#count++;
    return this.#count;
  }

  get count() {
    return this.#count;
  }

  #reset() {
    this.#count = 0;
  }

  resetPublic() {
    this.#reset();
    return this.#count;
  }
}

const counter = new Counter();
assertEquals(1, counter.increment());
assertEquals(2, counter.increment());
assertEquals(2, counter.count);
assertEquals(0, counter.resetPublic());

// A private member and a public member of the same name don't collide
// (each has its own storage, unlike a naive same-key implementation).
class NoCollision {
  #method() {
    return 42;
  }
  get method() {
    return this.#method();
  }
}
const nc = new NoCollision();
assertEquals(42, nc.method);

// Optional chaining on a private member access.
class OptionalPrivate {
  #value = 7;
  getValue(obj) {
    return obj?.#value;
  }
}
const op = new OptionalPrivate();
assertEquals(7, op.getValue(op));
assertEquals(undefined, op.getValue(null));



class MyClass {
  static {
    this.s1 = 5
  } 
  static s1 = 6

  static s2 = 6
  static {
    this.s2 = 5
  } 
  
  static _sf = 6;
  static sf() {
	return MyClass._sf;
  }
  static sg() {
  	return MyClass.sf() + 4;
  }
  
  static ['_sfp'] = 5;
  static get ['sfp']() {
	return this._sfp;
  }
  static set ['sfp'](v) {
  	this._sfp = v;
  }

  a
  b = 1
  bb = this.b + 2; 
  ['x'] = 66
  
  constructor () {
    this.c = 4;
  }
  f() {
		return this.c+2;
  }
  g() {
		return this.f()*2;
  }
  ['h']() {
	return 8;	
  }
  c = 55
  
  _pp = 4
  get pp() {
	return this._pp;
  }
  set pp(v) {
  	this._pp = v;
  }
  
  ['_qq'] = 44
  get ['qq']() {
  return this._qq;
  }
  set ['qq'](v) {
  	this._qq = v;
  }
}

assertEquals(6,MyClass.sf())
assertEquals(10,MyClass.sg())
MyClass._sf = 33
assertEquals(33,MyClass.sf())

assertEquals(5,MyClass.sfp)
MyClass.sfp = 8
assertEquals(8,MyClass.sfp)

const o = new MyClass();

assertEquals(6,MyClass.s1)
assertEquals(5,MyClass.s2)

assertEquals(undefined,o.a)
assertEquals(1,o.b)
assertEquals(3,o.bb)
assertEquals(4,o.c)
assertEquals(66,o.x)

assertEquals(6,o.f())
assertEquals(12,o.g())
assertEquals(8,o.h())

assertEquals(4,o._pp)
assertEquals(4,o.pp)
o.pp = 8
assertEquals(8,o._pp)
assertEquals(8,o.pp)

assertEquals(44,o._qq)
assertEquals(44,o.qq)
o.qq = 88
assertEquals(88,o._qq)
assertEquals(88,o.qq)


//
// Access to this
//
class C {
	
  instanceField = this;
  instanceMethod() {
    return this;
  }
  
  static staticField = this;
  static staticMethod() {
    return this;
  }
  
  static blockField;
  static {
    this.blockField = this;
  }
}

const c = new C();

assertEquals(true, c.instanceField === c); // true
assertEquals(true, c.instanceMethod() === c); // true

assertEquals(true, C.staticField === C); // true
assertEquals(true, C.blockField === C); // true
assertEquals(true, C.staticMethod() === C); // true

// A method definition needs no separator/ASI at all in a class body - it must
// parse correctly even immediately followed, on the SAME line, by another
// element (here, a computed-name field).
const fieldName = "y";
class D {
  m() { return 1; } [fieldName] = 2; z = 3;
}
const d = new D();
assertEquals(1, d.m());
assertEquals(2, d.y);
assertEquals(3, d.z);
