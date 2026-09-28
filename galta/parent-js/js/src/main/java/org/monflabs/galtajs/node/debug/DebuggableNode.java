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
package org.monflabs.galtajs.node.debug;

import org.monflabs.galtajs.node.INode;

/**
 * Marker for the node types NodeFactory.createDebugHook() must never wrap
 * when they appear as a sub-expression (a call argument, an if/while/for
 * test/update clause, ...) - see that method's own doc for why. These get
 * their own, more appropriate statement-level wrap instead
 * (createDebugHookStatement()), or none at all (ASTBlock).
 */
public interface DebuggableNode extends INode {
}
