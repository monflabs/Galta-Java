// Iterable to objects
const arr = [ ['a', '1'], ['b', '2'], ['c', '3'] ];
const obj = Object.fromEntries(arr);
console.log(obj); // { a: "1", b: "2", c: "3" }

// URLParams-style string turned into [key, value] entries
const paramsString = 'param1=foo&param2=baz';
const searchParams = paramsString.split('&').map(pair => pair.split('='));

console.log(Object.fromEntries(searchParams));    // => {param1: "foo", param2: "baz"}