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
package org.monflabs.galtajs.template.templates;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.template.Template;
import org.monflabs.galtajs.template.TemplateEngine;
import org.monflabs.galtajs.template.engines.JspTemplateEngine;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.util.StringUtil;


/**
 * Expression script Template.
 *  
 * @author Philippe Riand
 */
public class ScriptTemplate implements Template { 
	
	private static final String FUNCTION = JspTemplateEngine.FUNCTION;
	
	/**
	 * The {@code __emit__} function of a template: appends its arguments,
	 * converted with JS ToString, to the output of the template currently
	 * executing on the context.
	 */
	public static class TemplateWriter implements Callable {
		private final JSEnvironment env;
		private StringBuilder sw;
		private TemplateWriter(JSEnvironment env, StringBuilder sw) {
			this.env = env;
			this.sw = sw;
		}

		/**
		 * Redirects the output, returning the previous target.
		 */
		private StringBuilder redirect(StringBuilder target) {
			StringBuilder previous = sw;
			sw = target;
			return previous;
		}

		@Override
		public Object call(Object _this, Object[] parameters) {
			for(int i=0; i<parameters.length; i++) {
				append(parameters[i]);
			}
			return null;
		}
		private void append(Object s) {
			if(s!=null) {
				// JS ToString, not Java's: 3 renders "3", not "3.0"
				String text = RuntimeUtil.toString(env, s);
				if(StringUtil.isNotEmpty(text)) {
					sw.append(text);
				}
			}
		}
	}
		
	private TemplateEngine engine;
	private JSInterpretedUnit expression;
	
	public ScriptTemplate(TemplateEngine engine, JSInterpretedUnit expression) {
		this.engine = engine;
		this.expression = expression;
	}
	
	@Override
	public String toString() {
		return expression.getText();
	}
	
	public TemplateEngine getEngine() {
		return engine;
	}
	
	public JSInterpretedUnit getExpression() {
		return expression;
	}
	
	/**
	 * Executes the template on the context. The {@code __emit__} binding is
	 * created on the first execution only - it is a const, which cannot be
	 * declared twice - and later executions on the same context, nested ones
	 * included, redirect it to their own output.
	 */
	@Override
	public String execute(InterpretedGlobalRuntimeContext context) {
		final StringBuilder sw = new StringBuilder();
		VarAccessor existing = context.getLocalVariableEntry(FUNCTION);
		if(existing!=null && existing.getValue() instanceof TemplateWriter writer) {
			StringBuilder previous = writer.redirect(sw);
			try {
				expression.executeWithContext(context);
			} finally {
				writer.redirect(previous);
			}
		} else {
			context.createVariable(FUNCTION, new TemplateWriter(context.getEnvironment(), sw), VAR_TYPE.CONST);
			expression.executeWithContext(context);
		}
		return sw.toString();
	}
}
