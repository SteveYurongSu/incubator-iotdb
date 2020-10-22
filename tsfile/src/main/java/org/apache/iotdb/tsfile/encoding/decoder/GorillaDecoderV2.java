/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.tsfile.encoding.decoder;

import java.nio.ByteBuffer;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;

public abstract class GorillaDecoderV2 extends Decoder {

  protected boolean firstValueWasRead = false;
  protected int storedLeadingZeros = Integer.MAX_VALUE;
  protected int storedTrailingZeros = 0;

  private byte buffer = 0;
  private int bitsLeft = 0;

  public GorillaDecoderV2() {
    super(TSEncoding.GORILLA_V2);
  }

  @Override
  public final boolean hasNext(ByteBuffer buffer) {
    return 0 < buffer.remaining();
  }

  @Override
  public void reset() {
    firstValueWasRead = false;
    storedLeadingZeros = Integer.MAX_VALUE;
    storedTrailingZeros = 0;

    buffer = 0;
    bitsLeft = 0;
  }

  /**
   * Reads the next bit and returns a boolean representing it.
   *
   * @return true if the next bit is 1, otherwise 0.
   */
  protected boolean readBit(ByteBuffer in) {
    boolean bit = ((buffer >> (bitsLeft - 1)) & 1) == 1;
    bitsLeft--;
    flipByte(in);
    return bit;
  }

  /**
   * Reads a long from the next X bits that represent the least significant bits in the long value.
   *
   * @param bits How many next bits are read from the stream
   * @return long value that was read from the stream
   */
  protected long readLong(int bits, ByteBuffer in) {
    long value = 0;
    while (bits > 0) {
      if (bits > bitsLeft || bits == Byte.SIZE) {
        // Take only the bitsLeft "least significant" bits
        byte d = (byte) (buffer & ((1 << bitsLeft) - 1));
        value = (value << bitsLeft) + (d & 0xFF);
        bits -= bitsLeft;
        bitsLeft = 0;
      } else {
        // Shift to correct position and take only least significant bits
        byte d = (byte) ((buffer >>> (bitsLeft - bits)) & ((1 << bits) - 1));
        value = (value << bits) + (d & 0xFF);
        bitsLeft -= bits;
        bits = 0;
      }
      flipByte(in);
    }
    return value;
  }

  protected int readNextClearBit(int maxBits, ByteBuffer in) {
    int value = 0x00;
    for (int i = 0; i < maxBits; i++) {
      value <<= 1;
      if (readBit(in)) {
        value |= 0x01;
      } else {
        break;
      }
    }
    return value;
  }

  protected void flipByte(ByteBuffer in) {
    if (bitsLeft == 0) {
      buffer = in.get();
      bitsLeft = Byte.SIZE;
    }
  }
}