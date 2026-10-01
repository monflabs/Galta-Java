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
package org.monflabs.util;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Chained list of properties.
 */ 
public class Properties {
	
	public static Properties of(Object...args) {
		Properties p = new Properties();
		for(int i=0; i<args.length; i+=2) {
			p.put((String)args[i],args[i+1]);
		}
		return p;
	}

	private Properties parent;
	private Map<String,Object> props;
	
	public Properties() {
		this(null);
	}
	
	public Properties(Properties parent) {
		this(parent,null);
	}
	
	public Properties(Properties parent, Map<String,Object> initial) {
		this.parent = parent;
		this.props = new HashMap<String, Object>();
		if(initial!=null) {
			this.props.putAll(initial);
		}
	}
	
	public Properties getParent() {
		return parent;
	}
	
	public Map<String,Object> getPropertyMap() {
		return props;
	}

	public boolean has(String name) {
		if(props.containsKey(name)) {
			return true;
		}
		if(parent!=null) {
			return parent.has(name);
		}
		return false;
	}

	public Object get(String name) {
		return get(name,null);
	}
	public Object get(String name, Object defaultValue) {
		if(props.containsKey(name)) {
			return props.get(name);
		} else if(parent!=null) {
			return parent.get(name,defaultValue);
		}
		return defaultValue;
	}
	
	
	public int getInt(String name) {
		return getInt(name,0);
	}
	public int getInt(String name, int defaultValue) {
		if(props.containsKey(name)) {
			Object o = props.get(name);
			if(o instanceof Number n) {
				return n.intValue();
			}
			if(o instanceof String s) {
				return Integer.parseInt(s);
			}
		} else if(parent!=null) {
			return parent.getInt(name,defaultValue);
		}
		return defaultValue;
	}
	
	
	public long getLong(String name) {
		return getLong(name,0);
	}
	public long getLong(String name, long defaultValue) {
		if(props.containsKey(name)) {
			Object o = props.get(name);
			if(o instanceof String s) {
				return Long.parseLong(s);
			}
			if(o instanceof Number n) {
				return n.longValue();
			}
		} else if(parent!=null) {
			return parent.getLong(name,defaultValue);
		}
		return defaultValue;
	}
	
	
	public boolean getBoolean(String name) {
		return getBoolean(name,false);
	}
	public boolean getBoolean(String name, boolean defaultValue) {
		if(props.containsKey(name)) {
			Object o = props.get(name);
			if(o instanceof String s) {
				return Boolean.parseBoolean(s);
			}
			if(o instanceof Boolean b) {
				return b;
			}
			if(o instanceof Number n) {
				return n.intValue()!=0;
			}
		} else if(parent!=null) {
			return parent.getBoolean(name,defaultValue);
		}
		return defaultValue;
	}
	
	
	public String getString(String name) {
		return getString(name,null);
	}
	public String getString(String name, String defaultValue) {
		if(props.containsKey(name)) {
			Object o = props.get(name);
			if(o!=null) {
				return o.toString();
			}
		} else if(parent!=null) {
			return parent.getString(name,defaultValue);
		}
		return defaultValue;
	}
	

	public Properties put(String name, Object value) {
		this.props.put(name,value);
		return this;
	}

	public Properties putAll(Map<String,Object> props) {
		this.props.putAll(props);
		return this;
	}
	
	
	public static Map<String,Object> readMap(Reader reader) throws IOException {
		java.util.Properties javaProps = new java.util.Properties();
		javaProps.load(reader);
		@SuppressWarnings({ "unchecked", "rawtypes" })
		Map<String,Object> map = new HashMap<String,Object>((Map)javaProps);
		return map;
	}
}