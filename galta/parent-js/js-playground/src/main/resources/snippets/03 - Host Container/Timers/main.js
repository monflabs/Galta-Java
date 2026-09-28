console.log("1: synchronous code runs first");

setTimeout(() => console.log("3: setTimeout(..., 0) - runs once the script itself is done"), 0);

const handle = setInterval(() => console.log("never runs - cleared below"), 1000);
clearInterval(handle);

console.log("2: still synchronous - the callback above hasn't run yet");
