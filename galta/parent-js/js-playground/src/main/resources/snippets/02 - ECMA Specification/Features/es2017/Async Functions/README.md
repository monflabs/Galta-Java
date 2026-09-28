# Async Functions

An `async function` implicitly returns a Promise, and `await` inside it pauses execution until the awaited Promise settles - letting asynchronous code (like a `fetch` call here) read like synchronous code instead of chaining `.then()` callbacks.
