assertEquals( false, Number.isFinite() )
assertEquals( false, Number.isFinite(null) )
assertEquals( false, Number.isFinite(undefined) )

assertEquals( false, Number.isFinite(true) )
assertEquals( false, Number.isFinite(false) )
assertEquals( false, Number.isFinite('') )
assertEquals( false, Number.isFinite('NaN') )
assertEquals( false, Number.isFinite(NaN) )
assertEquals( false, Number.isFinite(Infinity) )
assertEquals( false, Number.isFinite(-Infinity) )

assertEquals( true, Number.isFinite(0) )
assertEquals( true, Number.isFinite(1.1) )
