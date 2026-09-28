# Accessing Java Classes  

Java classes can be loaded using `Java.type`. It uses the class loader available from `JSEnvironment.getClassLoader()`, which is by default the class loader of the environment. 

Fields and methods access is available like in Java. In case of method overloads, the runtime tries to access the best one with the calling parameter.  

Java bean properties are available through getter and setters.  

The sample class here is defined like this:  

```
package sample;

public class SampleObject {

	public static String staticField = "A field";
	public static String staticMethod() { return "A method"; }
	
	public String name = "Nobody";
	
	public int inc(int v) { return v+1; }
	public double inc(double v) { return v+2.0; }
	
	public String getTitle() { return "A sample object"; }
}
```
