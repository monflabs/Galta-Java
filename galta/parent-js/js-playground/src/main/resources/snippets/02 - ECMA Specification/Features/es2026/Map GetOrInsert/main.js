const cache = new Map();

// getOrInsert: returns the existing value, or stores and returns the
// given default - one call instead of has()/get()/set().
console.log(cache.getOrInsert("a", 1));
console.log(cache.getOrInsert("a", 999));   // "a" already there: 999 is ignored
console.log([...cache.entries()]);

// getOrInsertComputed: like getOrInsert, but the value is computed lazily -
// only called when the key is actually missing.
let calls = 0;
function expensive(key) {
  calls++;
  return key.length * 10;
}
console.log(cache.getOrInsertComputed("hello", expensive));
console.log(cache.getOrInsertComputed("hello", expensive));   // cached: expensive() not called again
console.log("expensive() calls:", calls);
