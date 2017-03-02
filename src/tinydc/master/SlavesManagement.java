package tinydc.master;

import java.util.*;
import java.net.*;
import java.io.*;
import javax.xml.stream.*;

import tinydc.common.*;
import tinydc.common.servicesdata.*;

public class SlavesManagement extends Thread
{
  TreeMap<Integer,SlaveHandle> slaves;
  private final Master master;
  private Thread slavesReportsThread;
  private boolean slavesStatusesThreadStarted;
  private int slaveIDCounter;

  SlavesManagement(Master master)
  {
    slaveIDCounter = 0;
    slaves = new TreeMap<Integer,SlaveHandle>();
    this.master = master;
    slavesStatusesThreadStarted = false;
    this.start();
    while (slavesStatusesThreadStarted == false) {}
    slavesReportsThread = new Thread(this);
    slavesReportsThread.start();
  }

  public void run()
  {
    if (slavesStatusesThreadStarted == false) 
    {
      slavesStatusesThreadStarted = true;
      slavesStatusesThread();
    }
    else slavesReportsAndLoginsThread();
  }

  private void slavesReportsAndLoginsThread()
  {
    ServerSocket srv;
    try
    {
      master.logger.dbg("waiting for slave requests on " + master.settings.masterWaitingForSlavesTCPPort);
      srv = new ServerSocket(master.settings.masterWaitingForSlavesTCPPort);
      while (true)
      {
	Socket socket = srv.accept();
        master.logger.dbg("slave request");
        try { manageSlaveReportOrLogin(socket); }
        catch (Exception e2)
        { master.logger.dbg("slave request failed: " + e2.getMessage()); }
        socket.close();
      }
    }
    catch (Exception e)
    {
      master.logger.dbg("problem communicating with slave: " + e.getMessage());
    }
  }

  private void slavesStatusesThread()
  {
    try
    {
      master.logger.dbg("waiting for UDP slave statuses on " + master.settings.masterWaitingForSlavesUDPPort);
      DatagramSocket socket = new DatagramSocket(master.settings.masterWaitingForSlavesUDPPort);
      while (true)
      {
        byte[] buf = new byte[Defaults.maxDatagramSize];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        byte[] extractedPacket = Arrays.copyOfRange(buf, 0, packet.getLength());
        InetAddress slaveAddress = packet.getAddress();
        master.logger.dbg("slave status arrived");
        master.logger.s2m(extractedPacket);
        try { manageSlaveStatus(extractedPacket, slaveAddress); }
        catch (Exception e2)
        {
          master.logger.dbg("slave status failed: " + e2.getMessage());
        }
      }
    }
    catch (Exception e)
    {
      master.logger.dbg("problem receiving slave status: " + e.getMessage());
    }
  }

