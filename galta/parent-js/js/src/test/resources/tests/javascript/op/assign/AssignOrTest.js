//
// Variable
let x;

x = null
x ||= 'here'
assertEquals('here', x);

x = ''
x ||= 'here again'
assertEquals('here again', x);

x = true
x ||= 'is it'
assertEquals(true, x);

x = false
x ||= 'was false'
assertEquals('was false', x);


//
// Object member
const a = { duration: 50, title: '' };

a.duration ||= 10;
assertEquals(50, a.duration);

a.title ||= 'title is empty.';
assertEquals('title is empty.', a.title);

// Array member
const arr = [1, 0];
arr[0] ||= 99;
assertEquals(1, arr[0]);
arr[1] ||= 99;
assertEquals(99, arr[1]);

// Short-circuit: when the LHS is already truthy, the RHS must not be evaluated
// and no write (PutValue) is attempted at all.
{
	let rhsEvaluated = false;
	const o = { truthy: 1 };
	o.truthy ||= (rhsEvaluated = true, 2);
	assertEquals(false, rhsEvaluated);
	assertEquals(1, o.truthy);

	// A non-writable but truthy property: since the write is never attempted,
	// this must not throw even though the property couldn't be assigned.
	const nonWritable = {};
	Object.defineProperty(nonWritable, "prop", { value: 2, writable: false, configurable: true });
	assertEquals(2, nonWritable.prop ||= 1);
	assertEquals(2, nonWritable.prop);
}

// NamedEvaluation: an anonymous function/arrow/class assigned via ||= to a plain
// identifier infers its .name from that identifier.
{
	let f = 0;
	f ||= function() {};
	assertEquals("f", f.name);

	let arrow = 0;
	arrow ||= () => {};
	assertEquals("arrow", arrow.name);

	let cls = 0;
	cls ||= class {};
	assertEquals("cls", cls.name);
}
