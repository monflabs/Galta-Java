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
package org.monflabs.galtajs.transpiler.context;

import org.monflabs.galtajs.node.ASTNode;

/**
 * Transpiler context for the statements of a region (see TranspilerMethodSplitter):
 * a break or continue leaving the region returns its jump marker instead.
 */
public class TranspilerGeneratorRegionContext extends JSTranspilerGeneratorContext {

	private final TranspilerMethodSplitter.Region region;

	public TranspilerGeneratorRegionContext(JSTranspilerGeneratorContext parent, TranspilerMethodSplitter.Region region) {
		super(parent);
		this.region = region;
	}

	/**
	 * The jump index of a break or continue that leaves the innermost region it is
	 * emitted in, or -1 when it is a plain jump.
	 */
	public static int findRegionJump(JSTranspilerGeneratorContext context, ASTNode jump) {
		for(JSTranspilerGeneratorContext c=context; c!=null; c=c.getParent()) {
			if(c instanceof TranspilerGeneratorRegionContext rc) {
				Integer index = rc.region.getEscapes().get(jump);
				return index!=null ? index : -1;
			}
		}
		return -1;
	}
}
