const SampleObject = Java.type("sample.SampleObject");

// Static member
console.log(SampleObject.staticField);
console.log(SampleObject.staticMethod());


const o = new SampleObject();

// Instance member
console.log(`name: ${o.name}`);
console.log(`Call int(1): ${o.inc(1)}, should be 1+1`);
console.log(`Call double(1.0): ${o.inc(1.0)}, should be 1+2.0`);


// Java bean
console.log(`Bean: '${o.title}' should be the same than '${o.getTitle()}'`);
