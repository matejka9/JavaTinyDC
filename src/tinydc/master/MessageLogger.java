package tinydc.master;

import java.io.*;

public class MessageLogger
{
  private String fileName;
  private boolean isOn;
  private boolean debugIsOn;

  MessageLogger(String fileName)
  {
    this.fileName = fileName + "." + System.currentTimeMillis();
    isOn = true;
    debugIsOn = false;
  }

  void turnOn() { isOn = true; }
  void turnOff() { isOn = false; }
  void turnDbgOn() { debugIsOn = true; }
  void turnDbgOff() { debugIsOn = false; }

  synchronized void c2m(byte [] message)
  { log("client", "master", new String(message)); }

  synchronized void c2m(String message)
  { log("client", "master", message); }

  synchronized void m2c(byte [] message)
  { log("master", "client", new String(message)); }

  synchronized void m2c(String message)
  { log("master", "client", message); }

  synchronized void m2s(byte [] message)
  { log("master", "slave", new String(message)); }

  synchronized void m2s(String message)
  { log("master", "slave", message); }

  synchronized void s2m(String message)
  { log("slave", "master", message); }

  synchronized void s2m(byte [] message)
  { log("slave", "master", new String(message)); }

  synchronized void log(String sender, String adresee, String message)
  {
    if (isOn == false) return;
    try
    {
      FileWriter ow = new FileWriter(fileName, true);
      ow.append(sender);
      ow.append(" -> ");
      ow.append(adresee);
      ow.append(":\n");
      ow.append(message);
      ow.append("\n---------\n");
      ow.close();
    }
    catch (IOException e)
    {
      System.err.println("Problem writing to log " + fileName);
    }
  }

  synchronized void dbg(String message)
  {
    if (debugIsOn == false) return;
    System.err.println(message);
  }
}
