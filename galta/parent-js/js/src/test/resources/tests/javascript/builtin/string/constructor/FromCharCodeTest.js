assertEquals( "", String.fromCharCode() )
assertEquals( "A", String.fromCharCode(65) )
assertEquals( "ABC", String.fromCharCode(65, 66, 67) )
assertEquals( "—", String.fromCharCode(0x2014) )
assertEquals( "—", String.fromCharCode(0x12014) )
assertEquals( "—", String.fromCharCode(8212) )

assertEquals( "\uD83C\uDF03", String.fromCharCode(55356, 57091) )

assertEquals( "\uD834\uDF06a\uD834\uDF07", String.fromCharCode(0xD834, 0xDF06, 0x61, 0xD834, 0xDF07) )
