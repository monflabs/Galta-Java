// Math Object in Action

// 1. Constants
console.log("Math.PI:", Math.PI); // The value of PI
console.log("Math.E:", Math.E);   // Euler's constant

// 2. Rounding Numbers
console.log("Math.round(4.7):", Math.round(4.7)); // Rounds to the nearest integer
console.log("Math.floor(4.7):", Math.floor(4.7)); // Rounds down
console.log("Math.ceil(4.3):", Math.ceil(4.3));   // Rounds up

// 3. Power and Square Root
console.log("Math.pow(2, 3):", Math.pow(2, 3));   // 2 raised to the power of 3
console.log("Math.sqrt(16):", Math.sqrt(16));     // Square root of 16

// 4. Trigonometric Functions
console.log("Math.sin(Math.PI / 2):", Math.sin(Math.PI / 2)); // Sine of 90 degrees
console.log("Math.cos(0):", Math.cos(0));                    // Cosine of 0 degrees

// 5. Absolute Value
console.log("Math.abs(-42):", Math.abs(-42)); // Absolute value of -42

// 6. Random Numbers
console.log("Math.random():", Math.random()); // Random number between 0 and 1
console.log("Random number between 1 and 100:", Math.floor(Math.random() * 100) + 1);

// 7. Maximum and Minimum
console.log("Math.max(10, 20, 30):", Math.max(10, 20, 30)); // Largest number
console.log("Math.min(10, 20, 30):", Math.min(10, 20, 30)); // Smallest number

// 8. Logarithms
console.log("Math.log(Math.E):", Math.log(Math.E)); // Natural log of E
console.log("Math.log10(1000):", Math.log10(1000)); // Base 10 log of 1000

// 9. Exponential
console.log("Math.exp(1):", Math.exp(1)); // e^1

// 10. Hyperbolic Functions
console.log("Math.sinh(1):", Math.sinh(1)); // Hyperbolic sine of 1
console.log("Math.cosh(1):", Math.cosh(1)); // Hyperbolic cosine of 1

// Combining Methods
const radius = 5;
const area = Math.PI * Math.pow(radius, 2);
console.log(`Area of a circle with radius ${radius}:`, area);
