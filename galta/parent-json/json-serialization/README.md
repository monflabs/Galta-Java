# JSON Serialization

This is a simple serialization module that serialize/deserialize a Java objects hierarchy into JSON.  

The goal of this library is *not* to serialize any Java object to JSON, but rather to create a set of Java objects hat map an existing JSON payload.  

Constraints/limitations:  
  - Each Java object must have a empty constructor (can be protected/private though)
  - There is no OOTB references for object cycles

  
 