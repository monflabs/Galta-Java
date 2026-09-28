var lp;

//
// Iterate objects
//
var idx=0; var values = [1,2,3,4,5]
for( var i of obj.getForAllList() ) {
	assertEquals(values[idx],i)
	idx++
} 
assertEquals(values.length,idx) 

for( var i of obj.getForAllMap() ) {
	assertTrue(i[1]=='D' || i[1]=='EE')	
} 

var idx=0
for( var i of "ABC" ) {
	assertEquals("ABC".charAt(idx),i)
	idx++	
} 

var idx=0; var values = [1,2,3]
for( var i of obj.getForAllIterator() ) {
	assertEquals(values[idx],i)
	idx++	
} 

var idx=0; var values = [1,2,3,4]
for( var i of obj.getForAllEnumeration() ) {
	assertEquals(values[idx],i)
	idx++	
}
assertEquals(4,idx) 

var idx=0; var values = [1,2,3,4,5]
for( var i of values ) {
	assertEquals(values[idx],i)
	idx++
} 


//
// Use VAR
//

lp=0;
for( var i of obj.getForAllList() )
	lp++
assertEquals(5,lp)

lp=0;
for( var i of obj.getForAllArray() )
	lp++
assertEquals(6,lp)


//
// Use LET
//

lp=0;
for( let i of obj.getForAllList() )
	lp++
assertEquals(5,lp)


//
// Use CONST
//

lp=0;
for( const i of obj.getForAllList() )
	lp++
assertEquals(5,lp)


//
// Use GLOBAL
//

lp=0; let g;
for( g of obj.getForAllList() ) 
	lp++;
assertEquals(5,lp)


//
// On Member property
//
const o = {a:null}
lp=0;
for( o.a of obj.getForAllList() )
	lp++;
assertEquals(5,lp)


//
// The loop's own collection expression must resolve a shadowed identifier
// to the INNER (block-scoped) binding, same as any other statement in the
// same block - not to an outer same-named binding.
//
let shadow = ["outer"];
{
	const shadow = ["inner1", "inner2"];
	let seen = [];
	for( const v of shadow )
		seen.push(v);
	assertEquals(["inner1", "inner2"], seen);
}
