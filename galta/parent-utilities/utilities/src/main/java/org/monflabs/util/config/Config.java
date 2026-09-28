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
package org.monflabs.util.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.function.Consumer;

import org.monflabs.util.PathUtil;
import org.monflabs.util.IOStreamUtil;

/**
 * Access to configuration items.
 * 
 * Many implementations are possible, including a 'vault'.
 */ 
public interface Config {
	
	public static final char SEPARATOR = '/';
	
	public interface Updater {
		public boolean put(String key, String value);
		public boolean remove(String key);
		public void cancel();
		public default boolean put(String key, boolean value) {
			return put(key, Boolean.toString(value));
		}
		public default boolean put(String key, int value) {
			return put(key, Integer.toString(value));
		}
		public default boolean put(String key, long value) {
			return put(key, Long.toString(value));
		}
		public default boolean put(String key, double value) {
			return put(key, Double.toString(value));
		}
	}

	public enum ENUM_KEYS {
		ALL, VALUES, FOLDERS
	}
	
	public default Iterator<String> keysOf(String key) {
		return keysOf(key,ENUM_KEYS.ALL);
	}
	public Iterator<String> keysOf(String key, ENUM_KEYS type);
	
	
	// Default implementation - can bve optimized
	public default Config subConfig(String...subKeys) {
		return subConfig(PathUtil.POSIX.concat(subKeys));
	}
	public default Config subConfig(String subKey) {
		return new Config() {
			// A key is relative to the sub configuration, even with a leading '/', and
			// cannot escape it with a ".." segment
			private String absoluteKey(String key) {
				if(key!=null) {
					for(String part: PathUtil.POSIX.getParts(key)) {
						if(part.equals("..")) {
							throw new ConfigException(null,"Invalid key {0}: a sub configuration key cannot contain ''..''",key);
						}
					}
				}
				return PathUtil.POSIX.concat(subKey, key);
			}
			@Override
			public Iterator<String> keysOf(String key, ENUM_KEYS type) {
				return Config.this.keysOf(absoluteKey(key),type);
			}
			@Override
			public boolean has(String key) {
				return Config.this.has(absoluteKey(key));
			}
			@Override
			public boolean isValue(String key) {
				return Config.this.isValue(absoluteKey(key));
			}
			@Override
			public boolean isFolder(String key) {
				return Config.this.isFolder(absoluteKey(key));
			}
			@Override
			public Object getValue(String key) {
				return Config.this.getValue(absoluteKey(key));
			}
			@Override
			public boolean isReadOnly() {
				return Config.this.isReadOnly();
			}
			@Override
			public boolean isAutoSave() {
				return Config.this.isAutoSave();
			}
			@Override
			public boolean updateValues(Consumer<Updater> c) {
				return Config.this.updateValues( (u) -> {
					Updater wu = new Updater() {
						@Override
						public boolean remove(String key) {
							return u.remove(absoluteKey(key));
						}
						@Override
						public boolean put(String key, String value) {
							return u.put(absoluteKey(key),value);
						}
						// The typed overloads are delegated as is: the default methods would
						// turn the values into strings before the parent updater sees them
						@Override
						public boolean put(String key, boolean value) {
							return u.put(absoluteKey(key),value);
						}
						@Override
						public boolean put(String key, int value) {
							return u.put(absoluteKey(key),value);
						}
						@Override
						public boolean put(String key, long value) {
							return u.put(absoluteKey(key),value);
						}
						@Override
						public boolean put(String key, double value) {
							return u.put(absoluteKey(key),value);
						}
						@Override
						public void cancel() {
							u.cancel();
						}
					};
					c.accept(wu);
				} );
			}
			@Override
			public InputStream getResource(String path) {
				return Config.this.getResource(path);
			}
			@Override
			public void setResource(String path, Consumer<OutputStream> save) {
				Config.this.setResource(path,save);
			}
		};
	}

	public boolean has(String key);
	public boolean isValue(String key);
	public boolean isFolder(String key);

	public Object getValue(String key);

	public default boolean isReadOnly() {
		return true;
	}
	public default boolean isAutoSave() {
		return true;
	}
	public default boolean updateValues(Consumer<Updater> updater) {
		return false;
	}
	
