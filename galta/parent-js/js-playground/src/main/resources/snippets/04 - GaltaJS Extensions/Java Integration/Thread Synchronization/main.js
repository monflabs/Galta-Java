const Thread = Java.type("java.lang.Thread");

let count = 0;
function inc(from) {
	synchronized(inc) {
		count++
		console.log(`Count=${count}, from ${from}`);
	}
}

const t1 = new Thread( () => {
	for(var i=0; i<10; i++) {
		inc("t1");
		Thread.sleep(100);
	}
});
const t2 = new Thread( () => {
	for(var i=0; i<10; i++) {
		inc("t2");
		Thread.sleep(100);
	}
});

t1.start();
t2.start();