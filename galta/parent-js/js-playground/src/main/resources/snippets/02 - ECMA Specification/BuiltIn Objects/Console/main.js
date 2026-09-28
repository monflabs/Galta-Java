// Outputting a single object
const someObject = { str: "Some text", id: 5 };
console.log(someObject);

// Outputting multiple objects
const car = "Dodge Charger";
console.info("My first car was a", car, ". The object is:", someObject);

// Using string substitutions
for (let i = 0; i < 5; i++) {
  console.log("Hello, %s. You've called me %d times.", "Bob", i + 1);
}
