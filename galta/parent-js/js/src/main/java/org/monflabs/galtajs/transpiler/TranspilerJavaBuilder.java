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
package org.monflabs.galtajs.transpiler;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTNode.HighlightPosition;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.StringUtil;

/**
 * 
 */ 
public class TranspilerJavaBuilder extends JavaBuilder {

	private JSTranspilerGeneratorContext context;

	public TranspilerJavaBuilder(JSTranspilerGeneratorContext context) {
		this.context = context;
	}
	
	public JSTranspilerGeneratorContext getContext() {
		return context;
	}
	
	public JSTranspilerOptions getTranspilerOptions() {
		return context.getOptions();
	}
	
	public void debugLocation(ASTNode node) {
		if(getTranspilerOptions().isSourceInCode()) {
			HighlightPosition p = node.getHighlightPosition();
			comment("line: {0}, col {1}", p.getBeginLine(), p.getBeginCol());
			String code = getContext().getMainContext().extractSourceCode(p);
			if(StringUtil.isNotEmpty(code)) {
				commentRaw(JSTranspiler.escapeUnicodeMarkerForJavac(code));
			}
		}
	}
}
