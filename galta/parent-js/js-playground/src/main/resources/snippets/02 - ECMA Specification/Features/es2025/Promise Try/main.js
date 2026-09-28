// A function that might throw synchronously OR return a promise -
// Promise.try() handles both the same way, without needing a
// try/catch around the call site.
function mightThrow(x) {
  if (x < 0) {
    throw new Error("negative: " + x);
  }
  return Promise.resolve(x * 2);
}

Promise.try(() => mightThrow(21))
  .then(value => console.log("resolved:", value));

Promise.try(() => mightThrow(-5))
  .catch(error => console.log("caught:", error.message));

// A plain synchronous function works too - its return value is wrapped
// in a promise just like Promise.resolve() would.
Promise.try(() => 40 + 2).then(value => console.log("sync result:", value));
