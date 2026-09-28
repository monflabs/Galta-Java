// print() is Rhino's own shell console function, predating console.log().
print("Hello from the Rhino-compatible print() function.");

console.log("version():", version());
version("170");
console.log("version() after version('170'):", version());

console.log("options():", options());

gc();
print("gc() ran without throwing.");
