class MyPromise extends Promise {}
assertTrue(Promise[Symbol.species] === Promise); // true by default
// Symbol.species is a getter-only accessor returning `this`, so a subclass's
// own [Symbol.species] resolves to the subclass, not the base class.
assertTrue(MyPromise[Symbol.species] === MyPromise);

let p = new MyPromise(res => res(1));
let q = p.then(v => v + 1);

assertTrue(q instanceof Promise);   // true
// .then() uses SpeciesConstructor(this, %Promise%): since MyPromise[Symbol.species]
// is MyPromise itself, the resulting promise is also a MyPromise instance.
assertTrue(q instanceof MyPromise);

// A subclass can still override [Symbol.species] to opt back into plain Promise.
class MyPromise2 extends Promise {
	static get [Symbol.species]() { return Promise; }
}
let p2 = new MyPromise2(res => res(1));
let q2 = p2.then(v => v + 1);
assertFalse(q2 instanceof MyPromise2);
