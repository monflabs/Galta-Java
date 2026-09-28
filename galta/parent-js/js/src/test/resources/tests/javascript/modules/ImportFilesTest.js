import a from "a.js"
import {b} from "b.js"
import ma from "mod/a.js"
import {b as mb} from "mod/b.js"
import mc from "mod/c.js"

assertEquals( "aa", a );
assertEquals( "bb", b );
assertEquals( "aa-mod", ma );
assertEquals( "bb-mod", mb );

assertEquals( "aa-mod-cc", mc );

// Extensionless imports (Node/bundler style): the resolver falls back to
// name+".js" when the exact name isn't found.
import noExtA from "a"
import {b as noExtB} from "mod/b"

assertEquals( "aa", noExtA );
assertEquals( "bb-mod", noExtB );
