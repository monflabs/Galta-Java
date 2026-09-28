var v = -1

switch(0) { 
	case 0: v=0; break; 
	case 1: v=1; break; 
	case 2: v=2; break; 
	default: v=99 
}
assertEquals(0,v);

switch(1) { 
	case 0: v=0; break; 
	case 1: v=1; break; 
	case 2: v=2; break; 
	default: v=99 
}
assertEquals(1,v);

switch(2) { 
	case 0: v=0; break; 
	case 1: v=1; break; 
	case 2: v=2; break; 
	default: v=99 
}
assertEquals(2,v);

switch(3) { 
	case 0: v=0; break; 
	case 1: v=1; break; 
	case 2: v=2; break; 
	default: v=99 
}
assertEquals(99,v);

switch(3) { 
	case 0: v=0; break; 
	default: v=99; break;
	case 1: v=1; break; 
	case 2: v=2; break; 
}
assertEquals(99,v);

switch(0) { 
	case 0: v=0; break;  
	case 1: v=1;  
	case 2: v=2;  
	default: v=99 
}
assertEquals(0,v);

switch(0) { 
	case 0: v=0;  
	case 1: v=1;  break;  
	case 2: v=2;  
	default: v=99 
}
assertEquals(1,v);

switch(0) {
	case 0: v=0;
	case 1: v=1;
	case 2: v=2;
	default: v=99
}
assertEquals(99,v);

// Unreachable code after return in switch case
function switchWithReturn(x) {
	switch(x) {
		case 1:
			return "one";
			break;
		case 2:
			return "two";
			break;
		default:
			return "other";
			break;
	}
}
assertEquals("one", switchWithReturn(1));
assertEquals("two", switchWithReturn(2));
assertEquals("other", switchWithReturn(3));

// Unreachable code after throw in switch case
function switchWithThrow(x) {
	let result;
	switch(x) {
		case 1:
			result = "ok";
			break;
		case 2:
			throw new Error("bad");
			break;
	}
	return result;
}
assertEquals("ok", switchWithThrow(1));
assertThrows(Error, () => switchWithThrow(2));

// Unreachable code after return in if-else
function ifElseReturn(x) {
	if(x > 0) {
		return "positive";
	} else {
		return "non-positive";
	}
	return "unreachable";
}
assertEquals("positive", ifElseReturn(1));
assertEquals("non-positive", ifElseReturn(-1));

// Unreachable code after while(true) in switch case
function switchWithInfiniteLoop(x) {
	switch(x) {
		case 1:
			if(x > 0) {
				var r1 = 0;
				while (true) {
					return "loop";
				}
				r1 = (r1 + 1) | 0;
			} else {
				break;
			}
			break;
	}
	return "after";
}
assertEquals("loop", switchWithInfiniteLoop(1));
assertEquals("after", switchWithInfiniteLoop(2));

// A switch's CaseBlock gets its own lexical environment - a let/const
// declared in any case shadows a same-named outer binding instead of
// colliding with it at parse time (previously: "Variable or function x is
// already defined in scope").
{
	let x = "outside";
	switch (1) {
		case 1:
			let x = "inside";
			assertEquals("inside", x);
			break;
	}
	assertEquals("outside", x);
}

// But the switch's own discriminant expression evaluates in the OUTER
// scope, BEFORE the CaseBlock's environment exists at all.
{
	let y = "outer-y";
	switch (y) {
		case "outer-y":
			let y = "inner-y";
			assertEquals("inner-y", y);
			break;
		default:
			throw new Error("discriminant should have resolved to the outer y");
	}
}