  private void manageSlaveStatus(byte [] packet, InetAddress ip) throws Exception
  {
    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new ByteArrayInputStream(packet));
    if (r.hasNext() != true)
      throw new Exception("Incorrect message from slave " + ip);
    r.next();
    if (!r.getLocalName().equals("SlaveStatus"))
      throw new Exception ("malformed SlaveStatus from slave " + ip);
    int taskID = -1;
    Integer slaveID = null;
    while (r.hasNext())
    {
      r.next();
      if (r.isStartElement() == true)
      {
        String tag = r.getLocalName();
      	if (tag.equals("SlaveID") == true)
          slaveID = Integer.valueOf(r.getElementText());
        else if (tag.equals("TaskID") == true)
          taskID = Integer.parseInt(r.getElementText());
      }
    }
    if (slaveID == null) throw new Exception("missing SlaveID in SlaveStatus");
    synchronized(master)
    {
      if (!slaves.containsKey(slaveID))
      {
        SlaveHandle.sendRestart(ip, master.settings.slavesWaitingForMasterTCPPort, slaveID);
        throw new Exception("ignoring SlaveStatus from non-existent slave " + slaveID +
                            " at " + ip + ", requesting restart");
      }
      SlaveHandle s = slaves.get(slaveID);
      if (!s.ip.equals(ip))
        throw new Exception("SlaveStatus - slave changed its IP");
      if (s.task != null)
      {
        if ((taskID < 0) && (s.task.state == TaskHandle.TaskState.running))
          taskCrashed(s);
        else if ((taskID > 0) && (s.task.state != TaskHandle.TaskState.running))
          throw new Exception("slave reports task running, but it should not.");
      }
      s.lastStatus = System.currentTimeMillis();
    }
  }

  private void taskCrashed(SlaveHandle s)
  {
    s.task.state = TaskHandle.TaskState.finished;
    s.task.result = "failed";
    s.task = null;
    master.reschedule();
  }

  void cancelTask(SlaveHandle slave, int taskID)
  {
    slave.sendCancelTask(taskID);
    slave.task = null;
  }
  
  private void manageSlaveReportOrLogin(Socket socket) throws Exception
  {
    InetAddress ip = socket.getInetAddress();
    InputStream is = socket.getInputStream();
    byte[] packet = Message.read(is);

    master.logger.s2m(packet);
    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new ByteArrayInputStream(packet));
    if (r.hasNext() != true) 
      throw new Exception("incorrect message from slave " + ip);
    r.next();
    if (r.getLocalName().equals("SlaveReport")) 
      manageSlaveReport(r, ip, is);
    else if (r.getLocalName().equals("SlaveInfo")) 
      manageSlaveLogin(r, ip, socket.getOutputStream());
    else 
      throw new Exception ("malformed SlaveReport from slave " + ip);
  }

  private void manageSlaveReport(XMLStreamReader r, InetAddress ip, InputStream is) throws Exception
  {
    Integer slaveID = null;
    String result = null;
    String errMsg = null;
    int taskID = -1;

    while (r.hasNext())
    {
      r.next();
      if (r.isStartElement() == true)
      {
        String tag = r.getLocalName();
        if (tag.equals("SlaveID") == true)
          slaveID = Integer.valueOf(r.getElementText());
        if (tag.equals("TaskID") == true)
          taskID = Integer.parseInt(r.getElementText());
        else if (tag.equals("Result") == true)
          result = r.getElementText();
        else if (tag.equals("ErrorMessage") == true)
          errMsg = r.getElementText();
      }
    }
    if (slaveID == null) throw new Exception("missing SlaveID in SlaveReport");
    if (taskID == -1) throw new Exception("missing TaskID in SlaveReport");

    synchronized(master)
    {
      if (!slaves.containsKey(slaveID))
        throw new Exception("ignoring SlaveReport from non-existent slave " + slaveID + " at " + ip);
      SlaveHandle s = slaves.get(slaveID);
      if (!s.ip.equals(ip))
        throw new Exception("SlaveReport - slave changed its IP");
      if ((s.task == null) || (s.task.state != TaskHandle.TaskState.running))
        throw new Exception("ingoring SlaveReport when task was not running.");

      if (s.task.taskID != taskID)
        throw new Exception("ignoring SlaveReport with incorrect TaskID.");
      if (result == null)
        throw new Exception("SlaveReport - missing result");
      TaskHandle finishedTask = s.task;
      if (result.equals("ok"))
      {
        ObjectInputStream ois = new ObjectInputStream(is);
        finishedTask.outputData = (ServiceOutput) ois.readObject();
        master.reporting.tasksOK++; 
      }
      else
      {
        finishedTask.outputData = null;
        master.reporting.tasksFailed++; 
      }

      finishedTask.state = TaskHandle.TaskState.finished;
      s.task = null;
      finishedTask.result = result;
      finishedTask.errorMessage = errMsg;
      master.reschedule();
    }
  }

  private void manageSlaveLogin(XMLStreamReader r, InetAddress ip, OutputStream os) throws Exception
  {
    slaveIDCounter++;
    SlaveHandle slvHandle = new SlaveHandle(master, slaveIDCounter, ip);

    while (r.hasNext())
    {
      r.next();
      if (r.isStartElement() == true)
      {
        String tag = r.getLocalName();
        if (tag.equals("Service") == true)
        {
          String version = r.getAttributeValue(null, "version");
          String service = r.getElementText();
          slvHandle.addService(service, version);
        }
      }
    }
    synchronized(this)
    {
      slaves.put(slaveIDCounter, slvHandle);

      String confirmSlaveInfo = "<ConfirmSlaveInfo>\n" +
                                "  <SlaveID>" + slvHandle.slaveID + "</SlaveID>\n" +
                                "</ConfirmSlaveInfo>";
      Message.write(os, confirmSlaveInfo.getBytes());
      master.logger.dbg("new slave from " + ip);
      master.logger.m2s(confirmSlaveInfo);
      master.reschedule();
    }
  }

  SlaveHandle findFreeSlaveForTask(TaskHandle task)
  {
    // first pass: exact version match, second pass: any version
    for (int pass = 1; pass < 3; pass++)
      for (Map.Entry<Integer,SlaveHandle> s : slaves.entrySet())
      {
        SlaveHandle slave = s.getValue();
        String slaveServiceVersion = slave.services.get(task.serviceName);
        if (slaveServiceVersion == null)
          continue; // service name does not match
        if (slave.task != null)
          continue;
        if (task.slavesThatRejected.contains(new Integer(slave.slaveID)))
          continue;

        if (pass == 1)
        {
          if (task.serviceVersion.equals(slaveServiceVersion))
            return slave;
        }
        else return slave;
      }
    return null;
  }

  void removeInactiveSlaves()
  {
    LinkedList<Integer> slavesToRemove = new LinkedList<Integer>();

    for (Map.Entry<Integer,SlaveHandle> s : slaves.entrySet())
    {
      SlaveHandle slave = s.getValue();
      if (System.currentTimeMillis() - slave.lastStatus >
          1000 * master.settings.masterRemoveInactiveSlavesDelay)
      {
        master.logger.dbg("Removing inactive slave " + slave.slaveID + " at " + slave.ip);
        if (slave.task != null)
        {
          slave.sendCancelTask(slave.task.taskID);
          slave.task.state = TaskHandle.TaskState.queued;
        }
        slavesToRemove.add(s.getKey());
      }
    }
    for (Integer i : slavesToRemove)
      slaves.remove(i);
  }
}
