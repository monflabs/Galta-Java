function* letters() {
  yield "a";
  yield "b";
}

// Iterator.concat() chains any number of iterable OBJECTS (generators,
// arrays, Sets, ...) into a single lazy iterator with all the Iterator
// helper methods available on the result - a bare string primitive isn't
// accepted, even though it's iterable, since it isn't an object.
const chained = Iterator.concat(letters(), [1, 2, 3], new Set(["x", "y"]));
console.log(chained.toArray().join(", "));

// It stays lazy - take(3) below stops after the first source, never
// touching the (infinite) second one.
function* naturals() {
  let n = 1;
  while (true) {
    yield n++;
  }
}
console.log(Iterator.concat([0], naturals()).take(3).toArray().join(", "));
