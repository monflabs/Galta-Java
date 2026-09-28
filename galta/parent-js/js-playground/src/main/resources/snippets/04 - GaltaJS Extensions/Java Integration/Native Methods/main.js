// Number, actually Integer
const n = 13;
console.log(n.$doubleValue());

// Array
const array = new Array(6,4,3,6,3,5)
console.log(array.$distinct());

// Map
const map = new Map([['a',1]])
map.$put('b',2)
console.log(JSON.stringify(map));
