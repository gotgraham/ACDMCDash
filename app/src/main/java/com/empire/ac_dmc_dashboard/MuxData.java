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

    phaseAmps = 0;
    reqPhaseAmps = 0;
    maxAmps = 0;
    fieldWeakening = 0;
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
      if (receivedBytes.size() >= 64) {
        decodePacket();
      }
    }
  }

  // Decode a packet of received data
  private void decodePacket() {
    ByteBuffer packet = ByteBuffer.wrap(receivedBytes.toByteArray());
    if (packet.getInt(0) == 0xAABBCCDD){
      dcDcTemp1 = decodeTemp(packet.getInt(4));
      dcDcTemp2 = decodeTemp(packet.getInt(8));
      dcDcVoltage = decodeTemp(packet.getInt(12));
      dcDcCurrent = decodeTemp(packet.getInt(16));

      igbt1 = decodeTemp(packet.getInt(20));
      igbt2 = decodeTemp(packet.getInt(24));
      igbt3 = decodeTemp(packet.getInt(28));
      igbt4 = decodeTemp(packet.getInt(32));
      igbt5 = decodeTemp(packet.getInt(36));
      igbt6 = decodeTemp(packet.getInt(40));

      phaseAmps = decodeCurrent(packet.getInt(44)) * -1; // The controller inverts the data for some reason
      reqPhaseAmps = decodeCurrent(packet.getInt(48));
      maxAmps = decodeCurrent(packet.getInt(52));
      fieldWeakening = decodeCurrent(packet.getInt(56));
      //eRPM = decodeRPM(packet.getInt(60));
    }
  }

  // Decode a value from the packet
  private float decodeValue(int i) {
    return new Float(Integer.reverseBytes(i));
  }

  // Decode a temp value from the packet
  private float decodeTemp(int i) {
    return decodeValue(i) / 100;
  }

  // Decode the RPM
  private float decodeRPM(int i) {
    float rpm = decodeValue(i);

    // PHI Int * f_sample Hz (24.030Hz) / 65535 * 60 sec per min
    return rpm * 24030 / 65535 * 60;
  }

  // Decode a current value from the packet
  private float decodeCurrent(int i) {
    // Raw value - 0 point (0x8000)
    double current = new Double(Integer.reverseBytes(i)) - 0x8000;

    // Divide by Clarke Factor (1.5)
    // Divide by Scaling Factor (16)
    // Divide by 10-bit ADC (1024)
    // Multiply by 5v (since 0-5v range)
    // Divide by mV/A for current sensor
    return new Float(current / 1.5 / 16 / 1024 * 5 / 0.0014);
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

  public float phaseAmps;
  public float reqPhaseAmps;
  public float maxAmps;
  public float fieldWeakening;
  public float eRPM;
}
