{
	const primes = new Set([2, 3, 5, 7, 11, 13, 17, 19]);
	const squares = new Set([1, 4, 9, 16]);
	assertTrue(primes.isDisjointFrom(squares)); 
}

{
	const composites = new Set([4, 6, 8, 9, 10, 12, 14, 15, 16, 18]);
	const squares = new Set([1, 4, 9, 16]);
	assertFalse(composites.isDisjointFrom(squares)); 
}
