assertTrue(1==1)
assertTrue(1==1L)
assertTrue(1==1n)
assertTrue(1==1.0f)
assertTrue(1==1.0)
assertTrue(1==1.0m)
assertTrue(true==true)
assertTrue(false==false)
assertTrue('a'=='a')

assertFalse(1==0)
assertFalse(1==0L)
assertFalse(1==0n)
assertFalse(1==0.0f)
assertFalse(1==0.0)
assertFalse(1==0.0m)
assertFalse(true==false)
assertFalse(false==true)
assertFalse('a'=='b')
assertFalse('b'=='a')

assertTrue(1=='1')
assertTrue(1==[1])
assertTrue(1==['1'])
assertTrue('1'==['1'])
assertTrue('1'==[1])
assertTrue(1==true)
assertFalse(2==true)

assertFalse(null==0)
assertFalse(null==1)
assertFalse(0==null)
assertFalse(1==null)

assertFalse(null==NaN)
assertFalse(NaN==null)
assertFalse(NaN==NaN)

assertTrue(22*==22)
assertFalse('abc'*=='xyz')


{
	let v0 = 12
	let v1 = new Number(12)
	let v2 = new Number(12)
	
	assertTrue(v0==12)
	assertTrue(v1==12)
	assertFalse(v1==v2)
}
{
	let v0 = 12.34
	let v1 = new Number(12.34)
	let v2 = new Number(12.34)
	
	assertTrue(v0==12.34)
	assertTrue(v1==12.34)
	assertFalse(v1==v2)
}
{
	let v0 = "abc"
	let v1 = new String("abc")
	let v2 = new String("abc")
	
	assertTrue(v0=="abc")
	assertTrue(v1=="abc")
	assertFalse(v1==v2)
}
{
	let v0 = true
	let v1 = new Boolean(true)
	let v2 = new Boolean(true)
	
	assertTrue(v0==true)
	assertTrue(v1==true)
	assertFalse(v1==v2)
}


{
	assertTrue( "A"=="A" )
	assertFalse( "A"=="B" )
	assertTrue( "A"==="A" )
	assertFalse( "A"==="B" )

	assertTrue( "A"==new String("A") )
	assertFalse( "A"===new String("A") )
	assertFalse( new String("A")==new String("A") )
	assertFalse( new String("A")===new String("A") )

	assertTrue( 8==8 )
	assertFalse( 8==9 )
	assertTrue( 8===8 )
	assertFalse( 8===9 )

	assertTrue( 8==new Number(8) )
	assertFalse( 8===new Number(8) )
	assertTrue( new Number(8)==Number(8) )
	assertFalse( new Number(8)===new Number(8) )

	assertTrue( true==true )
	assertFalse( true==false )
	assertTrue( true===true )
	assertFalse( true===false )

	assertTrue( true==new Boolean(true) )
	assertFalse( true===new Boolean(true) )
	assertTrue( new Boolean(true)==Boolean(true) )
	assertFalse( new Boolean(true)===new Boolean(true) )	
}
