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
package org.monflabs.galtajs.node.binaryop;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;



/**
 * Add binary operation Node.
 */
public class ASTAdd extends ASTArithmeticOp {

	public ASTAdd(Token t, ASTNode leftNode, ASTNode rightNode) {
		super(t,RuntimeUtil::add,leftNode,rightNode);
	}

    @Override
	public JSType getReturnedType() {
    	// If either operand is statically known to be a String, the `+` result
    	// is a String too (JS spec: string concat when either primitive is
    	// String). Callers (this node's own transpileJavaExpression and any
    	// ancestor ASTAdd) use this to route to the specialized String-typed
    	// RuntimeUtil.add overloads and/or to flatten chained + into
    	// stringConcat.
    	if(leftNode.getReturnedType() == JSType.STRING || rightNode.getReturnedType() == JSType.STRING) {
    		return JSType.STRING;
    	}
    	return JSType.UNKNOWN;
	}

    @Override
    protected boolean supportsFastPath() {
        return true;
    }

    @Override
    protected Object intIntOp(JSEnvironment env, int l, int r) {
        return RuntimeUtil.addExact(env, l, r);
    }

    @Override
    protected Object doubleDoubleOp(JSEnvironment env, double l, double r) {
        if(env.forceBigDecimalOperations()) {
            return getFunction().apply(env, l, r);
        }
        return l + r;
    }

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	if(isSequence()) {
    		return StringFormat.format("seq(RuntimeUtil::add,{0},{1})",JSTranspiler.asRawValue(jsContext,leftNode), JSTranspiler.asRawValue(jsContext,rightNode));
    	} else {
    		// When an operand's static JS type is STRING but its emitted Java
    		// expression has type Object (chained ASTAdd of STRING type returns
    		// Object from add()), a (CharSequence) cast forces Java overload
    		// resolution to pick the specialized RuntimeUtil.add(CharSequence,…)
    		// form. Skip the cast for String literals - they're already Java-typed
    		// String (a CharSequence) and resolution works without help.
    		// Must be CharSequence rather than String because a chained STRING-add
    		// under ENABLE_CONSSTRING produces a ConsString, not a String, and
    		// ConsString extends CharSequence.
    		String l = JSTranspiler.asValue(jsContext, leftNode);
    		String r = JSTranspiler.asValue(jsContext, rightNode);
    		if(leftNode.getReturnedType() == JSType.STRING && !isStringLiteralExpr(l)) {
    			l = "(CharSequence)(" + l + ")";
    		}
    		if(rightNode.getReturnedType() == JSType.STRING && !isStringLiteralExpr(r)) {
    			r = "(CharSequence)(" + r + ")";
    		}
    		return StringFormat.format("add({0},{1})", l, r);
    	}
    }

    // A String literal emitted via ASTLiteral.encodeString is already a
    // Java-typed String expression (starts with `"`), no cast needed.
    private static boolean isStringLiteralExpr(String s) {
    	return !s.isEmpty() && s.charAt(0) == '"';
    }

    @Override
	protected String decompileOperator() {
    	return " + ";
    }
}
