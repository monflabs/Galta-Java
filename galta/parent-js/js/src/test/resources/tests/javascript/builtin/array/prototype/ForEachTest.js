const arr1 = [10, 20, 30, 40];

let sum = 0;
arr1.forEach( (v) => { sum+=v } );
assertEquals( 100, sum );

sum = 0;
arr1.forEach( (v,i) => { sum+=i } );
assertEquals( 6, sum );



const _this = [];
arr1.forEach( function (v,i) {
	assertSame(_this,this);
}, _this );

sum = 0;
const _this2 = [1,2,3,4];
arr1.forEach( function (v,i) { sum+=this[i] }, _this2 );
assertEquals( 10, sum );

// forEach always returns undefined
assertEquals(undefined, arr1.forEach(() => {}));

// Empty array: callback never called
let called = false;
[].forEach(() => { called = true; });
assertFalse(called);

// TypeError for non-callable
assertThrows(TypeError, () => [1, 2].forEach(null));
assertThrows(TypeError, () => [1, 2].forEach(42));
assertThrows(TypeError, () => [1, 2].forEach({}));
