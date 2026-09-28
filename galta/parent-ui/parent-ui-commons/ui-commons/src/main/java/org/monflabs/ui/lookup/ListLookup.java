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
package org.monflabs.ui.lookup;

import java.util.List;
import java.util.function.Function;

/**
 * 
 */
public class ListLookup<C,T> extends AbstractLookup<T> {
    
    private List<C> list;
    private Function<C,T> idAccessor;
    private Function<C,String> labelAccessor;

    public ListLookup(List<C> list, Function<C,T> idAccessor) {
        this(list,idAccessor,null);
    }
    public ListLookup(List<C> list, Function<C,T> idAccessor, Function<C,String> labelAccessor) {
        this.list = list;
        this.idAccessor = idAccessor;
        this.labelAccessor = labelAccessor;
    }
    
    public List<C> getList() {
    	return list;
    }

    @Override
	public int size() {
        return list!=null ? list.size() : 0;
    }

	@Override
	public T getValue(int index) {
    	if(index<0 || index>=size()) {
    		throw new IndexOutOfBoundsException(index);
    	}
    	C o = list.get(index);
    	if(o!=null) {
   			return idAccessor.apply(o);
    	}
    	return null;
    }
    
    @Override
	public String getDisplayLabel(int index) {
    	if(index<0 || index>=size()) {
    		throw new IndexOutOfBoundsException(index);
    	}
    	C o = list.get(index);
    	if(o!=null) {
    		if(labelAccessor!=null) {
    			return labelAccessor.apply(o);
    		}
    		if(idAccessor!=null) {
    			T t = idAccessor.apply(o);
    			return t!=null ? t.toString() : null;
    		}
    		return o.toString();
    	}
    	return null;
    }
}
