class A {
	a = 1
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

assertSame(Function.constructor, A.constructor)
assertSame(A, A.prototype.constructor)
assertSame(Function.constructor, A.__proto__.constructor)

assertEquals(Object.prototype, A.prototype.__proto__)

assertSame(Object.prototype, A.prototype.__proto__)
assertSame(A.prototype,a.__proto__)
assertSame(B.prototype,b.__proto__)
assertSame(B.prototype.__proto__, A.prototype)

assertSame(Function.constructor, B.constructor)
assertSame(B, B.prototype.constructor)
assertSame(Function.constructor, B.__proto__.constructor)



class MyMap extends Map {
    constructor() {
        super();
        this.a = 1;
    }
    
    getA() {
        return this.a;
    }
}

const myMap = new MyMap();

assertEquals(1, myMap.getA());
assertEquals(0, myMap.size);
myMap.set('key1', 'value1');
assertEquals(1, myMap.size);
myMap.set('key2', 'value2');
assertEquals(2, myMap.size);
myMap.set('key1', 'newValue1');
assertEquals(2, myMap.size);
assertEquals('newValue1', myMap.get('key1'));
assertEquals('value2', myMap.get('key2'));
assertEquals(true, myMap instanceof MyMap);
assertEquals(true, myMap instanceof Map);
assertEquals(true, myMap instanceof Object);
