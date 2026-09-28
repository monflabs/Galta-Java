# Extending the Engine

GaltaJS exposes three extension points to a Java host. All of them are configured on the `JSEnvironment.Builder` before the environment is built, and all of them are plain Java: no annotations, no service files, no reflection magic.

| Extension point | What it contributes | Use it when |
|---|---|---|
| [Libraries](/GaltaJS/Extending/Libraries) | Global objects, global functions, configuration flags, module resolvers, accessors | You want to add `myApi.doSomething()` or a `require`-style global, or package a set of features as one reusable unit |
| [Accessors](/GaltaJS/Extending/Accessors) | JavaScript semantics (properties, methods, prototype) for a Java class | A Java value must behave like a JavaScript object without going through reflection |
| [Native modules & resolvers](/GaltaJS/Extending/NativeModules) | Modules that scripts `import` or `require`, whether written in JavaScript (memory, files, transpiled classes) or implemented in Java | Your host has a module system, or you want `import { x } from 'host:api'` |

The [bundled libraries](/GaltaJS/Extending/BundledLibraries) page lists what ships with the engine (`StandardLibrary`, `HostLibrary`, `FetchLibrary`, `NodeLibrary`...) and is a good source of reference implementations.

## Choosing an extension point

```
Do scripts need a new global name?            -> Library (GlobalLibrary / StaticLibrary)
Do scripts need to import something?           -> Module resolver (native or script)
Do scripts receive a Java object of class X?   -> Accessor (or let JavaLibrary reflect on it)
Do you need flags or resolvers set up
   together with the globals?                  -> Library.configureEnvironment()
```

Reflection-based access to arbitrary Java classes (`Java.type(...)`, bean properties, overloads) is itself a library, `JavaLibrary`; it is described in the User's Guide under [Java Interop](/GaltaJS/UserGuide/JavaInterop).

## The lifecycle at a glance

```
JSEnvironment.newBuilder()
   .registerLibrary(lib)            // stored
   .addModuleResolver(resolver)     // stored
   .build()
      |
      +-- lib.configureEnvironment(builder)      once per builder, before the environment exists
      +-- new JSEnvironment(...)
      |     +-- built-in constructors and prototypes
      |     +-- lib.configureStandardObjects(env, standardObjects)   once per environment
      |
      `-- at run time
            env.getAccessor(value) -> lib.createAccessor(env, value.getClass())   lazily, cached per class
            import 'name'          -> resolver.getModule(name)
```

## Source

`JSLibrary.java`, `JSEnvironment.java` (`Builder.registerLibrary`, `Builder.addModuleResolver`, `findAccessor`), `library/`, `modules/`, `rt/builtins/JSAccessor.java`.
