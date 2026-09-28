// Basic apply usage
const numbers = [5, 6, 2, 3, 7];
assertEquals(7, Math.max.apply(null, numbers));
assertEquals(2, Math.min.apply(null, numbers));

// thisArg binding
function greet(greeting, punctuation) {
    return greeting + ', ' + this.name + punctuation;
}
const person = { name: 'Alice' };
assertEquals('Hello, Alice!', greet.apply(person, ['Hello', '!']));

// null/undefined thisArg (global this in non-strict)
function getArgs() { return Array.from(arguments); }
assertEquals([1, 2, 3], getArgs.apply(null, [1, 2, 3]));
assertEquals([1, 2, 3], getArgs.apply(undefined, [1, 2, 3]));

// Array-like argument (not a real array)
function sum(a, b, c) { return a + b + c; }
const arrayLike = { 0: 10, 1: 20, 2: 30, length: 3 };
assertEquals(60, sum.apply(null, arrayLike));

// Empty args array
function noArgs() { return arguments.length; }
assertEquals(0, noArgs.apply(null, []));
assertEquals(0, noArgs.apply(null));

// apply with null args (treated as empty)
assertEquals(0, noArgs.apply(null, null));
assertEquals(0, noArgs.apply(null, undefined));

// TypeError when called on non-function
assertThrows(TypeError, () => Function.prototype.apply.call(42, null, []));
assertThrows(TypeError, () => Function.prototype.apply.call(null, null, []));

// apply preserves argument order
function ordered(a, b, c) { return [a, b, c]; }
assertEquals([1, 2, 3], ordered.apply(null, [1, 2, 3]));
