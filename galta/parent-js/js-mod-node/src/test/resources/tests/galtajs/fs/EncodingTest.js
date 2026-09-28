import fs from "fs";
// encoding as the third argument (not the data)
fs.writeFileSync("enc1","héllo","utf8")
assertEquals("héllo",fs.readFileSync("enc1","utf8"))
// options objects
fs.writeFileSync("enc2","héllo",{encoding:"ISO-8859-1"})
assertEquals("héllo",fs.readFileSync("enc2",{encoding:"ISO-8859-1"}))
// no encoding in the options: UTF-8
assertEquals("héllo",fs.readFileSync("enc1",{}))
var failed = false
try { fs.readFileSync("enc1","no-such-charset") } catch(e) { failed = e instanceof TypeError }
assertTrue(failed)
