const a = [11,22,,,44]
a.toto = 44;
a.titi = 55;
a

const r1 = []
for( let i in a ) {
	r1.push(i);
}
console.log(r1)
//assertEquals([ 11, 22, 44, 55 ], r1);

const r2 = []
for( let i of a ) {
	r2.push(i);
}
console.log(r2)
//assertEquals([ 2, 3 ], r2);

const r3 = []
for( let i in Object.getOwnPropertyNames(a) ) {
	r3.push(i);
}
console.log(r3)
//assertEquals([ '0', '1', '2', '3', '4' ], r2);

const r4 = []
for( let i in Object.getOwnPropertyDescriptors(a) ) {
	r4.push(i);
}
console.log(r4)
//assertEquals([ '0', '1', 'length', 'toto', 'titi' ], r2);

Object.getOwnPropertyDescriptor(a,'3')
