assertEquals(1, Math.round(0.9))
assertEquals(6, Math.round(5.95))
assertEquals(6, Math.round(5.5))
assertEquals(5, Math.round(5.05))

assertEquals(-6, Math.round(-5.95))
assertEquals(-5, Math.round(-5.5))
assertEquals(-5, Math.round(-5.05))

// NaN → NaN
assertTrue(isNaN(Math.round(NaN)));

// Integer → same value
assertEquals(5, Math.round(5));
assertEquals(-3, Math.round(-3));

// ±Infinity → ±Infinity
assertEquals(Infinity, Math.round(Infinity));
assertEquals(-Infinity, Math.round(-Infinity));

// -0 → -0 (per spec §21.3.2.28)
assertTrue(Object.is(-0, Math.round(-0)));

// +0 → +0
assertTrue(Object.is(+0, Math.round(+0)));

// -0.5 → -0 (half rounds toward +Infinity)
assertTrue(Object.is(-0, Math.round(-0.5)));

// 0.5 → 1 (rounds half toward +Infinity)
assertEquals(1, Math.round(0.5));

// Values in (0, 0.5) → +0
assertTrue(Object.is(+0, Math.round(0.1)));

// Values in (-0.5, 0) → -0
assertTrue(Object.is(-0, Math.round(-0.1)));
