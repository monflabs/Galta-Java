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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.monflabs.galtajs.debug.api.Breakpoint;
import org.monflabs.galtajs.debug.api.BreakpointRequest;
import org.monflabs.galtajs.debug.api.Location;

/**
 * Line-only breakpoint (v1 scope - see the plan's CDP protocol surface
 * section: no column precision, no condition expression yet).
 */
public class BreakpointImpl implements Breakpoint {

	private final String id;
	private final BreakpointRequest request;
	private final Pattern urlRegex;
	private final List<Location> locations = new ArrayList<>();

	public BreakpointImpl(String id, BreakpointRequest request) {
		this.id = id;
		this.request = request;
		this.urlRegex = request.urlRegex() != null ? Pattern.compile(request.urlRegex()) : null;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public BreakpointRequest request() {
		return request;
	}

	@Override
	public List<Location> locations() {
		return locations;
	}

	/**
	 * Whether this breakpoint's request matches the given script/line -
	 * called once per compiled script (to resolve a pending breakpoint) and
	 * reused by {@code DebuggerImpl}'s hit-testing.
	 */
	public boolean matches(DebugScriptImpl script, int line) {
		if (request.line() != line) {
			return false;
		}
		if (request.scriptId() != null) {
			return request.scriptId().equals(script.id());
		}
		if (request.url() != null) {
			return request.url().equals(script.url());
		}
		if (urlRegex != null) {
			return urlRegex.matcher(script.url()).find();
		}
		return false;
	}

	/**
	 * Whether a statement starting at the given (1-based) column is a hit. A
	 * request without a column matches every statement of its line; with a
	 * column, the statements starting at or after it.
	 */
	public boolean matchesColumn(int column) {
		return request.column() < 0 || column >= request.column();
	}

	public void addLocation(Location location) {
		locations.add(location);
	}
}
