package tests.rhino;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.test.rhino.RhinoTestEnvironment;
import org.monflabs.util.StringUtil;


public class RhinoShellTest extends RhinoTestCase {

	public void testVersion() throws Exception {
		JSEnvironment env = RhinoTestEnvironment.create();

    	JSInterpretedUnit sc1 = env.createScript("version();","Rhino Shell");
    	Object v1 = sc1.execute();
    	assertEquals("200", v1);
    	
    	JSInterpretedUnit sc2 = env.createScript("version(\"180\"); version();","Rhino Shell");
    	Object v2 = sc2.execute();
    	assertEquals("180", v2);
	}

	public void testPrint() throws Exception {
		JSEnvironment env = RhinoTestEnvironment.create();
    	InterpretedGlobalRuntimeContext jsContext = new InterpretedGlobalRuntimeContext(env,env.createExpressionExecutor());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        jsContext.setOutStream(ps);

    	JSInterpretedUnit sc = env.createScript("print('a'); print(); print('b','c'); print('d');","Rhino Shell");
    	sc.executeWithContext(jsContext);
    	ps.flush();
    	
    	String out = new String(bos.toByteArray());
    	out = StringUtil.normalizeLineBreaks(out);
    	
    	assertEquals("a\n\nbc\nd\n", out);
    }
}
