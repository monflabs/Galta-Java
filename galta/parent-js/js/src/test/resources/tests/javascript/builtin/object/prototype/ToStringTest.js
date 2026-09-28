// Basic object
assertEquals("[object Object]", {}.toString())
assertEquals("[object Object]", Object.prototype.toString.call({}))

// Null and Undefined
assertEquals("[object Null]", Object.prototype.toString.call(null))
assertEquals("[object Undefined]", Object.prototype.toString.call(undefined))

// Arrays
assertEquals("[object Array]", [].toString.call.call(Object.prototype.toString, []))
assertEquals("[object Array]", Object.prototype.toString.call([]))
assertEquals("[object Array]", Object.prototype.toString.call([1,2,3]))

// Functions
assertEquals("[object Function]", Object.prototype.toString.call(function(){}))
assertEquals("[object Function]", Object.prototype.toString.call(() => {}))

// Symbol.toStringTag customization
const tagged = { [Symbol.toStringTag]: 'Custom' }
assertEquals("[object Custom]", Object.prototype.toString.call(tagged))

// Promise has Symbol.toStringTag "Promise"
assertEquals("[object Promise]", Object.prototype.toString.call(Promise.resolve()))

// Map and Set
assertEquals("[object Map]", Object.prototype.toString.call(new Map()))
assertEquals("[object Set]", Object.prototype.toString.call(new Set()))

// RegExp
assertEquals("[object RegExp]", Object.prototype.toString.call(/abc/))

// Date
assertEquals("[object Date]", Object.prototype.toString.call(new Date()))
