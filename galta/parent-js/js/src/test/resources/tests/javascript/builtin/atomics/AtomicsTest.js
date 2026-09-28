assertEquals("Atomics", Object.prototype.toString.call(Atomics).slice(8,-1))

// add/and/or/sub/xor: return the OLD value, store the combined result.
{
	let ta = new Int32Array(new SharedArrayBuffer(16));
	ta[0] = 10;
	assertEquals(10, Atomics.add(ta, 0, 5));
	assertEquals(15, ta[0]);

	ta[0] = 0b1100;
	assertEquals(0b1100, Atomics.and(ta, 0, 0b1010));
	assertEquals(0b1000, ta[0]);

	ta[0] = 0b1100;
	assertEquals(0b1100, Atomics.or(ta, 0, 0b0011));
	assertEquals(0b1111, ta[0]);

	ta[0] = 10;
	assertEquals(10, Atomics.sub(ta, 0, 3));
	assertEquals(7, ta[0]);

	ta[0] = 0b1100;
	assertEquals(0b1100, Atomics.xor(ta, 0, 0b1010));
	assertEquals(0b0110, ta[0]);
}

// exchange: returns the old value, unconditionally stores the new one.
{
	let ta = new Int32Array(new SharedArrayBuffer(16));
	ta[0] = 42;
	assertEquals(42, Atomics.exchange(ta, 0, 99));
	assertEquals(99, ta[0]);
}

// compareExchange: only stores when the current value matches expected;
// always returns the value that was actually there before the call.
{
	let ta = new Int32Array(new SharedArrayBuffer(16));
	ta[0] = 5;
	assertEquals(5, Atomics.compareExchange(ta, 0, 5, 42));
	assertEquals(42, ta[0]);
	assertEquals(42, Atomics.compareExchange(ta, 0, 5, 99));
	assertEquals(42, ta[0]); // mismatch: unchanged
}

// load/store
{
	let ta = new Uint8Array(new SharedArrayBuffer(4));
	assertEquals(200, Atomics.store(ta, 0, 200));
	assertEquals(200, Atomics.load(ta, 0));
}

// Atomics operations also work on a regular (non-shared) ArrayBuffer -
// this was relaxed in the "Atomics on non-shared memory" spec update.
{
	let ta = new Int32Array(new ArrayBuffer(16));
	assertEquals(0, Atomics.add(ta, 0, 7));
	assertEquals(7, ta[0]);
}

// Only non-clamped integer TypedArrays are accepted.
{
	assertThrows(TypeError, () => Atomics.add(new Uint8ClampedArray(new SharedArrayBuffer(4)), 0, 1));
	assertThrows(TypeError, () => Atomics.add(new Float64Array(new SharedArrayBuffer(8)), 0, 1));
	assertThrows(TypeError, () => Atomics.add([1,2,3], 0, 1));
}

// Out-of-bounds / negative indices throw RangeError.
{
	let ta = new Int32Array(new SharedArrayBuffer(16));
	assertThrows(RangeError, () => Atomics.load(ta, 4));
	assertThrows(RangeError, () => Atomics.load(ta, -1));
}

// BigInt64Array / BigUint64Array
{
	let ta = new BigInt64Array(new SharedArrayBuffer(16));
	ta[0] = 10n;
	assertEquals(10n, Atomics.add(ta, 0, 5n));
	assertEquals(15n, ta[0]);
	assertEquals(15n, Atomics.compareExchange(ta, 0, 15n, -1n));
	assertEquals(-1n, ta[0]);

	// A BigUint64Array stores compareExchange's `expected` argument wrapped
	// to its own unsigned 64-bit representation before comparing, so a
	// negative BigInt still matches a value that itself wrapped on store.
	let tu = new BigUint64Array(new SharedArrayBuffer(8));
	tu[0] = -5n;
	assertEquals(18446744073709551611n, tu[0]);
	assertEquals(18446744073709551611n, Atomics.compareExchange(tu, 0, -5n, 0n));
	assertEquals(0n, tu[0]);
}

// isLockFree
{
	assertEquals(true, Atomics.isLockFree(4));
	assertEquals(false, Atomics.isLockFree(3));
	assertEquals("boolean", typeof Atomics.isLockFree(1));
}

// pause
{
	assertEquals(undefined, Atomics.pause());
	assertEquals(undefined, Atomics.pause(42));
	assertThrows(TypeError, () => Atomics.pause(42.5));
	assertThrows(TypeError, () => Atomics.pause(-1));
	assertThrows(TypeError, () => Atomics.pause("42"));
}
