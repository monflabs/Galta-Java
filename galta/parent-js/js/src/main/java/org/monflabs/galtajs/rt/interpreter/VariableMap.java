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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.util.Console;
import org.monflabs.util.StringFormat;
import org.monflabs.util.iterators.Iterables;

/**
 * Variable container
 */
public class VariableMap {
	
//	private static final boolean DEBUG = true;

    private static final int   MINIMAL_SIZE = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private float loadFactor = LOAD_FACTOR;
	
	protected VariableEntry[] elementData;
	protected int elementCount;
    private int threshold;

    // Phase 2c: declaration-order slot array for direct-index reads. Populated
    // once per frame (see initSlots) at frame construction time, then aliased
    // by VariableEntryArray-backed hash entries so name-based lookup and
    // index-based lookup share the same storage. Null when the frame has no
    // slot-eligible bindings (e.g. a block/for frame or the program frame,
    // which routes var/function to globalThis).
    private Object[] slots;

    // Phase 2d: structural-mutation counter for the ASTIdentifier entry cache.
    // Bumped whenever an entry is added, replaced, or removed - i.e. anything
    // that would invalidate a cached (map,entry) pair. Plain setValue() on an
    // existing entry does not bump this (the cached entry reference is still
    // valid; the fresh value is read via entry.getValue()). Declared volatile
    // so a mutation is visible to a reader that snapshots the epoch after a
    // volatile-ref load of the cache record.
    private volatile int mutationEpoch;
    
    // IF DEBUG
//    private int regularVarAccess;
//    private int cachedVarAccess;
    // ENDIF DEBUG

	public VariableMap() {
	}
	
    // IF DEBUG
//	@Override
//	public void finalize() {
//		dumpStats();
//	}
    // ENDIF DEBUG

	// Note: map.entry is not respected as setValue() should return the new value, not the old one
	protected abstract static class VariableEntry implements VarAccessor, Map.Entry<String, Object> {
        private int hashcode;
		protected String key;
		protected VAR_TYPE type;
		protected VariableEntry next;
		// Only meaningful for VAR_TYPE.VAR/FUNCTION: true when this specific binding was
		// hoisted into an already-existing scope by a direct eval (EvalDeclarationInstantiation
		// creates such bindings as deletable, unlike the function's own declared vars/functions,
		// which always get a non-deletable binding - see BaseEvalContext.createVariable).
		protected boolean configurable;
		// See VarAccessor.isCatchParameter()'s own doc - set post-creation via
		// markCatchParameter(), not threaded through the constructor (unlike
		// configurable above), since ASTCatch.bindException() only knows to
		// mark it AFTER calling the shared createVariable() that every other
		// VAR_TYPE.LET binding also goes through.
		private boolean catchParameter;
		protected VariableEntry(int hashcode, VariableEntry next, String key, VAR_TYPE type) {
			this(hashcode, next, key, type, false);
		}
		protected VariableEntry(int hashcode, VariableEntry next, String key, VAR_TYPE type, boolean configurable) {
			this.hashcode = hashcode;
			this.key = key;
			this.type = type;
			this.next = next;
			this.configurable = configurable;
		}
        @Override
		public String toString() {
        	return StringFormat.format("[{0},{1}]",getKey(),getValue());
        }
        @Override
		public boolean equals(Object o) {
        	return this==o;
        }
        @Override
		public int hashCode() {
        	return hashcode;
        }
        @Override
		public String getKey() {
        	return key;
        }
		@Override
		public VAR_TYPE getType() {
			return type;
		}
		public abstract boolean isWrapped();
		public abstract boolean isArray();
        @Override
		public abstract Object getValue();
        @Override
		public abstract Object setValue(Object value);
        @Override
		public boolean isConfigurable() {
        	return configurable;
        }
        @Override
		public boolean isCatchParameter() {
			return catchParameter;
		}
        @Override
		public void markCatchParameter() {
			catchParameter = true;
		}
	}
	protected static class VariableEntryImpl extends VariableEntry {
		protected Object value;
		protected VariableEntryImpl(int hashcode, VariableEntry next, String key, Object value, VAR_TYPE type) {
			super(hashcode, next, key, type);
			this.value = value;
		}
		protected VariableEntryImpl(int hashcode, VariableEntry next, String key, Object value, VAR_TYPE type, boolean configurable) {
			super(hashcode, next, key, type, configurable);
			this.value = value;
		}
        @Override
		public boolean isWrapped() {
			return false;
		}
        @Override
		public boolean isArray() {
			return false;
		}
		@Override
		public Object getValue() {
			return value;
		}
		@Override
		public Object setValue(Object value) {
			return this.value = value;
		}
	}
	protected static class VariableEntryWrapped extends VariableEntry {
		protected VarAccessor wrapped;
		protected VariableEntryWrapped(int hashcode, VariableEntry next, VarAccessor wrapped) {
			super(hashcode, next, wrapped.getKey(), wrapped.getType());
			this.wrapped = wrapped;
		}
        @Override
		public boolean isWrapped() {
			return true;
		}
        @Override
		public boolean isArray() {
			return false;
		}
		@Override
		public Object getValue() {
			return wrapped.getValue();
		}
		@Override
		public Object setValue(Object value) {
			return wrapped.setValue(value);
		}
        @Override
		public boolean isConfigurable() {
        	return wrapped.isConfigurable();
        }
	}
	protected static class VariableEntryArray extends VariableEntry {
		protected Object[] array;
		protected int index;
		protected VariableEntryArray(int hashcode, VariableEntry next, String key, Object[] array, int index, VAR_TYPE type) {
			super(hashcode, next, key, type);
			this.array = array;
			this.index = index;
		}
        @Override
		public boolean isWrapped() {
			return false;
		}
        @Override
		public boolean isArray() {
			return true;
		}
		@Override
		public Object getValue() {
			return array[index];
		}
		@Override
		public Object setValue(Object value) {
			return array[index] = value;
		}
	}

