const o1 = {a:1, b:2}

assertTrue( "a" in o1)
assertTrue( "b" in o1)
assertFalse( "c" in o1)

const o2 = {c: 3}
Object.setPrototypeOf(o2,o1)
assertTrue( "a" in o2)
assertTrue( "b" in o2)
assertTrue( "c" in o2)
assertFalse( "d" in o2)

const o3 = {d: 4}
Object.setPrototypeOf(o3,o2)
assertTrue( "a" in o3)
assertTrue( "c" in o3)
assertTrue( "d" in o3)

assertTrue( "toString" in o3)

const car = { make: 'Honda', model: 'Accord', year: 1998 };
assertTrue('make' in car)
delete car.make;
if ('make' in car === false) {
  car.make = 'Suzuki';
}
assertEquals("Suzuki",car.make);
