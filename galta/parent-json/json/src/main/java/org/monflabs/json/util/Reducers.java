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
package org.monflabs.json.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.BiFunction;

import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.JsonValues;

/**
 * Pre-implemented reducers.
 */
public final class Reducers {
	
	private Reducers() {}
	
	public static <V> Reducer<Integer,V> sumInt() { 
		return new ReducerImpl<Integer,V>((a,v) -> a.intValue()+intValue(v),0); 
	}
	public static <V> Reducer<Long,V> sumLong() { 
		return new ReducerImpl<Long,V>((a,v) -> a.longValue()+longValue(v),0L);
	}
	public static <V> Reducer<Double,V> sumDouble() { 
		return new ReducerImpl<Double,V>((a,v) -> a.doubleValue()+doubleValue(v),0.0);
	}
	public static <V> Reducer<BigInteger,V> sumBigInteger() { 
		return new ReducerImpl<BigInteger,V>((a,v) -> a.add(bigIntegerValue(v)),BigInteger.ZERO);
	}
	public static <V> Reducer<BigDecimal,V> sumBigDecimal() { 
		return new ReducerImpl<BigDecimal,V>((a,v) -> a.add(bigDecimalValue(v)),BigDecimal.ZERO);
	}
	
	private static int intValue(Object v) {
		if(v instanceof JsonValues jv) {
			return jv.intValue();
		}
		return JsonUtil.checkInt(v);
	}
	private static long longValue(Object v) {
		if(v instanceof JsonValues jv) {
			return jv.longValue();
		}
		return JsonUtil.checkLong(v);
	}
	private static double doubleValue(Object v) {
		if(v instanceof JsonValues jv) {
			return jv.doubleValue();
		}
		return JsonUtil.checkDouble(v);
	}
	private static BigInteger bigIntegerValue(Object v) {
		if(v instanceof JsonValues jv) {
			return jv.bigIntegerValue();
		}
		return JsonUtil.checkBigInteger(v);
	}
	private static BigDecimal bigDecimalValue(Object v) {
		if(v instanceof JsonValues jv) {
			return jv.bigDecimalValue();
		}
		return JsonUtil.checkBigDecimal(v);
	}
	
	private static final class ReducerImpl<T,V> implements Reducer<T,V> {
		private T initialValue;
		private BiFunction<T, V, T> reducer;
		private ReducerImpl(BiFunction<T, V, T> reducer, T initialValue) {
			this.reducer = reducer;
			this.initialValue = initialValue;
		}
		@Override
		public T initialValue() {
			return initialValue;
		}
		@Override
		public BiFunction<T, V, T> reducer() {
			return reducer;
		}
	}
}
