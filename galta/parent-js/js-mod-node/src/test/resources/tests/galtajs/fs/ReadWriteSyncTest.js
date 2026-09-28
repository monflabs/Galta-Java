import fs from "fs";
fs.writeFileSync("test1","some content")
const content = fs.readFileSync("test1")
assertEquals("some content",content)