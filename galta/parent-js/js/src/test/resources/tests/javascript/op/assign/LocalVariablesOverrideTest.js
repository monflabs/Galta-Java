function f() {
	return 1
}
assertEquals(2, f());


function f() {
	return 2;
}
assertEquals(2, f());


function func(t) {
  assertEquals(44,t);
  var t;
  assertEquals(44,t);
  var t=undefined;
  assertEquals(undefined,t);
  var t = 99;
  assertEquals(99,t);
}

func(44)
