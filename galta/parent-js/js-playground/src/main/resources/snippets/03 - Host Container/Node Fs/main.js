import fs from "fs";

console.log(fs.readFileSync("greeting.txt"));

fs.writeFileSync("written.txt", "Written by writeFileSync()");
console.log(fs.readFileSync("written.txt"));
