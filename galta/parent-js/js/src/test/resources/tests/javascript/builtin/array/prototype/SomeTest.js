const isBiggerThan10 = function(element, index, array) {
	return element > 10;
}
assertEquals( false, [2, 5, 8, 1, 4].some(isBiggerThan10) );
assertEquals( true, [12, 5, 8, 1, 4].some(isBiggerThan10) );

// Empty array → false
assertFalse([].some(x => true));

// Short-circuits on first truthy result
let callCount = 0;
[1, 2, 3, 4].some(x => { callCount++; return x === 2; });
assertEquals(2, callCount);

// thisArg binding
const obj = { threshold: 5 };
assertTrue([1, 10].some(function(x) { return x > this.threshold; }, obj));

// Receives (element, index, array) arguments
const srcArr = [42];
srcArr.some(function(v, i, a) {
    assertEquals(42, v);
    assertEquals(0, i);
    assertSame(srcArr, a);
});

// TypeError if callback is not callable
assertThrows(TypeError, () => [1].some(null));
assertThrows(TypeError, () => [1].some(42));
