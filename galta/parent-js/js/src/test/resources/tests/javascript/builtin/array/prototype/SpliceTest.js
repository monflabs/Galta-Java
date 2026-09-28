{
	// Remove 0 (zero) elements before index 2, and insert "drum"
	let myFish = ['angel', 'clown', 'mandarin', 'sturgeon']
	let removed = myFish.splice(2, 0, 'drum')
	assertEquals(["angel", "clown", "drum", "mandarin", "sturgeon"],myFish)
	assertEquals([],removed)
}
{
	// Remove 0 (zero) elements before index 2, and insert "drum" and "guitar"
	let myFish = ['angel', 'clown', 'mandarin', 'sturgeon']
	let removed = myFish.splice(2, 0, 'drum', 'guitar')
	assertEquals(["angel", "clown", "drum", "guitar", "mandarin", "sturgeon"],myFish)
	assertEquals([],removed)
}
{
	// Remove 1 element at index 3
	let myFish = ['angel', 'clown', 'drum', 'mandarin', 'sturgeon']
	let removed = myFish.splice(3, 1)
	assertEquals(["angel", "clown", "drum", "sturgeon"],myFish)
	assertEquals(["mandarin"],removed)
}
{
	// Remove 1 element at index 2, and insert "trumpet"
	let myFish = ['angel', 'clown', 'drum', 'sturgeon']
	let removed = myFish.splice(2, 1, 'trumpet')
	assertEquals(["angel", "clown", "trumpet", "sturgeon"],myFish)
	assertEquals(["drum"],removed)
}
{
	// Remove 2 elements from index 0, and insert "parrot", "anemone" and "blue"
	let myFish = ['angel', 'clown', 'trumpet', 'sturgeon']
	let removed = myFish.splice(0, 2, 'parrot', 'anemone', 'blue')
	assertEquals(["parrot", "anemone", "blue", "trumpet", "sturgeon"],myFish)
	assertEquals(["angel", "clown"],removed)
}
{
	// Remove 2 elements, starting from index 2
	let myFish = ['parrot', 'anemone', 'blue', 'trumpet', 'sturgeon']
	let removed = myFish.splice(2, 2)
	assertEquals(["parrot", "anemone", "sturgeon"],myFish)
	assertEquals(["blue", "trumpet"],removed)
}
{
	// Remove 1 element from index -2
	let myFish = ['angel', 'clown', 'mandarin', 'sturgeon']
	let removed = myFish.splice(-2, 1)
	assertEquals(["angel", "clown", "sturgeon"],myFish)
	assertEquals(["mandarin"],removed)
}
{
	// Remove all elements, starting from index 2
	let myFish = ['angel', 'clown', 'mandarin', 'sturgeon']
	let removed = myFish.splice(2)
	assertEquals(["angel", "clown"],myFish)
	assertEquals(["mandarin", "sturgeon"],removed)
}
