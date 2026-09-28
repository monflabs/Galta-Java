const map3 = new Map([
  [1, "one"],
  [2, "two"],
  [3, "three"],
]);

const a = [];
map3.forEach( (value,key,map) => {
	console.log(`${key}=${value}`)
	a.push([key,value])
	assertSame(map3,map)
});
assertEquals([[1,"one"],[2,"two"],[3,"three"]],a);

const aa = []
map3.forEach( function(value,key,map) {
	console.log(`${key}=${value}`)
	aa.push([key,this+value])
	assertSame(map3,map)
}, "AA");
assertEquals([[1,"AAone"],[2,"AAtwo"],[3,"AAthree"]],aa);
