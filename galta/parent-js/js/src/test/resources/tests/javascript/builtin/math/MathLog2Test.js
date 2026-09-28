assertEquals(1, Math.log2(2))
assertEquals(0, Math.log2(1))
assertEquals(Number.NEGATIVE_INFINITY, Math.log2(0))

// Can't use constants here as they vary based on the CPU
const JavaMath = Java.type("java.lang.Math")
function log2_emul(d) {
	if(isNaN(d)) {
        return Double.NaN;
    }
    return Math.log(d) / Math.LN2;
}
assertEquals(log2_emul(3), Math.log2(3))
