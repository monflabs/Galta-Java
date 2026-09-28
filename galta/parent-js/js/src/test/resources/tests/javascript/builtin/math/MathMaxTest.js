assertEquals(3, Math.max(1, 3, 2));
assertEquals(-1, Math.max(-1, -3, -2));
assertEquals(3, Math.max(1, '3', 2));

// No arguments → -Infinity
assertEquals(-Infinity, Math.max());

// NaN propagation
assertTrue(isNaN(Math.max(1, NaN, 3)));

// Single argument
assertEquals(5, Math.max(5));

// Spread
const a = [1, 2, 3];
assertEquals(3, Math.max(...a));
