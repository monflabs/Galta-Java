const isBigEnough = function (element, index, array) {
	return element >= 10;
}
assertFalse( [12, 5, 8, 130, 44].every(isBigEnough) )
assertTrue( [12, 54, 18, 130, 44].every(isBigEnough) )
assertFalse( [12, 5, 8, 130, 44].every(x => x >= 10) )
assertTrue( [12, 54, 18, 130, 44].every(x => x >= 10) )

const isSubset = (array1, array2) => {
	return array2.every(function (element) {
		return array1.includes(element);
	});
}
assertTrue(isSubset([1, 2, 3, 4, 5, 6, 7], [5, 7, 6]));
assertFalse(isSubset([1, 2, 3, 4, 5, 6, 7], [5, 8, 7]));

const _this = [];
[10,22].every(function (v,i) {
	assertEquals(i==0?10:22,v);
	assertSame(_this,this);
}, _this);

// Empty array → true (vacuously)
assertTrue([].every(x => false));

// Short-circuits on first falsy result
let callCount = 0;
[1, 2, 3, 4].every(x => { callCount++; return x < 2; });
assertEquals(2, callCount);

// TypeError if callback is not callable
assertThrows(TypeError, () => [1].every(null));
assertThrows(TypeError, () => [1].every("not a function"));
