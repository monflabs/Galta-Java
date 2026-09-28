function A() {
	this.a = 1
}
A.prototype = {
	f() {
        return this.a*4;
    }
}

class B extends A {
	b = 2
	f() {
		return this.a*6;
	}
}


const a = new A();
const b = new B();

assertEquals(1,a.a)
assertEquals(1,b.a)
assertEquals(undefined,a.b)
assertEquals(2,b.b)

assertEquals(4,a.f())
assertEquals(6,b.f())


assertEquals(A.prototype,a.__proto__)
assertEquals(B.prototype,b.__proto__)
assertEquals(B.prototype.__proto__, A.prototype)
assertEquals(Object.prototype, A.prototype.__proto__)
