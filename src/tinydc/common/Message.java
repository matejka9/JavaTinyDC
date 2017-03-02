package tinydc.common;

import java.io.*;

public class Message
{
  public static void write(OutputStream os, byte[] message) throws Exception
  {
    os.write(message.length & 255);
    os.write(message.length >> 8);
    os.write(message, 0, message.length);
    os.flush();
  }

  public static byte[] read(InputStream is) throws Exception
  {
    int nbts = is.read() + (is.read() << 8);
    byte bts[] = new byte[nbts];
    int bytesRead = 0; // how many bytes did we read so far
    do
    {
      int b = is.read(bts, bytesRead, bts.length - bytesRead);
      if (b > 0) bytesRead += b;
      else break;
    } while (bytesRead < bts.length);
    return bts;
  }

  public static void writeObjectIfNotNull(OutputStream os, Serializable obj) throws Exception
  {
    if (obj != null)
    {
      ObjectOutputStream oos = new ObjectOutputStream(os);
      oos.writeObject(obj);
      oos.flush();
    }
  }
}
