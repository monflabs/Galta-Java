# Array Setters

`JsonArray` content can be set though all the `List<>` methods. But it also provides
typed setters. The setters do not have the type in their name, as the method signature is
sufficient.  

Also, the setters return `this` to authorize chaining. A `addValue()` method is
also provided to return `this`, as `add()` does not.
