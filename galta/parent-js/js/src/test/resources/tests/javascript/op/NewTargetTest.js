// Unit tests for new.target meta-property

// Test 1: new.target is undefined when function is called without new
function testNewTargetUndefinedWithoutNew() {
    function TestFunction() {
        return new.target;
    }
    
    const result = TestFunction();
    assertUndefined(result);
}

// Test 2: new.target refers to constructor when called with new
function testNewTargetWithNew() {
    function TestConstructor() {
        return new.target;
    }
    
    const instance = new TestConstructor();
    assertEqualsStrict(TestConstructor, instance);
}

// Test 3: new.target in arrow functions refers to enclosing scope
function testNewTargetInArrowFunction() {
    function OuterConstructor() {
        const arrow = () => new.target;
        return arrow();
    }
    
    const result = new OuterConstructor();
    assertEqualsStrict(OuterConstructor, result);
}

// Test 4: new.target is undefined in arrow function without enclosing constructor
function testNewTargetInArrowFunctionNoEnclosing() {
    const arrow = () => new.target;
    const result = arrow();
    assertUndefined(result);
}

// Test 5: new.target with inheritance - points to actual constructor called
function testNewTargetWithInheritance() {
    function Parent() {
        this.parentTarget = new.target;
    }
    
    function Child() {
        Parent.call(this);
        this.childTarget = new.target;
    }
    
    Child.prototype = Object.create(Parent.prototype);
    Child.prototype.constructor = Child;
    
    const instance = new Child();
// Not sure about this assertion, as new.target in Parent should be Child when called via .call	
    //assertEqualsStrict(Parent, instance.parentTarget); // Parent's new.target is Parent (called via .call)
    assertEqualsStrict(Child, instance.childTarget);   // Child's new.target is Child
}

// Test 6: new.target with class constructors
function testNewTargetWithClasses() {
    class TestClass {
        constructor() {
            this.target = new.target;
        }
    }
    
    const instance = new TestClass();
    assertEqualsStrict(TestClass, instance.target);
}

// Test 7: new.target with class inheritance
function testNewTargetWithClassInheritance() {
    class Parent {
        constructor() {
            this.parentTarget = new.target;
        }
    }
    
    class Child extends Parent {
        constructor() {
            super();
            this.childTarget = new.target;
        }
    }
    
    const instance = new Child();
    assertEqualsStrict(Child, instance.parentTarget); // Parent's new.target is Child (the actual constructor)
    assertEqualsStrict(Child, instance.childTarget);
}

// Test 8: new.target when constructor returns different object
function testNewTargetWithReturnedObject() {
    let capturedTarget;
    
    function Constructor() {
        capturedTarget = new.target;
        return { custom: true };
    }
    
    const result = new Constructor();
    assertEqualsStrict(Constructor, capturedTarget);
    assertTrue(result.custom);
}

// Test 9: new.target in nested function calls
function testNewTargetInNestedCalls() {
    function Outer() {
        function inner() {
            return new.target; // Should be undefined - inner not called with new
        }
        this.outerTarget = new.target;
        this.innerResult = inner();
    }
    
    const instance = new Outer();
    assertEqualsStrict(Outer, instance.outerTarget);
    assertUndefined(instance.innerResult);
}

// Test 10: new.target with built-in constructors behavior simulation
function testNewTargetBuiltinBehavior() {
    function CustomArray() {
        if (!new.target) {
            throw new TypeError('Constructor requires new');
        }
        this.isCustomArray = true;
    }
    
    // Should work with new
    const instance = new CustomArray();
    assertTrue(instance.isCustomArray);
    
    // Should throw without new
    assertThrows(TypeError, () => CustomArray());
}

// Test 11: new.target in getter/setter
function testNewTargetInAccessor() {
    let getterTarget, setterTarget;
    
    function Constructor() {
		Object.defineProperty(this, 'prop', {
		    get() { getterTarget = new.target; return this._prop; },
		    set(value) { setterTarget = new.target; this._prop = value; },
		    configurable: true
		});
    }
    
    const instance = new Constructor();
    instance.prop = 'test';
    const value = instance.prop;
    
    assertUndefined(getterTarget); // Getter not called with new
    assertUndefined(setterTarget); // Setter not called with new
}

// Test 12: new.target with Reflect.construct
function testNewTargetWithReflectConstruct() {
    function Constructor() {
        return new.target;
    }
    
    function NewTarget() {}
    
    const result = Reflect.construct(Constructor, [], NewTarget);
    assertEqualsStrict(NewTarget, result);
}

// Test 13: new.target in method called from constructor
function testNewTargetInMethod() {
    function Constructor() {
        this.target = new.target;
        this.methodResult = this.getTarget();
    }
    
    Constructor.prototype.getTarget = function() {
        return new.target; // Should be undefined - method not called with new
    };
    
    const instance = new Constructor();
    assertEqualsStrict(Constructor, instance.target);
    assertUndefined(instance.methodResult);
}

// Test 14: new.target type checking
function testNewTargetType() {
    function Constructor() {
        assertTrue(typeof new.target === 'function' || typeof new.target === 'undefined');
        return new.target;
    }
    
    // With new - should be function
    const withNew = new Constructor();
    assertEqualsStrict('function', typeof withNew);
    
    // Without new - should be undefined
    const withoutNew = Constructor();
    assertEqualsStrict('undefined', typeof withoutNew);
}

// Test 15: new.target with proxy constructor
/* PHIL: TODO
function testNewTargetWithProxy() {
    function Original() {
        return new.target;
    }
    
    const ProxyConstructor = new Proxy(Original, {});
    
    const result = new ProxyConstructor();
    assertEqualsStrict(ProxyConstructor, result);
}
*/

// Run all tests
function runAllTests() {
    const tests = [
        testNewTargetUndefinedWithoutNew,
        testNewTargetWithNew,
        testNewTargetInArrowFunction,
        testNewTargetInArrowFunctionNoEnclosing,
        testNewTargetWithInheritance,
        testNewTargetWithClasses,
        testNewTargetWithClassInheritance,
        testNewTargetWithReturnedObject,
        testNewTargetInNestedCalls,
        testNewTargetBuiltinBehavior,
        testNewTargetInAccessor,
        //testNewTargetWithReflectConstruct,
        testNewTargetInMethod,
        testNewTargetType,
        //testNewTargetWithProxy
    ];
    
    for (const test of tests) {
        test();
    }
}

// Execute tests
runAllTests();
