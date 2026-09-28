console.log(Error.isError(new Error("boom")));
console.log(Error.isError(new TypeError("bad type")));

// Unlike `instanceof Error`, this also recognizes an error that came from
// a DIFFERENT realm (a different iframe/worker/vm context), where
// `instanceof` would wrongly say false since its Error constructor is a
// distinct object. Here, a plain object shaped like an error still isn't
// a real one.
console.log(Error.isError({ message: "looks like an error, isn't one" }));
console.log(Error.isError("boom"));
console.log(Error.isError(null));
