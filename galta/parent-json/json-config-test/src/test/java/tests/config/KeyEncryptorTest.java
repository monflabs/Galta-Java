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
package tests.config;

import org.monflabs.json.config.KeyEncryptor;
import org.monflabs.util.config.ConfigException;

import tests.ProjectTestCase;

public class KeyEncryptorTest extends ProjectTestCase {
	
	// Produced by the legacy format (AES-CBC, zero IV, SHA-1 key) for "A value to encrypt"
	private static final String LEGACY = "[[+hMf4ve48Pr2N6q4ZQ6dVn/6bfkOzPSgiQGLyyIC/XI=]]";

	public void testEncryptor() {
		KeyEncryptor e = new KeyEncryptor("akey", (s) -> { return s[s.length-1].equals("password"); } );
		
		String[] toEnc = new String[] {"password"};  
		String[] noEnc = new String[] {"user"};
		
		assertTrue (e.shouldEncrypt(toEnc));
		assertFalse(e.shouldEncrypt(noEnc));
	
		String orgValue = "A value to encrypt";
		String encValue = e.encrypt(toEnc, orgValue);
		assertTrue(encValue, encValue.startsWith("[[v2:") && encValue.endsWith("]]"));
		assertEquals(orgValue, e.encrypt(noEnc, orgValue));
		
		assertTrue (e.isEncrypted(encValue));
		assertFalse(e.isEncrypted(orgValue));
		
		String decValue = e.decrypt(toEnc, encValue);
		assertFalse(e.isEncrypted(decValue));
		assertEquals(orgValue, decValue);
		// Not selected by the predicate: left as is
		assertEquals(encValue, e.decrypt(noEnc, encValue));
	}
	
	public void testValueRoundTrip() {
		KeyEncryptor e = new KeyEncryptor("akey", (s) -> true );
		
		String orgValue = "A value to encrypt";
		String encValue = e.encryptValue(orgValue);
		assertTrue (e.isEncrypted(encValue));
		
		// decryptValue() must accept exactly what encryptValue() produces
		assertEquals(orgValue, e.decryptValue(encValue));
		// and stay consistent with the key based entry points
		assertEquals(orgValue, e.decrypt(new String[] {"any"}, encValue));
		assertEquals(orgValue, e.decrypt(new String[] {"any"}, e.encrypt(new String[] {"any"}, orgValue)));
		
		assertEquals("", e.decryptValue(e.encryptValue("")));
		String unicode = "p\u00e2ss \ud83d\ude00 \u00e9";
		assertEquals(unicode, e.decryptValue(e.encryptValue(unicode)));
	}
	
	public void testNonDeterministic() {
		// A random IV per value: equal values must not give equal ciphertexts, and
		// values with a common prefix must not share a ciphertext prefix
		KeyEncryptor e = new KeyEncryptor("akey", (s) -> true );
		String a = e.encryptValue("same value");
		String b = e.encryptValue("same value");
		assertFalse(a.equals(b));
		assertEquals("same value", e.decryptValue(a));
		assertEquals("same value", e.decryptValue(b));
		
		// Another instance (another salt) with the same password decrypts both
		KeyEncryptor e2 = new KeyEncryptor("akey", (s) -> true );
		assertEquals("same value", e2.decryptValue(a));
		String c = e2.encryptValue("same value");
		assertEquals("same value", e.decryptValue(c));
	}
	
	public void testWrongPassword() {
		KeyEncryptor e = new KeyEncryptor("akey", (s) -> true );
		KeyEncryptor other = new KeyEncryptor("another", (s) -> true );
		String v = e.encryptValue("secret");
		try {
			other.decryptValue(v);
			fail("A wrong password must not decrypt");
		} catch(ConfigException ex) {
			// expected: authenticated encryption
		}
	}
	
	public void testTampered() {
		KeyEncryptor e = new KeyEncryptor("akey", (s) -> true );
		String v = e.encryptValue("secret");
		char[] c = v.toCharArray();
		int i = c.length-6;
		c[i] = c[i]=='A' ? 'B' : 'A';
		try {
			e.decryptValue(new String(c));
			fail("A tampered value must not decrypt");
		} catch(ConfigException ex) {
			// expected
		}
	}
	
	public void testLegacyDecrypt() {
		KeyEncryptor e = new KeyEncryptor("akey", (s) -> true );
		assertTrue(e.isEncrypted(LEGACY));
		assertEquals("A value to encrypt", e.decryptValue(LEGACY));
		assertEquals("A value to encrypt", e.decrypt(new String[] {"any"}, LEGACY));
		// ...and still accept the bare legacy payload
		assertEquals("A value to encrypt", e.decryptValue(LEGACY.substring(2, LEGACY.length()-2)));
	}
	
	public void testIsEncryptedNoFalsePositive() {
		KeyEncryptor e = new KeyEncryptor("akey", (s) -> true );
		// Plain values that merely look like the wrapper are not encrypted values
		assertFalse(e.isEncrypted("[[x]]"));
		assertFalse(e.isEncrypted("[[]]"));
		assertFalse(e.isEncrypted("[["));
		assertFalse(e.isEncrypted("]]"));
		assertFalse(e.isEncrypted("[[[]]"));
		assertFalse(e.isEncrypted("[[v2:]]"));
		assertFalse(e.isEncrypted("[[v2:abcd]]"));
		assertFalse(e.isEncrypted("[[hello world]]"));
		assertFalse(e.isEncrypted(null));
		
		// ...so they are encrypted and round-trip
		String[] k = new String[] {"password"};
		for(String plain: new String[] {"[[x]]", "[[]]", "[[v2:abcd]]"}) {
			String enc = e.encrypt(k, plain);
			assertTrue(e.isEncrypted(enc));
			assertEquals(plain, e.decrypt(k, enc));
			// decrypt() leaves a non-encrypted value untouched
			assertEquals(plain, e.decrypt(k, plain));
		}
	}
}
