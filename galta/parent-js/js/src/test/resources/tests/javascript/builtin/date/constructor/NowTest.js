const System = Java.type("java.lang.System");

const jsNow = Date.now();
const javaNow = System.currentTimeMillis();

assertTrue( javaNow-jsNow < 5*1000 ) // Give it 5 secs max!
