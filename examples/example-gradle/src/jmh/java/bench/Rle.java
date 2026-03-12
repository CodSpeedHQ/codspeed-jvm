package bench;

public class Rle {

  public static byte[] encode(byte[] input) {
    if (input.length == 0) return new byte[0];

    // Worst case: no runs, every byte needs a count prefix
    byte[] buf = new byte[input.length * 2];
    int pos = 0;

    int i = 0;
    while (i < input.length) {
      byte value = input[i];
      int count = 1;
      while (i + count < input.length && input[i + count] == value && count < 255) {
        count++;
      }
      buf[pos++] = (byte) count;
      buf[pos++] = value;
      i += count;
    }

    byte[] result = new byte[pos];
    System.arraycopy(buf, 0, result, 0, pos);
    return result;
  }

  public static byte[] decode(byte[] input) {
    if (input.length % 2 != 0) {
      throw new IllegalArgumentException(
          "Invalid RLE input: length must be even, got " + input.length);
    }

    int totalLen = 0;
    for (int i = 0; i < input.length; i += 2) {
      totalLen += input[i] & 0xFF;
    }

    byte[] result = new byte[totalLen];
    int pos = 0;
    for (int i = 0; i < input.length; i += 2) {
      int count = input[i] & 0xFF;
      byte value = input[i + 1];
      for (int j = 0; j < count; j++) {
        result[pos++] = value;
      }
    }
    return result;
  }
}
