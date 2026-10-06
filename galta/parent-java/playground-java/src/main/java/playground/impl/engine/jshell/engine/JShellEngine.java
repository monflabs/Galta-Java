/*
 * Copyright (c) 2023-2026 Philippe Riand
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
package playground.impl.engine.jshell.engine;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.monflabs.playground.PlaygroundException;

import jdk.jshell.DeclarationSnippet;
import jdk.jshell.Diag;
import jdk.jshell.EvalException;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.Snippet.Status;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.execution.DirectExecutionControl;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;


//
// Examples:
//   https://github.com/eobermuhlner/jshell-scriptengine/blob/master/ch.obermuhlner.scriptengine.jshell/src/main/java/ch/obermuhlner/scriptengine/jshell/JShellCompiledScript.java
//   https://github.com/johnpoth/jshell-maven-plugin/blob/master/src/main/java/com/github/johnpoth/jshell/JShellMojo.java
//
public class JShellEngine {

	private PrintStream out;
	private PrintStream err;
	
	public JShellEngine() {
	}
	
	public PrintStream getOut() {
		return out;
	}
	public void setOut(PrintStream out) {
		this.out = out;
	}

	public PrintStream getErr() {
		return err;
	}
	public void setErr(PrintStream err) {
		this.err = err;
	}

	public void execute(String script) throws Exception {
		JShell.Builder builder = JShell.builder()
				.executionEngine(new SyncLocalExecutionControlProvider(new SyncLocalExecutionControl()), null);
		if(out!=null) {
			builder.out(out);
		}
		if(err!=null) {
			builder.err(err);
		}
		JShell shell = builder.build();
		
		// We cannot read the lines one by one but we need to do an analysis
		// as some statements can spawn multiple lines
		try {
			splitScript(shell,script).forEach( (line) -> {
				List<SnippetEvent> events = shell.eval(line);
				for(SnippetEvent event: events) {
			        Snippet snippet = event.snippet();
			        // Events caused by another snippet (e.g. a redeclared variable OVERWRITTEN
			        // by the new declaration) are not errors of the evaluated code
			        if(event.causeSnippet()!=null) {
			        	continue;
			        }
					if(event.status()!=Status.VALID) {
				        Optional<Diag> optionalDiag = shell.diagnostics(snippet).findAny();
				        if (optionalDiag.isPresent()) {
				            Diag diag = optionalDiag.get();
				            throw new PlaygroundException(null,"{0}\n{1}", diag.getMessage(null), snippet);
				        }
				        if (snippet instanceof DeclarationSnippet) {
				            DeclarationSnippet declarationSnippet = (DeclarationSnippet) snippet;
				            List<String> unresolvedDependencies = shell.unresolvedDependencies(declarationSnippet).collect(Collectors.toList());
				            if (!unresolvedDependencies.isEmpty()) {
				                throw new PlaygroundException(null,"Unresolved dependencies: {0}\n{1}", unresolvedDependencies, snippet);
				            }
				        }

				        throw new PlaygroundException(null, "Unknown Jshell error\n{0}", snippet);
				    }
					if(event.exception()!=null) {
			            Exception ex = event.exception();
			            if (ex instanceof EvalException) {
			                EvalException evalException = (EvalException) ex;
				            throw new PlaygroundException(ex,"{0}: {1}\n{2}", evalException.getExceptionClassName(), ex.getMessage(), snippet.source());
			            }
			            throw new PlaygroundException(ex,"{0}\n{1}", ex.getMessage(), snippet.source());
					}
				}
			});
		} finally {
			shell.close();
		}
	}
	
    private static List<String> splitScript(JShell jshell, String script) {
        List<String> snippets = new ArrayList<>();
        while (!script.isEmpty()) {
            SourceCodeAnalysis.CompletionInfo completionInfo = jshell.sourceCodeAnalysis().analyzeCompletion(script);
            if (completionInfo.completeness()==SourceCodeAnalysis.Completeness.EMPTY) {
            	// Only white spaces or comments are left
            	break;
            }
            if (!completionInfo.completeness().isComplete()) {
                throw new PlaygroundException(null, "Incomplete script\n{0}", script);
            }
            snippets.add(completionInfo.source());
            script = completionInfo.remaining();
        }

        return snippets;
    }
	
	// LocalExecutionControl spawns threads an executes within these
	// In the playground, we need to execute in the current thread
    private static class SyncLocalExecutionControl extends DirectExecutionControl {
        @Override
        protected String invoke(Method doitMethod) throws Exception {
            Object value = doitMethod.invoke(null);
            return valueString(value);
        }
    }
    private static class SyncLocalExecutionControlProvider implements ExecutionControlProvider {
        private SyncLocalExecutionControl executionControl;

        SyncLocalExecutionControlProvider(SyncLocalExecutionControl executionControl) {
            this.executionControl = executionControl;
        }

        @Override
        public String name() {
            return "accessdirect";
        }

        @Override
        public SyncLocalExecutionControl generate(ExecutionEnv env, Map<String, String> parameters) throws Throwable {
            return executionControl;
        }
    }
}
