var v=0

if(true) v=1
assertEquals(1,v)
if(true) {v=2}
assertEquals(2,v)

if(false) v=3
assertEquals(2,v)
if(false) {v=3}
assertEquals(2,v)

if(true) v=4; else v=5
assertEquals(4,v)
if(false) v=6; else v=7
assertEquals(7,v)
if(false) v=8
else v=9
assertEquals(9,v)
// No automatic semicolon insertion before "else" on the same line
assertThrows(SyntaxError, () => eval("if(true) v=4 else v=5"))

if(1) v=10
assertEquals(10,v)
if(0) v=11
assertEquals(10,v)
if('str') v=12
assertEquals(12,v)
if('') v=13
assertEquals(12,v)

if(true && true) v=20
assertEquals(20,v)
if(false && true) v=21
assertEquals(20,v)
if(1 && true) v=22
assertEquals(22,v)
if(true && 1) v=23
assertEquals(23,v)
