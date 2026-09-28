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
package tests.json.util;

import static org.junit.Assert.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonUtil;

import tests.ProjectTestCase;

public class JsonDateTimeTest extends ProjectTestCase {

    public void testLocalDate() throws Exception {
    	LocalDate v1 = JsonUtil.parseLocalDate("2020-09-24");
    	assertEquals(LocalDate.of(2020, 9, 24), v1);
    	assertThrows( JsonException.class, () -> JsonUtil.parseLocalDate("") );
    	assertThrows( JsonException.class, () -> JsonUtil.parseLocalDate("abc") );
    	
		String s1 = JsonUtil.toString(LocalDate.of(2022, 8, 14));
    	assertEquals("2022-08-14", s1);
    	assertThrows( JsonException.class, () -> JsonUtil.toString((LocalDate)null) );
    }

    public void testLocalTime() throws Exception {
    	LocalTime v1 = JsonUtil.parseLocalTime("20:45:25");
    	assertEquals(LocalTime.of(20, 45, 25), v1);
    	assertThrows( JsonException.class, () -> JsonUtil.parseLocalTime("") );
    	assertThrows( JsonException.class, () -> JsonUtil.parseLocalTime("abc") );
    	
		String s1 = JsonUtil.toString(LocalTime.of(19, 34, 28));
    	assertEquals("19:34:28", s1);
    	assertThrows( JsonException.class, () -> JsonUtil.toString((LocalTime)null) );
    }
    
    public void testLocalDateTime() throws Exception {
    	LocalDateTime v1 = JsonUtil.parseLocalDateTime("2020-09-24T21:45:25");
    	assertEquals(LocalDateTime.of(2020, 9, 24, 21, 45, 25), v1);
    	assertThrows( JsonException.class, () -> JsonUtil.parseLocalDateTime("") );
    	assertThrows( JsonException.class, () -> JsonUtil.parseLocalDateTime("abc") );
    	
		String s1 = JsonUtil.toString(LocalDateTime.of(2022, 8, 14, 14, 6, 43));
    	assertEquals("2022-08-14T14:06:43", s1);
    	assertThrows( JsonException.class, () -> JsonUtil.toString((LocalDateTime)null) );
    }

    public void testOffsetTime() throws Exception {
    	ZoneOffset zo = ZoneOffset.ofHours(5);

    	OffsetTime v1 = JsonUtil.parseOffsetTime("20:45:25+05:00");
    	assertEquals(OffsetTime.of(20, 45, 25, 0, zo), v1);
    	assertThrows( JsonException.class, () -> JsonUtil.parseOffsetTime("") );
    	assertThrows( JsonException.class, () -> JsonUtil.parseOffsetTime("abc") );
    	
		String s1 = JsonUtil.toString(OffsetTime.of(19, 34, 28, 0, zo));
    	assertEquals("19:34:28+05:00", s1);
    	assertThrows( JsonException.class, () -> JsonUtil.toString((OffsetTime)null) );
    }
    
    public void testOffsetDateTime() throws Exception {
    	ZoneOffset zo = ZoneOffset.ofHours(4);

    	OffsetDateTime v1 = JsonUtil.parseOffsetDateTime("2020-09-24T21:45:25+04:00");
    	assertEquals(OffsetDateTime.of(2020, 9, 24, 21, 45, 25, 0, zo), v1);
    	assertThrows( JsonException.class, () -> JsonUtil.parseOffsetDateTime("") );
    	assertThrows( JsonException.class, () -> JsonUtil.parseOffsetDateTime("abc") );
    	
		String s1 = JsonUtil.toString(OffsetDateTime.of(2022, 8, 14, 14, 6, 43, 0, zo));
    	assertEquals("2022-08-14T14:06:43+04:00", s1);
    	assertThrows( JsonException.class, () -> JsonUtil.toString((OffsetDateTime)null) );
    }
    
    public void testZonedDateTime() throws Exception {
    	ZoneId zi = ZoneId.of("US/Eastern");

    	ZonedDateTime v1 = JsonUtil.parseZonedDateTime("2022-08-14T14:06:43-04:00[US/Eastern]");
    	assertEquals(ZonedDateTime.of(2022, 8, 14, 14, 6, 43, 0, zi), v1);
    	assertThrows( JsonException.class, () -> JsonUtil.parseZonedDateTime("") );
    	assertThrows( JsonException.class, () -> JsonUtil.parseZonedDateTime("abc") );
    	
    	String s1 = JsonUtil.toString(ZonedDateTime.of(2022, 8, 14, 14, 6, 43, 0, zi));
    	assertEquals("2022-08-14T14:06:43-04:00[US/Eastern]", s1);
    	assertThrows( JsonException.class, () -> JsonUtil.toString((ZonedDateTime)null) );
    }
}
