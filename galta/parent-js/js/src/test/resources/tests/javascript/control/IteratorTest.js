//
// Test the iterator names
//
// See the list here:
//    https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Iterator

const ts = Object.prototype.toString;

// Array
assertEquals("[object Array Iterator]", ts.apply([].values()));
assertEquals("[object Array Iterator]", ts.apply([].keys()));
assertEquals("[object Array Iterator]", ts.apply([].entries()));
assertEquals("[object Array Iterator]", ts.apply([][Symbol.iterator]()));

// String
assertEquals("[object String Iterator]", ts.apply(""[Symbol.iterator]()));
assertEquals("[object RegExp String Iterator]", ts.apply("".matchAll("")));

// Map iterator
assertEquals("[object Map Iterator]", ts.apply((new Map()).values()));
assertEquals("[object Map Iterator]", ts.apply((new Map()).keys()));
assertEquals("[object Map Iterator]", ts.apply((new Map()).entries()));
assertEquals("[object Map Iterator]", ts.apply((new Map())[Symbol.iterator]()));

// Set iterator
assertEquals("[object Set Iterator]", ts.apply((new Set()).values()));
assertEquals("[object Set Iterator]", ts.apply((new Set()).keys()));
assertEquals("[object Set Iterator]", ts.apply((new Set()).entries()));
assertEquals("[object Set Iterator]", ts.apply((new Set())[Symbol.iterator]()));

//Regexp
assertEquals("[object RegExp String Iterator]", ts.apply(/abc/[Symbol.matchAll]()));
