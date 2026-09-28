// TypeScript-style type hints: GaltaJS parses the full modern TypeScript
// annotation syntax - it's just never CHECKED. Every annotation below is
// discarded the moment it's parsed: no type error, ever, no matter how
// wrong the annotation is.

// Variables
let age: number = 42;
let name: string = "Ada";
let scores: number[] = [10, 20, 30];
let pair: [string, number] = ["x", 1];
let mode: "fast" | "slow" = "fast";
let anything: unknown = { any: "thing" };

// A type annotation is pure decoration - GaltaJS never checks it.
let lie: number = "not a number at all";
console.log(`lie = ${lie} (typeof ${typeof lie}) - annotated "number", never checked`);

// Object and function types
let user: { name: string, age: number } = { name: "Ada", age: 42 };
let add: (a: number, b: number) => number = (a, b) => a + b;
console.log(`${user.name} is ${user.age}, add(2,3) = ${add(2, 3)}`);

// Functions: parameter types, optional parameters, default values, return types
function greet(who: string, excited?: boolean): string {
	return excited ? `Hello, ${who}!!!` : `Hello, ${who}.`;
}
console.log(greet("world"), greet("world", true));

function sum(...values: number[]): number {
	return values.reduce((a, b) => a + b, 0);
}
console.log(`sum(1,2,3) = ${sum(1, 2, 3)}`);

// Generics - parsed, never instantiated or checked
function identity<T>(value: T): T {
	return value;
}
console.log(`identity("hi") = ${identity("hi")}, identity(7) = ${identity(7)}`);

// Classes: typed fields, typed/optional constructor and method parameters, generics
class Box<T> {
	value: T;
	label?: string;

	constructor(value: T, label?: string) {
		this.value = value;
		this.label = label;
	}

	map<R>(f: (v: T) => R): Box<R> {
		return new Box(f(this.value), this.label);
	}
}
const box: Box<number> = new Box(21, "life");
const doubled: Box<number> = box.map((v: number): number => v * 2);
console.log(`${doubled.label} = ${doubled.value}`);

interface Shape {
	area(): number;
}
class Circle implements Shape {
	radius: number = 1;
	area(): number {
		return Math.PI * this.radius * this.radius;
	}
}
console.log(`circle area ~ ${new Circle().area().toFixed(2)}`);

// "type"/"interface" statements: parsed for their syntax, then thrown away -
// they produce no runtime object, no class, nothing at all.
type Point = { x: number, y: number };
type Ternary<T> = T extends string ? "string" : "other";
interface Named {
	name: string;
}
declare function externallyDefined(x: number): void;

console.log("typeof Point:", typeof Point);      // undefined - "type" never creates a value
console.log("typeof Named:", typeof Named);      // undefined - neither does "interface"

// "type"/"interface"/"declare" remain ordinary identifiers everywhere else
let type = "still just a variable";
let interfaceCount = 3;
console.log(type, interfaceCount);
