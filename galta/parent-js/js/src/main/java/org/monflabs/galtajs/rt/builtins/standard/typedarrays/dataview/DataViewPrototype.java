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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.dataview;

import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.bigint.BuiltinBigIntConstructor;
import org.monflabs.util.TypeUtil;

public class DataViewPrototype extends BasePrototype {

	public static DataViewPrototype get(JSEnvironment env) {
		DataViewPrototype proto = (DataViewPrototype)env.getRegisteredPrototype(DataViewPrototype.class);
		if(proto==null) {
			proto = new DataViewPrototype(env);
			env.registerPrototype(DataViewPrototype.class,proto);
		}
		return proto;
	}

	private DataViewPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,DataViewConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnProperty("buffer",true,false,
				(t,k) -> {
					if(t instanceof DataView v) {
						return v.getArrayBuffer();
					}
			    	throw RuntimeUtil.typeError("Property DataView.buffer called on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null
			);
		setOwnProperty("byteLength",true,false,
				(t,k) -> {
					if(t instanceof DataView ab) {
						return ab.getByteLength();
					}
			    	throw RuntimeUtil.typeError("Property DataView.byteLength called on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null
			);
		setOwnProperty("byteOffset",true,false,
				(t,k) -> {
					if(t instanceof DataView ab) {
						return ab.getCheckedByteOffset();
					}
			    	throw RuntimeUtil.typeError("Property DataView.byteOffset called on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null
			);

		setOwnMethod(new Method(env,MethodId.getBigInt64,1));
		setOwnMethod(new Method(env,MethodId.getBigUint64,1));
		if(env.supportFloat16Array()) {
			setOwnMethod(new Method(env,MethodId.getFloat16,1));
		}
		setOwnMethod(new Method(env,MethodId.getFloat32,1));
		setOwnMethod(new Method(env,MethodId.getFloat64,1));
		setOwnMethod(new Method(env,MethodId.getInt8,1));
		setOwnMethod(new Method(env,MethodId.getInt16,1));
		setOwnMethod(new Method(env,MethodId.getInt32,1));
		setOwnMethod(new Method(env,MethodId.getUint8,1));
		setOwnMethod(new Method(env,MethodId.getUint16,1));
		setOwnMethod(new Method(env,MethodId.getUint32,1));

		setOwnMethod(new Method(env,MethodId.setBigInt64,2));
		setOwnMethod(new Method(env,MethodId.setBigUint64,2));
		if(env.supportFloat16Array()) {
			setOwnMethod(new Method(env,MethodId.setFloat16,2));
		}
		setOwnMethod(new Method(env,MethodId.setFloat32,2));
		setOwnMethod(new Method(env,MethodId.setFloat64,2));
		setOwnMethod(new Method(env,MethodId.setInt8,2));
		setOwnMethod(new Method(env,MethodId.setInt16,2));
		setOwnMethod(new Method(env,MethodId.setInt32,2));
		setOwnMethod(new Method(env,MethodId.setUint8,2));
		setOwnMethod(new Method(env,MethodId.setUint16,2));
		setOwnMethod(new Method(env,MethodId.setUint32,2));

	}

	@Override
	public String getClassName() {
		return DataViewConstructor.CLASSNAME;
	}

	private static enum MethodId {
		getBigInt64,
		getBigUint64,
		getFloat16,
		getFloat32,
		getFloat64,
		getInt8,
		getInt16,
		getInt32,
		getUint8,
		getUint16,
		getUint32,

		setBigInt64,
		setBigUint64,
		setFloat16,
		setFloat32,
		setFloat64,
		setInt8,
		setInt16,
		setInt32,
		setUint8,
		setUint16,
		setUint32,
		;
		Object id;
		MethodId() {
			this.id = name();
		}
		MethodId(Symbol id) {
			this.id = id;
		}
	}


	private final static class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}

	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	    	if(!(obj instanceof DataView)) {
	    		throw RuntimeUtil.typeError("Method DataView.prototype.{0} called on incompatible receiver {1}", methodId.toString(), RuntimeUtil.objectTypeName(getEnvironment(),obj));
	    	}

	    	// Current Object
			final DataView _this = (DataView)obj;
			final JSEnvironment env = getEnvironment();

			// GetViewValue/SetViewValue's requestIndex conversion (ToIndex) is
			// always the FIRST thing to happen - before the out-of-bounds/
			// detached check, the element range check, and (for a set method)
			// even before the value conversion.
			// SetViewValue's own IsImmutableBuffer check is step 3, BEFORE
			// even ToIndex(requestIndex) (step 4) - unlike transfer()/
			// transferToFixedLength(), where the immutable check comes
			// AFTER reading newLength. Confirmed via DataView's own
			// immutable-buffer.js tests (e.g. setUint32/immutable-buffer.js:
			// "Must verify mutability before reading arguments" - neither
			// byteOffset nor value's valueOf() may be called at all).
			if(methodId.name().startsWith("set") && _this.getArrayBuffer().isImmutable()) {
				throw RuntimeUtil.typeError("Cannot write to a DataView backed by an immutable ArrayBuffer");
			}

			long getIndex = RuntimeUtil.toIndex(env, param(args, 0, RuntimeUtil.UNDEFINED));

	    	switch(methodId) {

	    		case getBigInt64-> {
	    			boolean littleIndian = paramBoolean(args, 1, false);
	    			int index = checkedBufferIndex(_this, getIndex, 8);
	    			return _this.getArrayBuffer().readBigInt64(index,littleIndian);
	    		}
	    		case getBigUint64-> {
	    			boolean littleIndian = paramBoolean(args, 1, false);
	    			int index = checkedBufferIndex(_this, getIndex, 8);
	    			return _this.getArrayBuffer().readBigUint64(index,littleIndian);
	    		}
	    		case getFloat16-> {
	    			boolean littleIndian = paramBoolean(args, 1, false);
	    			int index = checkedBufferIndex(_this, getIndex, 2);
	    			// Widen to double/Double: GaltaJS represents every JS Number
	    			// as Double (or Integer/Long/BigInteger/BigDecimal) - a bare
	    			// Float leaks past that invariant and silently breaks NaN
	    			// identity checks (===/SameValue special-case NaN only for
	    			// Double, e.g. RuntimeUtil.eqStrict).
	    			return (double)_this.getArrayBuffer().readFloat16(index,littleIndian);
	    		}
	    		case getFloat32-> {
	    			boolean littleIndian = paramBoolean(args, 1, false);
	    			int index = checkedBufferIndex(_this, getIndex, 4);
	    			return (double)_this.getArrayBuffer().readFloat32(index,littleIndian);
	    		}
	    		case getFloat64-> {
	    			boolean littleIndian = paramBoolean(args, 1, false);
	    			int index = checkedBufferIndex(_this, getIndex, 8);
	    			return _this.getArrayBuffer().readFloat64(index,littleIndian);
	    		}
	    		case getInt8-> {
	    			int index = checkedBufferIndex(_this, getIndex, 1);
	    			return _this.getArrayBuffer().readInt8(index);
	    		}
	    		case getInt16-> {
	    			boolean littleIndian = paramBoolean(args, 1, false);
	    			int index = checkedBufferIndex(_this, getIndex, 2);
	    			return _this.getArrayBuffer().readInt16(index,littleIndian);
	    		}
	    		case getInt32-> {
	    			boolean littleIndian = paramBoolean(args, 1, false);
	    			int index = checkedBufferIndex(_this, getIndex, 4);
	    			return _this.getArrayBuffer().readInt32(index,littleIndian);
	    		}
	    		case getUint8-> {
	    			int index = checkedBufferIndex(_this, getIndex, 1);
	    			return _this.getArrayBuffer().readUint8(index);
	    		}
	    		case getUint16-> {
	    			boolean littleIndian = paramBoolean(args, 1, false);
	    			int index = checkedBufferIndex(_this, getIndex, 2);
	    			return _this.getArrayBuffer().readUint16(index,littleIndian);
	    		}
	    		case getUint32-> {
	    			boolean littleIndian = paramBoolean(args, 1, false);
	    			int index = checkedBufferIndex(_this, getIndex, 4);
	    			return _this.getArrayBuffer().readUint32(index,littleIndian);
	    		}

	    		case setBigInt64-> {
	    			BigInteger value = toBigIntValue(env, param(args, 1, RuntimeUtil.UNDEFINED));
	    			boolean littleIndian = paramBoolean(args, 2, false);
	    			int index = checkedBufferIndex(_this, getIndex, 8);
	    			_this.getArrayBuffer().writeBigInt64(index,value,littleIndian);
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case setBigUint64-> {
	    			BigInteger value = toBigIntValue(env, param(args, 1, RuntimeUtil.UNDEFINED));
	    			boolean littleIndian = paramBoolean(args, 2, false);
	    			int index = checkedBufferIndex(_this, getIndex, 8);
	    			_this.getArrayBuffer().writeBigUint64(index,value,littleIndian);
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case setFloat16-> {
	    			double value = RuntimeUtil.toDouble(env, param(args, 1, RuntimeUtil.UNDEFINED));
	    			boolean littleIndian = paramBoolean(args, 2, false);
	    			int index = checkedBufferIndex(_this, getIndex, 2);
	    			_this.getArrayBuffer().writeFloat16(index,value,littleIndian);
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case setFloat32-> {
	    			float value = TypeUtil.toFloat(RuntimeUtil.toNumber(env, param(args, 1, RuntimeUtil.UNDEFINED)));
	    			boolean littleIndian = paramBoolean(args, 2, false);
	    			int index = checkedBufferIndex(_this, getIndex, 4);
	    			_this.getArrayBuffer().writeFloat32(index,value,littleIndian);
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case setFloat64-> {
	    			double value = RuntimeUtil.toDouble(env, param(args, 1, RuntimeUtil.UNDEFINED));
	    			boolean littleIndian = paramBoolean(args, 2, false);
	    			int index = checkedBufferIndex(_this, getIndex, 8);
	    			_this.getArrayBuffer().writeFloat64(index,value,littleIndian);
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case setInt8-> {
	    			byte value = RuntimeUtil.toInt8(env, param(args, 1, RuntimeUtil.UNDEFINED));
	    			int index = checkedBufferIndex(_this, getIndex, 1);
	    			_this.getArrayBuffer().writeInt8(index,value);
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case setInt16-> {
	    			short value = RuntimeUtil.toInt16(env, param(args, 1, RuntimeUtil.UNDEFINED));
	    			boolean littleIndian = paramBoolean(args, 2, false);
	    			int index = checkedBufferIndex(_this, getIndex, 2);
	    			_this.getArrayBuffer().writeInt16(index,value,littleIndian);
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case setInt32-> {
	    			int value = RuntimeUtil.toInt32(env, param(args, 1, RuntimeUtil.UNDEFINED));
	    			boolean littleIndian = paramBoolean(args, 2, false);
	    			int index = checkedBufferIndex(_this, getIndex, 4);
	    			_this.getArrayBuffer().writeInt32(index,value,littleIndian);
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case setUint8-> {
	    			short value = RuntimeUtil.toUInt8(env, param(args, 1, RuntimeUtil.UNDEFINED));
	    			int index = checkedBufferIndex(_this, getIndex, 1);
	    			_this.getArrayBuffer().writeUint8(index,value);
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case setUint16-> {
	    			int value = RuntimeUtil.toUInt16(env, param(args, 1, RuntimeUtil.UNDEFINED));
	    			boolean littleIndian = paramBoolean(args, 2, false);
	    			int index = checkedBufferIndex(_this, getIndex, 2);
	    			_this.getArrayBuffer().writeUint16(index,value,littleIndian);
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case setUint32-> {
	    			long value = RuntimeUtil.toUInt32(env, param(args, 1, RuntimeUtil.UNDEFINED));
	    			boolean littleIndian = paramBoolean(args, 2, false);
	    			int index = checkedBufferIndex(_this, getIndex, 4);
	    			_this.getArrayBuffer().writeUint32(index,value,littleIndian);
	    			return RuntimeUtil.UNDEFINED;
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }

	    // IsViewOutOfBounds (detached, or shrunk-below-view for a resizable
	    // buffer) is checked AFTER ToIndex/value-conversion but BEFORE the
	    // element-vs-viewSize range check - both per GetViewValue/SetViewValue's
	    // exact step ordering (test262 exercises this ordering explicitly).
	    private int checkedBufferIndex(DataView _this, long getIndex, int elementSize) {
	    	if(_this.isOutOfBounds()) {
	    		throw RuntimeUtil.typeError("DataView is out of bounds of its buffer");
	    	}
	    	int viewSize = _this.getViewByteLength();
	    	if(getIndex+elementSize>viewSize) {
	    		throw RuntimeUtil.rangeError("Invalid index {0}", getIndex);
	    	}
	    	return _this.getByteOffset()+(int)getIndex;
	    }

	    // ToBigInt(value): the argument must already be a primitive (ToPrimitive
	    // applied first), matching ToBigInt's own precondition.
	    private BigInteger toBigIntValue(JSEnvironment env, Object value) {
	    	Object prim = RuntimeUtil.toPrimitive(env, value, RuntimeUtil.HINT.NUMBER);
	    	return BuiltinBigIntConstructor.toBigInt(env, prim);
	    }
	}
}
