// Tail calls have been removed from the specification
// Factorial(10000) fails
function factorial(n, acc = 1) {
    if (n === 0) {
        return acc
    }
    return factorial(n - 1, n * acc)
}
console.log(factorial(5)); //120

// Larger inputs recurse deeply since there is no tail-call optimization -
// catch the resulting stack overflow rather than let it crash the script.
[10, 100, 1000, 10000].forEach(function(n) {
    try {
        console.log(factorial(n));
    } catch (e) {
        console.log("factorial(" + n + ") failed: " + e.message);
    }
});