//
// Custom Iterator
//
const array = [1,2]
function makeIterator() {
	let nextIndex = 0;
	return {
		next() {
			if (nextIndex < array.length) {
				return { value: array[nextIndex++], done: false };
			}
			return { value: undefined, done: true };
		},
	};
}
const iter = makeIterator();
assertEquals({value: 1, done: false}, iter.next());
assertEquals({value: 2, done: false}, iter.next());
array.push(3)
assertEquals({value: 3, done: false}, iter.next());

assertEquals({value: undefined, done: true}, iter.next());



// General Iteration Behavior
// When you call set.values() or use for...of on a Set, it returns an iterator that:
//  - Traverses elements in the order they were inserted.
//  - Skips elements that are deleted before they are yielded.
//  - Includes elements added after iteration begins, only if they haven't yet been visited.


{
	// 1. Deletion Before Visit
	const s = new Set([1, 2, 3]);
	const it = s.values();
	
	s.delete(2); // Deleted before visiting
	assertEquals([1, 3], [...it]);
}

{
	// 2. Deletion After Visit
	const s = new Set([1, 2, 3]);
	const it = s.values();
	
	assertEquals(1, it.next().value);
	s.delete(1); // Already visited, doesn't affect anything
	assertEquals([2, 3], [...it]); 
}

{
	// 3. Addition During Iteration
	const s = new Set([1]);
	const it = s.values();
	
	assertEquals(1, it.next().value);
	s.add(2); // Added after first element
	assertEquals(2, it.next().value);
}

{
	// 4. Remove All Then Add Back
	const s = new Set([1, 2]);
	const it = s.values();
	
	s.delete(1);
	s.delete(2);
	s.add(2);
	
	assertEquals({ value: 2, done: false }, it.next());  
}

{
	// 5. Remove add at the end
	const s = new Set([1, 2]);
	const it = s.values();
	it.next();
	
	s.delete(1);
	s.delete(2);
	s.add(2);
	
	assertEquals({ value: 2, done: false }, it.next());  
}
