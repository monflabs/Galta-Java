assertEquals( 1, Object.valueOf.call(1) )
assertEquals( "abc", Object.valueOf.call("abc") )
assertEquals( true, Object.valueOf.call(true) )
assertEquals( {}, Object.valueOf.call({}))
assertEquals( [], Object.valueOf.call([]))

assertTrue( Object.valueOf() instanceof Object )
