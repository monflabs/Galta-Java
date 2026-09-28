const add = require('ts.js')
assertEquals(7, add(3,4))

// Default extensions
const v1 = require('req1').value
assertEquals(1, v1)

const v2 = require('req2').value
assertEquals(2, v2)

// No JSON for now
//const v3 = require('req3').value
//assertEquals(3, v3)
