const { promise, resolve, reject} = Promise.withResolvers();

// Resolves/rejects on a later microtask instead of a real timer
Promise.resolve().then(() => { Math.random() > 0.5 ? resolve("Success") : reject("Error") });
promise.then(result => console.log(result)).catch(error => console.error(error));