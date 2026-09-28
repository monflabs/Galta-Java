// Basic: sum right to left
assertEquals(6, [0, 1, 2, 3].reduceRight((a, b) => a + b))

// Flatten in reverse order
assertEquals([4,5,2,3,0,1], [[0,1],[2,3],[4,5]].reduceRight((a,b) => a.concat(b), []))

// Single element, no initialValue: returns that element without calling callback
let called = false
assertEquals(42, [42].reduceRight(() => { called = true; return 0; }))
assertFalse(called)

// Empty array with initialValue returns initialValue without calling callback
assertEquals(7, [].reduceRight(() => { called = true; return 0; }, 7))
assertFalse(called)

// Empty array without initialValue throws TypeError
assertThrows(TypeError, () => [].reduceRight((a,b) => a+b))

// Callback receives (accumulator, currentValue, currentIndex, array)
const arr = [10, 20, 30]
const indices = []
const values = []
arr.reduceRight((acc, val, idx, src) => {
    indices.push(idx)
    values.push(val)
    assertSame(arr, src)
    return acc + val
}, 0)
assertEquals([2,1,0], indices)
assertEquals([30,20,10], values)

// String concatenation right-to-left
assertEquals('cba', ['a','b','c'].reduceRight((acc,v) => acc + v, ''))

// Two-element array without initial value: rightmost is accumulator first
assertEquals('ba', ['a','b'].reduceRight((acc,v) => acc+v))
