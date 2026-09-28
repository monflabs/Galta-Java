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

import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.MemberAccessor;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;

/**
 * Represents a node that accesses a member through
 */
public interface MemberNode extends INode {

	public ASTNode getNode();

	public boolean isNullOp();

	public boolean isSingleIndex();

	public Object getSingleValue(JSInterpretedRuntimeContext context, Object base);
	
	public void forEachEntries(JSInterpretedRuntimeContext context, JSResult sequence, MemberAccessor accessor, boolean forUpdate);
}