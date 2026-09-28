var lp;

//
// Iterate objects
//
var values = [0,1,2,3,4]

var idx=0;
for( var i in obj.getForAllArray() ) {
	assertEqualsStrict(String(idx),i)
	idx++
}

var idx=0;
for( var i in obj.getForAllList() ) {
	assertEqualsStrict(String(idx),i)
	idx++
} 

var idx=0;
for( var i in obj.getForAllMap() ) {
	assertTrue(i=='d' || i=='e')	
} 

var idx=0
for( var i in "ABC" ) {
	assertEqualsStrict(["0","1","2"][idx],i)
	idx++	
} 


var idx=0
const ss = new String("ABC")
ss.ten = "10";
for( var i in "ABC" ) {
	assertEqualsStrict(["0","1","2","ten"][idx],i)
	idx++	
} 


//
// Use VAR
//

lp=0;
for( var i in obj.getForAllList() )
	lp++
assertEquals(5,lp)

lp=0;
for( var i in obj.getForAllArray() )
	lp++
assertEquals(6,lp)

lp=0;
for( var i in "ABC" )
	lp++
assertEquals(3,lp)


//
// Use LET
//

lp=0;
for( let i in obj.getForAllList() )
	lp++
assertEquals(5,lp)


//
// Use GLOBAL
//

lp=0; let g;
for( g in obj.getForAllList() ) 
	lp++;
assertEquals(5,lp)


//
// On Member property
//
const o = {a:null}
lp=0;
for( o.a in obj.getForAllList() )
	lp++;
assertEquals(5,lp)


//
// The loop's own collection expression must resolve a shadowed identifier
// to the INNER (block-scoped) binding, same as any other statement in the
// same block - not to an outer same-named binding.
//
let shadow = { outerKey: 1 };
{
	const shadow = { innerKey: 1 };
	let seen = [];
	for( const k in shadow )
		seen.push(k);
	assertEquals(["innerKey"], seen);
}
