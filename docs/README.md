# Welcome to the Galta Project

Galta is a set of generic libraries and utilities for Java. It is mostly focused on a JSON library, **GaltaJSON**, and a JavaScript engine for the JVM, **GaltaJS**.

## A prototyping library

!> Galta is a **prototyping library**. It is meant for quickly building prototypes, and it is provided **as is, without any production support**.

There is no support commitment, no service level and no guarantee of fixes, compatibility between versions, or fitness for production use. The code is released under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0), which likewise comes with no warranty. If you build on Galta, you are responsible for evaluating, testing and maintaining it for your own needs.

## Overview

The JavaScript engine is intimately linked to the JSON library: it borrows some of its concepts and relies on some of its code. A JavaScript object *is* a `JsonObject`, so JSON data flows between Java and JavaScript without any conversion.

Galta is designed to minimize its external dependencies. The initial reason was to fully control what is used, with which version, and to run on constrained environments (RoboVM, Java AOT compilers, CheerpJ in the browser...). That is why it does not use well established libraries like Guava. This constraint could be relaxed nowadays but keeping the dependencies minimal is still a good thing.

The Galta project is split into many sub-projects, organized in a hierarchy and published as Maven artifacts (`groupId` `org.monflabs.galta`). The dependencies are transitive, so a consumer only references the JSON or the JavaScript library, plus optional modules when needed. All the libraries work well in a fat jar if a single jar file is preferred.

## Documentation

- [GaltaJSON](/GaltaJSON/) - the JSON library:
  - [Guide](/GaltaJSON/) - values, parsing, JSON Path, pointers, collections, schema metadata.
  - [Add-on Modules](/GaltaJSON/Modules/) - serialization, configuration, import/export, in-memory database, YAML, Jayway JsonPath, JSON Schema validation.
- [Utilities](/Utilities/) - the general-purpose libraries everything else builds on: strings, numbers, dates, collections, I/O, file systems, reflection, generators, in-memory Java compilation and test support.
- [GaltaJS](/GaltaJS/) - the JavaScript engine:
  - [User's Guide](/GaltaJS/UserGuide/) - embedding and configuring the engine, executing code, Java interop, async, modules...
  - [Extending the Engine](/GaltaJS/Extending/) - libraries, accessors, native modules.
  - [Extensions](/GaltaJS/Extensions/) - what GaltaJS adds on top of ECMAScript.
  - [Architecture](/GaltaJS/Architecture/) - how the interpreter, the transpiler, the async runtime and the object model work.
  - [Known ECMAScript Gaps](/GaltaJS/KnownGaps) - the living list of spec deviations found with test262.

Every Java and JavaScript sample in the documentation is backed by a JUnit test in a `doc_examples` folder of the module it documents, run by every build of that module.

## The project

- [Building and Releasing](/BuildAndRelease) - requirements, profiles, versioning and publishing.
- [Generating the Documentation](/Documentation) - viewing the docsify site locally, and how it is wired.

## License

Galta is licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0). The full text is in the `LICENSE` file at the root of the repository, and every Galta source file carries the Apache 2.0 header.

Galta also bundles some third-party code, such as the Joni regular expression engine and parts of Mozilla Rhino. That code keeps its original license, stated in each file's header and summarized in the `NOTICE` file.
