assertEquals(1, Math.exp(0))

// Can't use constants here as they vary based on the CPU
const JavaMath = Java.type("java.lang.Math")
assertEquals(JavaMath.exp(1), Math.exp(1))
assertEquals(JavaMath.exp(-1), Math.exp(-1))
assertEquals(JavaMath.exp(2), Math.exp(2))