	public final int size() {
		return elementCount;
	}

	// Phase 2d: cache-invalidation snapshot for ASTIdentifier's IdentIC.
	// Reader loads the cache record's epoch and compares against this value;
	// any mismatch means the record is stale.
	public final int getMutationEpoch() {
		return mutationEpoch;
	}

	public boolean isEmpty() {
		return elementCount==0;
	}

	public final void clear() {
		if(elementData!=null) {
			this.elementCount = 0;
			this.elementData = null;
			this.slots = null;
			mutationEpoch++;
		}
	}

	public boolean contains(String varName) {
		return getEntry(varName)!=null;
	}

	public Object get(String varName) {
		VariableEntry e = getEntry(varName);
		if(e==null) {
			throw RuntimeUtil.typeError("Variable {0} is not available", varName);
		}
		return e.getValue();
	}
	public Object get(String varName, Object defaultValue) {
		VariableEntry e = getEntry(varName);
		return e!=null ? e.getValue() : defaultValue;
	}
	public Object getInScope(String varName, Object defaultValue) {
		VariableEntry e = getEntry(varName);
		return e!=null && !e.isWrapped() ? e.getValue() : defaultValue;
	}
	
	// Phase 2d: public typed getter used by the ASTIdentifier IdentIC fast path;
	// callers outside this package cannot name the protected VariableEntry type.
	public VarAccessor getAccessor(String varName) {
		return getEntry(varName);
	}

	public VariableEntry getEntry(String varName) {
		if(elementCount>0) {
			int hashcode;
	        int slot = (hashcode=hash(varName)) & elementData.length-1; // & works when power 2 (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
			for(VariableEntry e=elementData[slot]; e!=null; e=e.next) {
				// Phase 1b: identifier ids are interned (ASTIdentifier.java:46),
				// so `==` short-circuits `.equals()` for the common case.
				if(e.key==varName) {
					return e;
				}
				if(e.hashcode==hashcode && e.key.equals(varName)) {
					return e;
				}
			}
		}
		return null;
	}
	
	public boolean set(String varName, Object value) {
		VariableEntry e = getEntry(varName);
		if(e!=null) {
			e.setValue(value);
			return true;
		}
		return false;
	}

	public boolean delete(String varName) {
		if(elementCount>0) {
			int hashcode;
	        int slot = (hashcode=hash(varName)) & elementData.length-1; // & works when power 2 (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
			for(VariableEntry e=elementData[slot]; e!=null; e=e.next) {
				if(e.key==varName || (e.hashcode==hashcode && e.key.equals(varName))) {
					delete(slot, e);
					mutationEpoch++;
					return !e.isWrapped();
				}
			}
		}
		return false;
	}
	private void delete(int slot, VariableEntry toRemove) {
		VariableEntry prev = null;
		for(VariableEntry e=elementData[slot]; e!=null; e=e.next) {
			if(e==toRemove) {
				if(prev==null) {
					elementData[slot] = e.next;
				} else {
					prev.next = e.next;
				}
				elementCount--;
				return;
			}
			prev = e;
		}
	}


