// Ordinary left-to-right addition accumulates rounding error as the
// magnitudes of the numbers being added drift apart.
const values = [1e20, 0.1, -1e20];
console.log(values.reduce((total, n) => total + n, 0));   // rounding error: not 0.1

// Math.sumPrecise sums the same values with a correctly-rounded algorithm,
// as if computed with unlimited precision and rounded once at the end.
console.log(Math.sumPrecise(values));
