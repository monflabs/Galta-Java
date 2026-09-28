const arr1 = [1, 2, 3, 4];
assertEquals( [2, 4, 6, 8], arr1.flatMap(x => [x * 2]) );

// only one level is flattened
assertEquals( [[2], [4], [6], [8]], arr1.flatMap(x => [[x * 2]]) );

const _this = [];
[10,22].flatMap(function (v,i) {
	assertEquals(i==0?10:22,v);
	assertSame(_this,this);
}, _this);
