package tinydc.slave;

import java.net.*;

import tinydc.common.Defaults;

public class PresenceReporter extends Thread
{
    private final Slave slave;

    public PresenceReporter(Slave slave)
    {
      this.slave = slave;
    }

    private byte[] preparePacket() throws Exception
    {
      String message;
      synchronized(slave)
      {
        message = "<SlaveStatus>\n" +
                  "  <SlaveID>" + slave.slaveID + "</SlaveID>\n" +
                  (slave.taskRunning?("  <TaskID>" + slave.taskID + "</TaskID>\n"):("")) +
                  "</SlaveStatus>";
      }
      byte[] packet = message.getBytes();
      if (packet.length > Defaults.maxDatagramSize)
        throw new Error("SlaveStatus size exceeded");
      return packet;
    }

    public void run()
    {
      try 
      {
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(slave.settings.masterIP);
        do
        {
          byte[] message = preparePacket();
          DatagramPacket packet = new DatagramPacket(message, message.length,
                address, slave.settings.masterWaitingForSlavesUDPPort);
          socket.send(packet);
          sleep(slave.settings.slaveStatusPeriod * 1000);
        } while (true);
      }
      catch (Exception e)
      {
        System.err.println("problem sending slave status");
        e.printStackTrace();
        System.exit(1);
      }
    }

}
