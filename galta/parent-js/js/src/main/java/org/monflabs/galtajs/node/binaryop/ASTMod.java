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
 * Modulo binary operation Node.
 */
public class ASTMod extends ASTArithmeticOp {

	public ASTMod(Token t, ASTNode leftNode, ASTNode rightNode) {
		super(t,RuntimeUtil::mod,leftNode,rightNode);
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}

    @Override
    protected boolean supportsFastPath() {
        return true;
    }

    @Override
    protected Object intIntOp(JSEnvironment env, int l, int r) {
        if(r==0) {
            return Double.NaN;
        }
        int rem = l % r;
        // A zero remainder from a negative dividend is IEEE-754 -0; matches
        // RuntimeUtil.mod's int/int path exactly (spec: 1/(-1 % 1) === -Infinity).
        return (rem==0 && l<0) ? (Object)(-0.0) : (Object)rem;
    }

    @Override
    protected Object doubleDoubleOp(JSEnvironment env, double l, double r) {
        if(env.forceBigDecimalOperations()) {
            return getFunction().apply(env, l, r);
        }
        if(r==0) {
            return Double.NaN;
        }
        return l % r;
    }

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	if(isSequence()) {
    		return StringFormat.format("seq(RuntimeUtil::mod,{0},{1})",JSTranspiler.asRawValue(jsContext,leftNode), JSTranspiler.asRawValue(jsContext,rightNode));
    	} else {
    		return StringFormat.format("mod({0},{1})",JSTranspiler.asValue(jsContext,leftNode), JSTranspiler.asValue(jsContext,rightNode));
    	}
    }

    @Override
	protected String decompileOperator() {
    	return " % ";
    }
}
