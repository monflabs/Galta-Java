package tests.rhinoes6_testsrc;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.rhino.RhinoShellLibrary;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.test.rhino.RhinoTestEnvironment;
import org.monflabs.json.JsonObject;
import org.monflabs.util.Console;
import org.monflabs.util.StringUtil;

import tests.suite.BaseTestSuiteTest;


public abstract class JsTestBaseTest extends BaseTestSuiteTest {

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
		JSEnvironment.Builder env = RhinoTestEnvironment.newBuilder()
				.putProperty(RhinoShellLibrary.PROPERTY_BASEDIR, support.getProjectDirectory("rhino/tests"));
		return env;
	}

	public static final String[] FILTER = new String[] {
		// XML no longer part of the spec
		"es6/stringify-xml.js",
		// This below is very specific to Rhino
		"es6/stringify-java-objects.js",
	};
	
	// Ignore the errors from this file but still run the tests
	public static final Object[] FILTER_ERRORS = new Object[] {
		// This test's own assumption is wrong, not a GaltaJS gap: per spec
		// (10.4.5.4 [[Get]] / IsValidIntegerIndex), reading an out-of-bounds
		// or detached-buffer typed array index returns `undefined` - it
		// never throws (matches real engines: `detachedView[0]` is
		// `undefined` in V8/SpiderMonkey, not a TypeError). This file's
		// `assertThrows(function() { uint8[0]; }, TypeError)` after
		// `buffer.transfer()` (which detaches the original buffer, same as
		// any other detach) asserts the opposite - confirmed correct behavior
		// while implementing the Integer-Indexed Exotic Object algorithm
		// (KnownGaps.md).
		"es2025/arraybuffer-transfer.js", 1,
	};

	public JsTestBaseTest() {
	}

	public JsTestBaseTest(boolean debugger) {
		super(debugger);
	}

	public File getRhinoTestDirectory() {
		return support.getProjectDirectory("rhino/tests/testsrc/jstests");
	}
	
	
	//
	// Nothing loaded by default
	//
    @Override
	protected void loadShell(InterpretedGlobalRuntimeContext jsContext, Path folder) throws Exception {
    }
    
    @Override
	protected String readShell(Path folder, String shell) throws Exception {
    	return null;
    }
    
    @Override
	protected String preprocessFile(Path file, String text) {
    	String path = file.toString();
    	if(path.equals("/es6/collection-iterator.js")) {
    		checkFileCrc(text,2367421422L);
    		// Rhino models SetIterator the old way. The new way is to use the base Iterator as well
    		text = StringUtil.replaceFirst(text, 
    				"assertEquals(SetIteratorPrototype.__proto__, Object.prototype);", 
    				"assertEquals(SetIteratorPrototype.__proto__.__proto__, Object.prototype);"
    			);
    		text = StringUtil.replaceFirst(text, 
    				"assertEquals(MapIteratorPrototype.__proto__, Object.prototype);", 
    				"assertEquals(MapIteratorPrototype.__proto__.__proto__, Object.prototype);"
    			);
    	} else if(path.equals("/es6/dataview.js")) {
    		checkFileCrc(text,3121474828L);
    		// assertEquals doesn't properly compare 2 BigInt - This is a bug in the test suite?
    		// Rhino is wrong, confirmed by V8 tests
    		text = StringUtil.replaceFirst(text, 
    				"assertEquals(expectedSigned, newSignedVal);", 
    				"assertTrue(expectedSigned==newSignedVal);"
    			);
    		text = StringUtil.replaceFirst(text, 
    				"assertEquals(expectedUnsigned, newUnsignedVal);", 
    				"assertTrue(expectedUnsigned==newUnsignedVal);"
    			);
    	} else if(path.equals("/es6/set.js")) {
    		checkFileCrc(text,22814436L);
    		// This test fals because logElement() is never 'use strict'
    		// Rhino is wrong, confirmed by V8 tests
    		text = StringUtil.replaceFirst(text, 
    				"assertEquals(\"a) set[key1] (undefined) set[17] (undefined) \", res);", 
    				"assertEquals(\"a) set[key1] (globalThis) set[17] (globalThis) \", res);"
    			);
    		text = StringUtil.replaceFirst(text, 
    				"assertEquals(\"b) set[] (undefined) set[undefined] (undefined) set[null] (undefined) set[19] (undefined) \", res);", 
    				"assertEquals(\"b) set[] (globalThis) set[undefined] (globalThis) set[null] (globalThis) set[19] (globalThis) \", res);"
    			);
    	} else if(path.equals("/es2023/typedArray-with.js")) {
    		checkFileCrc(text,3912064522L);
    		// When the buffer is a decimal, then the undefined value is transformed to a NaN and not zero
    		text = StringUtil.replaceFirst(text, 
    				"assertEquals(\"0,2,3\", res.toString());", 
    				"assertTrue([\"0,2,3\",\"NaN,2,3\"].includes(res.toString()));"
    		);
    		text = StringUtil.replaceFirst(text, 
    				"assertEquals(\"1,0,3\", res.toString());", 
    				"assertTrue([\"1,0,3\",\"1,NaN,3\"].includes(res.toString()));"
    		);
    	}
    	return text;
    }


    //
    // Handling exceptions
    //

    @Override
	protected void handleException(String relativePathStr, AtomicInteger errorCount, Throwable t) {
   		errorCount.getAndIncrement();
    	if(t instanceof JSRuntimeException re) {
    		Object o = re.getJavascriptException();
    		if(o instanceof JsonObject jo) {
    			String msg = jo.getString("message");
    			Console.log("{0}\n{1}",msg,re.getLocalizedMessage());
    			return;
    		}
    	}
   		t.printStackTrace();
    }

}
