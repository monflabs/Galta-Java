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
package org.monflabs.json.impexp.util;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.impl.JsonTargetImpl;

public class StringDumpTarget extends JsonTargetImpl {
	
	public static class Builder extends TargetBuilder<StringDumpTarget,Builder> {
		private Builder() {}
		@Override
		protected StringDumpTarget _build() {
			return new StringDumpTarget(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}

	private StringBuilder b;
	
	protected StringDumpTarget(Builder builder) {
		super(builder);
	}

	@Override
	public boolean supportsDeletions() {
		return true;
	}
	
	public String getString() {
		return b!=null ? b.toString() : null;
	}

	@Override
	public void saveJsonContent(JsonContent content) {
		switch(content.getType()) {
			case RECORD -> {
				b.append(content.getKey().toString());
				b.append(": ");
				b.append(JsonFactory.get().stringifySorted(content.getJson()));
				b.append("\n");
			}
			case DELETION -> {
				b.append(content.getKey().toString());
				b.append(": <DELETED>");
				b.append("\n");
			}
		}
	}

	@Override
	public void init() {
		super.init();
		b = new StringBuilder(2048);
	}

	@Override
	public void close() {
		super.close();
	}
}