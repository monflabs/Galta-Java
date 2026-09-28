---
sidebar_position: 25
---

# Standard Libraries

The standard library more or less mimics the standard JavaScript one for the JSON values (JsonObject, JsonArray, Number, Boolean, String, null). It is not a full emulation, but it keeps what makes sense in a non JavaScript environment, while some uncommon functions are not implemented.  


## console

`log` & `error` are implemented. When  the first parameter is a message, then it is a [Java String format](https://docs.oracle.com/javase/7/docs/api/java/lang/String.html#format(java.lang.String,%20java.lang.Object...)), which is more complete than the JavaScript one.


## JSON

`parse(obj,reviver)`  
The reviver function must be null, else it generates an exception 

`JSON.stringify(obj,replacer,space)`  
The replacer function must be null, else it generates an exception  
