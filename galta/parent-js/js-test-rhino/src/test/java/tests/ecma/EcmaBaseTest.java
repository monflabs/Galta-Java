package tests.ecma;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.test.rhino.RhinoTestEnvironment;
import org.monflabs.util.StringUtil;
import org.monflabs.util.path.FilesUtil;

import tests.suite.BaseTestSuiteTest;


public abstract class EcmaBaseTest extends BaseTestSuiteTest {

	@Override
	protected String[] getFILTER() {
		return FILTER;
	}
	@Override
	protected Object[] getFILTER_ERRORS() {
		return FILTER_ERRORS;
	}
	
	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder env = RhinoTestEnvironment.newBuilder();
		return env;
	}

	public static final String[] FILTER = new String[] {
		"browser.js",
		"jsref.js",
		"shell.js",
		"template.js",
		
		// This test is not ECMA compliant
		// "Boolean.prototype.foo = 34; for ( j in Boolean ) Boolean[j]"
		// Rhino returns 34, V8 returns undefined and V8 is right
		"ecma/Statements/12.6.3-2.js",
		
		// In GaltaJS, the variable declaration happens before the expression is evaluated
		// and thus the current variable appears in global.
		// This can be changed but it is going to be harder with Transpiler so it is kept the way
		// it is right now. Shouldn't impact program execution.
		"ecma/Statements/12.6.3-12.js",
		
		// Difference in the 'with' behavior as GaltaJS returns a method as a closure
		// because the call () operator doesn't have a 'this' context to call it
		// As a result, the 2 method pointers are different as one is a wrapper
		"ecma_2/Statements/forin-002.js",
		
		// Looks like this test use a regexp as a function, which is not implemented
		// in other JS engines /a||b/('')
		"ecma_2/RegExp/regress-001.js",
		
		// eval() declaring e function should erase the local variable outside eval()
		"ecma_3/ExecutionContexts/10.1.3-2.js",
		"ecma_3/Function/regress-193555.js",
		
		// Uses 'uneval', a spider monkey extension that is not standard
		"ecma_3/extensions/7.9.1.js",
		"ecma_3/extensions/regress-385393-03.js",
		
		// Regexp $1...$9 are not supported (deprecated from JavaScript)
		"ecma_3/extensions/regress-220367-002.js",
		
		// Use regexp .compile, which is deprecated
		"ecma_3/extensions/regress-327170.js",
		
		// Unicode inside the source code
		"ecma_3/extensions/regress-274152.js",
		
		// BOM chars are not supported
		"ecma_3/extensions/regress-368516.js",
		
		// Testing functions with double-byte names
		// ex: function f\u02B2() {return 42;}
		"ecma_3/Function/regress-58274.js",
		
		// Function.prototype.arguments is deprecated and not implemented
		"ecma_3/Function/regress-85880.js",
		
		// eval() scope with Functions
		"ecma_3/Function/regress-131964.js",
		
		// This test seems to be wrong, as V8 returns 3 as well 
		"ecma_3/Statements/12.10-01.js",
		
		// catch (identifier if condition) is specific to Rhino
		"ecma_3_1/Object/regress-444787.js",

		// Pre-ES5 lenient string escapes: a backslash-x with fewer than 2 hex
		// digits ("x0", "xG", "xCG"), a backslash-u with no hex digits (7.7.4),
		// backslash-x "1g" (hex-001), and every backslash + char from 0x20 to
		// 0xFE, which includes a bare backslash-u and backslash-x (15.4.5.1-1,
		// through eval). Since ES5 a string literal's hex escape needs exactly
		// 2 hex digits and the unicode escape 4 (or braces): x and u are
		// EscapeCharacters, not NonEscapeCharacters, so anything else is a
		// SyntaxError, and Annex B only adds legacy octal escapes.
		// (No literal backslash-u here: javac decodes it even in comments.)
		// test262 checks it (language/literals/string/unicode-escape-no-hex-err-*.js,
		// and the \x cases), and GaltaJS now reports it at parse time; each
		// file aborts on its first such literal, so its other cases cannot run.
		"ecma/LexicalConventions/7.7.4.js",
		"ecma_2/RegExp/hex-001.js",
		"ecma/Array/15.4.5.1-1.js"

	};
	
	// Ignore the errors from this file but still run the tests
	public static final Object[] FILTER_ERRORS = new Object[] {
		// toLowerCase()/toUpperCase() case-mapping tables grew since Unicode
		// 2.0 (this suite's baseline) - specific code points (Georgian,
		// Cyrillic, Armenian, micro sign) now map differently, matching V8.
		// Two of the five files below also expect Function.prototype.length
		// to be non-configurable (ES3); ES5+ made it configurable.
		"ecma/String/15.5.4.11-2.js", 40,
		"ecma/String/15.5.4.11-5.js", 3,
		"ecma/String/15.5.4.12-1.js", 3, 
		"ecma/String/15.5.4.12-4.js", 2,
		"ecma/String/15.5.4.12-5.js", 1,
		
		// Omitting separator or passing undefined causes split() to return an array with the calling string as a single element
		"ecma/String/15.5.4.8-2.js", 6,
		
		// parseInt/parseFloat/escape/unescape/isNaN/isFinite/eval all expect
		// a "prototype" property visible via for-in (ES3; ES5+ only gives
		// constructors one) and a non-configurable .length (ES3; ES5+ made
		// it configurable). parseInt's extra failures are its removed ES3
		// "leading zero parses as octal" behavior - also stale in ES5+.
		"ecma/GlobalObject/15.1.2.2-1.js", 30,
		"ecma/GlobalObject/15.1.2.3-1.js", 3,
		"ecma/GlobalObject/15.1.2.4.js", 3,
		"ecma/GlobalObject/15.1.2.5-1.js", 3,
		"ecma/GlobalObject/15.1.2.6.js", 3,
		"ecma/GlobalObject/15.1.2.7.js", 3,
		"ecma/extensions/15.1.2.1-1.js", 1,
		"ecma/extensions/15.1.2.1-1.js", 2,

		// Tests the ES3/ES5-only rule that Date.prototype is itself a Date
		// (getClass() "[object Date]", valueOf() NaN) - removed in ES6+, so
		// valueOf() now correctly THROWS instead (matching V8/Node.js),
		// aborting the script before either assertion prints. Still nets to
		// the same tolerated 1 as when getClass() alone used to fail here.
		"ecma/Date/15.9.5.js", 1,

		// With returns a Closure and thus the equality == fails.		
		"ecma/ExecutionContexts/10.2.2-2.js",1,

		// TypeError: xxxx nessage os different in the text, but TypeError are thrown
		"ecma_3/Array/regress-387501.js", 3,
		// Dense Arrays and holes : Expected value 'foo,bar,baz', Actual value 'foo,,baz' 
		"ecma_3/Array/regress-421325.js", 1,
		// Dense Arrays should inherit deleted elements from Array.prototype 
		"ecma_3/Array/regress-430717.js",1,
		
		// Wrong Date edge cases using the mills since 1/1/70 
		"ecma_3/Date/15.9.5.5-02.js", 1,

		// Function arguments and the `argument` pseudo variables are disconnected and changes in one are not reflected in the others.
		"ecma_3/ExecutionContexts/10.1.3-1.js", 3,
		// var can never be deleted, even when created in an eval() context
		"ecma_3/ExecutionContexts/10.1.4-1.js",1,
		// Execution context with `var` and with or catch
		"ecma_3/ExecutionContexts/regress-448595-01.js",1,
		
		// Edge case with date string parsing
		"ecma_3/Date/15.9.3.2-1.js",3,

		// Function.prototype.arguments/.caller were normative in ES3 (null when the
		// function isn't currently executing) but were removed from the core spec by
		// ES5. What replaced them is Annex B.2.3 ("Additional Properties of the
		// Function Prototype Object"), which defines them as poison-pill accessors
		// that always throw a TypeError - there's no normative "return null" behavior
		// anymore. These tests still assert the ES3 semantics, so GaltaJS reporting
		// the property as absent (undefined) instead of null doesn't violate the
		// current spec; it's the tests that are stale. See also the FILTER entry for
		// ecma_3/Function/regress-85880.js, which excludes an equivalent test outright.
		"ecma/FunctionObjects/15.3.1.1-1.js", 2,
		"ecma/FunctionObjects/15.3.1.1-2.js", 3,
		"ecma/FunctionObjects/15.3.2.1-1.js", 2,
		"ecma/FunctionObjects/15.3.2.1-2.js", 3,
		"ecma/FunctionObjects/15.3.5-2.js", 1,
		"ecma/FunctionObjects/15.3.5.3.js", 1,

		// Asserts Number("-0x123456789abcde8") === -81985529216486880 (i.e. a
		// signed hex string IS parsed as hex) - this predates the current
		// StringNumericLiteral grammar, where NonDecimalIntegerLiteral (0x/0o/0b)
		// has no Sign production at all, so a signed hex/octal/binary string must
		// be NaN. test262 explicitly verifies the modern behavior
		// (built-ins/Number/string-hex-literal-invalid.js: Number("+0x10") and
		// Number("-0x10") are both NaN) - it's this old test that's stale.
		"ecma/TypeConversion/9.3.1-3.js", 2,

		// Asserts (new RegExp()).source === "" (an empty pattern's source is
		// the empty string). This predates the ES2015+ requirement that an
		// empty RegExp source be rendered as the literal "(?:)" instead (so
		// that RegExp.prototype.toString()'s "/" + source + "/" round-trips
		// to a valid, non-empty pattern) - test262 explicitly verifies the
		// modern behavior. GaltaJS's `source` getter was fixed this session
		// to match (see docs/GaltaJS/KnownGaps.md's RegExp section); these
		// old tests encode the pre-fix, now-stale expectation.
		"ecma_2/RegExp/constructor-001.js", 1,
		"ecma_2/RegExp/function-001.js", 1,
		"ecma_2/RegExp/properties-001.js", 1,
	};
	
    @Override
	protected String preprocessFile(Path file, String text) {
    	String path = file.toString();
    	if(path.equals("/ecma/String/15.5.4.6-2.js")) {
    		checkFileCrc(text,176628311L);
    		// In sloopy mode, Rhino assigns 'this' to any method when the actual this is undefined. The spec says that it should only be for custom functions
    		// It should generate TypeError: Function indexOf is called on null
    		text = StringUtil.replaceFirst(text, "eval(\"var f = new Object( String.prototype.indexOf ); f('\"+GLOBAL+\"')\") );", "eval(\"try { var f = new Object( String.prototype.indexOf ); f('\"+GLOBAL+\"') } catch(e) {}; 0;\") );");
    	}
    	return text;
    }

	public EcmaBaseTest() {
	}

	public EcmaBaseTest(boolean debugger) {
		super(debugger);
	}

	public File getRhinoTestsDirectory() {
		return support.getProjectDirectory("rhino/tests/testsrc/tests");
	}
	public File getRhinoJSTestsDirectory() {
		return support.getProjectDirectory("rhino/tests/testsrc/jstests");
	}
	
    @Override
	protected String readShell(Path folder, String shell) throws Exception {
    	if(!FilesUtil.isRoot(folder)) {
    		shell = readShell(folder.getParent(),shell);
    	}
    	Path shellFile = folder.resolve("shell.js");
    	if(Files.exists(shellFile)) {
    		String shellCode = loadScript(shellFile);
    		String name = shellFile.toString();
    		return (shell!=null ? shell + "\n\n" : "") + "//\n//  " + name + "\n//\n\n" + shellCode;
    	}
    	return shell;
    }

    //
    // Handling exceptions
    //
    
    @Override
	protected void handleException(String relativePathStr, AtomicInteger errorCount, Throwable t) {
    	// Negative tests throw an exception
    	// In Rhino, they use an error reported or window.onerror
    	// Here, we just catch the exception
    	if(!relativePathStr.endsWith("-n.js")) {
    		errorCount.getAndIncrement();
    		t.printStackTrace();
    	}	        	        	
    }
}