	// This is for dump, so we don't need a 'virtual' set
	public final Iterable<VarAccessor> entries() {
		if(elementCount>0) {
			List<VarAccessor> res = new ArrayList<>();
			for(int i=0; i<elementData.length; i++) {
				for(VariableEntry e=elementData[i]; e!=null; e=e.next) {
					res.add(e);
				}
			}
			return res;
		}
		return Iterables.empty();
	}
	public final Set<String> keySet() {
		if(elementCount>0) {
			Set<String> res = new HashSet<>();
			for(int i=0; i<elementData.length; i++) {
				for(VariableEntry e=elementData[i]; e!=null; e=e.next) {
					res.add(e.key);
				}
			}
			return res;
		}
		return Collections.emptySet();
	}
	public final Collection<Object> values() {
		if(elementCount>0) {
			List<Object> res = new ArrayList<>();
			for(int i=0; i<elementData.length; i++) {
				for(VariableEntry e=elementData[i]; e!=null; e=e.next) {
					res.add(e.getValue());
				}
			}
			return res;
		}
		return Collections.emptyList();
	}

	public VarAccessor createVariable(String varName, Object value, VAR_TYPE type) {
		return createVariable(varName, value, type, false);
	}
	public VarAccessor createVariable(String varName, Object value, VAR_TYPE type, boolean configurable) {
		if(elementData==null || elementCount>=threshold) {
			rehash(elementCount+1);
		}
		int hashcode;
        int slot = (hashcode=hash(varName)) & elementData.length-1; // & works when power 2 (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
		for(VariableEntry e=elementData[slot]; e!=null; e=e.next) {
			if(e.key==varName || (e.hashcode==hashcode && e.key.equals(varName)) ) {
				if(e.isWrapped()) {
					// Remove the wrapper and add the new variable
					delete(slot, e);
					break;
				} else if(e.isArray()) {
					// Phase 2c: preserve the slot alias. Before Phase 2c the
					// existing behavior was to delete+recreate as a plain
					// VariableEntryImpl - this severed the alias between the
					// hash-view and the slot storage, so a subsequent
					// getSlot(idx) fast-path read would return the pre-init
					// UNDEFINED instead of the freshly-written value. Now that
					// hoisted var/function bindings are alias-backed from
					// bindParametersAndVars, a var re-declaration (destructuring
					// initializer, plain `var x = 1` after hoisting, etc.) must
					// write THROUGH the alias so both views stay in sync.
					if(!type.canOverride() || !e.type.canBeOverriden()) {
						throw RuntimeUtil.error("A variable {0} is already defined in the context", varName);
					}
					if(value!=RuntimeUtil.NOT_AVAILABLE) {
						e.setValue(value);
					}
					return e;
				} else {
					if(!type.canOverride() || !e.type.canBeOverriden()) {
						throw RuntimeUtil.error("A variable {0} is already defined in the context", varName);
					}
					if(value!=RuntimeUtil.NOT_AVAILABLE) {
						e.setValue(value);
					}
					return e;
				}
			}
		}
		elementData[slot] = new VariableEntryImpl(hashcode,elementData[slot],varName,value!=RuntimeUtil.NOT_AVAILABLE?value:RuntimeUtil.UNDEFINED,type,configurable);
		elementCount++;
		mutationEpoch++;
		return elementData[slot];
	}

	// This must be the called on an empty map, before any other addition
	// It doesn't check
	public VarAccessor initVariable(String varName, Object[] array, int index, VAR_TYPE type) {
		if(elementData==null || array.length>=threshold) {
			rehash(array.length+1);
		}
		int hashcode;
        int slot = (hashcode=hash(varName)) & elementData.length-1; // & works when power 2 (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
		elementData[slot] = new VariableEntryArray(hashcode,elementData[slot],varName,array,index,type);
		elementCount++;
		mutationEpoch++;
		return elementData[slot];
	}

	// Phase 2c: allocate this frame's declaration-order slot array, pre-filled
	// with UNDEFINED (so a slot that hasn't yet been bound - e.g. a `var` decl
	// whose initializer hasn't run yet - reads as undefined, matching the
	// pre-slots hash-lookup behavior). Called once per frame; safe on an empty
	// map, no-op on a repeat call.
	public void initSlots(int size) {
		if(slots!=null) {
			return;
		}
		Object[] a = new Object[size];
		for(int i=0; i<size; i++) {
			a[i] = RuntimeUtil.UNDEFINED;
		}
		slots = a;
	}

