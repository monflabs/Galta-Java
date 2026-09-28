assertEquals(5.5, Math.f16round(5.5))
assertEquals(1, Math.f16round(1))
assertEquals(0, Math.f16round(0))
assertEquals(true, Object.is(-0, Math.f16round(-0)))
assertEquals(true, isNaN(Math.f16round(NaN)))
assertEquals(Infinity, Math.f16round(Infinity))
assertEquals(-Infinity, Math.f16round(-Infinity))

// Rounding directly from double must not double-round through float32 first:
// these values sit right around the boundary between 0 and the smallest
// positive subnormal float16 (2^-24), where a double->float->float16 path
// gives the wrong answer but double->float16 does not.
assertEquals(0, Math.f16round(2.9802322387695312e-8))
assertEquals(5.960464477539063e-8, Math.f16round(2.980232238769532e-8))
assertEquals(5.960464477539063e-8, Math.f16round(5.960464477539063e-8))

// Ties-to-even between two subnormal float16 values.
assertEquals(1.1920928955078125e-7, Math.f16round(8.940696716308594e-8))
assertEquals(1.1920928955078125e-7, Math.f16round(1.4901161193847656e-7))
