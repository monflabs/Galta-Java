function* naturals() {
  let n = 1;
  while (true) {
    yield n++;
  }
}

// map/filter/take chain lazily over an infinite generator - nothing runs
// until toArray() actually pulls values.
const firstSquares = naturals()
  .map(n => n * n)
  .filter(n => n % 2 === 0)
  .take(5)
  .toArray();
console.log(firstSquares.join(", "));

// drop skips the first n values, reduce folds the rest.
const sum = naturals().drop(10).take(5).reduce((total, n) => total + n, 0);
console.log(sum);

// Iterator.from() wraps any iterable (here, a plain array) with the same
// helper methods.
const upper = Iterator.from(["a", "b", "c"]).map(s => s.toUpperCase()).toArray();
console.log(upper.join(""));
