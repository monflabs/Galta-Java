const set3 = new Set([1,2,3]);

const a = [];
set3.forEach( (value,key,map) => {
	console.log(`${key}=${value}`)
	a.push([key,value])
	assertSame(set3,map)
});
assertEquals([[1,1],[2,2],[3,3]],a);

const aa = []
set3.forEach( function(value,key,map) {
	console.log(`${key}=${value}`)
	aa.push([key,this+value])
	assertSame(set3,map)
}, "AA");
assertEquals([[1,"AA1"],[2,"AA2"],[3,"AA3"]],aa);
