---
sidebar_position: 1
---

# What is JSON Script

JsonScript is a language designed to run scripts in the JVM and manipulate JSON data. It is heavily inspired by JavaScript but it is not *not* JavaScript. It is designed to leverage and integrate well with the underlying platform. As such, it has specifc goals and non goals.

## Goals

- Create a script language that can directly handle JVM artifacts. The language must be  easy to learn for both Java and JavaScript developers.  
- It is extensible directly with Java. It maybe a good start for DSL, although the syntax will have to be extended for that.   
- Provide a easy to use set of primitives to deal with JSON data, including JSONPath concepts.  
- Freely experiment some powerful scripting constructs.  

## Non Goals

- Create a 100% compatible JavaScript interpreter.
  There are several available like [Nashorn](https://github.com/openjdk/nashorn), [Rhino](https://github.com/mozilla/rhino), [GraalVM](https://github.com/oracle/graaljs) or [ES6Draft](https://github.com/anba/es6draft), just to name the major ones. JsonScript is heavily inspired by modern JS, but is not JS.  
- Run large, long running programs.  
  Rather, it is a scripting language used to automate tasks defined in the underlying platform, typically in Java or Kotlin.  

