// Basic call usage
function greet(greeting) {
    return greeting + ', ' + this.name;
}
const person = { name: 'Bob' };
assertEquals('Hello, Bob', greet.call(person, 'Hello'));

// Multiple arguments
function sum(a, b, c) { return a + b + c; }
assertEquals(60, sum.call(null, 10, 20, 30));

// null/undefined thisArg
function getThis() { return this; }
// In strict mode this stays null/undefined, in non-strict it becomes global
// Just verify it doesn't throw
getThis.call(null);
getThis.call(undefined);

// call with no arguments
function noArgs() { return arguments.length; }
assertEquals(0, noArgs.call());
assertEquals(0, noArgs.call(null));

// call passes correct thisArg
function whoAmI() { return this.id; }
assertEquals(42, whoAmI.call({ id: 42 }));

// call is used to borrow methods
const arr = [3, 1, 2];
const joined = Array.prototype.join.call(arr, '-');
assertEquals('3-1-2', joined);

// TypeError when called on non-function
assertThrows(TypeError, () => Function.prototype.call.call(42));
assertThrows(TypeError, () => Function.prototype.call.call(null));

// call with primitives as thisArg (boxed in non-strict)
const result = String.prototype.toUpperCase.call('hello');
assertEquals('HELLO', result);