	public InputStream getResource(String path);
	public default String getResourceAsString(String path) {
		InputStream is=getResource(path);
		if(is!=null) {
			try {
				return IOStreamUtil.readContent(is);
			} catch(IOException e) {
				throw new ConfigException(e,"Error while loading resource '{0}'",path);
			} finally {
				IOStreamUtil.close(is);;
			}
		}
		return null;
	}
	public default String getResourceAsString(String path, Charset cs) {
		InputStream is=getResource(path);
		if(is!=null) {
			try {
				return IOStreamUtil.readContent(is,cs);
			} catch(IOException e) {
				throw new ConfigException(e,"Error while loading resource '{0}'",path);
			} finally {
				IOStreamUtil.close(is);;
			}
		}
		return null;
	}
	
	public default void setResource(String path, Consumer<OutputStream> save) {
		throw new ConfigException(null,"Cannot save resource '{0}'",path);
	}
	public default void setResource(String path, String content) {
		setResource(path, os -> {
			try {
				IOStreamUtil.setContent(os, content);
			} catch(IOException e) {
				throw new ConfigException(e,"Error while writing resource '{0}'",path);
			}
		} );
	}
	public default void setResource(String path, String content, Charset cs) {
		setResource(path, os -> {
			try {
				IOStreamUtil.setContent(os, content, cs);
			} catch(IOException e) {
				throw new ConfigException(e,"Error while writing resource '{0}'",path);
			}
		} );
	}


	public default String getString(String key) {
		return getString(key,null);
	}
	public default String getString(String key, String defaultValue) {
		Object v = getValue(key);
		if(v==null) {
			return defaultValue;
		}
		return v.toString();
	}

	public default boolean getBoolean(String key) {
		return getBoolean(key,false);
	}
	/**
	 * Returns a boolean value. A Boolean is returned as is, a string must be "true" or "false"
	 * (ignoring case and surrounding spaces).
	 * @throws ConfigException if the value is not a valid boolean
	 */
	public default boolean getBoolean(String key, boolean defaultValue) {
		Object v = getValue(key);
		if(v==null) {
			return defaultValue;
		}
		if(v instanceof Boolean b) {
			return b;
		}
		// Boolean.parseBoolean() never fails: anything but "true" silently became false
		String s = v.toString().trim();
		if(s.equalsIgnoreCase("true")) {
			return true;
		}
		if(s.equalsIgnoreCase("false")) {
			return false;
		}
		throw new ConfigException(null, "Invalid boolean value for key {0}", key);
	}

	/**
	 * Returns an integer value. A Number must be an integral value within the int range,
	 * like a string (no silent truncation of 3.7 or of a long).
	 * @throws ConfigException if the value is not a valid integer
	 */
	public default int getInt(String key) {
		return getInt(key,0);
	}
	public default int getInt(String key, int defaultValue) {
		Object v = getValue(key);
		if(v==null) {
			return defaultValue;
		}
		try {
			if(v instanceof Number n) {
				return toBigDecimal(n).intValueExact();
			}
			return Integer.parseInt(v.toString().trim());
		} catch(Exception e) {
			throw new ConfigException(e, "Invalid integer value for key {0}", key);
		}
	}

	/**
	 * Returns a long value. A Number must be an integral value within the long range.
	 * @throws ConfigException if the value is not a valid long
	 */
	public default long getLong(String key) {
		return getLong(key,0L);
	}
	public default long getLong(String key, long defaultValue) {
		Object v = getValue(key);
		if(v==null) {
			return defaultValue;
		}
		try {
			if(v instanceof Number n) {
				return toBigDecimal(n).longValueExact();
			}
			return Long.parseLong(v.toString().trim());
		} catch(Exception e) {
			throw new ConfigException(e, "Invalid long integer value for key {0}", key);
		}
	}

	public default double getDouble(String key) {
		return getDouble(key,0.0);
	}
	public default double getDouble(String key, double defaultValue) {
		Object v = getValue(key);
		if(v==null) {
			return defaultValue;
		}
		if(v instanceof Number n) {
			return n.doubleValue();
		}
		try {
			return Double.parseDouble(v.toString());
		} catch(Exception e) {
			throw new ConfigException(e, "Invalid double number for key {0}", key);
		}
	}

	private static java.math.BigDecimal toBigDecimal(Number n) {
		if(n instanceof Double d && (d.isNaN() || d.isInfinite())) {
			throw new ArithmeticException("Not a finite number: "+n);
		}
		if(n instanceof Float f && (f.isNaN() || f.isInfinite())) {
			throw new ArithmeticException("Not a finite number: "+n);
		}
		return org.monflabs.util.TypeUtil.toBigDecimal(n);
	}
}
