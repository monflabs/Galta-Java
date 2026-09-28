assertEquals("123456789123456789", 123456789123456789n.toString());
assertEquals("123456789123456789", 123456789123456789n.toString(10));

// Radix conversions
assertEquals("0", 0n.toString());
assertEquals("11111111", 255n.toString(2));
assertEquals("ff", 255n.toString(16));
assertEquals("77", 63n.toString(8));
assertEquals("-ff", (-255n).toString(16));

// RangeError for invalid radix
assertThrows(RangeError, () => 1n.toString(1));
assertThrows(RangeError, () => 1n.toString(37));

// TypeError for non-BigInt
assertThrows(TypeError, () => BigInt.prototype.toString.call(42));
assertThrows(TypeError, () => BigInt.prototype.toString.call("hello"));
