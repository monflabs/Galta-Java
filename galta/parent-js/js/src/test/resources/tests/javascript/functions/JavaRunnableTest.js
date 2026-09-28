var a = 1
assertEquals(1,a)

this.execSync(() => {
	a = 2;
});
assertEquals(2,a)

this.execSync(() => 3)
assertEquals(2,a)

this.execSync(() => {
	a = 4
});
assertEquals(4,a)

//var v;
//this.execSync(() => {
//	v = this.one();
//});
//assertEquals(v,"ONE")
//function f(cb) {
//	cb();
//}

//f( () => {
//	v = this.one();	
//})
//assertEquals(v,"ONE")
