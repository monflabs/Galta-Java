function f(p1,p2) {
	return (p1?this[p1]:'')+'-'+(p2?this[p2]:'')
}

var o = {
	a: 'AA',
	b: 'BB'
}

var c1 = f.call(o)
assertEquals('-',c1)
var c2 = f.call(o,'a')
assertEquals('AA-',c2)
var c3 = f.call(o,'a','b')
assertEquals('AA-BB',c3)

var a1 = f.apply(o)
assertEquals('-',a1)
assertThrows( () => f.apply(o,'a') );
var a3 = f.apply(o,['a'])
assertEquals('AA-',a3)
var a4 = f.apply(o,['a','b'])
assertEquals('AA-BB',a4)
