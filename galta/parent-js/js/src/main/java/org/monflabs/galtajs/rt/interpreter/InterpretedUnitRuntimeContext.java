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
package org.monflabs.galtajs.rt.interpreter;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.util.StringFormat;

/**
 * Main runtime context.
 * 
 * This can represent the main execution file or a module.
 */
public abstract class InterpretedUnitRuntimeContext extends InterpretedRuntimeContext implements JSInterpretedUnitRuntimeContext {
	
	public static class Signal {
		public static Signal NONE = new Signal(Type.NONE,null);
		public static Signal _CONTINUE = new Signal(Type.CONTINUE,null);
		public static Signal _BREAK = new Signal(Type.BREAK,null);
		public static Signal _RETURN = new Signal(Type.RETURN,null);
		
		public static enum Type {
			NONE, CONTINUE, BREAK, RETURN,
		}
		private Type type;
		private String label;
		public Signal(Type type, String label) {
			this.type = type;
			this.label = label;
		}
		@Override
		public String toString() {
			if(label!=null) {
				return StringFormat.format("{0}, {1}", type.toString(), label);
			}
			return type.toString();
		}
		public Type getType() {
			return type;
		}
		public String getLabel() {
			return label;
		}
	}
	
	private Object _this;
	
	public InterpretedUnitRuntimeContext(JSEnvironment env, Object _this) {
		this.env = env;
		this._this = _this;
		this.mainContext = this;
	}

	@Override
	public Object getThis() {
		return _this;
	}

	@Override
	public String toString() {
		return "Main Context";
	}

}
