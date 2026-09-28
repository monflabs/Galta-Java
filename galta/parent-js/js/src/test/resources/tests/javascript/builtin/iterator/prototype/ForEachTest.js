function createTestIterator() {
    return ['a', 'b', 'c', 'd', 'e'].values();
}

// forEach: Apply a function to each element
let result = [];
createTestIterator().forEach(x => result.push(x.toUpperCase()));
assertEquals(result, ['A', 'B', 'C', 'D', 'E']); // Transformed elements

result = [];
createTestIterator().forEach(() => {});
assertEquals(result, []); // No effect

let count = 0;
createTestIterator().forEach(() => count++);
assertEquals(count, 5); // Count iterations
