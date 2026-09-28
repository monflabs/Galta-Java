// Object
const o = { a: 1, b: 2}
const descObject = Object.getOwnPropertyDescriptors(o);

const objectExpected = {
  a: {
    value: 1,
    writable: true,
    enumerable: true,
    configurable: true
  },
  b: {
    value: 2,
    writable: true,
    enumerable: true,
    configurable: true
  }
}
assertEquals( objectExpected, descObject );


// array
const a = [10,20];
const descArray = Object.getOwnPropertyDescriptors(a)
const arrayExpected = {
  '0': {
    value: 10,
    writable: true,
    enumerable: true,
    configurable: true
  },
  '1': {
    value: 20,
    writable: true,
    enumerable: true,
    configurable: true
  },
  length: {
    value: 2,
    writable: true,
    enumerable: false,
    configurable: false
  }
}
assertEquals( arrayExpected, descArray );
