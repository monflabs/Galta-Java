// Float16Array stores half-precision (16-bit) floats - half the memory of
// Float32Array, at the cost of precision: only about 3 significant digits.
const precise = new Float64Array([1.1, 2.2, 3.3]);
const half = new Float16Array([1.1, 2.2, 3.3]);
console.log([...precise]);
console.log([...half]);

console.log(Float16Array.BYTES_PER_ELEMENT, Float32Array.BYTES_PER_ELEMENT, Float64Array.BYTES_PER_ELEMENT);

// DataView's own getFloat16/setFloat16 read and write the same 16-bit
// format at an explicit byte offset, little- or big-endian.
const buffer = new ArrayBuffer(2);
const view = new DataView(buffer);
view.setFloat16(0, 3.14, true);
console.log(view.getFloat16(0, true));
