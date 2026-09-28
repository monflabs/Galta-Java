function f() {
	return 1;
}

// Function declared above
var vf = f();
assertEquals(vf,1)


// Function declared below
var vg = g();
assertEquals(vg,2)

function g() {
	return 2;
}


var t = function t(v) {
	if(v>3) {
		return 5;
	}
	return t(v+1);
}(2)
assertEquals(t,5)


// Last named parameter wins
function fp1(a,b,a) {
	return a;
}
function fp2(a,a,b) {
	return a;
}
assertEquals(3,fp1(1,2,3))
assertEquals(2,fp2(1,2,3))
