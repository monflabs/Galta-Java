const s1 = "ABC"
assertEquals( "ABC", s1)

const s2 = "A\
BC"
assertEquals( "ABC", s2)

const s3 = "A \
BC"
assertEquals( "A BC", s3)

const s4 = "A\
 BC"
assertEquals( "A BC", s4)

const s5 = "A \
 BC"
assertEquals( "A  BC", s5)

// U+2028 LINE SEPARATOR / U+2029 PARAGRAPH SEPARATOR may appear raw inside a
// string literal (ES2019+ json-superset) and as a line continuation (empty).
const s6 = "A B"
assertEquals( "A\u2028B", s6 )
assertEquals( 3, s6.length )

const s7 = "A B"
assertEquals( "A\u2029B", s7 )

const s8 = "A\ B"
assertEquals( "AB", s8 )

const s9 = "A\ B"
assertEquals( "AB", s9 )
