const v_e = null
const v_b = true
const v_n = 12
const v_s = "ss"

assertEquals( "undefined", typeof v_fake )
assertEquals( "object", typeof v_e )
assertEquals( "number", typeof v_n )
assertEquals( "string", typeof v_s )
assertEquals( "object", typeof new Object() )

const a = { x: 77 }
assertEquals( "object", typeof a )
assertEquals( "number", typeof a.x )
assertEquals( "undefined", typeof a.y )
assertEquals( "number", typeof a['x'] )
assertEquals( "undefined", typeof a['y'] )

const b = [ 12 ]
assertEquals( "object", typeof b )
assertEquals( "number", typeof b[0] )
assertEquals( "undefined", typeof b[1] )
