const bytes = new Uint8Array([72, 101, 108, 108, 111]);   // "Hello"

const base64 = bytes.toBase64();
console.log(base64);
console.log([...Uint8Array.fromBase64(base64)].join(", "));

const hex = bytes.toHex();
console.log(hex);
console.log([...Uint8Array.fromHex(hex)].join(", "));

// setFromBase64/setFromHex decode INTO an existing buffer instead of
// allocating a new one.
const target = new Uint8Array(5);
target.setFromBase64(base64);
console.log([...target].join(", "));
