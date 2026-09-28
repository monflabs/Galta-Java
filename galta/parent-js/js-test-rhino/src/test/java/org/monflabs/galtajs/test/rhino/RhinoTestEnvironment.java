package org.monflabs.galtajs.test.rhino;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.UnitTestLibrary;
import org.monflabs.galtajs.library.java.JavaLibrary;
import org.monflabs.galtajs.library.rhino.RhinoShellLibrary;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.util.IOStreamUtil;


public class RhinoTestEnvironment {
	
	public static JSEnvironment.Builder newBuilder() {
		return JSEnvironment.newBuilder()
				.supportParseIntOctal(true)
				.registerLibrary(new StandardLibrary())
				.registerLibrary(new UnitTestLibrary())
				.registerLibrary(new RhinoShellLibrary())
				.registerLibrary(new RhinoTestLibrary())
				.registerLibrary(new JavaPackageLibrary())
				.registerLibrary(new JavaLibrary());
	}
	
	public static JSEnvironment create() {
		JSEnvironment env = newBuilder().build();
		// Load the polyfill
		// Could create a compile version of it...
		try(InputStreamReader is=new InputStreamReader(RhinoTestEnvironment.class.getClassLoader().getResource("polyfills/toSource.js").openStream(),StandardCharsets.UTF_8)) {
	        String toSource = IOStreamUtil.readString(is);
	        env.createScript(toSource, "toSource.js").execute();
		} catch(Exception e) {
			throw RuntimeUtil.wrap(e);
		}
		return env;
	}
}
