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
package org.monflabs.galtajs.rt.transpiler;

/**
 * Implemented ONLY by a transpiled head closure class (a closure literal
 * hoisted directly to a `let`/`const` for-loop's own test/increment clause -
 * see ASTVarContainer.needsHeadClosureSnapshot()'s doc for the general
 * mechanism) whose defensive array snapshot must be taken AFTER a later
 * write in the SAME clause, rather than at the closure's own construction
 * site - see ASTFor's "deferred head closure snapshot" doc for the full
 * rationale (the "let-closure-inside-next-expression" test262 shape: a
 * closure immediately followed, in the very same increment clause, by a
 * further write to the captured loop variable, e.g.
 * {@code for(let i=0; i<5; a.push(()=>i), i++)}).
 *
 * Every OTHER head closure keeps its snapshot field {@code final}, set once
 * by the constructor - this setter (and the non-final field backing it)
 * only exists for the narrow subset of generated classes that opt in via
 * {@code ASTFunction.getDeferredHeadSnapshotTempVar()} being non-null.
 */
public interface HeadClosureSnapshotHolder {

	void setHeadClosureSnapshot(Object[] snapshot);
}
