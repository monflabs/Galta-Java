// Source-phase import of a native (Java-implemented) module: the binding is
// the module's Module Source Object, not its namespace.
import source nat from "native-lib";
assertEquals("object", typeof nat);
assertEquals("[object ModuleSource]", Object.prototype.toString.call(nat));
assertTrue(nat.source.indexOf("native module") >= 0);

// One Module Source Object per module.
import source nat2 from "native-lib";
assertTrue(nat === nat2);

// Prototype chain: ModuleSource.prototype -> %AbstractModuleSource%.prototype
// -> Object.prototype; %AbstractModuleSource% itself is not constructible.
var abstractProto = Object.getPrototypeOf(Object.getPrototypeOf(nat));
assertEquals("AbstractModuleSource", abstractProto.constructor.name);
assertTrue(Object.getPrototypeOf(abstractProto) === Object.prototype);
assertThrows(TypeError, () => new abstractProto.constructor());
// @@toStringTag is a getter that only answers for a Module Source Object.
var tag = Object.getOwnPropertyDescriptor(abstractProto, Symbol.toStringTag).get;
assertEquals("ModuleSource", tag.call(nat));
assertEquals(undefined, tag.call(abstractProto));

// Re-exported through another module: still the same object.
import { x } from "reexport-source.js";
assertTrue(x === nat);

// Dynamic form.
var calls = 0;
var p1 = import.source("native-lib").then(function(ms) {
	calls++;
	assertTrue(ms === nat);
});
// A JS source text module has no source phase representation (spec
// GetModuleSource): SyntaxError.
var p2 = import.source("a.js").then(function() {
	fail("import.source(\"a.js\") unexpectedly resolved");
}, function(reason) {
	calls++;
	assertTrue(reason instanceof SyntaxError);
});
// An unresolvable specifier rejects with the usual TypeError.
var p3 = import.source("does-not-exist.js").then(function() {
	fail("import.source(\"does-not-exist.js\") unexpectedly resolved");
}, function(reason) {
	calls++;
	assertTrue(reason instanceof TypeError);
});
assertEquals(0, calls);
Promise.all([p1, p2, p3]).then(function() {
	assertEquals(3, calls);
}, function(reason) {
	fail("unexpected rejection: " + reason);
});

// `with { type: "bytes" }`: a Uint8Array over an immutable ArrayBuffer holding
// the file's raw bytes (a binary file, not text).
import data from "data.bin" with { type: "bytes" };
assertTrue(data instanceof Uint8Array);
assertEquals(4, data.length);
assertEquals(1, data[0]);
assertEquals(2, data[1]);
assertEquals(255, data[2]);
assertEquals(10, data[3]);
assertTrue(data.buffer.immutable);
assertThrows(TypeError, () => data.buffer.transfer());

// The bytes of a native module are its placeholder source, UTF-8 encoded.
import natBytes from "native-lib" with { type: "bytes" };
assertEquals(nat.source, String.fromCharCode.apply(null, Array.from(natBytes)));
