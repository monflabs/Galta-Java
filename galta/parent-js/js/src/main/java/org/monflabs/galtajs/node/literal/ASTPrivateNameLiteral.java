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

import org.monflabs.galtajs.parser.Token;

/**
 * Marks a genuine `#name in obj` private-name brand-check reference,
 * distinct from an ordinary string literal that merely happens to start
 * with "#" (e.g. `"#name" in obj`, a normal property-existence check). The
 * parser represents both as a plain string value ("#name"), so without this
 * marker they'd be structurally indistinguishable once parsed - see
 * JSParser.jj's IN-with-private-name grammar rule, the only place this is
 * constructed. ASTIn checks `instanceof ASTPrivateNameLiteral`, not just
 * "does the string start with #", to tell them apart.
 */
public class ASTPrivateNameLiteral extends ASTLiteral {

	public ASTPrivateNameLiteral(Token t, String name) {
		super(t, name);
	}

	// Must decompile back to the bare `#name` source form, not the inherited
	// quoted-string-literal form (`"#name"`) - the "decompile, then re-parse"
	// test pass round-trips every AST node through source text, and a quoted
	// string would re-parse as an ORDINARY string literal, silently losing
	// the private-name brand-check semantics (ASTIn would then take the
	// wrong branch on re-parse).
	@Override
	public String decompileExpression() {
		return (String)getValue();
	}
}
