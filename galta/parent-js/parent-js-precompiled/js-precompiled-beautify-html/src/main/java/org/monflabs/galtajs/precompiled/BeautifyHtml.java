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
package org.monflabs.galtajs.precompiled;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.jsonfactory.JSValue;
import org.monflabs.galtajs.rt.transpiler.TranspiledGlobalRuntimeContext;
import org.monflabs.util.ObjectBuilder;

public class BeautifyHtml {
	
	public static final class Builder extends ObjectBuilder<BeautifyHtml> {
		
		private JSEnvironment env;
		
		private Builder() {}
		public Builder environment(JSEnvironment env) {
			this.env = env;
			return this;
		}
		
		@Override
		protected BeautifyHtml _build() {
			return new BeautifyHtml(this);
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}

	private JSEnvironment env;
	private TranspiledGlobalRuntimeContext runtimeContext;
	
	private BeautifyHtml(Builder b) {
		this.env = b.env;
		if(env==null) {
			env = JavaScriptEnvironment.newBuilder()
					.supportGlobalAlias(true)
					.build();
		}
		js.Beautifyhtml js = new js.Beautifyhtml(env);
		runtimeContext = new TranspiledGlobalRuntimeContext(env,env.createProgramExecutor());
        js.executeWithContext(runtimeContext);	
	}
	
	public String execute(String source) {
		JSValue beautifier = runtimeContext.global("html_beautify");
		String res = beautifier.call(source).stringValue();
		return res;
	}
}
