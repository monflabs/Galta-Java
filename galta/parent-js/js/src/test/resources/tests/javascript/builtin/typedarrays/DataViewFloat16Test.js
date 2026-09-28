// DataView.prototype.getFloat16/setFloat16 (only run when the host has this
// ES2025 feature enabled - see JSEnvironment.supportFloat16Array() and the
// Java-side guard in DataViewFloat16Test.java).
function testDataViewFloat16() {
    let buffer = new ArrayBuffer(2);
    let view = new DataView(buffer);
    view.setFloat16(0, 1.5);
    assertEquals(1.5, view.getFloat16(0));
    view.setFloat16(0, NaN);
    assertEquals(true, Number.isNaN(view.getFloat16(0)));
    // Smallest positive subnormal float16 - exercises the single-rounding-step
    // double->float16 path (a naive double->float->float16 double-rounds and
    // wrongly produces 0 here).
    view.setFloat16(0, 2.980232238769532e-8);
    assertEquals(5.960464477539063e-8, view.getFloat16(0));
}

testDataViewFloat16();
