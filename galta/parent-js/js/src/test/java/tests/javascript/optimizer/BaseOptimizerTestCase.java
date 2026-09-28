/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.javascript.optimizer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;
import org.monflabs.util.Console;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public abstract class BaseOptimizerTestCase extends JavaScriptStrictTestCase {
	

	protected String asString(JSEnvironment env, ScriptOptimizer optimizer, String script) throws Exception {
		ASTProgram p = optimize(env,optimizer, script);
		return dumpAsString(p);
	}
	protected String asCodeString(JSEnvironment env, ScriptOptimizer optimizer, String script) throws Exception {
		ASTProgram p = optimize(env,optimizer, script);
		return p.decompile();
	}

	protected ASTProgram optimize(JSEnvironment env, ScriptOptimizer optimizer, String script) throws Exception {
		boolean TRACE = JavaScriptStrictTestCase._ALLTESTS==false;
		
		ASTProgram p = env.createScript(script,"Optimizer").getProgram();
		if(TRACE) {
			Console.log("*******************************************************************");
			Console.log("*** Before optimization");
			p.dump();
			Console.log("");
		}
		optimizer.optimize(env,p);
		if(TRACE) {
			Console.log("*** After optimization");
			p.dump();
		}
		return p;
	}
	
	protected String dumpAsString(ASTNode node) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        node.dump(printStream);
        return byteArrayOutputStream.toString();
	}
}
