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
package org.monflabs.json.jsonpath;

import java.util.List;

/**
 * Expression node evaluating a sub path.
 * 
 * @author priand
 */
public final class ExprPathOp extends ExprNode {
	
	public static enum VALUE {
		ROOT,
		CURRENT,
	};
	
	private PathNode node;
	private VALUE value;
	
	public ExprPathOp(PathNode node, VALUE value) {
		this.node = node;
		this.value = value;
	}
	
	@Override
	public String toString() {
		// "$" or "@", followed by the sub path (if any)
		StringBuilder b = new StringBuilder();
		b.append(value==VALUE.ROOT ? "$" : "@");
		// The whole sub path, not only its first node
		for(PathNode p=node; p!=null; p=p.next()) {
			p.addCanonicalPath(b);
		}
		return b.toString();
	}
	
	public PathNode getPathNode() {
		return node;
	}
	
	public boolean isDefinite() {
		return node==null || node.isDefinite();
	}
	
	/**
	 * True if the path selects at most one node (RFC 9535 singular query): only member
	 * names and single indexes, one per segment.
	 */
	public boolean isSingular() {
		for(PathNode p=node; p!=null; p=p.next()) {
			if(!(p instanceof PathIndex pi)) {
				return false;
			}
			PathIndex.Index i = pi.getIndex();
			if(i==null || i.next()!=null || !(i instanceof PathIndex.Member || i instanceof PathIndex.SingleIndex)) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * The value of a singular query, or {@link ExprNode#NOTHING} when it selects no node.
	 */
	@Override
	public Object evaluate(Object root, Object current) {
		JsonValues r = executePath(root, current);
		return r._size()==1 ? r._get(0) : NOTHING;
	}


	public JsonValues executePath(Object root, Object current) {
		Object result=null; 
		switch(value) {
			case ROOT -> 	result = root;
			case CURRENT -> result = current;
		}
		// Not JsonValues.of(result): for a null node that returns the shared NULL
		// singleton, which the loop below then mutates for the whole JVM
		JsonValues r = new JsonValues(result);
		for(PathNode p=node; p!=null; p=p.next()) {
			switch(r.getType()) {
				case EMPTY -> {
				}
				case VALUE -> { 
					Object v=r.value(); 
					r.clear(); 
					p.execute(r,root,v, null); // no pointer needed
				}
				case LIST ->  {
					List<?> l=(List<?>)r.value; 
					r.clear(); 
					for(int i=0; i<l.size(); i++) {
						p.execute(r,root,l.get(i), null); // no pointer needed
					}
				}
			}
		}
		return r;
	}
	
	@Override
	public boolean execute(Object root, Object current) {
		JsonValues r = executePath(root, current);
		return !r.isEmpty();
	}
}
