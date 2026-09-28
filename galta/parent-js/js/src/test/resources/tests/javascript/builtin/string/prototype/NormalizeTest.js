const name1 = '\u0041\u006d\u00e9\u006c\u0069\u0065';
const name2 = '\u0041\u006d\u0065\u0301\u006c\u0069\u0065';
console.log(`${name1}, ${name2}`);

/*
// expected: "Amélie, Amélie"
assertEquals( false, name1 === name2 )
// JavaScript seems to be false here and Java true
// is it because of String.intern() for the constants?
//assertEquals( false, name1.length === name2.length )

const name1NFC = name1.normalize('NFC');
const name2NFC = name2.normalize('NFC');
console.log(`${name1NFC}, ${name2NFC}`);

// expected : "Amélie, Amélie"
assertEquals( true, name1NFC === name2NFC);
assertEquals( true, name1NFC.length === name2NFC.length);
assertEquals( 6, name1NFC.length );
assertEquals( 6, name2NFC.length );
*/