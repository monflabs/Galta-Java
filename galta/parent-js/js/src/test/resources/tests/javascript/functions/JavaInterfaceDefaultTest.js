// exec() expects a functional interface that return a value
// its java implementation check that the value is correct (the parameter)
// and also invoke and test default methods

this.exec( () => 4, 4 )

function f2() {
  return 2;
}
this.exec( f2, 2 )
