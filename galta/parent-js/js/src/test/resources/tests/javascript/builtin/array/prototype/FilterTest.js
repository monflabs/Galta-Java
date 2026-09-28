const isBigEnough = function (element, index, array) {
	return element >= 10;
}
assertEquals( [12, 130, 44], [12, 5, 8, 130, 44].filter(isBigEnough) )

const _this = [];
[10,22].filter(function (v,i) {
	assertEquals(i==0?10:22,v);
	assertSame(_this,this);
}, _this);

// Returns new array, doesn't mutate original
const orig = [1, 2, 3, 4, 5];
const filtered = orig.filter(x => x > 2);
assertEquals([3, 4, 5], filtered);
assertEquals([1, 2, 3, 4, 5], orig);

// Empty array returns empty array
assertEquals([], [].filter(() => true));

// All filtered out
assertEquals([], [1, 2, 3].filter(() => false));

// Callback receives (element, index, array)
const arr = [10, 20, 30];
const seenIndices = [];
arr.filter((v, i, a) => {
	seenIndices.push(i);
	assertSame(arr, a);
	return false;
});
assertEquals([0, 1, 2], seenIndices);

// TypeError for non-callable
assertThrows(TypeError, () => [1, 2].filter(null));
assertThrows(TypeError, () => [1, 2].filter(42));
assertThrows(TypeError, () => [1, 2].filter({}));
