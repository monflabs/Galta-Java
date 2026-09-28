assertEquals(1, Math.min(1, 3, 2));
assertEquals(-3, Math.min(-1, -3, -2));
assertEquals(1, Math.min(1, '3', 2));

// No arguments → +Infinity
assertEquals(Infinity, Math.min());

// NaN propagation
assertTrue(isNaN(Math.min(1, NaN, 3)));

// Single argument
assertEquals(5, Math.min(5));

// Spread
const a = [1, 2, 3];
assertEquals(1, Math.min(...a));
