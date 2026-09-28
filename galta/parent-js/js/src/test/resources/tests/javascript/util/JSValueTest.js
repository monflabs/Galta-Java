class C {
	va = "A";
	get a() { return this.va; }
	set a(v) { this.va = va; }
}

class CC {
	vb = new C();
	get b() { return this.vb; }
	set b(v) { this.va = vb; }
}

var aC = new C();
var aCC = new CC();
