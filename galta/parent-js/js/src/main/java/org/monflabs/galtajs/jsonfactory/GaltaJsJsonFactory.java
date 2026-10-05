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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArray;
import org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObject;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.java.JavaJsonFactory;

/**
 * Factory for Json Script engine
 */
public class GaltaJsJsonFactory extends JavaJsonFactory {
	
	//public static GaltaJsJsonFactory instance = new GaltaJsJsonFactory();
	
	private JSEnvironment env;
	
	public GaltaJsJsonFactory(JSEnvironment env) {
		this.env = env;
	}
	
	public JSEnvironment getEnvironment() {
		return env;
	}

	
	//
	// To be compatible with JavaScript behaviors
	//
	
	
	@Override
	public DECIMAL defaultDecimal() { 
		//return DECIMAL.DOUBLE; 
		return env.forceBigDecimalOperations() ? DECIMAL.BIGDEC : DECIMAL.DOUBLE;
	}
	@Override
	public OVERFLOW_INTEGER overflowInteger() { 
		//return OVERFLOW_INTEGER.DOUBLE; 
		return env.supportBigIntPromotion() ? OVERFLOW_INTEGER.BIGINT : OVERFLOW_INTEGER.DOUBLE;
	}
	@Override
	public OVERFLOW_DECIMAL overflowDecimal() { 
		//return OVERFLOW_DECIMAL.DOUBLE; 
		return env.supportBigDecimalPromotion() ? OVERFLOW_DECIMAL.BIGDEC : OVERFLOW_DECIMAL.DOUBLE;
	}
	@Override
	public boolean useLongIntegers() {
		return false;
	}


	
	//
	// Export to other libraries
	//

	// Like JSON.stringify(): undefined, a function and a symbol are not JSON values
	@Override
	public Object exportValue(Object value) {
		if(value==RuntimeUtil.UNDEFINED || value instanceof Symbol || (value instanceof Callable c && c.isCallable())) {
			return NO_VALUE;
		}
		return value;
	}


	//
	// Object handling
	//
	
	@Override
	public JsonObject createObject() {
		return new BuiltinObject(env);
	}

	
	//
	// Array handling
	//

	@Override
	public JsonArray createArray() {
		return new BuiltinArray(env);
	}

	@Override
	public JsonArray createArray(int initialCapacity) {
		return new BuiltinArray(env,initialCapacity);
	}
}
