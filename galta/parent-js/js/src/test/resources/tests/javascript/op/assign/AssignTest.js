var aa=1
assertEquals(1,aa)

aa+=2
assertEquals(3,aa)

aa-=1
assertEquals(2,aa)

aa*=3
assertEquals(6,aa)

aa/=2
assertEquals(3,aa)

aa|=16
assertEquals(19,aa)

aa&=6
assertEquals(2,aa)

let bb = 1;
bb<<=3
assertEquals(8,bb)

bb>>=2
assertEquals(2,bb)

let cc = 2;
cc<<=3
assertEquals(16,cc)

cc>>>=2
assertEquals(4,cc)


//
// HEX token as a variable name
const x11 = 44
assertEquals(44,x11)

// Assigning to an undeclared identifier throws ReferenceError (not a generic Error).
assertThrows(ReferenceError, () => { undeclaredAssignTarget = 1; });
