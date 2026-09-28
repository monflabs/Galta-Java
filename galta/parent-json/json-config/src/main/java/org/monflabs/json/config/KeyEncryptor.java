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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

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
 * Encrypted values are written as <code>[[v2:&lt;base64&gt;]]</code>, where the payload is
 * <code>salt(16) | iv(12) | AES-GCM ciphertext+tag</code>. The AES key is derived from the
 * password with PBKDF2-HMAC-SHA256 and the salt, and every value gets a random IV, so encrypting
 * the same value twice gives different results, and a tampered value fails to decrypt.
 * <p>
 * The legacy format written by earlier versions (<code>[[&lt;base64&gt;]]</code>, AES-CBC with a
 * zero IV and a truncated SHA-1 key) is still decrypted, and re-encrypted with the current format
 * the next time the value is encrypted.
 * <p>
 * A plain value is recognised as encrypted only when it has one of these exact shapes: a
 * <code>[[v2:...]]</code> value with a well-formed payload, or a legacy <code>[[...]]</code>
 * value whose content is base64 of a multiple of 16 bytes. Other values starting with
 * <code>[[</code>, like <code>[[x]]</code>, are regular values and are encrypted as such.
 */
public class KeyEncryptor implements ValueEncryptor {
	
	public static final String ENC_START = "[[";
	public static final String ENC_END   = "]]";
	public static final String ENC_V2    = "v2:";

	private static final int SALT_LENGTH = 16;
	private static final int IV_LENGTH = 12;
	private static final int TAG_BITS = 128;
	private static final int KEY_BITS = 256;
	private static final int ITERATIONS = 65536;
	private static final int V2_MIN_LENGTH = SALT_LENGTH+IV_LENGTH+TAG_BITS/8;
	
	private static final SecureRandom RANDOM = new SecureRandom();

	private final char[] password;
	private final byte[] salt;
	private final SecretKey key;
	private final Map<String,SecretKey> keysBySalt = new ConcurrentHashMap<>();
	private final SecretKeySpec legacyKey;
	private final Predicate<String[]> encryptPredicate;
	
	public KeyEncryptor(String encryptionKey, Predicate<String[]> encryptPredicate) {
		this.encryptPredicate = encryptPredicate;
		try {
			this.password = encryptionKey.toCharArray();
			this.salt = new byte[SALT_LENGTH];
			RANDOM.nextBytes(salt);
			this.key = deriveKey(salt);
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			this.legacyKey = new SecretKeySpec(Arrays.copyOf(digest.digest(encryptionKey.getBytes(StandardCharsets.UTF_16)),16), "AES");
		} catch(Exception ex) {
			throw new ConfigException(ex, "Error while creating encryption key");
		}
	}
	
	@Override
	public String encrypt(String[] keys, String value) {
		if(shouldEncrypt(keys)) {
			try {
				return encryptValue(value);
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
					return decryptValue(value);
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
		if(s==null || !s.startsWith(ENC_START) || !s.endsWith(ENC_END) || s.length()<ENC_START.length()+ENC_END.length()) {
			return false;
		}
		String content = s.substring(ENC_START.length(), s.length()-ENC_END.length());
		if(content.startsWith(ENC_V2)) {
			byte[] b = decodeBase64(content.substring(ENC_V2.length()));
			return b!=null && b.length>=V2_MIN_LENGTH;
		}
		return isLegacyPayload(content);
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
	
	@Override
	public String encryptValue(String value) {
		try {
			byte[] iv = new byte[IV_LENGTH];
			RANDOM.nextBytes(iv);
			Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
			aes.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] ct = aes.doFinal(value.getBytes(StandardCharsets.UTF_8));
			ByteBuffer bb = ByteBuffer.allocate(SALT_LENGTH+IV_LENGTH+ct.length);
			bb.put(salt).put(iv).put(ct);
			return ENC_START+ENC_V2+Base64.getEncoder().encodeToString(bb.array())+ENC_END;
		} catch (Exception ex) {
			throw new ConfigException(ex, "Error while encrypting value");
		}
	}

	/**
	 * Decrypt a value produced by {@link #encryptValue(String)}. For backward compatibility, this
	 * also accepts a legacy value, with or without its <code>[[ ]]</code> wrapper.
	 */
	@Override
	public String decryptValue(String value) {
		try {
			String s = JsonUtil.checkString(value);
			if(s.startsWith(ENC_START) && s.endsWith(ENC_END) && s.length()>=ENC_START.length()+ENC_END.length()) {
				s = s.substring(ENC_START.length(), s.length()-ENC_END.length());
			}
			if(s.startsWith(ENC_V2)) {
				byte[] b = Base64.getDecoder().decode(s.substring(ENC_V2.length()));
				if(b.length<V2_MIN_LENGTH) {
					throw new ConfigException(null, "Invalid encrypted value");
				}
				byte[] valueSalt = Arrays.copyOfRange(b, 0, SALT_LENGTH);
				Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
				aes.init(Cipher.DECRYPT_MODE, keyForSalt(valueSalt), new GCMParameterSpec(TAG_BITS, b, SALT_LENGTH, IV_LENGTH));
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
	
	private SecretKey keyForSalt(byte[] valueSalt) throws GeneralSecurityException {
		if(Arrays.equals(valueSalt, salt)) {
			return key;
		}
		String k = Base64.getEncoder().encodeToString(valueSalt);
		SecretKey sk = keysBySalt.get(k);
		if(sk==null) {
			sk = deriveKey(valueSalt);
			keysBySalt.put(k, sk);
		}
		return sk;
	}

	private SecretKey deriveKey(byte[] s) throws GeneralSecurityException {
		SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
		byte[] k = f.generateSecret(new PBEKeySpec(password, s, ITERATIONS, KEY_BITS)).getEncoded();
		return new SecretKeySpec(k, "AES");
	}
}
