assertEquals(1, Math.sign(3));
assertEquals(-1, Math.sign(-3));
assertEquals(0, Math.sign(0));
assertEquals(-1, Math.sign('-3'));

// -0 input → -0 output
assertTrue(Object.is(-0, Math.sign(-0)));
// +0 input → +0 output
assertTrue(Object.is(+0, Math.sign(+0)));

// NaN → NaN
assertTrue(isNaN(Math.sign(NaN)));

// Positive/negative infinity
assertEquals(1, Math.sign(Infinity));
assertEquals(-1, Math.sign(-Infinity));
