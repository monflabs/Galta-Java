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
package org.monflabs.galtajs.node;

import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.util.StringFormat;

/**
 * Represents a node that can be chained in an expression.
 * This is used to deal with optional chaining
 *    ?. ?[] ?()
 */
public interface ChainingNode extends INode {

	public ASTNode getNode();
	
	public boolean isNullOp();
	
    public String transpileChainedNode(JSTranspilerGeneratorContext jsContext);
    
	public String transpileChainingNode(JSTranspilerGeneratorContext jsContext, String chain);
	
    public default String transpileChain(JSTranspilerGeneratorContext jsContext, ChainingNode node) {
      	// . [] ()

    	// 1. We go down the chain of nodes that are chaining nodes
    	ChainingNode last = node;
    	while(true) {
    		ASTNode next = last.getNode();
    		if(!(next instanceof ChainingNode)) {
    			break;
    		}
    		last = (ChainingNode)next;
    	}

    	String chain = last.transpileChainedNode(jsContext);
    	return transpileNode(jsContext, node, last, chain);
    }
    public default String transpileNode(JSTranspilerGeneratorContext jsContext, ChainingNode node, ChainingNode last, String chain) {
    	for(INode _n=last; _n!=node.getParent(); _n=_n.getParent()) {
    		ChainingNode n = (ChainingNode)_n;
    		if(n.isNullOp()) {
    			String varName = "c"+jsContext.generateUniqueId();
    			String nodeTranspiled = n.transpileChainingNode(jsContext, varName);
    			if(n.getParent()!=node.getParent()) {
        			return StringFormat.format("applyNotNullOrUndefined({0}, {1} -> {2})",chain,varName,transpileNode(jsContext, node, (ChainingNode)n.getParent(), nodeTranspiled));
    			} else {
    				return StringFormat.format("applyNotNullOrUndefined({0}, {1} -> {2})",chain,varName,nodeTranspiled);
    			}
    		} else {
        		chain = n.transpileChainingNode(jsContext, chain);
    		}
    	}
    	
    	return chain;

    }
}