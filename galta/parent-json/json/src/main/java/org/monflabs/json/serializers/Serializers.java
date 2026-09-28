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
package org.monflabs.json.serializers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;

import org.monflabs.json.JsonUtil;

public final class Serializers {
	
	private Serializers() {}
	
	public static final JsonSerializer<LocalDate> localDate = new JsonSerializer<>() {
		public String serialize(LocalDate t) {
			return JsonUtil.toString(t);
		}
		public LocalDate deserialize(String value) {
			return JsonUtil.parseLocalDate(value);
		}
	};

	public static final JsonSerializer<LocalTime> localTime = new JsonSerializer<>() {
		public String serialize(LocalTime t) {
			return JsonUtil.toString(t);
		}
		public LocalTime deserialize(String value) {
			return JsonUtil.parseLocalTime(value);
		}
	};

	public static final JsonSerializer<LocalDateTime> localDateTime = new JsonSerializer<>() {
		public String serialize(LocalDateTime t) {
			return JsonUtil.toString(t);
		}
		public LocalDateTime deserialize(String value) {
			return JsonUtil.parseLocalDateTime(value);
		}
	};

	public static final JsonSerializer<OffsetTime> offsetTime = new JsonSerializer<>() {
		public String serialize(OffsetTime t) {
			return JsonUtil.toString(t);
		}
		public OffsetTime deserialize(String value) {
			return JsonUtil.parseOffsetTime(value);
		}
	};

	public static final JsonSerializer<OffsetDateTime> offsetDateTime = new JsonSerializer<>() {
		public String serialize(OffsetDateTime t) {
			return JsonUtil.toString(t);
		}
		public OffsetDateTime deserialize(String value) {
			return JsonUtil.parseOffsetDateTime(value);
		}
	};

	public static final JsonSerializer<ZonedDateTime> zonedDateTime = new JsonSerializer<>() {
		public String serialize(ZonedDateTime t) {
			return JsonUtil.toString(t);
		}
		public ZonedDateTime deserialize(String value) {
			return JsonUtil.parseZonedDateTime(value);
		}
	};
}
