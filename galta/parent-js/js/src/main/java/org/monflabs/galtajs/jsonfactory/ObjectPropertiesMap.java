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

import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;

/**
 * Specialized map to store Object properties (String or Symbol).
 * Allows the use of property descriptors to manage the access.
 */
public abstract class ObjectPropertiesMap<T> extends CustomLinkedMap<T> {
	
    public static final int FLAG_SEALED				= 0x0001;
	public static final int FLAG_FROZEN				= 0x0002;
	public static final int FLAG_PREVENTEXTENSION	= 0x0004;

	private int flags;

    public ObjectPropertiesMap() {
    }
    
	public int getObjectFlags() {
		return flags;
	}
	public void setObjectFlags(int flags) {
		this.flags = flags;
	}
	
	public final boolean isSealed() {
		int flags = getObjectFlags();
		return flags!=0 && (flags&FLAG_SEALED)!=0;
	}
	public final boolean isFrozen() {
		int flags = getObjectFlags();
		return flags!=0 && (flags&FLAG_FROZEN)!=0;
	}
	public final boolean isExtensible() {
		int flags = getObjectFlags();
		return flags==0 || (flags&(FLAG_PREVENTEXTENSION|FLAG_SEALED|FLAG_FROZEN))==0;
	}
	
	public void seal() {
		setObjectFlags(getObjectFlags() | FLAG_SEALED);
		// See CustomLinkedMap.updateAllPropertyDescriptors's doc comment -
		// seal only touches [[Configurable]], leaving [[Writable]] (and any
		// getter/setter) as-is.
		updateAllPropertyDescriptors(d -> d.isConfigurable()
				? (d.isAccessor()
					? PropertyDescriptor.of(d.isWritable(), false, d.isEnumerable(), d.getGetter(), d.getSetter())
					: PropertyDescriptor.of(d.isWritable(), false, d.isEnumerable()))
				: null);
	}
	public void freeze() {
		setObjectFlags(getObjectFlags() | FLAG_FROZEN);
		// See CustomLinkedMap.updateAllPropertyDescriptors's doc comment -
		// freeze also forces [[Writable]] false for a DATA property (an
		// accessor property has no such concept - only its
		// [[Configurable]] changes, matching seal).
		updateAllPropertyDescriptors(d -> {
			boolean needsConfig = d.isConfigurable();
			boolean needsWritable = d.isData() && d.isWritable();
			if(!needsConfig && !needsWritable) {
				return null;
			}
			return d.isAccessor()
					? PropertyDescriptor.of(d.isWritable(), false, d.isEnumerable(), d.getGetter(), d.getSetter())
					: PropertyDescriptor.of(false, false, d.isEnumerable());
		});
	}
	public boolean preventExtensions() {
		setObjectFlags(getObjectFlags() | FLAG_PREVENTEXTENSION);
		return true;
	}

	@Override
	public boolean canAddEntry() {
		return (getObjectFlags()&(FLAG_PREVENTEXTENSION|FLAG_SEALED|FLAG_FROZEN))==0;
	}
	@Override
	public boolean canUpdateEntry() {
		return (getObjectFlags()&(FLAG_FROZEN))==0;
	}
	@Override
	public boolean canRemoveEntry() {
		return (getObjectFlags()&(FLAG_SEALED|FLAG_FROZEN))==0;
	}
	
	@Override
	protected int _hash(Object key) {
		return key.hashCode();
	}

	@Override
	protected boolean equalsKey(Object o1, Object o2) {
		if (o1 == o2) {
			return true;
		}
		if (o1 == null || o2 == null) {
			return false;
		}
		return o1.equals(o2);
	}

	@Override
	protected boolean equalsValue(Object o1, Object o2) {
		if (o1 == o2) {
			return true;
		}
		if (o1 == null || o2 == null) {
			return false;
		}
		return o1.equals(o2);
	}
}
