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
package org.monflabs.json.serialization.classes.primitives;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.SerializationException;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

/**
 * Adapter of a value written as a JSON string, like the <code>java.time</code> types.
 * <p>
 * The standard adapters use the ISO-8601 formats, the ones of {@link JsonUtil}
 * (<code>checkLocalDate()</code>...) and of the JSON stringifier: a
 * <code>java.util.Date</code> is the ISO-8601 instant in UTC
 * (<code>2026-09-26T10:15:30Z</code>), like the stringifier writes it.
 */
public class StringValueClassAdapter<T> extends BaseClassAdapter {

	/**
	 * The adapters of the standard value types: <code>LocalDate</code>,
	 * <code>LocalDateTime</code>, <code>LocalTime</code>, <code>OffsetTime</code>,
	 * <code>OffsetDateTime</code>, <code>ZonedDateTime</code>, <code>Instant</code>,
	 * <code>Duration</code>, <code>Period</code>, <code>ZoneId</code>, <code>UUID</code>,
	 * <code>URI</code> and <code>java.util.Date</code>.
	 */
	public static List<StringValueClassAdapter<?>> standardAdapters() {
		return List.of(
			new StringValueClassAdapter<>(LocalDate.class, JsonUtil::toString, JsonUtil::parseLocalDate),
			new StringValueClassAdapter<>(LocalDateTime.class, JsonUtil::toString, JsonUtil::parseLocalDateTime),
			new StringValueClassAdapter<>(LocalTime.class, JsonUtil::toString, JsonUtil::parseLocalTime),
			new StringValueClassAdapter<>(OffsetTime.class, JsonUtil::toString, JsonUtil::parseOffsetTime),
			new StringValueClassAdapter<>(OffsetDateTime.class, JsonUtil::toString, JsonUtil::parseOffsetDateTime),
			new StringValueClassAdapter<>(ZonedDateTime.class, JsonUtil::toString, JsonUtil::parseZonedDateTime),
			new StringValueClassAdapter<>(Instant.class, Instant::toString, Instant::parse),
			new StringValueClassAdapter<>(Duration.class, Duration::toString, Duration::parse),
			new StringValueClassAdapter<>(Period.class, Period::toString, Period::parse),
			new StringValueClassAdapter<>(ZoneId.class, ZoneId::getId, ZoneId::of),
			new StringValueClassAdapter<>(UUID.class, UUID::toString, UUID::fromString),
			new StringValueClassAdapter<>(URI.class, URI::toString, URI::create),
			new StringValueClassAdapter<>(Date.class, d -> Instant.ofEpochMilli(d.getTime()).toString(), s -> new Date(Instant.parse(s).toEpochMilli()))
		);
	}

	private final Function<T,String> toString;
	private final Function<String,T> parse;

	public StringValueClassAdapter(Class<T> clazz, Function<T,String> toString, Function<String,T> parse) {
		super(clazz);
		this.toString = toString;
		this.parse = parse;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		return value!=null ? toString.apply((T)value) : null;
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue==null) {
			return null;
		}
		if(jsonValue instanceof String s) {
			try {
				return parse.apply(s);
			} catch(RuntimeException ex) {
				throw new JsonException(ex, "\"{0}\" is not a valid {1}", s, getAdaptedClazz().getSimpleName());
			}
		}
		throw new JsonException(null, "Cannot deserialize {0} into a {1}: a JSON string is expected", SerializationException.describe(jsonValue), getAdaptedClazz().getSimpleName());
	}
}
