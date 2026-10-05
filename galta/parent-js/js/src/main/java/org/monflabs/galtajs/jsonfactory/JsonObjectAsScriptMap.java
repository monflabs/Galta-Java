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
package org.monflabs.galtajs.jsonfactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.JsonValues;


/**
 * Json Object implemented as a Map wrapper.
 * 
 * Content copied: from org.monflabs.json.java.JsonObjectAsLinkedMap on top of BaseJsonObjectMap 
 */
public abstract class JsonObjectAsScriptMap extends StringPropertyMap implements JsonObject, JSObjectInternal {
	
	private String reference;

	public JsonObjectAsScriptMap() {
	}

	@Override
	public JsonObjectAsScriptMap clone() {
		return (JsonObjectAsScriptMap)super.clone();
	}

	@Override
	public boolean equals(Object o) {
		if(this==o) {
			return true;
		}
		if(o instanceof JsonObject jo) {
			return JsonUtil.equalsObject(this, jo);
		}
		return false;
	}

	@Override
	public JsonValues jsonValues() {
		return JsonValues.of(this);
	}
	
	@Override
	public String toString() {
		return stringify(false);
	}
	

	@Override
	public String getReference() {
		return reference;
	}
	@Override
	public  void setReference(String reference) {
		this.reference = reference;
	}
}
