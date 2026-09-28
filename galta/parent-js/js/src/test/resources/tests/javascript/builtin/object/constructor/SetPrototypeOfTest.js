// Object.setPrototypeOf — changes the prototype of an object

const animal = {
    speak() { return 'generic sound' }
}
const dog = {}
Object.setPrototypeOf(dog, animal)

assertEquals('generic sound', dog.speak())
assertSame(animal, Object.getPrototypeOf(dog))

// Override method after prototype set
dog.speak = function() { return 'woof' }
assertEquals('woof', dog.speak())

// Chain: dog → animal → Object.prototype
const poodle = {}
Object.setPrototypeOf(poodle, dog)
assertEquals('woof', poodle.speak())
assertSame(dog, Object.getPrototypeOf(poodle))

// setPrototypeOf to null removes prototype chain
const noProto = {}
Object.setPrototypeOf(noProto, null)
assertSame(null, Object.getPrototypeOf(noProto))

// setPrototypeOf returns the first argument
const obj = {}
const result = Object.setPrototypeOf(obj, animal)
assertSame(obj, result)

// After setting prototype, hasOwnProperty still works for own props
const base = { shared: 1 }
const derived = { own: 2 }
Object.setPrototypeOf(derived, base)
assertEquals(true, derived.hasOwnProperty('own'))
assertEquals(false, derived.hasOwnProperty('shared'))
assertEquals(1, derived.shared)
