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
package org.monflabs.galtajs.debug.api.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.monflabs.galtajs.debug.api.DebugScript;
import org.monflabs.galtajs.debug.api.ExecutionContext;
import org.monflabs.galtajs.debug.api.Location;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSScriptUnit;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;

/**
 * Wraps one {@link JSScriptUnit} compiled with
 * {@code JSEnvironment.Builder.debug(true)} (interpreted) or
 * {@code JSTranspilerOptions.debuggable(true)} (transpiled) - both concrete
 * unit types are handled here since they share no common source/module-flag
 * accessor.
 */
public class DebugScriptImpl implements DebugScript {

	private final String id;
	private final JSScriptUnit unit;
	private final ExecutionContextImpl context;

	public DebugScriptImpl(String id, JSScriptUnit unit, ExecutionContextImpl context) {
		this.id = id;
		this.unit = unit;
		this.context = context;
	}

	public JSScriptUnit getUnit() {
		return unit;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public String url() {
		return unit.getDescriptor().getName();
	}

	@Override
	public String name() {
		return unit.getDescriptor().getName();
	}

	@Override
	public String source() {
		if (unit instanceof JSInterpretedUnit interpreted) {
			return interpreted.getProgram().getText();
		}
		if (unit instanceof JSTranspiledUnit transpiled) {
			String code = transpiled.getFullSourceCode();
			return code != null ? code : "";
		}
		return "";
	}

	@Override
	public String hash() {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(source().getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(source().hashCode());
		}
	}

	@Override
	public int endLine() {
		String text = source();
		int line = 1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	@Override
	public int endColumn() {
		String text = source();
		int lastNewline = text.lastIndexOf('\n');
		return text.length() - lastNewline;
	}

	@Override
	public int length() {
		return source().length();
	}

	@Override
	public boolean isEval() {
		return false;
	}

	@Override
	public boolean isModule() {
		if (unit instanceof JSInterpretedUnit interpreted) {
			return interpreted.getProgram().isModule();
		}
		if (unit instanceof JSTranspiledUnit transpiled) {
			return transpiled.isModuleUnit();
		}
		return false;
	}

	@Override
	public ExecutionContext context() {
		return context;
	}

	// v1: reports only the script's start (0,0-equivalent) - real
	// statement-boundary enumeration would need to walk the compiled AST for
	// every ASTDebugHook.isStatementLevel() node in range, which is not
	// needed yet since setBreakpointByUrl only ever needs ONE resolvable
	// location per request, not the full set DevTools could offer for a
	// "possible breakpoints" gutter decoration.
	@Override
	public List<Location> possibleBreakpoints(int startLine, int startColumn, int endLine, int endColumn) {
		return new ArrayList<>();
	}
}
