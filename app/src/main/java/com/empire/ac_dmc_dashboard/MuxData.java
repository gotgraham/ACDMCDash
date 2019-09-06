package com.empire.ac_dmc_dashboard;

import java.nio.ByteBuffer;
import java.io.*;

public class MuxData {

  public MuxData() {
    receivedBytes = new ByteArrayOutputStream();

    dcDcTemp1 = 0;
    dcDcTemp2 = 0;
    dcDcVoltage = 0;
    dcDcCurrent = 0;

    igbt1 = 0;
    igbt2 = 0;
    igbt3 = 0;
    igbt4 = 0;
    igbt5 = 0;
    igbt6 = 0;
  }

  // Process the given bytes
  public void processData(byte[] data) {
    ByteBuffer buffer = ByteBuffer.wrap(data);
    buffer.rewind();

    boolean header_found = false;
    for (int i = 0; i < data.length - 4 && !header_found;) {
      // Is this our header?
      if (buffer.getInt(i) == 0xAABBCCDD) {
        receivedBytes.reset(); // Clear our saved buffer

        header_found = true;

        // Copy the buffer from this point on
        for (; i < data.length; ++i) {
          receivedBytes.write(data[i]);
        }
      } else {
        // Copy this byte out - not the header
        receivedBytes.write(data[i++]);
      }
      // Copy any remaining buffer out if we have a partial integer left.
      // Since we have a sliding window for checking integer data, we can only
      // advance through the buffer until we get to the last 4 bytes
      // If these aren't the header, we need to copy the remaining 3 bytes of
      // the integer out - the first byte of the integer will be copied above.
      int remaining_bytes = data.length - (i + 1);
      if (remaining_bytes > 0 && remaining_bytes < 4) {
        for (; i < data.length; ++i) {
          receivedBytes.write(data[i]);
        }
      }

      // Have we received a full packet?
      if (receivedBytes.size() >= 44) {
        decodePacket();
      }
    }
  }

  // Decode a packet of received data
  private void decodePacket() {
    ByteBuffer packet = ByteBuffer.wrap(receivedBytes.toByteArray());
    if (packet.getInt(0) == 0xAABBCCDD){
      dcDcTemp1 = decodeValue(packet.getInt(4));
      dcDcTemp2 = decodeValue(packet.getInt(8));
      dcDcVoltage = decodeValue(packet.getInt(12));
      dcDcCurrent = decodeValue(packet.getInt(16));

      igbt1 = decodeValue(packet.getInt(20));
      igbt2 = decodeValue(packet.getInt(24));
      igbt3 = decodeValue(packet.getInt(28));
      igbt4 = decodeValue(packet.getInt(32));
      igbt5 = decodeValue(packet.getInt(36));
      igbt6 = decodeValue(packet.getInt(40));
    }
  }

  // Decode a value from the packet
  private float decodeValue(int i) {
    return new Float(Integer.reverseBytes(i)) / 100;
  }

  // private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
  // public static String bytesToHex(byte[] bytes) {
  //   char[] hexChars = new char[bytes.length * 2];
  //   for (int j = 0; j < bytes.length; j++) {
  //     int v = bytes[j] & 0xFF;
  //     hexChars[j * 2] = HEX_ARRAY[v >>> 4];
  //     hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
  //   }
  //   return new String(hexChars);
  // }

  private ByteArrayOutputStream receivedBytes;

  // Decoded data
  public float dcDcTemp1;
  public float dcDcTemp2;
  public float dcDcVoltage;
  public float dcDcCurrent;

  public float igbt1;
  public float igbt2;
  public float igbt3;
  public float igbt4;
  public float igbt5;
  public float igbt6;
}
