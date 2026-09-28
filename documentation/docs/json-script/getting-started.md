---
sidebar_position: 2
---

# Getting started

JsonScript is provided as a set of jar files, better added to a project through `maven` or `gradle`. Here is an example using `maven`:  
```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json-script</artifactId>
  <version>${galta.version}</version>
</dependency>
```   

To execute a script, we first need to create a `JSEnvironment`. This object contains the compiler and runtime options, including the libraries to use. To avoid configuration, we create an instance of `JSDefaultEnvironment`, which includes the JavaScript standard library and the Java bridge.  

Then we compile the script, which gives us a read to use `JSCript` object. Calling `exeute()` executes the code and return the result. Because JsonScript is handling Java type, the result bellow is an `Integer`:

```java
JSEnvironment env = new JSDefaultEnvironment();
JSScript sc = env.compileProgram("1+parseInt('1')");
Object r = sc.execute();
System.out.println("Result="+r);
```  

Congratulations, you executed your first script!
