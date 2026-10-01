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
package org.monflabs.json.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.monflabs.json.JsonUtil;
import org.monflabs.util.StringUtil;
import org.monflabs.util.config.ConfigException;

/**
 * Encrypts the configuration values selected by a key predicate.
 * <p>
 * Encrypted values are written as <code>[[v3:&lt;base64&gt;]]</code>, where the payload is
 * <code>iterations(4) | salt(16) | iv(12) | AES-GCM ciphertext+tag</code>. The AES key is
 * derived from the password with PBKDF2-HMAC-SHA256, the salt and the iteration count (600,000
 * for the values encrypted by this version), and every value gets a random IV, so encrypting the
 * same value twice gives different results, and a tampered value fails to decrypt.
 * <p>
 * The key path of the value (its keys joined with "/") is bound to the ciphertext as additional
 * authenticated data: a value encrypted for a key cannot be moved to another key, it then fails to
 * decrypt. {@link #encryptValue(String)} and {@link #decryptValue(String)}, which have no key, use
 * an empty key path: such a value is not bound to a key, and is accepted for any key.
 * <p>
 * The formats written by earlier versions are still decrypted: <code>[[v2:...]]</code> (the same
 * without key path and iteration count, 65,536 iterations) and the legacy <code>[[&lt;base64&gt;]]</code>
 * (AES-CBC with a zero IV and a truncated SHA-1 key). {@link #needsReencryption(String)} reports
 * them, so a (writable) configuration encrypts them again with the current format when it is loaded.
 * <p>
 * A plain value is recognised as encrypted only when it has one of these exact shapes: a
 * <code>[[v3:...]]</code> or <code>[[v2:...]]</code> value with a well-formed payload, or a legacy
 * <code>[[...]]</code> value whose content is base64 of a multiple of 16 bytes. The legacy shape can
 * be disabled (see {@link #KeyEncryptor(String, Predicate, boolean)}), so plain values with that
 * shape can be stored. Other values starting with <code>[[</code>, like <code>[[x]]</code>, are
 * regular values and are encrypted as such.
 */
public class KeyEncryptor implements ValueEncryptor {

	public static final String ENC_START = "[[";
	public static final String ENC_END   = "]]";
	public static final String ENC_V2    = "v2:";
	public static final String ENC_V3    = "v3:";

