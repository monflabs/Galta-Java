assertEquals(343, Math.pow(7,3))
assertEquals(2, Math.pow(4,0.5))
assertEquals(NaN, Math.pow(-7,0.5))

// Can't use constants here as they vary based on the CPU
const JavaMath = Java.type("java.lang.Math")
assertEquals(JavaMath.pow(7,-2), Math.pow(7,-2))
