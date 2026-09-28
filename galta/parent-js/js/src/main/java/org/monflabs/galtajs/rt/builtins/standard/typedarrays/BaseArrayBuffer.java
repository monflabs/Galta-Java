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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays;

import static java.lang.Float.SIZE;

import java.math.BigInteger;
import java.nio.ByteOrder;

import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.AbstractPropertiesHolder;

public abstract class BaseArrayBuffer extends AbstractPropertiesHolder {
	
	public static final boolean LITTLE_INDIAN = !ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);

	protected byte[] buf;
	protected int maxByteLength;
	// [[ArrayBufferIsImmutable]] (Stage 3 "Immutable ArrayBuffer" proposal) -
	// only ever set true by ArrayBuffer.transferToImmutable()/
	// sliceToImmutable(); every other buffer (the overwhelming majority)
	// defaults false, so this is purely additive - existing code paths are
	// unaffected. An immutable buffer is always fixed-length (maxByteLength
	// stays -1), so ArrayBuffer.prototype.resize()'s existing
	// !isResizable() check already rejects it before ever reading its
	// newLength argument, matching that method's own "verify internal
	// slots before reading newLength" ordering requirement - no separate
	// immutable check is needed there.
	protected boolean immutable;

	protected BaseArrayBuffer(byte[] buf, int maxByteLength) {
		this.buf = buf;
		this.maxByteLength = maxByteLength;
	}

	protected abstract BaseArrayBuffer create(byte[] buf, int maxByteLength);

	public byte[] getBytes() {
		return buf;
	}

	public boolean isDetached() {
		return buf==null;
	}

	public boolean isImmutable() {
		return immutable;
	}
	protected void setImmutable(boolean immutable) {
		this.immutable = immutable;
	}

	public int getByteLength() {
		return buf!=null ? buf.length : 0;
	}
	
	public int getMaxByteLength() {
		return maxByteLength;
	}
	
	public boolean isResizable() {
		return maxByteLength>=0;
	}

	public void resize(int newSize) {
		resize(newSize,true);
	}
	public void resize(int newSize, boolean check) {
		if(check) {
			if (buf==null || maxByteLength<0) {
				throw RuntimeUtil.typeError("Buffer is detached or nor resizable");
			}
		}
		if(newSize!=buf.length) {
			if(check) { 
				if (newSize > maxByteLength) {
					throw RuntimeUtil.rangeError("Buffer size exceeds maximum size");
				}
			}
			byte[] newBuf = new byte[newSize];
			System.arraycopy(buf, 0, newBuf, 0, Math.min(buf.length, newSize));
			buf = newBuf;
		}
	}
	
	public BaseArrayBuffer slice(int start, int end) {
		if (buf==null) {
			throw RuntimeUtil.typeError("Buffer is detached");
		}
		int length = buf.length;
		start = actualIndex(start,length);
		end = actualIndex(end,length);
		if(start>=end) {
			return create(new byte[0],-1);
		}
		byte[] newBuf = new byte[end-start];
		System.arraycopy(buf, start, newBuf, 0, newBuf.length);
		return create(newBuf,-1);
	}
	// Package-visible (was private) so ArrayBuffer.sliceToImmutable() can
	// reuse the SAME ResolveBounds-equivalent index resolution against an
	// explicitly-passed, pre-coercion length (see its own doc comment for
	// why plain slice() above can't be reused as-is for that case).
	protected static int actualIndex(int index, int length) {
		if(index<0) {
			if(index>=-length) {
				return index + length;
			}
			return 0;
		}
		return Math.min(index, length);
	}

	
	//
	// Data Accessors
	//
	
	private void checkBuffer() {
		if(buf==null) {
			throw RuntimeUtil.typeError("Buffer is detached");
		}
	}
	// Reads stay allowed on an immutable buffer (checkBuffer() alone) -
	// only the primitive write* methods below (shared by every DataView
	// setter and every TypedArray element write) call this.
	private void checkWritable() {
		checkBuffer();
		if(immutable) {
			throw RuntimeUtil.typeError("Cannot write to an immutable ArrayBuffer");
		}
	}
    
    // INT 8
    public byte readInt8(int byteOffset) {
    	checkBuffer();
        return buf[byteOffset];
    }
    public void writeInt8(int byteOffset, byte val) {
    	checkWritable();
        buf[byteOffset] = (byte) val;
    }
    public short readUint8(int byteOffset) {
    	checkBuffer();
        return (short)(buf[byteOffset] & 0xFF);
    }
    public void writeUint8(int byteOffset, short val) {
    	checkWritable();
        buf[byteOffset] = (byte) (val & 0xFF);
    }

    // INT 16
    public short readInt16(int byteOffset, boolean littleEndian) {
    	checkBuffer();
        if (littleEndian) {
            return (short) ((buf[byteOffset] & 0xFF) | ((buf[byteOffset + 1] & 0xFF) << 8));
        }
        return (short) (((buf[byteOffset] & 0xFF) << 8) | (buf[byteOffset + 1] & 0xFF));
    }
    public void writeInt16(int byteOffset, short val, boolean littleEndian) {
    	checkWritable();
        if (littleEndian) {
            buf[byteOffset] = (byte) (val & 0xFF);
            buf[byteOffset + 1] = (byte) ((val >>> 8) & 0xFF);
        } else {
            buf[byteOffset] = (byte) ((val >>> 8) & 0xFF);
            buf[byteOffset + 1] = (byte) (val & 0xFF);
        }
    }
    public int readUint16(int byteOffset, boolean littleEndian) {
    	checkBuffer();
        if (littleEndian) {
            return ((buf[byteOffset] & 0xFF) | ((buf[byteOffset + 1] & 0xFF) << 8));
        }
        return (((buf[byteOffset] & 0xFF) << 8) | (buf[byteOffset + 1] & 0xFF));
    }
    public void writeUint16(int byteOffset, int val, boolean littleEndian) {
    	checkWritable();
        if (littleEndian) {
            buf[byteOffset] = (byte) (val & 0xFF);
            buf[byteOffset + 1] = (byte) ((val >>> 8) & 0xFF);
        } else {
            buf[byteOffset] = (byte) ((val >>> 8) & 0xFF);
            buf[byteOffset + 1] = (byte) (val & 0xFF);
        }
    }

    // INT 32
    public int readInt32(int byteOffset, boolean littleEndian) {
    	checkBuffer();
        if (littleEndian) {
            return (buf[byteOffset] & 0xFF)
		                 | ((buf[byteOffset + 1] & 0xFF) << 8)
		                 | ((buf[byteOffset + 2] & 0xFF) << 16)
		                 | ((buf[byteOffset + 3] & 0xFF) << 24);
        }
        return ((buf[byteOffset] & 0xFF) << 24)
                        | ((buf[byteOffset + 1] & 0xFF) << 16)
                        | ((buf[byteOffset + 2] & 0xFF) << 8)
                        | (buf[byteOffset + 3] & 0xFF);
    }
    public void writeInt32(int byteOffset, int val, boolean littleEndian) {
    	checkWritable();
        if (littleEndian) {
            buf[byteOffset] = (byte) (val & 0xFF);
            buf[byteOffset + 1] = (byte) ((val >>> 8) & 0xFF);
            buf[byteOffset + 2] = (byte) ((val >>> 16) & 0xFF);
            buf[byteOffset + 3] = (byte) ((val >>> 24) & 0xFF);
        } else {
            buf[byteOffset] = (byte) ((val >>> 24) & 0xFF);
            buf[byteOffset + 1] = (byte) ((val >>> 16) & 0xFF);
            buf[byteOffset + 2] = (byte) ((val >>> 8) & 0xFF);
            buf[byteOffset + 3] = (byte) (val & 0xFF);
        }
    }
    public long readUint32(int byteOffset, boolean littleEndian) {
    	checkBuffer();
        if (littleEndian) {
            return ((buf[byteOffset] & 0xFFL)
                            | ((buf[byteOffset + 1] & 0xFFL) << 8L)
                            | ((buf[byteOffset + 2] & 0xFFL) << 16L)
                            | ((buf[byteOffset + 3] & 0xFFL) << 24L))
                    & 0xFFFFffffL;
        }
        return (((buf[byteOffset] & 0xFFL) << 24L)
                        | ((buf[byteOffset + 1] & 0xFFL) << 16L)
                        | ((buf[byteOffset + 2] & 0xFFL) << 8L)
                        | (buf[byteOffset + 3] & 0xFFL))
                & 0xFFFFffffL;
    }
    public void writeUint32(int byteOffset, long val, boolean littleEndian) {
    	checkWritable();
        if (littleEndian) {
            buf[byteOffset] = (byte) (val & 0xFFL);
            buf[byteOffset + 1] = (byte) ((val >>> 8L) & 0xFFL);
            buf[byteOffset + 2] = (byte) ((val >>> 16L) & 0xFFL);
            buf[byteOffset + 3] = (byte) ((val >>> 24L) & 0xFFL);
        } else {
            buf[byteOffset] = (byte) ((val >>> 24L) & 0xFFL);
            buf[byteOffset + 1] = (byte) ((val >>> 16L) & 0xFFL);
            buf[byteOffset + 2] = (byte) ((val >>> 8L) & 0xFFL);
            buf[byteOffset + 3] = (byte) (val & 0xFFL);
        }
    }
    
    // INT 64
    public long readInt64(int byteOffset, boolean littleEndian) {
    	checkBuffer();
        if (littleEndian) {
            return ((buf[byteOffset] & 0xFFL)
                    | ((buf[byteOffset + 1] & 0xFFL) << 8L)
                    | ((buf[byteOffset + 2] & 0xFFL) << 16L)
                    | ((buf[byteOffset + 3] & 0xFFL) << 24L)
                    | ((buf[byteOffset + 4] & 0xFFL) << 32L)
                    | ((buf[byteOffset + 5] & 0xFFL) << 40L)
                    | ((buf[byteOffset + 6] & 0xFFL) << 48L)
                    | ((buf[byteOffset + 7] & 0xFFL) << 56L));
        }
        return (((buf[byteOffset] & 0xFFL) << 56L)
                | ((buf[byteOffset + 1] & 0xFFL) << 48L)
                | ((buf[byteOffset + 2] & 0xFFL) << 40L)
                | ((buf[byteOffset + 3] & 0xFFL) << 32L)
                | ((buf[byteOffset + 4] & 0xFFL) << 24L)
                | ((buf[byteOffset + 5] & 0xFFL) << 16L)
                | ((buf[byteOffset + 6] & 0xFFL) << 8L)
                | ((buf[byteOffset + 7] & 0xFFL) << 0L));
    }
    public void writeInt64(int byteOffset, long val, boolean littleEndian) {
    	checkWritable();
        if (littleEndian) {
            buf[byteOffset] = (byte) (val & 0xFFL);
            buf[byteOffset + 1] = (byte) ((val >>> 8L) & 0xFFL);
            buf[byteOffset + 2] = (byte) ((val >>> 16L) & 0xFFL);
            buf[byteOffset + 3] = (byte) ((val >>> 24L) & 0xFFL);
            buf[byteOffset + 4] = (byte) ((val >>> 32L) & 0xFFL);
            buf[byteOffset + 5] = (byte) ((val >>> 40L) & 0xFFL);
            buf[byteOffset + 6] = (byte) ((val >>> 48L) & 0xFFL);
            buf[byteOffset + 7] = (byte) ((val >>> 56L) & 0xFFL);
        } else {
            buf[byteOffset] = (byte) ((val >>> 56L) & 0xFFL);
            buf[byteOffset + 1] = (byte) ((val >>> 48L) & 0xFFL);
            buf[byteOffset + 2] = (byte) ((val >>> 40L) & 0xFFL);
            buf[byteOffset + 3] = (byte) ((val >>> 32L) & 0xFFL);
            buf[byteOffset + 4] = (byte) ((val >>> 24L) & 0xFFL);
            buf[byteOffset + 5] = (byte) ((val >>> 16L) & 0xFFL);
            buf[byteOffset + 6] = (byte) ((val >>> 8L) & 0xFFL);
            buf[byteOffset + 7] = (byte) (val & 0xFFL);
        }
    }


    public long readUint64(int byteOffset, boolean littleEndian) {
    	checkBuffer();
        if (littleEndian) {
            return ((buf[byteOffset] & 0xFFL)
                    | ((buf[byteOffset + 1] & 0xFFL) << 8L)
                    | ((buf[byteOffset + 2] & 0xFFL) << 16L)
                    | ((buf[byteOffset + 3] & 0xFFL) << 24L)
                    | ((buf[byteOffset + 4] & 0xFFL) << 32L)
                    | ((buf[byteOffset + 5] & 0xFFL) << 40L)
                    | ((buf[byteOffset + 6] & 0xFFL) << 48L)
                    | ((buf[byteOffset + 7] & 0xFFL) << 56L));
        }
        return (((buf[byteOffset] & 0xFFL) << 56L)
                | ((buf[byteOffset + 1] & 0xFFL) << 48L)
                | ((buf[byteOffset + 2] & 0xFFL) << 40L)
                | ((buf[byteOffset + 3] & 0xFFL) << 32L)
                | ((buf[byteOffset + 4] & 0xFFL) << 24L)
                | ((buf[byteOffset + 5] & 0xFFL) << 16L)
                | ((buf[byteOffset + 6] & 0xFFL) << 8L)
                | ((buf[byteOffset + 7] & 0xFFL) << 0L));
    }
    public void writeUint64(int byteOffset, long val, boolean littleEndian) {
    	checkWritable();
        if (littleEndian) {
            buf[byteOffset] = (byte) (val & 0xFFL);
            buf[byteOffset + 1] = (byte) ((val >>> 8L) & 0xFFL);
            buf[byteOffset + 2] = (byte) ((val >>> 16L) & 0xFFL);
            buf[byteOffset + 3] = (byte) ((val >>> 24L) & 0xFFL);
            buf[byteOffset + 4] = (byte) ((val >>> 32L) & 0xFFL);
            buf[byteOffset + 5] = (byte) ((val >>> 40L) & 0xFFL);
            buf[byteOffset + 6] = (byte) ((val >>> 48L) & 0xFFL);
            buf[byteOffset + 7] = (byte) ((val >>> 56L) & 0xFFL);
        } else {
            buf[byteOffset] = (byte) ((val >>> 56L) & 0xFFL);
            buf[byteOffset + 1] = (byte) ((val >>> 48L) & 0xFFL);
            buf[byteOffset + 2] = (byte) ((val >>> 40L) & 0xFFL);
            buf[byteOffset + 3] = (byte) ((val >>> 32L) & 0xFFL);
            buf[byteOffset + 4] = (byte) ((val >>> 24L) & 0xFFL);
            buf[byteOffset + 5] = (byte) ((val >>> 16L) & 0xFFL);
            buf[byteOffset + 6] = (byte) ((val >>> 8L) & 0xFFL);
            buf[byteOffset + 7] = (byte) (val & 0xFFL);
        }
    }
    
    // BIGINT 64
    public BigInteger readBigInt64(int byteOffset, boolean littleEndian) {
    	return RuntimeUtil.bigIntegerInt64(readInt64(byteOffset,littleEndian));
    }
    public void writeBigInt64(int byteOffset, BigInteger val, boolean littleEndian) {
        writeInt64(byteOffset, RuntimeUtil.int64BigInteger(val), littleEndian);
    }
    public BigInteger readBigUint64(int byteOffset, boolean littleEndian) {
    	return RuntimeUtil.bigIntegerUint64(readUint64(byteOffset,littleEndian));
    }
    public void writeBigUint64(int byteOffset, BigInteger val, boolean littleEndian) {
        writeUint64(byteOffset, RuntimeUtil.uint64BigInteger(val), littleEndian);
    }
    
    
    // FLOAT 16
    public float readFloat16(int offset, boolean littleEndian) {
    	short v = (short)readInt16(offset,littleEndian);
    	// Java 21 has Float.float16ToFloat - temporary until Java 21
    	//return Float.float16ToFloat(v);
    	return float16ToFloat(v);
    }
    public void writeFloat16(int offset, double val, boolean littleEndian) {
    	// Round directly from the double (single rounding step) rather than
    	// narrowing to float first - a double->float->float16 path double-
    	// rounds and gives a wrong answer at certain tie-boundaries (see
    	// doubleToFloat16's own comment; same fix as Math.f16round).
    	short int16 = doubleToFloat16(val);
    	writeInt16(offset,int16,littleEndian);
    }
    
    // FLOAT 32
    public float readFloat32(int offset, boolean littleEndian) {
        long base = readUint32(offset, littleEndian);
        return Float.intBitsToFloat((int) base);
    }

    public void writeFloat32(int offset, double val, boolean littleEndian) {
        long base = Float.floatToIntBits((float) val);
        writeUint32(offset, base, littleEndian);
    }

    // FLOAT 64
    public double readFloat64(int offset, boolean littleEndian) {
        long base = readUint64(offset, littleEndian);
        return Double.longBitsToDouble(base);
    }

    public void writeFloat64(int offset, double val, boolean littleEndian) {
        long base = Double.doubleToLongBits(val);
        writeUint64(offset, base, littleEndian);
    }
    

    
    
    //
    // Temporary until Java21
    //
    
    private static class FloatConsts {
        private FloatConsts() {}
        public static final int SIGNIFICAND_WIDTH = 24;
        public static final int EXP_BIAS =
                (1 << (SIZE - SIGNIFICAND_WIDTH - 1)) - 1; // 127

    }
    public static float float16ToFloat(short floatBinary16) {
        int bin16arg = (int)floatBinary16;
        int bin16SignBit     = 0x8000 & bin16arg;
        int bin16ExpBits     = 0x7c00 & bin16arg;
        int bin16SignifBits  = 0x03FF & bin16arg;

        final int SIGNIF_SHIFT = (FloatConsts.SIGNIFICAND_WIDTH - 11);

        float sign = (bin16SignBit != 0) ? -1.0f : 1.0f;

        int bin16Exp = (bin16ExpBits >> 10) - 15;
        if (bin16Exp == -15) {
            return sign * (0x1p-24f * bin16SignifBits);
        } else if (bin16Exp == 16) {
            return (bin16SignifBits == 0) ?
                sign * Float.POSITIVE_INFINITY :
                Float.intBitsToFloat((bin16SignBit << 16) |
                                     0x7f80_0000 |
                                     ( bin16SignifBits << SIGNIF_SHIFT ));
        }

        assert -15 < bin16Exp  && bin16Exp < 16;

        int floatExpBits = (bin16Exp + FloatConsts.EXP_BIAS)
            << (FloatConsts.SIGNIFICAND_WIDTH - 1);

        return Float.intBitsToFloat((bin16SignBit << 16) |
                                    floatExpBits |
                                    (bin16SignifBits << SIGNIF_SHIFT));
    }
    public static short floatToFloat16(float f) {
        int doppel = Float.floatToRawIntBits(f);
        short sign_bit = (short)((doppel & 0x8000_0000) >> 16);

        if (Float.isNaN(f)) {
            return (short)(sign_bit
                    | 0x7c00 
                    | (doppel & 0x007f_e000) >> 13 
                    | (doppel & 0x0000_1ff0) >> 4  
                    | (doppel & 0x0000_000f));     
        }

        float abs_f = Math.abs(f);

        if (abs_f >= (0x1.ffcp15f + 0x0.002p15f) ) {
            return (short)(sign_bit | 0x7c00); 
        }
        if (abs_f <= 0x1.0p-24f * 0.5f) { 
            return sign_bit; 
        }
        int exp = Math.getExponent(f);
        assert -25 <= exp && exp <= 15;
        int expdelta = 0;
        int msb = 0x0000_0000;
        if (exp < -14) {
            expdelta = -14 - exp;
            exp = -15;
            msb = 0x0080_0000;
        }
        int f_signif_bits = doppel & 0x007f_ffff | msb;
        short signif_bits = (short)(f_signif_bits >> (13 + expdelta));
        int lsb    = f_signif_bits & (1 << 13 + expdelta);
        int round  = f_signif_bits & (1 << 12 + expdelta);
        int sticky = f_signif_bits & ((1 << 12 + expdelta) - 1);

        if (round != 0 && ((lsb | sticky) != 0 )) {
            signif_bits++;
        }
        assert (0xf800 & signif_bits) == 0x0;

        return (short)(sign_bit | ( ((exp + 15) << 10) + signif_bits ) );
    }
    // Same algorithm as floatToFloat16(float), but rounding straight from a
    // double's 52-bit mantissa rather than first rounding to a float's 23-bit
    // mantissa - converting via an intermediate float (double->float->float16)
    // double-rounds and can give a wrong result exactly at a tie between two
    // representable float16 values (e.g. Math.f16round needs this, since its
    // argument is a JS Number/double, not already a float).
    public static short doubleToFloat16(double d) {
        long doppel = Double.doubleToRawLongBits(d);
        short sign_bit = (short)((doppel >> 48) & 0x8000);

        if (Double.isNaN(d)) {
            return (short)(sign_bit | 0x7e00);
        }

        double abs_d = Math.abs(d);

        if (abs_d >= (0x1.ffcp15 + 0x0.002p15)) {
            return (short)(sign_bit | 0x7c00);
        }
        if (abs_d <= 0x1.0p-24 * 0.5) {
            return sign_bit;
        }
        int exp = Math.getExponent(d);
        int expdelta = 0;
        long msb = 0x0000_0000_0000_0000L;
        if (exp < -14) {
            expdelta = -14 - exp;
            exp = -15;
            msb = 0x0010_0000_0000_0000L;
        }
        long d_signif_bits = (doppel & 0x000f_ffff_ffff_ffffL) | msb;
        int shift = 42 + expdelta;
        short signif_bits = (short)(d_signif_bits >> shift);
        long lsb    = d_signif_bits & (1L << shift);
        long round  = d_signif_bits & (1L << (shift - 1));
        long sticky = d_signif_bits & ((1L << (shift - 1)) - 1);

        if (round != 0 && ((lsb | sticky) != 0)) {
            signif_bits++;
        }

        return (short)(sign_bit | ( ((exp + 15) << 10) + signif_bits ) );
    }

	
}
