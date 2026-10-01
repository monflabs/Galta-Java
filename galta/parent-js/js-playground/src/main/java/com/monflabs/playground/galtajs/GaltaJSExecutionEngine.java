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
package com.monflabs.playground.galtajs;


import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSPathModuleResolver;
import org.monflabs.galtajs.library.node.NodeModuleResolver;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;
import org.monflabs.galtajs.rt.JSGlobalContext.RunningState;
import org.monflabs.galtajs.rt.JSRuntimeInterruptException;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.ExecutionEngine;
import org.monflabs.util.StringUtil;

public class GaltaJSExecutionEngine extends ExecutionEngine {
	
	public static final String OPTION_OPTIMIZE = "Optimize";
	public static final String OPTION_GALTAJS = "GaltaJS";
	public static final String OPTION_STRICTMODE = "StrictMode";

	public static final String DEFAULT_JS = "main.js";
	
	public GaltaJSExecutionEngine(ExecutionContext context) {
		super(context);
	}
	
	private volatile InterpretedGlobalRuntimeContext runningContext;
	private volatile boolean stopRequested;
	
	/**
	 * Execute main.js. Returns null when there is nothing to execute (a missing or blank main.js).
	 */
	@Override
	protected GaltaJSExecutionResult _execute() throws Exception {
		ExecutionContext context = getExecutionContext();
		String mainSource = context.getContent(DEFAULT_JS);
		if(mainSource==null || StringUtil.isEmpty(mainSource.trim())) {
			return null;
		}
		boolean galtaJS = (Boolean)context.getExecutionOption(OPTION_GALTAJS,false);
		boolean strictMode = (Boolean)context.getExecutionOption(OPTION_STRICTMODE,true);
		
		JSEnvironment env = SnippetEnvironment.newBuilder(galtaJS,strictMode)
				.addModuleResolver(new JSPathModuleResolver(context.getSnippetFs()))
				.addModuleResolver(new NodeModuleResolver(context.getSnippetFs()))
				.configure( (b) -> {
					if((Boolean)context.getExecutionOption(OPTION_OPTIMIZE,false)) {
				    	b.scriptOptimizer(ScriptOptimizer.defaultOptimizer());
					}
				})
				.build();

		JSInterpretedUnit expr = env.createScript(mainSource,DEFAULT_JS,0); // no cache
		
		// We don't do a REPL here but we hook on the execution
		// This is more optimized and works equally well
		if(getExecutionContext().isLogStatements()) {
			installConsoleTrace(expr);
		}
		
		InterpretedGlobalRuntimeContext programContext = createProgramRuntimeContext(env);
		programContext.setOutStream(context.getConsoleOut());
		programContext.setErrStream(context.getConsoleErr());
		
		var res = new GaltaJSExecutionResult(expr, programContext);
		runningContext = programContext;
		try {
			if(stopRequested) {
				throw new JSRuntimeInterruptException();
			}
			expr.executeWithContext(programContext);
		} catch(Throwable t) {
			res.setException(t);
		} finally {
			runningContext = null;
		}
		return res;
	}
	
	@Override
	public boolean isSoftInterruptable() {
		return true;
	}
	
	/**
	 * Ask the running script to stop: the engine checks its running state at every
	 * statement and throws an uncatchable interrupt. The request is repeated for a
	 * short while, as the execution sets its own state when it starts, and the
	 * execution thread is interrupted to wake up blocking waits.
	 */
	@Override
	public void softInterrupt() {
		stopRequested = true;
		Thread t = getExecutionThread();
		if(t!=null) {
			t.interrupt();
		}
		// a short-lived poller: a virtual thread (the script itself keeps
		// running on its platform thread)
		Thread.ofVirtual().name("galtajs-playground-stop").start(() -> {
			long end = System.currentTimeMillis()+10_000;
			while(getState()==STATE.RUNNING && System.currentTimeMillis()<end) {
				InterpretedGlobalRuntimeContext c = runningContext;
				if(c!=null) {
					c.setRunningState(RunningState.STOPPING);
				}
				try {
					Thread.sleep(20);
				} catch(InterruptedException e) {
					return;
				}
			}
		});
	}
	
	private void installConsoleTrace(JSInterpretedUnit expr) {
		// We inject a trace node to all program statements
		ASTProgram p = expr.getProgram();
		ASTNode[] nodes = p.getStatements();
		if(nodes!=null) {
			for(int i=0; i<nodes.length; i++) {
				nodes[i] = new ASTTrace(getExecutionContext(),nodes[i]);
			}
			p.setStatements(nodes);
		}
	}

	public InterpretedGlobalRuntimeContext createProgramRuntimeContext(JSEnvironment env) {
		ExecutionContext context = getExecutionContext();
		InterpretedGlobalRuntimeContext programContext = new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor());
		programContext.putProperty(SnippetLibrary.PROP_EXECUTION_CONTEXT, context);
		return programContext;
	}
}
