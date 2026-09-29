// Test #1 - MDN example
const obj1 = {
  method1() {
    return 1;
  },
};

const obj2 = {
  method2() {
    return super.method1();
  },
};

Object.setPrototypeOf(obj2, obj1);
assertEquals( 1, obj2.method2() );


// Test #2 - MDN example
const parent1 = { prop: 1 };
const parent2 = { prop: 2 };

const child = {
  myParent() {
    return super.prop;
  },
};

Object.setPrototypeOf(child, parent1);
assertEquals( 1, child.myParent());

const myParent = child.myParent;
assertEquals( 1, myParent());

const anotherChild = { __proto__: parent2, myParent };
assertEquals( 1, anotherChild.myParent() )


// Test failures
// super outside of a method/derived constructor is an early SyntaxError
assertThrows( () => {
	eval("() => super(1,2,3)");
}, SyntaxError);
assertThrows( () => {
	eval("() => super.f(1,2,3)");
}, SyntaxError);
assertThrows( () => {
	eval("function f() { return super.f(1,2,3); }");
}, SyntaxError);
assertThrows( () => {
	eval("function f() { return super(1,2,3); }");
}, SyntaxError);

// SuperProperty's `this` binding for accessors, and property-creation-on-receiver
// for plain assignment, are covered by tests.javascript.object.SuperReceiverTest
// (interpreted-mode only -- the transpiler's super-property runtime helpers don't
// yet thread a separate receiver through).
