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
package org.monflabs.galtajs.rt.builtins.privatename;

/**
 * Spec's PrivateName (6.2.11): a unique identity token minted once per
 * private-name declaration (#name) per class EVALUATION - never once per
 * name, and never once per source location, since a class expression can be
 * evaluated more than once (e.g. inside a function called twice), and each
 * evaluation must have its own, unrelated private names even when the
 * source text is identical. Reference identity (default Object equals/
 * hashCode) is exactly the semantics required - deliberately NOT a Symbol
 * (which is JS-visible and carries irrelevant wrapper/typeof/well-known
 * machinery); this type is purely an internal Java key and must never be
 * exposed to JS code.
 */
public final class PrivateName {

	private final String description;

	public PrivateName(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}

	@Override
	public String toString() {
		return description;
	}
}
