for( var i=0; i>7; i++ ) {}

var idx=0
for( var i=0; i<5; i++ )
	idx++
assertEquals(5,idx)
	 
var idx=0
for( var i=0; i<5; i++ ) {
	idx++
}	
assertEquals(5,idx)

var idx=0
for( let i=0; i<5; i++ )
	idx++
assertEquals(5,idx)

var idx=0
for( i=0; i<5; i++ )
	idx++
assertEquals(5,idx)

var idx=0
loop: for( var i=0; i<5; i++ ) {
	idx++;
	break;
}
assertEquals(1,idx)

var idx=0
loop: for( var j=0; j<5; j++ ) { 
	for( var i=0; i<5; i++ ) {
		idx++; 
		break loop;
	} 
}
assertEquals(1,idx)

var idx=0
for( var j=0; j<5; j++ ) { 
	loop: for( var i=0; i<5; i++ ) {
		idx++; 
		break loop;
	} 
}
assertEquals(5,idx)

var idx=0
for( var i=0; i<5; i++ ) {
	idx++;
	continue;
}
assertEquals(5,idx)

var idx=0
loop: for( var j=0; j<5; j++ ) { 
	for( var i=0; i<5; i++ ) {
		idx++; 
		continue loop;
	} 
}
assertEquals(5,idx)

var idx=0
for( var j=0; j<5; j++ ) { 
	loop: for( var i=0; i<5; i++ ) {
		idx++; 
		if(idx<4) {
			continue loop;
		}
	} 
}
assertEquals(25,idx)


let l2, loop=0;
for( let l=0; l<5; l++ ) {
	let l = 33;
	l2 = l;
	loop++;
}
assertEquals(33,l2)
assertEquals(5,loop)

let loop2;
for( let l=0; l<5; l++ ) {
	loop2++;
}
assertEquals(NaN,loop2)

// Const is allowed but the variable cannot be updated
let ll=0;
for( const l=0; ll<2; ll++ ) {
}
assertEquals(2,ll)

assertThrows( () => eval(`{
	for( const l2=0; l2<2; l2++ ) {
	}`)
)

// A `let`-declared loop variable gets a FRESH binding per iteration, so a
// closure created in the body captures ITS OWN iteration's value - unlike
// `var`, which is shared/mutated across the whole loop.
{
	let fns = [];
	for( let i=0; i<5; i++ ) {
		fns.push(() => i);
	}
	assertEquals([0,1,2,3,4], fns.map(f => f()));
}

{
	let fns = [];
	for( var v=0; v<3; v++ ) {
		fns.push(() => v);
	}
	assertEquals([3,3,3], fns.map(f => f()));
}

// continue still advances to a fresh binding for the next iteration.
{
	let fns = [];
	for( let i=0; i<5; i++ ) {
		if(i===2) continue;
		fns.push(() => i);
	}
	assertEquals([0,1,3,4], fns.map(f => f()));
}

// A mutation made inside the body carries over into the NEXT iteration's
// fresh binding (the per-iteration copy isn't re-derived from the original
// init value - it reflects whatever the body left the variable as).
{
	let seen = [];
	for( let i=0; i<5; i++ ) {
		seen.push(i);
		if(i===1) i = 3;
	}
	assertEquals([0,1,4], seen);
}

// Nested for(let...) loops each get their own independent per-iteration binding.
{
	let fns = [];
	for( let i=0; i<2; i++ ) {
		for( let k=0; k<2; k++ ) {
			fns.push(() => i+"-"+k);
		}
	}
	assertEquals(["0-0","0-1","1-0","1-1"], fns.map(f => f()));
}

// Destructured loop-head declarations are all copied per iteration.
{
	let fns = [];
	for( let [a,b]=[0,10]; a<3; a++,b++ ) {
		fns.push(() => a+","+b);
	}
	assertEquals(["0,10","1,11","2,12"], fns.map(f => f()));
}

// A direct eval() inside the loop body must see the CURRENT iteration's
// loop variable, not some stale/unrelated value - eval's direct-eval
// detection correlates the calling context against JSContext.get(), which
// must stay consistent with whatever is explicitly threaded through the
// AST for each iteration's own fresh context.
{
	let out = [];
	for( let a=0; a<3; a++ ) {
		out.push(eval("a"));
	}
	assertEquals([0,1,2], out);
}

