const arr1 = [1, 2, 3, 4];

assertEquals( [10, 20, 30, 40], arr1.map(x => x * 10) );

const _this = [];
[10,22].map(function (v,i) {
	assertEquals(i==0?10:22,v);
	assertSame(_this,this);
}, _this);

// Callback receives (element, index, array)
const indices = [];
[10, 20, 30].map((v, i, arr) => {
	indices.push(i);
	assertSame(arr1, arr1); // just a sanity check
	return v;
});
assertEquals([0, 1, 2], indices);

// Returns new array, doesn't mutate original
const orig = [1, 2, 3];
const mapped = orig.map(x => x * 2);
assertEquals([2, 4, 6], mapped);
assertEquals([1, 2, 3], orig);

// Empty array returns empty array
assertEquals([], [].map(x => x * 2));

// TypeError for non-callable callback
assertThrows(TypeError, () => [1, 2].map(null));
assertThrows(TypeError, () => [1, 2].map(42));
assertThrows(TypeError, () => [1, 2].map({}));
