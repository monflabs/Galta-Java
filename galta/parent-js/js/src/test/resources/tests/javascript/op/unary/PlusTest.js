const i = 7
const l = 7L
const d = 7.0
const bi = 7n
const bd = 7m

assertEquals(7, +i )
assertEquals(7, +l )
assertEquals(7, +d )
assertThrows( TypeError, () => +bi )
assertThrows( TypeError, () => +bd )
