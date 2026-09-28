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

import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.template.Template;
import org.monflabs.galtajs.template.TemplateEngine;
import org.monflabs.galtajs.template.engines.JspTemplateEngine;
import org.monflabs.util.StringUtil;


/**
 * Expression script Template.
 *  
 * @author Philippe Riand
 */
public class ScriptTemplate implements Template { 
	
	private static final String FUNCTION = JspTemplateEngine.FUNCTION;
	
	public static class TemplateWriter implements Callable {
		private StringBuilder sw;
		private TemplateWriter(StringBuilder sw) {
			this.sw = sw;
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
				String text = s.toString();
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
	
	@Override
	public String execute(InterpretedGlobalRuntimeContext context) {
		final StringBuilder sw = new StringBuilder();
		context.createVariable(FUNCTION, new TemplateWriter(sw), VAR_TYPE.CONST);
		expression.executeWithContext(context);
		return sw.toString();
	}
}
