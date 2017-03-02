package tinydc.slave;

import java.io.*;
import java.net.*;
import javax.xml.stream.*;

import tinydc.common.Message;
import tinydc.common.servicesdata.*;
import tinydc.slave.services.*;

public class Slave extends Thread implements ResultCollector
{
  Settings settings;
  private Services services;

  private Socket masterSocket;
  private OutputStream masterOutputStream;
  private InputStream masterInputStream;

  private String taskServiceName;
  private String taskServiceVersion;
  private Service taskService;
  
  int slaveID;
  int taskID;
  boolean taskRunning;

  private int newTaskID;
  private String newTaskServiceName;
  private String newTaskServiceVersion;

  private boolean restart;

  public Slave()
  {
    settings = new Settings();
    settings.readFromXMLFile();
    services = new Services(settings.services);
    taskRunning = false;
  }

  public static void main(String args[])
  {
    new Slave().start();
  }

  public void loginToMaster() throws Exception
  {
    connectToMaster();
      sendSlaveInfoToMaster();
      getSlaveIDFromMaster();
    disconnectFromMaster();    
  }

  public void run()
  {
    restart = false;
    synchronized(this)
    {
      try { loginToMaster(); }
      catch (Exception e)
      {
        System.err.println("Could not connect to master: " + e.getMessage());
      }
    }
    startReportingThread();
    waitForTasksAndExecuteThem();
  }

  public void waitForTasksAndExecuteThem()
  {
    ServerSocket srv = null;
    Socket socket = null;
    try
    {
      srv = new ServerSocket(settings.slavesWaitingForMasterTCPPort);
      while (true)
      {
        socket = srv.accept();
        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();
        try { manageMasterRequest(is, os); }
        catch (Exception e2)
        {
          if (restart) throw e2;
          System.out.println("warning: error processing request: " + e2.getMessage());
        }
        os.close();
        is.close();
        socket.close();
      }
    }
    catch (Exception e)
    {
      if (restart)
      {
        try
        {
          socket.close();
          srv.close();
        }
        catch (Exception e2) {}
        run();
      }
      else System.err.println("Problem receiving or processing request:" + e.getMessage());
    }
  }

