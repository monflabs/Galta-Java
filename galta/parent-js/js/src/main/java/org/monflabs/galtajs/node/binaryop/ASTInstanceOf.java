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

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.RuntimeUtil.MODE;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;



/**
 * Instanceof Node.
 */
public class ASTInstanceOf extends ASTComparisonOp {

	public ASTInstanceOf(Token t, RuntimeUtil.MODE mode, ASTNode leftNode, ASTNode rightNode) {
		super(t,RuntimeUtil::instanceOf,mode,leftNode,rightNode);
	}
	
    @Override
	public JSType getReturnedType() {
    	return JSType.BOOLEAN;
	}

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	if(leftNode.isSequence() || rightNode.isSequence()) {
    		return StringFormat.format("seqCmp(RuntimeUtil::instanceOf,{0},{1},MODE.{2})",JSTranspiler.asRawValue(jsContext,leftNode), JSTranspiler.asRawValue(jsContext,rightNode), getMode());
//    		return StringFormat.format("seqCmp((v1,v2) -> instanceOf(v1,v2),{1},{2},MODE.{3})", JSTranspiler.asRawValue(jsContext,leftNode), JSTranspiler.asRawValue(jsContext,rightNode), getMode());
    	} else {
    		return StringFormat.format("instanceOf({0},{1})",JSTranspiler.asValue(jsContext,leftNode), JSTranspiler.asValue(jsContext,rightNode));
    	}
    }
    
    @Override
	protected String decompileOperator() {
    	return getMode()==MODE.ALL ? " *instanceof " : " instanceof ";
    }
}