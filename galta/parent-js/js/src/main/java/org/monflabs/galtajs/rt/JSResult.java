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
package org.monflabs.galtajs.rt;

import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.util.iterators.Iterators;

/**List
 * Expression Result.
 * Can carry a value or a reference
 */
public final class JSResult implements Iterable<Object>{
	
	private static enum SEQ_TYPE {
		// For member node with nullop
		CHAINING_NULL,
		// These contain references to 0-n entries
		SEQ_EMPTY,
		SEQ_ONE,
		SEQ_MULTI
		;
		SEQ_TYPE() {
		}
	}	

	private SEQ_TYPE valueType;
	private Object value;
	
	public JSResult() {
	}
	
	public JSResult(Object value) {
		this.value = value;
	}

	private JSResult(JSResult from) {
		this.valueType = from.valueType;
		this.value = from.value;
	}
	
	public void copyFrom(JSResult from) {
		this.valueType = from.valueType;
		this.value = from.value;
	}
	
	@Override
	public Iterator<Object> iterator() {
		if(valueType==null) {
			return Iterators.single(value);
		} else {
			switch(valueType) {
				case CHAINING_NULL -> {
					return Iterators.empty();
				}
				case SEQ_EMPTY -> {
					return Iterators.empty();
				}
				case SEQ_ONE -> {
					return Iterators.single(value);
				}
				case SEQ_MULTI -> {
					return ((JSArray)value).arrayIterator();
				}
			}
		}
		throw new IllegalStateException();
	}

	public boolean isUndefined() {
		return valueType==null && value==RuntimeUtil.UNDEFINED;
	}

	public boolean isSequence() {
		return valueType!=null;
	}

	public boolean isChainingNull() {
		return valueType==SEQ_TYPE.CHAINING_NULL;
	}
	
	public Object getValue() {
		if(JSEnvironment.CHECK_FOR_DEBUG) {
			if(valueType!=null && valueType!=SEQ_TYPE.CHAINING_NULL) {
				throw RuntimeUtil.error("Result access error: Cannot access 'value' for a sequence");
			}
		}
		return value;
	}
			
	public int size() {
		if(valueType==null) {
			return 1;
		} else {
			switch(valueType) {
				case CHAINING_NULL -> {
					return 0;
				}
				case SEQ_EMPTY -> {
					return 0;
				}
				case SEQ_ONE -> {
					return 1;
				}
				case SEQ_MULTI -> {
					return (int)((JSArray)value).arrayLength();
				}
			}
		}
		throw new IllegalStateException();
	}
	
	public Object get(int index) {
		if(JSEnvironment.CHECK_FOR_DEBUG) {
			if(index<0 || index>=size()) {
				throw RuntimeUtil.error("Result access error: index {0}, size {1}", index, size());
			}
		}
		if(valueType==null) {
			return value;
		} else {
			switch(valueType) {
				case CHAINING_NULL -> {
					return null;
				}
				case SEQ_EMPTY -> {
					return RuntimeUtil.UNDEFINED;
				}
				case SEQ_ONE -> {
					return value;
				}
				case SEQ_MULTI -> {
					return ((JSArray)value).getProperty(index);
				}
			}
		}
		throw new IllegalStateException();
	}
	
	public Object deref() {
		return value;
	}

	public JSArray derefArray(JSArray array) {
		if(valueType==null) {
			array.arrayAdd(value);
		} else {
			switch(valueType) {
				case CHAINING_NULL -> {
				}
				case SEQ_EMPTY -> {
				}
				case SEQ_ONE -> {
					array.arrayAdd(value);
				}
				case SEQ_MULTI -> {
					JSArray a = (JSArray)value;
					array.arrayAddAll(a);
				}
			}
		}
		return array;
	}
	public List<Object> derefList(List<Object> list) {
		if(valueType==null) {
			list.add(value);
		} else {
			switch(valueType) {
				case CHAINING_NULL -> {
				}
				case SEQ_EMPTY -> {
				}
				case SEQ_ONE -> {
					list.add(value);
				}
				case SEQ_MULTI -> {
					JSArray a = (JSArray)value;
					for(Iterator<Object> it=a.arrayIterator(); it.hasNext(); ) {
						Object v=it.next();
						list.add(v);
					}
				}
			}
		}
		return list;
	}
	public JSArray derefAsArray(JSEnvironment env) {
		if(valueType==null) {
			JSArray a = JSArray.create(env);
			a.arrayAdd(value);
			return a;
		} else {
			switch(valueType) {
				case CHAINING_NULL -> {
					return JSArray.create(env,0);
				}
				case SEQ_EMPTY -> {
					return JSArray.create(env,0);
				}
				case SEQ_ONE -> {
					JSArray a = JSArray.create(env,1);
					a.arrayAdd(value);
					return a;
				}
				case SEQ_MULTI -> {
					JSArray values = (JSArray)value;
					return values;
				}
			}
			throw new IllegalStateException();
		}
	}
	
