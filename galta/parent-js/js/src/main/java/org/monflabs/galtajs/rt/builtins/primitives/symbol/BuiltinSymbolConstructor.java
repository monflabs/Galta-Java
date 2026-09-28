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
package org.monflabs.galtajs.rt.builtins.primitives.symbol;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

/**
 * 
 */
public class BuiltinSymbolConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "Symbol";
	
	public BuiltinSymbolConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinSymbolPrototype.get(env),0);

		setOwnProperty("asyncIterator",Symbol.ASYNC_ITERATOR,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("asyncDispose",Symbol.ASYNC_DISPOSE,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("dispose",Symbol.DISPOSE,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("hasInstance",Symbol.HAS_INSTANCE,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("isConcatSpreadable",Symbol.IS_CONCAT_SPREDABLE,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("isRegExp",Symbol.IS_REGEXP,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("iterator",Symbol.ITERATOR,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("match",Symbol.MATCH,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("matchAll",Symbol.MATCH_ALL,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("replace",Symbol.REPLACE,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("search",Symbol.SEARCH,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("species",Symbol.SPECIES,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("split",Symbol.SPLIT,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("toPrimitive",Symbol.TO_PRIMITIVE,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("toStringTag",Symbol.TO_STRING_TAG,PropertyDescriptor.DESC_STATICFIELDS);
		setOwnProperty("unscopables",Symbol.UNSCOPABLES,PropertyDescriptor.DESC_STATICFIELDS);

		setOwnMethod(new Method(env,MethodId.for_,"for",1));
		setOwnMethod(new Method(env,MethodId.keyFor,1));
	}
	
	@Override
	public Class<?> getNativeClass() {
		return Symbol.class;
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		if(parameters.length==0) {
			return new Symbol(RuntimeUtil.UNDEFINED);
		}
		Object p = parameters[0];
		if(p==RuntimeUtil.UNDEFINED) {
			return new Symbol(RuntimeUtil.UNDEFINED);
		}
		return new Symbol(RuntimeUtil.toString(getEnvironment(),p));
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
        throw RuntimeUtil.typeError("Cannot intanciate a symbol object");
	}
	
	private static enum MethodId {
		for_,
		keyFor,
	}
	private static final class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		private Method(JSEnvironment env, MethodId methodId, String name, int length) {
			super(env,name,length);
			this.methodId = methodId;
		}
		
	    @Override
		public Object call(final Object obj, final Object[] args) {
	        switch(methodId){
	        	case for_ -> {
	        		String desc = paramString(args,0);
	        		return Symbol.for_(desc);
	        	}
	        	case keyFor -> {
	        		Object p = param(args,0);
	        		// Type(sym) must be Symbol - a boxed Object(symbol) wrapper
	        		// (tracked via the primitive property map) is an Object, not
	        		// a Symbol, and must be rejected too.
	        		if(p instanceof Symbol symb && !RuntimeUtil.isBoxedSymbol(getEnvironment(),p)) {
	        			return symb.isGlobal() ? symb.getDescription() : RuntimeUtil.UNDEFINED;
	        		}
	        		throw RuntimeUtil.typeError("Object is not a symbol");
	        	}
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}
