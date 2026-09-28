assertEquals("ab",true ? "ab" : "cd")
assertEquals("cd",false ? "ab" : "cd")

assertEquals("ab","ab" ?: "cd")
assertEquals("cd",false ?: "cd")
