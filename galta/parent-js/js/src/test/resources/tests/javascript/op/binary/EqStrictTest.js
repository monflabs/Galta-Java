assertTrue(1===1)
assertTrue(1===1L)
assertFalse(1===1n)
assertTrue(1===1.0f)
assertTrue(1===1.0)
assertFalse(1===1.0m)
assertTrue(true===true)
assertTrue(false===false)
assertTrue('a'=='a')

assertFalse(1===0)
assertFalse(1===0L)
assertFalse(1===0n)
assertFalse(1===0.0f)
assertFalse(1===0.0)
assertFalse(1===0.0m)
assertFalse(true===false)
assertFalse(false===true)
assertFalse('a'==='b')
assertFalse('b'==='a')

assertFalse(1==='1')
assertFalse(1===[1])
assertFalse(1===['1'])
assertFalse('1'===['1'])
assertFalse('1'===[1])
assertFalse(1===true)
assertFalse(2===true)

assertFalse(NaN===NaN)

assertTrue(22*==22)
assertFalse('abc'*=='xyz')


{
	let v0 = 12
	let v1 = new Number(12)
	let v2 = new Number(12)
	
	assertTrue(v0===12)
	assertFalse(v1===12)
	assertFalse(v1===v2)
}
{
	let v0 = 12.34
	let v1 = new Number(12.34)
	let v2 = new Number(12.34)
	
	assertTrue(v0===12.34)
	assertFalse(v1===12.34)
	assertFalse(v1===v2)
}
{
	let v0 = "abc"
	let v1 = new String("abc")
	let v2 = new String("abc")
	
	assertTrue(v0==="abc")
	assertFalse(v1==="abc")
	assertFalse(v1===v2)
}
{
	let v0 = true
	let v1 = new Boolean(true)
	let v2 = new Boolean(true)
	
	assertTrue(v0===true)
	assertFalse(v1===true)
	assertFalse(v1===v2)
}
