assertEquals( false, Number.isNaN() )
assertEquals( false, Number.isNaN(null) )
assertEquals( false, Number.isNaN(undefined) )

assertEquals( false, Number.isNaN(0) )
assertEquals( false, Number.isNaN(Infinity) )
assertEquals( false, Number.isNaN(true) )
assertEquals( false, Number.isNaN(false) )
assertEquals( false, Number.isNaN('') )
assertEquals( false, Number.isNaN('NaN') )

assertEquals( true, Number.isNaN(NaN) )
