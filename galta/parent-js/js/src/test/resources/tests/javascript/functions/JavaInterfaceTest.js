// exec() expects a functional interface that return a value
// its java implementation check that the value is correct (the parameter)

this.exec( () => 4, 4 )

function f2() {
  return 2;
}
this.exec( f2, 2 )
