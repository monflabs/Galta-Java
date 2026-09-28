const Thread = Java.type("java.lang.Thread");

// Just to make sure that the method works, we are not testing the result

const o1 = { a: 11, b:22, c:23}
const o2 = ['str1','str2']

//
// log
//
console.log("*** log")
console.log()
console.log('ABC')
console.log('ABCD','EFG')

console.log('AB%sCD','EFG')
console.log('AB%sC%sD','EFG','XYZ')
console.log('AB%sCD','EFG','XYZ')

console.log('%i %d',12,25.26)
console.log('%f %f',12,25.26)
console.log('OBJ %o ',o1,"END")
console.log('ARR %o ',o2,"END")


console.log(o1)
console.log(o1,o2)
console.log("A=",o1,",B=",o2)


console.log("\n*** error")
console.error()
console.error('ABC')

console.log("\n*** info")
console.info()
console.info('ABC')

console.log("\n*** warn")
console.warn()
console.warn('ABC')

console.log("\n*** debug")
console.debug()
console.debug('ABC')

console.log("\n*** dir")
console.dir(o1)


console.log("\n*** assert")
console.assert( 1===1, "One is One")
console.assert( 1===2, "Two is not 1?", "guess!")

console.log("\n*** time")
console.time('test')
Thread.sleep(150)
console.timeLog('test')
Thread.sleep(150)
console.timeEnd('test')

console.log("\n*** count")
console.count('cpt1')
console.count('cpt1')
console.count('cpt2')
console.countReset('cpt1')
console.count('cpt1')


console.log("\n**** NOT IMPLEMENTED ***")
console.clear()
console.dirxml()
console.group()
console.groupCollapsed()
console.groupEnd()
console.profile()
console.profileEnd()
console.table()
console.table()
console.trace()
