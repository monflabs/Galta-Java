// Performance examples
const Thread = Java.type("java.lang.Thread")

assertTrue(performance.getEntries().$isEmpty())


//
// Measure a simple Thread sleep

performance.mark("start")
Thread.sleep(10)
const m1 = performance.measure("Sleep(10)","start")
console.log(performance.toJSON())

assertEquals(2,performance.getEntries().length)

assertEquals(1,performance.getEntriesByType("mark").length)
assertEquals(1,performance.getEntriesByType("measure").length)

assertEquals(1,performance.getEntriesByName("Sleep(10)").length)
assertEquals(1,performance.getEntriesByName("start").length)
assertEquals(1,performance.getEntriesByName("start","mark").length)
assertEquals(0,performance.getEntriesByName("Sleep(10)","mark").length)

performance.clearMarks()
assertEquals(1,performance.getEntries().length)
performance.clearMeasures()
assertEquals(0,performance.getEntries().length)


//
// Should pickup the closest mark with the name "start"
//
performance.mark("start")
Thread.sleep(20)
const m2 = performance.measure("empty-sleep","start")
performance.mark("start")
const m3 = performance.measure("empty-sleep","start")
console.log(m2.toJSON())
console.log(m3.toJSON())
assertTrue(m2.duration>15) // use 15 ms as a check
assertTrue(m3.duration<15) // use 15 ms as a check


//
// Access to now()
//
const n1 = performance.now();
assertTrue(n1>1)
const n2 = performance.now();
assertTrue(n2-n1<1000)
