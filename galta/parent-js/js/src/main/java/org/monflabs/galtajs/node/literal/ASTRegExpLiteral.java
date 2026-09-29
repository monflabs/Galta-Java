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
package org.monflabs.galtajs.node.literal;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExp;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * Regular expression Literal.
 */
public class ASTRegExpLiteral extends ASTNode {

	private String regexp;

	public ASTRegExpLiteral(Token t, String regexp) {
		super(t);
		this.regexp = regexp;
	}

	// A RegularExpressionLiteral whose pattern or flags are invalid is an
	// early SyntaxError: validated here at parse time by building the RegExp
	// once, rather than only when the literal is evaluated.
	@Override
	protected void init(InitContext initContext) {
		super.init(initContext);
		int pos = regexp.lastIndexOf("/");
		try {
			new RegExp(initContext.getEnvironment(),regexp.substring(1,pos),regexp.substring(pos+1));
		} catch(org.monflabs.galtajs.rt.JSRuntimeException ex) {
			throw new org.monflabs.galtajs.JSParseException(ex, this, "Invalid regular expression {0}: {1}", regexp, ex.getMessage());
		}
	}

	@Override
	public String getNodeString() {
		return "Regexp, "+regexp;
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}

	@Override
	public Object evaluateValue(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// Evaluating a RegularExpressionLiteral must return a fresh RegExp object
			// every time (Annex B/ES5+ semantics) - unlike ES3, two literals at the
			// same source position (e.g. inside a loop) must not compare as ===.
			int pos = regexp.lastIndexOf("/");
			String source = regexp.substring(1,pos);
			String flags = regexp.substring(pos+1);
			return new RegExp(context.getEnvironment(),source,flags);
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
		int pos = regexp.lastIndexOf("/");
		String source = regexp.substring(1,pos);
		String flags = regexp.substring(pos+1);
    	return StringFormat.format("new RegExp({0},{1},{2})",JSTranspiler.MAIN_ENVIRONMENT,ASTLiteral.encodeString(source),ASTLiteral.encodeString(flags));
    }
    
    @Override
	public String decompileExpression() {
    	return regexp;
    }
}