	public void setNull() {
		this.valueType = null;
		this.value = null;
	}
	
	public void setUndefined() {
		this.valueType = null;
		this.value = RuntimeUtil.UNDEFINED;
	}
	
	public void setChainingNull() {
		this.valueType = SEQ_TYPE.CHAINING_NULL;
		this.value = RuntimeUtil.UNDEFINED;
	}

	public void setValue(Object value) {
		this.valueType = null;
		this.value = value;
	}
	
	public void initSequence() {
		this.valueType = SEQ_TYPE.SEQ_EMPTY;
		this.value = RuntimeUtil.UNDEFINED;
	}
	
	public void convertToSequence() {
		if(valueType==null) {
			// The single value becomes the one element of the sequence
			this.valueType = SEQ_TYPE.SEQ_ONE;
		}
	}
	
	public void addToSequence(JSEnvironment env, Object value) {
		if(valueType==null) {
			throw new IllegalStateException();
		} else {
			switch(valueType) {
				case CHAINING_NULL -> {
					throw new IllegalStateException();
				}
				case SEQ_EMPTY -> {
					this.valueType = SEQ_TYPE.SEQ_ONE;
					this.value = value;
				}
				case SEQ_ONE -> {
					this.valueType = SEQ_TYPE.SEQ_MULTI;
					JSArray a = JSArray.create(env);
					a.arrayAdd(this.value);
					a.arrayAdd(value);
					this.value = a;
				}
				case SEQ_MULTI -> {
					JSArray a = (JSArray)this.value;
					a.arrayAdd(value);
				}
			}
		}
	}
	
	public JSResult ejectAndSequence() {
		JSResult r = new JSResult(this);
		this.valueType = SEQ_TYPE.SEQ_EMPTY;
		this.value = RuntimeUtil.UNDEFINED;
		return r;
	}

	public void reduceToSequence(BiConsumer<Object,JSResult> cb) {
		if(valueType==null) {
			Object value = this.value;
			initSequence();
			cb.accept(value,this);
		} else {
			switch(valueType) {
				case CHAINING_NULL -> {
					// nothing...
				}
				case SEQ_EMPTY -> {
					// initSequence(); // Not necessary
				} 
				case SEQ_ONE -> {
					Object value = this.value;
					initSequence();
					cb.accept(value,this);
				} 
				case SEQ_MULTI -> {
					JSArray a = (JSArray)this.value;
					initSequence();
					int sz = (int)a.arrayLength();
					for(int i=0; i<sz; i++) {
						cb.accept(a.getProperty(i),this);
					}
				} 
			}
		}
	}


	@Override
	public void forEach(Consumer<Object> cb) {
		if(valueType==null) {
			cb.accept(value);
		} else {
			switch(valueType) {
				case CHAINING_NULL -> {
					// nothing...
				}
				case SEQ_EMPTY -> {
				} 
				case SEQ_ONE -> {
					cb.accept(value);
				} 
				case SEQ_MULTI -> {
					JSArray a = (JSArray)this.value;
					int sz = (int)a.arrayLength();
					for(int i=0; i<sz; i++) {
						cb.accept(a.getProperty(i));
					}
				} 
			}
		}
	}
	
	public void map(JSEnvironment env, Function<Object,Object> cb) {
		if(valueType==null) {
			setValue(cb.apply(value));
		} else {
			switch(valueType) {
				case CHAINING_NULL -> {
					// nothing...
				}
				case SEQ_EMPTY -> {
					// initSequence(); // Not necessary
				} 
				case SEQ_ONE -> {
					Object value = this.value;
					initSequence();
					addToSequence(env,cb.apply(value));
				} 
				case SEQ_MULTI -> {
					JSArray a = (JSArray)this.value;
					initSequence();
					int sz = (int)a.arrayLength();
					for(int i=0; i<sz; i++) {
						addToSequence(env,cb.apply(a.getProperty(i)));
					}
				}
			}
		}
	}
}