	private static final int SALT_LENGTH = 16;
	private static final int IV_LENGTH = 12;
	private static final int TAG_BITS = 128;
	private static final int KEY_BITS = 256;
	private static final int V2_ITERATIONS = 65536;
	/** The PBKDF2 iteration count of the values encrypted by this version. */
	public static final int ITERATIONS = 600_000;
	// Bounds of the iteration count read from a value, so a forged value cannot make the
	// key derivation (almost) endless
	private static final int MIN_ITERATIONS = 10_000;
	private static final int MAX_ITERATIONS = 10_000_000;
	private static final int V2_MIN_LENGTH = SALT_LENGTH+IV_LENGTH+TAG_BITS/8;
	private static final int V3_MIN_LENGTH = 4+SALT_LENGTH+IV_LENGTH+TAG_BITS/8;
	// The keys derived for the salts of other encryptors (values written by another process)
	private static final int MAX_CACHED_KEYS = 16;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final char[] password;
	private final byte[] salt;
	// Derived on first use: the derivation is purposely slow
	private volatile SecretKey key;
	private final Map<String,SecretKey> keysBySalt = new LinkedHashMap<>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;
		@Override
		protected boolean removeEldestEntry(Map.Entry<String,SecretKey> eldest) {
			return size()>MAX_CACHED_KEYS;
		}
	};
	private final SecretKeySpec legacyKey;
	private final Predicate<String[]> encryptPredicate;
	private final boolean acceptLegacy;

	public KeyEncryptor(String encryptionKey, Predicate<String[]> encryptPredicate) {
		this(encryptionKey, encryptPredicate, true);
	}

	/**
	 * @param encryptionKey the password
	 * @param encryptPredicate selects the key paths whose values are encrypted
	 * @param acceptLegacy whether values with the legacy shape (<code>[[base64]]</code>) are
	 * decrypted. When false, such values are plain values (encrypted when they are saved).
	 */
	public KeyEncryptor(String encryptionKey, Predicate<String[]> encryptPredicate, boolean acceptLegacy) {
		this.encryptPredicate = encryptPredicate;
		this.acceptLegacy = acceptLegacy;
		try {
			this.password = encryptionKey.toCharArray();
			this.salt = new byte[SALT_LENGTH];
			RANDOM.nextBytes(salt);
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] pwd = encryptionKey.getBytes(StandardCharsets.UTF_16);
			byte[] d = digest.digest(pwd);
			this.legacyKey = new SecretKeySpec(Arrays.copyOf(d,16), "AES");
			Arrays.fill(pwd, (byte)0);
			Arrays.fill(d, (byte)0);
		} catch(Exception ex) {
			throw new ConfigException(ex, "Error while creating encryption key");
		}
	}

	private static String keyPath(String[] keys) {
		return keys!=null ? String.join("/", keys) : "";
	}

	@Override
	public String encrypt(String[] keys, String value) {
		if(shouldEncrypt(keys)) {
			try {
				return encrypt(keyPath(keys), value);
			} catch(Exception e) {
				throw new ConfigException(e,"Error while encrypting key {0}", StringUtil.toString(keys));
			}
		}
		return value;
	}

	@Override
	public String decrypt(String[] keys, String value) {
		if(shouldEncrypt(keys)) {
			if(isEncrypted(value)) {
				try {
					return decrypt(keyPath(keys), value);
				} catch(Exception e) {
					throw new ConfigException(e,"Error while decrypting key {0}", StringUtil.toString(keys));
				}
			}
		}
		return value;
	}

	@Override
	public boolean shouldEncrypt(String[] keys) {
		return encryptPredicate!=null && encryptPredicate.test(keys);
	}

	@Override
	public boolean isEncrypted(String s) {
		String content = unwrap(s);
		if(content==null) {
			return false;
		}
		if(content.startsWith(ENC_V3)) {
			byte[] b = decodeBase64(content.substring(ENC_V3.length()));
			return b!=null && b.length>=V3_MIN_LENGTH;
		}
		if(content.startsWith(ENC_V2)) {
			byte[] b = decodeBase64(content.substring(ENC_V2.length()));
			return b!=null && b.length>=V2_MIN_LENGTH;
		}
		return acceptLegacy && isLegacyPayload(content);
	}
	private static String unwrap(String s) {
		if(s==null || !s.startsWith(ENC_START) || !s.endsWith(ENC_END) || s.length()<ENC_START.length()+ENC_END.length()) {
			return null;
		}
		return s.substring(ENC_START.length(), s.length()-ENC_END.length());
	}

	/**
	 * A value in an older format (legacy, v2), or encrypted with fewer iterations than the
	 * current count, has to be encrypted again.
	 */
	@Override
	public boolean needsReencryption(String s) {
		if(!isEncrypted(s)) {
			return false;
		}
		String content = unwrap(s);
		if(!content.startsWith(ENC_V3)) {
			return true;
		}
		byte[] b = decodeBase64(content.substring(ENC_V3.length()));
		return ByteBuffer.wrap(b).getInt()<ITERATIONS;
	}

	private static boolean isLegacyPayload(String content) {
		byte[] b = decodeBase64(content);
		return b!=null && b.length>0 && b.length%16==0;
	}

	private static byte[] decodeBase64(String s) {
		if(s.isEmpty() || s.length()%4!=0) {
			return null;
		}
		try {
			return Base64.getDecoder().decode(s);
		} catch(IllegalArgumentException ex) {
			return null;
		}
	}

	/**
	 * Encrypts a value with an empty key path.
	 */
	@Override
	public String encryptValue(String value) {
		return encrypt("", value);
	}

	/**
	 * Decrypts a value produced by {@link #encryptValue(String)} (a value encrypted for a key
	 * path is decrypted with {@link #decrypt(String[], String)}). For backward compatibility,
	 * this also accepts a v2 or a legacy value, with or without its <code>[[ ]]</code> wrapper.
	 */
	@Override
	public String decryptValue(String value) {
		return decrypt("", value);
	}

	private String encrypt(String keyPath, String value) {
		try {
			byte[] iv = new byte[IV_LENGTH];
			RANDOM.nextBytes(iv);
			Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
			aes.init(Cipher.ENCRYPT_MODE, ownKey(), new GCMParameterSpec(TAG_BITS, iv));
			aes.updateAAD(keyPath.getBytes(StandardCharsets.UTF_8));
			byte[] ct = aes.doFinal(value.getBytes(StandardCharsets.UTF_8));
			ByteBuffer bb = ByteBuffer.allocate(4+SALT_LENGTH+IV_LENGTH+ct.length);
			bb.putInt(ITERATIONS).put(salt).put(iv).put(ct);
			return ENC_START+ENC_V3+Base64.getEncoder().encodeToString(bb.array())+ENC_END;
		} catch (Exception ex) {
			throw new ConfigException(ex, "Error while encrypting value");
		}
	}

	private String decrypt(String keyPath, String value) {
		try {
			String s = JsonUtil.checkString(value);
			String content = unwrap(s);
			if(content!=null) {
				s = content;
			}
			if(s.startsWith(ENC_V3)) {
				byte[] b = Base64.getDecoder().decode(s.substring(ENC_V3.length()));
				if(b.length<V3_MIN_LENGTH) {
					throw new ConfigException(null, "Invalid encrypted value");
				}
				int iterations = ByteBuffer.wrap(b).getInt();
				if(iterations<MIN_ITERATIONS || iterations>MAX_ITERATIONS) {
					throw new ConfigException(null, "Invalid encrypted value");
				}
				byte[] valueSalt = Arrays.copyOfRange(b, 4, 4+SALT_LENGTH);
				SecretKey k = keyFor(valueSalt, iterations);
				try {
					return decryptV3(b, k, keyPath);
				} catch(AEADBadTagException ex) {
					// A value encrypted without key path (encryptValue()) is not bound to a key
					if(keyPath.isEmpty()) {
						throw ex;
					}
					return decryptV3(b, k, "");
				}
			}
			if(s.startsWith(ENC_V2)) {
				byte[] b = Base64.getDecoder().decode(s.substring(ENC_V2.length()));
				if(b.length<V2_MIN_LENGTH) {
					throw new ConfigException(null, "Invalid encrypted value");
				}
				byte[] valueSalt = Arrays.copyOfRange(b, 0, SALT_LENGTH);
				Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
				aes.init(Cipher.DECRYPT_MODE, keyFor(valueSalt, V2_ITERATIONS), new GCMParameterSpec(TAG_BITS, b, SALT_LENGTH, IV_LENGTH));
				byte[] pt = aes.doFinal(b, SALT_LENGTH+IV_LENGTH, b.length-SALT_LENGTH-IV_LENGTH);
				return new String(pt,StandardCharsets.UTF_8);
			}
			// Legacy format
			Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
			aes.init(Cipher.DECRYPT_MODE, legacyKey, new IvParameterSpec(new byte[16]));
			return new String(aes.doFinal(Base64.getDecoder().decode(s)),StandardCharsets.UTF_8);
		} catch (ConfigException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new ConfigException(ex, "Error while decrypting value");
		}
	}

	private static String decryptV3(byte[] b, SecretKey k, String keyPath) throws GeneralSecurityException {
		Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
		aes.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, b, 4+SALT_LENGTH, IV_LENGTH));
		aes.updateAAD(keyPath.getBytes(StandardCharsets.UTF_8));
		byte[] pt = aes.doFinal(b, 4+SALT_LENGTH+IV_LENGTH, b.length-4-SALT_LENGTH-IV_LENGTH);
		return new String(pt,StandardCharsets.UTF_8);
	}
	
	private SecretKey ownKey() throws GeneralSecurityException {
		SecretKey k = key;
		if(k==null) {
			synchronized(this) {
				k = key;
				if(k==null) {
					k = key = deriveKey(salt, ITERATIONS);
				}
			}
		}
		return k;
	}

	private SecretKey keyFor(byte[] valueSalt, int iterations) throws GeneralSecurityException {
		if(iterations==ITERATIONS && Arrays.equals(valueSalt, salt)) {
			return ownKey();
		}
		String k = iterations+":"+Base64.getEncoder().encodeToString(valueSalt);
		synchronized(keysBySalt) {
			SecretKey sk = keysBySalt.get(k);
			if(sk!=null) {
				return sk;
			}
		}
		SecretKey sk = deriveKey(valueSalt, iterations);
		synchronized(keysBySalt) {
			keysBySalt.put(k, sk);
		}
		return sk;
	}

	private SecretKey deriveKey(byte[] s, int iterations) throws GeneralSecurityException {
		SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
		PBEKeySpec spec = new PBEKeySpec(password, s, iterations, KEY_BITS);
		try {
			byte[] k = f.generateSecret(spec).getEncoded();
			try {
				return new SecretKeySpec(k, "AES");
			} finally {
				Arrays.fill(k, (byte)0);
			}
		} finally {
			spec.clearPassword();
		}
	}
}
