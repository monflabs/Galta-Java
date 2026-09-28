// The letter "a" is before "c" yielding a negative value
assertTrue('a'.localeCompare('c') < 0 )

// Alphabetically the word "check" comes after "against" yielding a positive value
assertTrue('check'.localeCompare('against')>0)

// "a" and "a" are equivalent yielding a neutral value of zero
assertEquals(0,'a'.localeCompare('a'))


//
// With Locale
//
const s1 = "péché"
const s2 = "pêche"

assertTrue( s1.localeCompare(s2, 'en') < 0);
assertTrue( s1.localeCompare(s2, 'fr-FR') > 0);


const a = 'réservé'; // with accents
const b = 'reserve'; // no accents

assertTrue( a.localeCompare(b) > 0 );
assertTrue( a.localeCompare(b, 'en') > 0 );
// The bellow seems to be different in JS and Java?
assertTrue( a.localeCompare(b, 'fr-FR') > 0 );
