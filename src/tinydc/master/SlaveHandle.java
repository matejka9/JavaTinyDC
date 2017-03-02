package tinydc.master;

import java.util.*;
import java.net.*;
import java.io.*;
import javax.xml.stream.*;

import tinydc.common.*;

public class SlaveHandle
{
  public InetAddress ip;
  public TaskHandle task;
  public long lastStatus;
  public int slaveID;
  public TreeMap<String,String> services;

  private static Master master;

  public SlaveHandle(Master master, int slaveID, InetAddress ip)
  {
    this.master = master;
    this.slaveID = slaveID;
    this.ip = ip;
    lastStatus = System.currentTimeMillis();
    task = null;
    services = new TreeMap<String,String>();
  }

  public void addService(String service, String version)
  {
    services.put(service, version);
  }

  boolean submitTask(TaskHandle task)
  {
    String message = "<StartTask>\n" +
                     "  <SlaveID>" + slaveID + "</SlaveID>\n" +
                     "  <TaskID>" + task.taskID + "</TaskID>\n" +
                     "  <Service version=\"" + task.serviceVersion + "\">"
                                             + task.serviceName + "</Service>\n" +
                     "</StartTask>";
    try
    {
      master.logger.dbg("connecting to " + ip + ":" + master.settings.slavesWaitingForMasterTCPPort);
      Socket socket = new Socket(ip, master.settings.slavesWaitingForMasterTCPPort);
      OutputStream os = socket.getOutputStream();
      InputStream is = socket.getInputStream();
      Message.write(os, message.getBytes());
      Message.writeObjectIfNotNull(os, task.inputData);
      master.logger.m2s(message);

      byte [] packet = Message.read(is);
      master.logger.s2m(packet);
      os.close();
      is.close();
      socket.close();

      String submitStatus = retrieveTaskConfirm(packet, task.taskID);
      if ("accepted".equals(submitStatus))
      {
        this.task = task;
        task.slave = this;
        task.state = TaskHandle.TaskState.running;
        master.logger.dbg("slave accepted the task");
        return true;
      }
      else if ("rejected".equals(submitStatus))
      {
        task.slavesThatRejected.add(new Integer(slaveID));
        master.logger.dbg("slave rejected the task");
        return false;
      }
    }
    catch (Exception e)
    {
      master.logger.dbg("problem sending StartTask to slave at " + ip + ": " + e.getMessage());
      return false;
    }
    return true;
  }

  private String retrieveTaskConfirm(byte [] packet, int taskID) throws Exception
  {
    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new ByteArrayInputStream(packet));
    if (r.hasNext() != true)
        throw new Exception("Slave did not respond properly to StarTask");
    r.next();
    if (!r.getLocalName().equals("TaskConfirm"))
        throw new Exception("Slave didn't respond properly to StarTask");
    while (r.hasNext())
    {
      r.next();
      if (r.isStartElement() == true)
      	if (r.getLocalName().equals("TaskID") == true)
        {
          if (taskID != Integer.parseInt(r.getElementText()))
            throw new Exception("Slave responded with wrong taskID");
        }
        else if (r.getLocalName().equals("Status") == true)
          return r.getElementText();
    }
    return "rejected";
  }

  void sendCancelTask(int taskID)
  {
    try
    {
      Socket socket = new Socket(ip, master.settings.slavesWaitingForMasterTCPPort);
      OutputStream os = socket.getOutputStream();
      String message = "<StopTask>\n" +
                       "  <TaskID>" + taskID + "</TaskID>\n" +
                       "</StopTask>";
      Message.write(os, message.getBytes());
      os.close();
      master.logger.m2s(message);
    }
    catch (Exception e)
    {
      master.logger.dbg("problem sending StopTask to slave at " + ip + ": " + e.getMessage());
    }
  }

  static void sendRestart(InetAddress ip, int port, int slaveID)
  {
    try
    {
      Socket socket = new Socket(ip, port);
      OutputStream os = socket.getOutputStream();
      String message = "<SlaveRestart>\n" +
                       "  <SlaveID>" + slaveID + "</SlaveID>\n" +
                       "</SlaveRestart>";
      Message.write(os, message.getBytes());
      os.close();
      master.logger.m2s(message);
    }
    catch (Exception e)
    {
      master.logger.dbg("problem sending SlaveRestart to slave at " + ip + ": " + e.getMessage());
    }
  }
}
