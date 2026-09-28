// Number Object in Action

// 1. Properties of Number
console.log("Number.MAX_VALUE:", Number.MAX_VALUE); // Largest possible number
console.log("Number.MIN_VALUE:", Number.MIN_VALUE); // Smallest possible number
console.log("Number.POSITIVE_INFINITY:", Number.POSITIVE_INFINITY); // Positive infinity
console.log("Number.NEGATIVE_INFINITY:", Number.NEGATIVE_INFINITY); // Negative infinity
console.log("Number.NaN:", Number.NaN); // Not-a-Number

// 2. Checking if a value is finite
console.log("Number.isFinite(100):", Number.isFinite(100)); // true
console.log("Number.isFinite(Infinity):", Number.isFinite(Infinity)); // false

// 3. Checking if a value is NaN
console.log("Number.isNaN(NaN):", Number.isNaN(NaN)); // true
console.log("Number.isNaN(123):", Number.isNaN(123)); // false

// 4. Checking if a value is an integer
console.log("Number.isInteger(42):", Number.isInteger(42)); // true
console.log("Number.isInteger(42.5):", Number.isInteger(42.5)); // false

// 5. Parsing numbers from strings
const num1 = Number("42");
const num2 = Number("42.5");
const num3 = Number("not a number");
console.log("Number('42'):", num1); // 42
console.log("Number('42.5'):", num2); // 42.5
console.log("Number('not a number'):", num3); // NaN

// 6. Converting to Fixed Decimal Places
const number = 123.456789;
console.log("number.toFixed(2):", number.toFixed(2)); // "123.46"
console.log("number.toFixed(0):", number.toFixed(0)); // "123"

// 7. Converting to Exponential Notation
console.log("number.toExponential(2):", number.toExponential(2)); // "1.23e+2"

// 8. Converting to a String
console.log("number.toString():", number.toString()); // "123.456789"
console.log("number.toString(16):", number.toString(16)); // "7b.74bc6a7ef9db22d" (Hexadecimal)

// 9. Checking Safe Integers
console.log("Number.isSafeInteger(42):", Number.isSafeInteger(42)); // true
console.log("Number.isSafeInteger(Math.pow(2, 53)):", Number.isSafeInteger(Math.pow(2, 53))); // false

// 10. Working with EPSILON
const a = 0.1 + 0.2;
console.log("0.1 + 0.2 === 0.3:", a === 0.3); // false (due to floating-point precision)
console.log("Math.abs(a - 0.3) < Number.EPSILON:", Math.abs(a - 0.3) < Number.EPSILON); // true

// Combining Methods
const largeNumber = 123456789;
console.log("Large number in exponential:", largeNumber.toExponential());
console.log("Large number in fixed format:", largeNumber.toFixed(2));
