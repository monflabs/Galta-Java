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
package tests.json.serializers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.monflabs.json.serializers.Serializers;

import tests.ProjectTestCase;


/**
 * @author priand
 */
public class SerializersTest extends ProjectTestCase {

	public void testDefaultSerializers() {
		LocalDate ld = LocalDate.of(2020, 2, 20);
		assertEquals( ld, Serializers.localDate.deserialize(Serializers.localDate.serialize(ld)));
		
		LocalTime lt = LocalTime.of(13, 44, 18);
		assertEquals( lt, Serializers.localTime.deserialize(Serializers.localTime.serialize(lt)));

		LocalDateTime ldt = LocalDateTime.of(2020, 2, 20, 13, 44, 18);
		assertEquals( ldt, Serializers.localDateTime.deserialize(Serializers.localDateTime.serialize(ldt)));

		OffsetTime ot = OffsetTime.of(13, 44, 18, 0, ZoneOffset.ofHours(2));
		assertEquals( ot, Serializers.offsetTime.deserialize(Serializers.offsetTime.serialize(ot)));

		OffsetDateTime odt = OffsetDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneOffset.ofHours(2));
		assertEquals( odt, Serializers.offsetDateTime.deserialize(Serializers.offsetDateTime.serialize(odt)));

		ZonedDateTime zdt = ZonedDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneId.of("US/Eastern"));
		assertEquals( zdt, Serializers.zonedDateTime.deserialize(Serializers.zonedDateTime.serialize(zdt)));
	}
}

