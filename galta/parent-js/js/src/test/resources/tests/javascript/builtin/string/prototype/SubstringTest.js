const b = "0123456789"


assertEquals( '0123456789', b.substring() )
assertEquals( '0123456789', b.substring(0) )
assertEquals( '0123456789', b.substring(0,100) )
assertEquals( '0123456789', b.substring(null,100) )
assertEquals( '23456789', b.substring(2) )
assertEquals( '23', b.substring(2,4) )
assertEquals( '23', b.substring(4,2) )
assertEquals( '0123456789', b.substring(-1) )


//
// Mozilla example
//
let anyString = 'Mozilla'

// Displays 'M'
assertEquals( 'M', anyString.substring(0, 1))
assertEquals( 'M', anyString.substring(1, 0))

// Displays 'Mozill'
assertEquals( 'Mozill', anyString.substring(0, 6))

// Displays 'lla'
assertEquals( 'lla', anyString.substring(4))
assertEquals( 'lla', anyString.substring(4, 7))
assertEquals( 'lla', anyString.substring(7, 4))

// Displays 'Mozilla'
assertEquals( 'Mozilla', anyString.substring(0, 7))
assertEquals( 'Mozilla', anyString.substring(0, 10))


//
// substring vs slice
//

let text = 'Mozilla'
assertEquals( 'zil', text.substring(5, 2)) 
assertEquals( '', text.slice(5, 2))

assertEquals( 'Mo', text.substring(-5, 2)) 
assertEquals( '', text.substring(-5, -2)) 

assertEquals( '', text.slice(-5, 2))  
assertEquals( 'zil', text.slice(-5, -2))  
