const Thread = Java.type("java.lang.Thread");

const t = new Thread( () => {
	console.log("Executing thread!");
});

t.start()
t.join(); 

console.log("Thread completed");
