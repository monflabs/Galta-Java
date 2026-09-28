// Performance Object in Action

// 1. Measuring Code Execution Time
console.log("Measuring code execution time...");
performance.mark("start");

// Simulate some work
for (let i = 0; i < 1e6; i++) {}

performance.mark("end");
performance.measure("Loop Execution Time", "start", "end");

const measures = performance.getEntriesByName("Loop Execution Time");
console.log("Execution time (ms):", measures[0].duration);

// 2. Using Performance.now()
console.log("\nUsing performance.now()...");
const start = performance.now();

// Simulate a different task
let sum = 0;
for (let i = 0; i < 1e6; i++) {
  sum += i;
}

const end = performance.now();
console.log("Sum:", sum);
console.log("Execution time (ms):", (end - start).toFixed(2));

// 3. Retrieving Performance Metrics
console.log("\nRetrieving performance metrics...");
const allEntries = performance.getEntries();
console.log("All performance entries:", allEntries);

// 4. Using PerformanceObserver (a browser API, not part of the JS
// language itself - not every host environment provides it)
console.log("\nUsing PerformanceObserver...");
if (typeof PerformanceObserver !== "undefined") {
  const observer = new PerformanceObserver((list) => {
    const entries = list.getEntries();
    entries.forEach((entry) => {
      console.log(`Observed: ${entry.name}, Type: ${entry.entryType}, Duration: ${entry.duration}ms`);
    });
  });

  observer.observe({ entryTypes: ["mark", "measure"] });
} else {
  console.log("PerformanceObserver is not available in this environment.");
}

// Additional Performance Marks and Measures
performance.mark("custom-start");
// Simulate some work
for (let i = 0; i < 1e5; i++) {}
performance.mark("custom-end");
performance.measure("Custom Task", "custom-start", "custom-end");

// 5. Clear Marks and Measures
console.log("\nClearing marks and measures...");
performance.clearMarks();
performance.clearMeasures();

// Check entries after clearing
const entriesAfterClear = performance.getEntries();
console.log("Entries after clearing:", entriesAfterClear);
