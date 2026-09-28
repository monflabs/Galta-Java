package doc_examples.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.monflabs.util.ArrayUtil;
import org.monflabs.util.DtoA;
import org.monflabs.util.EnumUtil;
import org.monflabs.util.TypeUtil;
import org.monflabs.util.Version;

import tests.ProjectTestCase;

/**
 * Samples for docs/Utilities/NumbersAndTypes.md
 */
public class NumbersAndTypesExamples extends ProjectTestCase {

	public void testDtoA() throws Exception {
		assertEquals("1", DtoA.toStandard(1.0));                 // integral values have no ".0"
		assertEquals("0.30000000000000004", DtoA.toStandard(0.1 + 0.2));  // shortest round-trip digits
		assertEquals("0", DtoA.toStandard(-0.0));
		assertEquals("Infinity", DtoA.toStandard(Double.POSITIVE_INFINITY));
		assertEquals("NaN", DtoA.toStandard(Double.NaN));
		assertEquals("1200000000000000000", DtoA.toStandard(1.2e18));   // below 1e21: plain digits
		assertEquals("4611686018427388000", DtoA.toStandard(0x1p62));   // shortest digits, padded with zeros
		assertEquals("1e+21", DtoA.toStandard(1e21));                   // from 1e21: exponent with a sign
		assertEquals("0.0001", DtoA.toStandard(0.0001));                // plain decimal down to 1e-6
		assertEquals("1e-7", DtoA.toStandard(1e-7));
		assertEquals("12300000.5", DtoA.toStandard(12300000.5));
		assertEquals("1.5e-7", DtoA.toStandard(1.5e-7f));               // float overload: shortest float digits
		assertEquals("1.0e21", DtoA.toJavaLiteral(1e21));               // Java-style, for generated Java source
	}

	public void testTypeUtil() throws Exception {
		// Primitive targets follow Java's narrowing rules
		assertEquals(0, TypeUtil.toInt(Double.NaN));
		assertEquals(Integer.MAX_VALUE, TypeUtil.toInt(Double.POSITIVE_INFINITY));
		assertEquals(44, TypeUtil.toByte(300));                          // wraps

		// Big targets map NaN to zero and infinities to the largest finite value
		assertEquals(BigDecimal.ZERO, TypeUtil.toBigDecimal(Double.NaN));
		assertEquals(BigDecimal.valueOf(Double.MAX_VALUE), TypeUtil.toBigDecimal(Double.POSITIVE_INFINITY));
		assertEquals(BigInteger.valueOf(Long.MIN_VALUE), TypeUtil.toBigInteger(Double.NEGATIVE_INFINITY));

		assertEquals(new BigDecimal("0.1"), TypeUtil.toBigDecimal(0.1));   // via Double.toString, not the binary value
		assertEquals(BigInteger.valueOf(1900), TypeUtil.toBigInteger(1.9e3));
		assertEquals(BigInteger.ONE, TypeUtil.toBigInteger(new BigDecimal("1.99")));  // truncates
	}

	public void testVersionParse() throws Exception {
		Version v = Version.parse("2.1-beta");
		assertEquals(2, v.getMajor());
		assertEquals(1, v.getMinor());
		assertEquals(0, v.getSubversion());                   // missing parts are 0
		assertEquals("beta", v.getQualifier());
		assertEquals("2.1.0-beta", v.toString());

		assertEquals(Version.EMPTY, Version.parse(""));        // empty or null
		assertEquals(Version.parse("1.0.0"), Version.parse("1.0.0-"));   // an empty qualifier is no qualifier
		assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2.3.4"));
		assertThrows(IllegalArgumentException.class, () -> Version.parse("v1"));
	}

	public void testVersionOrdering() throws Exception {
		List<Version> list = new ArrayList<>();
		for (String s : new String[] {"1.10", "1.2", "1.0.0", "1.0.0-SNAPSHOT", "1.0.0-alpha", "1.0.0-rc10", "1.0.0-rc2"}) {
			list.add(Version.parse(s));
		}
		Collections.sort(list);
		assertEquals("[1.0.0-SNAPSHOT, 1.0.0-alpha, 1.0.0-rc10, 1.0.0-rc2, 1.0.0, 1.2.0, 1.10.0]", list.toString());

		Version snapshot = Version.parse("1.0.0-SNAPSHOT");
		assertTrue(snapshot.lessThan(Version.parse("1.0.0")));  // pre-release < release
		assertTrue(snapshot.isAtLeast(1, 0, 0));                 // isAtLeast ignores the qualifier
	}

	public void testEnumAndArray() throws Exception {
		assertArrayEquals(new String[] {"NANOSECONDS", "MICROSECONDS", "MILLISECONDS", "SECONDS", "MINUTES", "HOURS", "DAYS"},
				EnumUtil.names(TimeUnit.class));
		assertTrue(ArrayUtil.contains(new Object[] {"a", null, 3}, null));
		assertTrue(ArrayUtil.contains(new Object[] {"a", null, 3}, 3));     // equals(), not ==
		assertFalse(ArrayUtil.contains(null, "a"));
	}
}