  private void manageMasterRequest(InputStream is, OutputStream os) throws Exception
  {
    byte[] taskRequest = Message.read(is);

    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new ByteArrayInputStream(taskRequest));
    if (r.hasNext() != true)
      throw new Exception("incorrect message from master");
    r.next();
    if (r.getLocalName().equals("SlaveRestart"))
    {
      restart = true;
      throw new Exception("Restart slave");
    }
    if (r.getLocalName().equals("StartTask"))
    {
      if (extractTaskOptions(r))
      {
        if (taskRunning) rejectTask(os);
        else startTask(is, os);
      }
      else rejectTask(os);
    }
    else if (r.getLocalName().equals("StopTask"))
    {
      synchronized(this)
      {
        if (!taskRunning) return;
        int taskIDToStop = extractTaskID(r);
        if (taskIDToStop != taskID) return;
        taskService.stopTask();
      }
    }
    else
      throw new Exception("unrecognized request from master");
  }

  private boolean extractTaskOptions(XMLStreamReader r) throws Exception
  {
    boolean correctTask = true;
    newTaskID = -1;
    while (r.hasNext())
    {
      r.next();
      if (r.isStartElement() == true)
      {
        String tag = r.getLocalName();
        if (tag.equals("SlaveID") == true)
        {
          if (slaveID != Integer.parseInt(r.getElementText()))
            correctTask = false;
        }
        else if (tag.equals("TaskID") == true)
          newTaskID = Integer.parseInt(r.getElementText());
        else if (tag.equals("Service") == true)
        {          
          newTaskServiceVersion = r.getAttributeValue(null, "version");
          newTaskServiceName = r.getElementText();
        }
      }
    }
    if ((newTaskID == -1) || (newTaskServiceName == null) || (newTaskServiceVersion == null))
      throw new Exception("warning: task start request incomplete");
    return correctTask;
  }

  private int extractTaskID(XMLStreamReader r) throws Exception
  {
    while (r.hasNext())
    {
      r.next();
      if (r.isStartElement() == true)
      {
        String tag = r.getLocalName();
      	if (tag.equals("TaskID") == true)
        {
          return Integer.parseInt(r.getElementText());
        }
      }
    }
    throw new Exception("missing TaskID in master request");
  }

  private void rejectTask(OutputStream os) throws Exception
  {
    String message = "<TaskConfirm>\n" +
                     "  <TaskID>" + newTaskID + "</TaskID>\n" +
                     "  <Status>rejected</Status>\n" +
                     "</TaskConfirm>";
    byte[] packet = message.getBytes();
    Message.write(os, packet);
  }

  private synchronized void startTask(InputStream is, OutputStream os) throws Exception
  {
    ServiceInput taskData = null;

    taskServiceName = newTaskServiceName;
    taskServiceVersion = newTaskServiceVersion;
    taskID = newTaskID;

    taskService = services.services.get(taskServiceName);
    if (taskService == null) rejectTask(os);
    if ((!taskService.getServiceVersion().equals(taskServiceVersion)) &&
        (!taskServiceVersion.equals("*")))
      if (!taskService.canRunAlsoTasksOf(taskServiceVersion))
      {
        rejectTask(os);
        return;
      }

    try
    {
      ObjectInputStream ois = new ObjectInputStream(is);
      taskData = (ServiceInput)ois.readObject();
    }
    catch (Exception e)
    {
      rejectTask(os);
      return;
    }
    
    taskRunning = true;

    String message = "<TaskConfirm>\n" +
                     "  <TaskID>" + taskID + "</TaskID>\n" +
                     "  <Status>accepted</Status>\n" +
                     "</TaskConfirm>";
    byte[] taskConfirmPacket = message.getBytes();
    Message.write(os, taskConfirmPacket);

    taskService.runTask(this, taskData);
  }

  private void sendSlaveInfoToMaster() throws Exception
  {
    StringBuffer message = new StringBuffer("<SlaveInfo>\n  <Services>\n");
    for (Service s : services.services.values())
      message.append("    <Service version=\"" + s.getServiceVersion() + "\">" +
                             s.getServiceName() + "</Service>\n");
    message.append("  </Services>\n</SlaveInfo>");
    byte[] slaveInfoPacket = message.toString().getBytes();
    Message.write(masterOutputStream, slaveInfoPacket);
  }

  private void getSlaveIDFromMaster() throws Exception
  {
    byte[] confirmSlaveInfo = Message.read(masterInputStream);

    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new ByteArrayInputStream(confirmSlaveInfo));
    if (r.hasNext() != true)
        throw new Exception("master did not respond properly to SlaveInfo");
    r.next();
    if (!r.getLocalName().equals("ConfirmSlaveInfo"))
        throw new Exception("master did not respond properly to SlaveInfo");
    while (r.hasNext())
    {
      r.next();
      if (r.isStartElement() == true)
      	if (r.getLocalName().equals("SlaveID") == true)
        {
          slaveID = Integer.parseInt(r.getElementText());
          break;
        }
    }
  }

  private PresenceReporter presenceReporter;
  private void startReportingThread()
  {
    if (presenceReporter == null)
    {
      presenceReporter = new PresenceReporter(this);
      presenceReporter.start();
    }
  }

  private void connectToMaster()
  {
    boolean retry;
    do {
      retry = false;
      try
      {
        masterSocket = new Socket(settings.masterIP, settings.masterWaitingForSlavesTCPPort);
        masterOutputStream = masterSocket.getOutputStream();
        masterInputStream = masterSocket.getInputStream();
      }
      catch (UnknownHostException e)
      {
        System.err.println("could not connect to master at " + settings.masterIP + ":" +
                             settings.masterWaitingForSlavesTCPPort + " - " + e.getMessage());
        System.exit(1);
      }
      catch (ConnectException e)
      {
        System.err.println("connection to master failed. Is master running?");
        try { Thread.sleep(1000 * settings.slaveStatusPeriod); }
        catch (InterruptedException e2) { }
        retry = true;
      }
      catch (IOException e)
      {
        System.err.println("Problem connecting to master.");
        e.printStackTrace();
        System.exit(1);
      }
    } while (retry);
  }

  private void disconnectFromMaster()
  {
    try
    {
      masterOutputStream.close();
      masterInputStream.close();
      masterSocket.close();
      masterOutputStream = null;
      masterInputStream = null;
      masterSocket = null;
    }
    catch (IOException e)
    {
      System.err.println("Problem closing connection to master.");
      e.printStackTrace();
    }
  }

  public synchronized void taskCompleted(ServiceOutput outputData)
  {
    connectToMaster();
      sendSlaveReportToMaster("ok", null, outputData);
    disconnectFromMaster();

    taskRunning = false;
  }

  public synchronized void taskFailed(String error)
  {
    connectToMaster();
      sendSlaveReportToMaster("failed", error, null);
    disconnectFromMaster();

    taskRunning = false;
  }

  private void sendSlaveReportToMaster(String result, String error, ServiceOutput outputData)
  {
    String msg = "<SlaveReport>\n" +
                 "  <SlaveID>" + slaveID + "</SlaveID>\n" +
                 "  <TaskID>" + taskID + "</TaskID>\n" +
                 "  <Result>" + result + "</Result>\n" +
                 ((error != null) ? ("<ErrorMessage>" + error + "</ErrorMessage>\n") : "") +
                 "</SlaveReport>";
    byte [] packet = msg.getBytes();
    try
    {
      Message.write(masterOutputStream, packet);
      Message.writeObjectIfNotNull(masterOutputStream, outputData);
    }
    catch (Exception e)
    {
      System.err.println("Problem sending SlaveReport to master!");
      e.printStackTrace();
      System.exit(1);
    }
  }
}