	// Phase 2c: create a hash-entry aliased to this frame's slot array. Later
	// getEntry(name) returns the alias entry (so name-based lookup and slot-based
	// lookup share storage); getSlot(idx) returns the raw slot value directly,
	// skipping the hash probe.
	public VarAccessor initVariableInSlot(String varName, int index, VAR_TYPE type) {
		if(slots==null) {
			throw RuntimeUtil.illegalState();
		}
		return initVariable(varName, slots, index, type);
	}

	// Phase 2c: direct slot read; caller MUST have verified slots!=null (via
	// hasSlots()) before invoking. Falls out of range only if the caller wired
	// a wrong slot index, which is a codegen bug, not a runtime condition.
	public Object getSlot(int index) {
		return slots[index];
	}

	// Phase 2c: direct slot write. Same preconditions as getSlot.
	public void setSlot(int index, Object value) {
		slots[index] = value;
	}

	public boolean hasSlots() {
		return slots!=null;
	}

	public void cache(VarAccessor var) {
		//if(getEntry(var.getKey())!=null) {
		//	throw new IllegalStateException("Cannot cache an existing variable "+var.getKey());
		//}
		if(elementData==null || elementCount>=threshold) {
			rehash(elementCount+1);
		}
		String varName = var.getKey();
		int hashcode;
        int slot = (hashcode=hash(varName)) & elementData.length-1; // & works when power 2 (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
		elementData[slot] = new VariableEntryWrapped(hashcode, elementData[slot], var);
		elementCount++;
		mutationEpoch++;
	}

	
    //
    // Managing the EntryImpl list
    //
    private final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }
    private void rehash(int newSize) {
    	int length = Math.max( MINIMAL_SIZE, powerOfTwo((int)(newSize/loadFactor)) );
    	VariableEntry[] newData = new VariableEntry[length];
        if(elementCount>0) {
        	for(int i=0; i<elementData.length; i++) {
                for(VariableEntry e=elementData[i]; e!=null; ) {
                	VariableEntry next = e.next;
                    int index = e.hashcode & length-1; // & works when power 2 (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
                    e.next = newData[index];
                    newData[index] = e;
                    e = next;
                }
        	}
        }
        elementData = newData;
    	this.threshold = (int)(length * loadFactor);
    }
    private int powerOfTwo(int number) {
    	return 1 << (32 - Integer.numberOfLeadingZeros(number - 1));
    }
    
    // Dump Stats
    public void dumpStats() {
    	int total = 0;
    	int min = 0;
    	int max = 0;
    	double avg = 0;
    	int count = elementData.length;
    	int cachedCount = 0;
    	StringBuilder rv = new StringBuilder();
    	StringBuilder cv = new StringBuilder();
    	if(count>0) {
        	min = Integer.MAX_VALUE;
        	max = Integer.MIN_VALUE;
	    	for(int i=0; i<count; i++) {
	    		int c=0;
	    		for(VariableEntry e=elementData[i]; e!=null; e=e.next) {
	    			c++;
	    			if(e instanceof VariableEntryWrapped) {
	    				cachedCount++;
	    				if(!rv.isEmpty()) rv.append(", ");
	    				rv.append(e.getKey());
	    			} else {
	    				if(!cv.isEmpty()) cv.append(", ");
	    				cv.append(e.getKey());
	    			}
	    		}
	    		min = Math.min(min,c);
	    		max = Math.max(max,c);
	    		total += c;
	    	}
	    	avg = ((double)total) / count;
    	}
    	if(cachedCount==0) {
    		return;
		}
    	Console.log("Map count={0}", elementCount);
    	Console.log("  Total entries={0}", total);
    	Console.log("  Cached entries={0}", cachedCount);
    	Console.log("  Map slots={0}", elementData.length);
    	Console.log("  Avg slot={0}", avg);
    	Console.log("  Min slot={0}", min);
    	Console.log("  Max slot={0}", max);
    	// IF DEBUG
//    	Console.log("  Reg var={0} [{1}]", regularVarAccess, rv);
//    	Console.log("  Cached vars={0} [{1}]", cachedVarAccess, cv);
    	// ENDIF DEBUG
    }
	
}
