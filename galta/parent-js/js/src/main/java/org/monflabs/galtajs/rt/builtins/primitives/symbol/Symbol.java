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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.galtajs.rt.RuntimeUtil;

/**
 * Primitive symbol. 
 * Such a primitive is *not* environment deoendent. Moreover the registry is global (inter env)
 * Similar to Boolean or Number. 
 */
public class Symbol {
	
	// Declared before the well-known symbols below, which use it while the class initializes
	private static final AtomicInteger counter = new AtomicInteger();

	//
	// Well known symbols
	
	public static final Symbol ASYNC_ITERATOR 		= new Symbol("Symbol.asyncIterator");
	public static final Symbol ASYNC_DISPOSE 		= new Symbol("Symbol.asyncDispose");
	public static final Symbol DISPOSE 				= new Symbol("Symbol.dispose");
	public static final Symbol HAS_INSTANCE 		= new Symbol("Symbol.hasInstance");
	public static final Symbol IS_CONCAT_SPREDABLE 	= new Symbol("Symbol.isConcatSpreadable");
	public static final Symbol IS_REGEXP 			= new Symbol("Symbol.isRegExp");
	public static final Symbol ITERATOR 			= new Symbol("Symbol.iterator");
	public static final Symbol MATCH 				= new Symbol("Symbol.match");
	public static final Symbol MATCH_ALL 			= new Symbol("Symbol.matchAll");
	public static final Symbol REPLACE 				= new Symbol("Symbol.replace");
	public static final Symbol SEARCH 				= new Symbol("Symbol.search");
	public static final Symbol SPECIES 				= new Symbol("Symbol.species");
	public static final Symbol SPLIT 				= new Symbol("Symbol.split");
	public static final Symbol TO_PRIMITIVE 		= new Symbol("Symbol.toPrimitive");
	public static final Symbol TO_STRING_TAG 		= new Symbol("Symbol.toStringTag");
	public static final Symbol UNSCOPABLES 			= new Symbol("Symbol.unscopables");

	
	//
	// Symbol registry (GlobalSymbolRegistry)
	//
	// The specification shares one registry between all the realms of an agent,
	// so it is not per environment. It holds the symbols weakly: a registered
	// symbol that nobody references any more cannot be observed, and a later
	// Symbol.for() with the same key creating a new one is indistinguishable.
	// That keeps the registry from growing without bound, with no fixed cap.
	private static final Map<String,SymbolRef> globals = new ConcurrentHashMap<>();
	private static final ReferenceQueue<Symbol> collected = new ReferenceQueue<>();

	private static final class SymbolRef extends WeakReference<Symbol> {
		final String key;
		SymbolRef(String key, Symbol s) {
			super(s, collected);
			this.key = key;
		}
	}

	public static Symbol for_(String name) {
		expungeCollected();
		while(true) {
			SymbolRef ref = globals.get(name);
			Symbol s = ref!=null ? ref.get() : null;
			if(s!=null) {
				return s;
			}
			Symbol created = new Symbol(name,true);
			SymbolRef newRef = new SymbolRef(name, created);
			if(ref==null ? globals.putIfAbsent(name,newRef)==null : globals.replace(name,ref,newRef)) {
				return created;
			}
		}
	}

	private static void expungeCollected() {
		Reference<? extends Symbol> r;
		while((r=collected.poll())!=null) {
			SymbolRef ref = (SymbolRef)r;
			globals.remove(ref.key, ref);
		}
	}

	public static Symbol wrap(Symbol s) {
		return new Symbol(s);
	}

	
	
    private Symbol primitive;
    private Object description;
    private boolean global;
    private int hashCode;

    private Symbol(Symbol delegate) {
    	this.primitive = delegate.getPrimitive();
	}

	public Symbol(Object description) {
		this.description = description;
		this.hashCode = counter.getAndIncrement();
	}

	public Symbol(Object description, boolean global) {
		this.description = description;
		this.global = global;
		this.hashCode = counter.getAndIncrement();
	}
	
	public Symbol getPrimitive() {
		if(primitive!=null) {
			return primitive;
		}
		return this;
	}

	
	@Override
	public boolean equals(Object o) {
		return o==this;
	}
	
	public boolean isRegistered() {
		return getPrimitive().global;
	}
	
	@Override
	public int hashCode() {
		return getPrimitive().hashCode; 
	}

	public boolean isGlobal() {
		return global;
	}
	
	public Object getDescription() {
		return getPrimitive().description;
	}
	
	@Override
	public String toString() {
		Object d = getDescription();
		if(d==RuntimeUtil.UNDEFINED) {
			return "Symbol()";
		}
		return "Symbol("+d.toString()+")";
	}

	public String toStringDebug() {
		return toString()+"-"+hashCode;
	}
}
