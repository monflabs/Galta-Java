const module = {
  x: 42,
  getX: function () {
	// strict mode, this==undefined
    return this ? this.x : undefined;
  },
};

const unboundGetX = module.getX;
assertEquals( undefined, unboundGetX()) // The function gets invoked at the global scope

const boundGetX = unboundGetX.bind(module);
assertEquals( 42, boundGetX()) 



function Base(...args) {
	this.values = args;
}
const BoundBase = Base.bind(null,1,2);

const b = new BoundBase(3,4)
assertEquals( [1,2,3,4], b.values )



// Tests for Function.prototype.bind

// 1. Basic binding of 'this'
function testBindThisBasic() {
    function f() { return this.value; }
    const bound = f.bind({ value: 42 });
    assertEquals(42, bound());
}

testBindThisBasic();

// 2. Binding with arguments
function testBindWithArgs() {
    function sum(a, b) { return a + b; }
    const addFive = sum.bind(null, 5);
    assertEquals(9, addFive(4));
}

testBindWithArgs();

// 3. Binding with multiple arguments
function testBindWithMultipleArgs() {
    function concat(a, b, c) { return a + b + c; }
    const greet = concat.bind(null, 'Hello, ', 'World');
    assertEquals('Hello, World!', greet('!'));
}

testBindWithMultipleArgs();

// 4. 'bind' on non-callable should throw TypeError
function testBindNonCallable() {
    assertThrows(TypeError, () => {
        Function.prototype.bind.call({}, null);
    });
}

testBindNonCallable();

// 5. Bound function length property
function testBindLength() {
    function f(a, b, c) {}
    const bound = f.bind(null, 1);
    assertEquals(2, bound.length); // one argument pre-bound
}

testBindLength();

// 6. Bound function name property
function testBindName() {
    function originalName() {}
    const bound = originalName.bind(null);
    assertTrue(bound.name.includes('bound '));
}

testBindName();

// 7. Bound function used with 'new' (constructor behavior)
function testBindNew() {
    function Point(x, y) {
        this.x = x;
        this.y = y;
    }
    const BoundPoint = Point.bind(null, 10);
    const p = new BoundPoint(20);
    assertEquals(10, p.x);
    assertEquals(20, p.y);
    assertTrue(p instanceof Point);
}

testBindNew();

// 8. Bound function ignores bind-time this when used with 'new'
function testBindNewIgnoresThis() {
    function F() { this.prop = true; }
    const bound = F.bind({});
    const instance = new bound();
    assertTrue(instance.prop);
}

testBindNewIgnoresThis();

// 9. Binding undefined/null this in non-strict mode defaults to global object
function testBindUndefinedThisNonStrict() {
    function getThis() { return this; }
    const bound = getThis.bind(undefined);
    const result = bound();
    assertNotNull(result);
}

testBindUndefinedThisNonStrict();

// 10. Binding undefined/null this in strict mode keeps undefined/null
function testBindUndefinedThisStrict() {
    'use strict';
    function getThisStrict() { return this; }
    const bound = getThisStrict.bind(undefined);
    const result = bound();
    assertSame(undefined, result);
}

testBindUndefinedThisStrict();

// 11. [[Construct]] must use [[BoundArgs]] prepended to the call's own
// arguments, and must NEVER use [[BoundThis]] at all (only [[Call]] does) -
// this covers both "shortcut" branches of the construct path separately
// (zero bound args, and zero call-time args), not just the general
// merge-both-arrays case already covered above.
function testBindConstructZeroBoundArgs() {
    function Point(x, y) { this.x = x; this.y = y; }
    const BoundPoint = Point.bind({ x: 999, y: 999 });
    const p = new BoundPoint(1, 2);
    assertEquals(1, p.x);
    assertEquals(2, p.y);
}

testBindConstructZeroBoundArgs();

function testBindConstructZeroCallArgs() {
    function Point(x, y) { this.x = x; this.y = y; }
    const BoundPoint = Point.bind({ x: 999, y: 999 }, 10, 20);
    const p = new BoundPoint();
    assertEquals(10, p.x);
    assertEquals(20, p.y);
}

testBindConstructZeroCallArgs();

// 12. BoundFunctionExoticObject [[Construct]] (10.4.1.2): "If SameValue(F,
// newTarget) is true, set newTarget to target" - `new`-ing a bound function
// (or Reflect.construct'ing it with itself as newTarget) must make
// new.target inside the ORIGINAL target be the target itself, not the
// wrapper - including through a bound-of-a-bound chain. Any OTHER explicit
// newTarget passed via Reflect.construct still passes through unchanged.
function testBindConstructNewTargetSelf() {
    function Base() { this.seenNewTarget = new.target; }
    const Bound = Base.bind(null);
    const b = new Bound();
    assertSame(Base, b.seenNewTarget);
}

testBindConstructNewTargetSelf();

function testBindConstructNewTargetSelfReflect() {
    function Base() { this.seenNewTarget = new.target; }
    const Bound = Base.bind(null);
    const b = Reflect.construct(Bound, [], Bound);
    assertSame(Base, b.seenNewTarget);
}

testBindConstructNewTargetSelfReflect();

function testBindConstructNewTargetChain() {
    function Base() { this.seenNewTarget = new.target; }
    const Bound1 = Base.bind(null);
    const Bound2 = Bound1.bind(null);
    const b = new Bound2();
    assertSame(Base, b.seenNewTarget);
}

testBindConstructNewTargetChain();

function testBindConstructNewTargetExplicitPassesThrough() {
    function Base() { this.seenNewTarget = new.target; }
    function Other() {}
    const Bound = Base.bind(null);
    const b = Reflect.construct(Bound, [], Other);
    assertSame(Other, b.seenNewTarget);
}

testBindConstructNewTargetExplicitPassesThrough();

function testBindPoisonsCallerArguments() {
    function target() {}
    const bound = target.bind(null);

    assertEquals(false, bound.hasOwnProperty('caller'));
    assertEquals(false, bound.hasOwnProperty('arguments'));

    assertThrows(TypeError, () => bound.caller);
    assertThrows(TypeError, () => { bound.caller = {}; });
    assertThrows(TypeError, () => bound.arguments);
    assertThrows(TypeError, () => { bound.arguments = {}; });
}

testBindPoisonsCallerArguments();

// The poison pill applies regardless of the TARGET function's own
// strictness - a bound wrapper around a non-strict function still poisons.
function testBindPoisonsCallerArgumentsNonStrictTarget() {
    function target() { return arguments.length; }
    const bound = target.bind(null);

    assertThrows(TypeError, () => bound.caller);
    assertThrows(TypeError, () => bound.arguments);
}

testBindPoisonsCallerArgumentsNonStrictTarget();
