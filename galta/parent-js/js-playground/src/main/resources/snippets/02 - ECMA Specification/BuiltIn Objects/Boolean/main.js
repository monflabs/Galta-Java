// Boolean Values and Boolean Methods in Action

// 1. Boolean Values
const isTrue = true;  // A true boolean value
const isFalse = false; // A false boolean value

console.log("Boolean Value - isTrue:", isTrue); // true
console.log("Boolean Value - isFalse:", isFalse); // false

// 2. Using the Boolean Constructor
const boolObjectTrue = new Boolean(true);
const boolObjectFalse = new Boolean(false);

console.log("Boolean Object - boolObjectTrue:", boolObjectTrue); // [Boolean: true]
console.log("Boolean Object - boolObjectFalse:", boolObjectFalse); // [Boolean: false]

// 3. Boolean Method: toString()
// Converts a Boolean value or Boolean object to a string
console.log("boolObjectTrue.toString():", boolObjectTrue.toString()); // "true"
console.log("boolObjectFalse.toString():", boolObjectFalse.toString()); // "false"
console.log("isTrue.toString():", isTrue.toString()); // "true"

// 4. Boolean Method: valueOf()
// Returns the primitive value of a Boolean object
console.log("boolObjectTrue.valueOf():", boolObjectTrue.valueOf()); // true
console.log("boolObjectFalse.valueOf():", boolObjectFalse.valueOf()); // false

// 5. Boolean Conversion Using Boolean() Function
console.log("Boolean('Hello'):", Boolean("Hello")); // true (non-empty string is truthy)
console.log("Boolean(''):", Boolean("")); // false (empty string is falsy)
console.log("Boolean(1):", Boolean(1)); // true (1 is truthy)
console.log("Boolean(0):", Boolean(0)); // false (0 is falsy)
console.log("Boolean(null):", Boolean(null)); // false (null is falsy)

// 6. Comparing Boolean Values and Objects
console.log("boolObjectTrue == true:", boolObjectTrue == true); // true (type coercion)
console.log("boolObjectTrue === true:", boolObjectTrue === true); // false (strict equality)
console.log("boolObjectTrue.valueOf() === true:", boolObjectTrue.valueOf() === true); // true

// 7. Boolean in Logical Operations
console.log("true && false:", true && false); // false
console.log("true || false:", true || false); // true
console.log("!false:", !false); // true
