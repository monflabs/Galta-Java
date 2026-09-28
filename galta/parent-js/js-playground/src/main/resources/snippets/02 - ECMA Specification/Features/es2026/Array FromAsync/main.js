async function* fetchPages() {
  for (let page = 1; page <= 3; page++) {
    // Resolves on a later microtask instead of a real timer.
    await Promise.resolve();
    yield "page-" + page;
  }
}

// Array.from() can't consume an async iterable - Array.fromAsync() awaits
// each value as it collects them, returning a promise of the array. (Its
// own log line lands SECOND below: each async-generator yield needs more
// microtask round-trips than the plain-array call underneath it.)
Array.fromAsync(fetchPages()).then(pages => console.log(pages.join(", ")));

// It also works on a plain array of promises, with an optional map function.
Array.fromAsync([Promise.resolve(1), Promise.resolve(2), 3], n => n * 10)
  .then(result => console.log(result.join(", ")));
