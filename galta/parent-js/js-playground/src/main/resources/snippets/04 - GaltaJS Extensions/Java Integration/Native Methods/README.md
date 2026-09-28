# Accessing Native Methods  

Any JavaScript object is a Java object behind the scene. One can access the native Java methods by using the leading `$` character. For example `obj.$toString()` calls the native Java `toString()`.  

This capability can be disabled from the `JSEnvironment.supportJavaNative()`.
