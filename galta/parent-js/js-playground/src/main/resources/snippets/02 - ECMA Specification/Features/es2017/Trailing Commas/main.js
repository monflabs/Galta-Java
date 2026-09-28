// Params include trailing comma
function func(a,b,) { // declaration
    console.log(a, b);
}
func(1,2,); // invocation

// A trailing comma is only allowed after at least one parameter/argument -
// a comma with nothing before it is a syntax error, not a trailing comma:
//   function func1(,) { ... }  // SyntaxError: missing formal parameter
//   func1(,);                 // SyntaxError: expected expression, got ','