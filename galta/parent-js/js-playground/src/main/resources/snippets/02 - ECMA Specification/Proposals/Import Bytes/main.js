// `with { type: "bytes" }` imports a file's raw bytes as a Uint8Array over an
// immutable ArrayBuffer - binary files included, no decoding of any kind.
import sample from "./sample.bin" with { type: "bytes" };

console.log(sample instanceof Uint8Array, sample.length, sample.buffer.immutable);
console.log(Array.from(sample, b => b.toString(16).padStart(2, "0")).join(" "));

// A text file comes back as bytes too; decode it yourself.
import hello from "./hello.txt" with { type: "bytes" };
console.log(String.fromCharCode(...hello).trim());

// The buffer is immutable: it can be read, but not resized or transferred.
try {
	sample.buffer.transfer();
} catch (e) {
	console.log(`${e.name}: ${e.message}`);
}
