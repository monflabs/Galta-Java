// Test file: test_class_behavior.js

// Basic class definition
(function testClassDeclarationAndInstantiation() {
	class Person {
		constructor(name) {
			this.name = name;
		}

		getName() {
			return this.name;
		}
	}

	const p = new Person("Alice");
	assertEquals("Alice", p.getName());
	assertEquals("Alice", p.name);
	assertTrue(p instanceof Person);
})();

// Static method test
(function testStaticMethod() {
	class Util {
		static double(x) {
			return x * 2;
		}
	}

	assertEquals(6, Util.double(3));
	assertUndefined((new Util()).double); // Should not exist on instance
})();

// Class fields
(function testClassFields() {
	class Counter {
		count = 0;

		increment() {
			this.count++;
		}
	}

	const c = new Counter();
	assertEquals(0, c.count);
	c.increment();
	assertEquals(1, c.count);
})();

// Inheritance with constructor and super()
(function testInheritanceAndSuper() {
	class Animal {
		constructor(name) {
			this.name = name;
		}
		speak() {
			return `${this.name} makes a noise`;
		}
	}

	class Dog extends Animal {
		constructor(name, breed) {
			super(name);
			this.breed = breed;
		}

		speak() {
			return `${this.name} barks`;
		}
	}

	const d = new Dog("Fido", "Labrador");
	assertEquals("Fido", d.name);
	assertEquals("Labrador", d.breed);
	assertEquals("Fido barks", d.speak());
	assertTrue(d instanceof Dog);
	assertTrue(d instanceof Animal);
})();

// Static methods and inheritance
(function testStaticInheritance() {
	class Base {
		static foo() {
			return "base";
		}
	}
	class Derived extends Base {}

	assertEquals("base", Derived.foo());
})();

// Method override and `super.method()`
(function testMethodOverrideAndSuperCall() {
	class Parent {
		sayHi() {
			return "hi from parent";
		}
	}
	class Child extends Parent {
		sayHi() {
			return super.sayHi() + " and child";
		}
	}

	const c = new Child();
	assertEquals("hi from parent and child", c.sayHi());
})();

// Class expressions
(function testClassExpression() {
	const MyClass = class {
		constructor(value) {
			this.value = value;
		}
		getValue() {
			return this.value;
		}
	};

	const m = new MyClass(42);
	assertEquals(42, m.getValue());
})();

// Invalid super() usage
(function testMissingSuperInSubclass() {
	assertThrows(ReferenceError, () => {
		class Base {}

		class Broken extends Base {
			constructor() {
				// Missing super() before accessing `this`
				this.x = 1;
			}
		}

		new Broken();
	});
})();

// new.target behavior
(function testNewTarget() {
	class Foo {
		constructor() {
			this.caller = new.target.name;
		}
	}
	class Bar extends Foo {}

	const f = new Foo();
	const b = new Bar();

	assertEquals("Foo", f.caller);
	assertEquals("Bar", b.caller);
})();

// Getter and setter
(function testGetterSetter() {
	class Box {
		constructor() {
			this._value = 0;
		}

		get value() {
			return this._value;
		}

		set value(v) {
			this._value = v;
		}
	}

	const box = new Box();
	box.value = 100;
	assertEquals(100, box.value);
})();

// Class with no constructor
(function testClassWithNoConstructor() {
	class NoConstructor {}

	const nc = new NoConstructor();
	assertTrue(nc instanceof NoConstructor);
})();

// Instance vs prototype methods
(function testPrototypeMethods() {
	class A {
		method() {
			return 1;
		}
	}

	const a = new A();
	assertEquals(1, a.method());
	assertTrue(A.prototype.hasOwnProperty("method"));
	assertFalse(a.hasOwnProperty("method"));
})();

// Check if class body is strict
(function testClassBodyIsStrictMode() {
	// A runtime ReferenceError (the assignment is valid syntax)
	assertThrows(ReferenceError, () => {
		class Bad {
			// Assigning to undeclared variable should throw in strict mode
			badMethod() {
				undeclaredVar = 10;
			}
		}
		new Bad().badMethod();
	});
})();