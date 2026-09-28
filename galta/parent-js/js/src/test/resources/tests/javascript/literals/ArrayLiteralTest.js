var a = 'A'
var ab = [ 'a', 'b']

var v1 = [a]
var v2 = [...ab]
var v3 = ['A',...ab]
var v4 = [...ab,'B']
var v5 = ['A',...ab,'B']

assertEquals(['A'],v1);
assertEquals(['a','b'],v2);
assertEquals(['A','a','b'],v3);
assertEquals(['a','b','B'],v4);
assertEquals(['A','a','b','B'],v5);


//
// Expressions in spread
function f() {return [6,7] }
const f2 = () => { return [8] };

assertEquals( [6,7], [...f()] )
assertEquals( [1,6,7], [1,...f()] )
assertEquals( [1,8], [1, ...f2()] )

assertEquals( [1,4,5], [1, ...(() => { return [4,5] })() ] )
assertEquals( [1,4,5,6], [1, ...(() => { return [4,5] })(), 6 ] )
assertEquals( [1,9], [1, ...( function f() { return [9] } )() ] )
assertEquals( [1,4,5,9], [1, ...(() => { return [4,5] })(), 9 ] )


//
// Trailing coma, empty coma
const a10 = []
const a11 = [,]
const a12 = [,,]
const a13 = [1,]
const a14 = [1,2,]
const a15 = [,1,2,]
const a16 = [,1,2,,]
assertEquals(SPARSE(), a10)
assertEquals(SPARSE(EMPTY(1)), a11)
assertEquals(SPARSE(EMPTY(2)), a12) 
assertEquals(SPARSE(1), a13)
assertEquals(SPARSE(1,2), a14)
assertEquals(SPARSE(EMPTY(1),1,2), a15)
assertEquals(SPARSE(EMPTY(1),1,2,EMPTY(1)), a16)
