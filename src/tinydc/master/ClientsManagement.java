package tinydc.master;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.stream.*;

import tinydc.common.*;
import tinydc.common.servicesdata.*;

public class ClientsManagement extends Thread
{
  private final Master master;
  private final JobsManagement jobs;

  public ClientsManagement(Master master, JobsManagement jobs)
  {
    this.master = master;
    this.jobs = jobs;
    this.start();
  }

  public void run()
  {
    ServerSocket srv;
    try
    {
      master.logger.dbg("waiting for clients on " + master.settings.masterWaitingForClientsTCPPort);
      srv = new ServerSocket(master.settings.masterWaitingForClientsTCPPort);
      while (true)
      {
	Socket socket = srv.accept();
        master.logger.dbg("client request arriving");
        try { manageClientRequest(socket); }
        catch (Exception e2)
        { master.logger.dbg("client request failed: " + e2.getMessage()); }
        socket.close();
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  public void manageClientRequest(Socket socket) throws Exception
  {
    InputStream is = socket.getInputStream();
    OutputStream os = socket.getOutputStream();
    byte[] packet = Message.read(is);
    master.logger.c2m(packet);

    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new ByteArrayInputStream(packet));
    if (r.hasNext() != true)
      throw new Exception("incorrect message from client.");
    r.next();
    String tag = r.getLocalName();
    if ("ClientTask".equals(tag))
      manageClientTask(r, is, os);
    else if ("ClientTaskRequest".equals(tag))
      manageTaskRequest(r, os);
    else if ("ClientTaskList".equals(tag))
      manageTaskList(r, os);
    else if ("CollectTask".equals(tag))
      manageCollectTask(r, os);
    else
      throw new Exception("malformed request from client: " + tag);
   
    is.close();
    os.close();
  }
  
  public void manageClientTask(XMLStreamReader r, InputStream is, OutputStream os) throws Exception
  {
    String jobID = null;
    String serviceName = null;
    String serviceVersion = null;

    while (r.hasNext())
    {
      r.next();
      if (r.isStartElement() == true)
      {
        String tag = r.getLocalName();
      	if (tag.equals("JobID") == true)
          jobID = r.getElementText();
        else if (tag.equals("Service") == true)
        {
          serviceVersion = r.getAttributeValue(null, "version");
          serviceName = r.getElementText();
        }
      }
    }
    if (jobID == null) throw new Exception("missing JobID in ClientTask");
    if (serviceName == null) throw new Exception("missing Service in ClientTask");
    if (serviceVersion == null) throw new Exception("missing service version in ClientTask");
    synchronized(master)
    {
      JobHandle job = jobs.getOrAdd(jobID);
      ObjectInputStream ois = new ObjectInputStream(is);
      ServiceInput taskInputData = (ServiceInput)ois.readObject();
      int taskID = job.newTask(serviceName, serviceVersion, taskInputData);
      String confirmation = "<ConfirmClientTask>\n" +
                            "  <JobID>" + jobID + "</JobID>\n" +
                            "  <TaskID>" + taskID + "</TaskID>\n" +
                            "</ConfirmClientTask>";
      Message.write(os, confirmation.getBytes());
      ois.close();
      master.logger.m2c(confirmation);
      master.reporting.tasksSubmitted++;
      master.reschedule();
    }
  }

  public void manageTaskRequest(XMLStreamReader r, OutputStream os) throws Exception
  {
    int taskID = -1;
    String jobID = null;
    String operation = null;

    while (r.hasNext())
    {
      r.next();
      if (r.isStartElement() == true)
      {
        String tag = r.getLocalName();
        if (tag.equals("JobID") == true)
          jobID = r.getElementText();
        else if (tag.equals("TaskID") == true)
          taskID = Integer.parseInt(r.getElementText());
        else if (tag.equals("Operation") == true)
          operation = r.getElementText();
      }
    }
    if (taskID == -1) throw new Exception("missing TaskID in ClientTaskRequest");
    if (jobID == null) throw new Exception("missing JobID in ClientTaskRequest");
    if (operation == null) throw new Exception("missing Operation in ClientTaskRequest");
    synchronized(master)
    {
      JobHandle job = jobs.jobs.get(jobID);
      TaskHandle task = null;
      if (job != null) task = job.myTasks.get(new Integer(taskID));
      String message = "<MasterTaskStatus>\n" +
                       "  <TaskID>" + taskID + "</TaskID>\n" +
                       "  <Status>";
      if ((job == null) || (task == null))
        message += "missing";
      else
      {
        if (operation.equals("status"))
          message += task.stateAsString();
        else if (operation.equals("cancel"))
        {
          job.cancelTask(taskID);
          message += "cancelled";
        }
        else throw new Exception("unknown operation in ClientTaskRequest");
      }
      message += "</Status>\n";
      message += "</MasterTaskStatus>";
      Message.write(os, message.getBytes());
      master.logger.m2c(message);
    }
  }

  public void manageTaskList(XMLStreamReader r, OutputStream os) throws Exception
  {
    String jobID = null;
    while (r.hasNext())
    {
      r.next();
      if (r.isStartElement() == true)
      {
        String tag = r.getLocalName();
      	if (tag.equals("JobID") == true)
          jobID = r.getElementText();
      }
    }
    if (jobID == null) throw new Exception("missing JobID in ClientTaskList");

    synchronized(master)
    {
      JobHandle job = jobs.jobs.get(jobID);
      String message = "<MasterTaskList>\n" +
                       "  <JobID>" + jobID + "</JobID>\n";
      if (job != null)
        for (Map.Entry<Integer,TaskHandle> t:job.myTasks.entrySet())
        {
          message += "  <MasterTaskStatus>\n" +
                     "    <TaskID>" + t.getKey() + "</TaskID>\n" +
                     "    <Status>" + t.getValue().stateAsString() + "</Status>\n" +
                     "  </MasterTaskStatus>\n";
        }
      message += "</MasterTaskList>";
      Message.write(os, message.getBytes());
      master.logger.m2c(message);
    }
  }

  public void manageCollectTask(XMLStreamReader r, OutputStream os) throws Exception
  {
    String jobID = null;
    int taskID = -1;
    while (r.hasNext())
    {
      r.next();
      if (r.isStartElement() == true)
      {
        String tag = r.getLocalName();
        if (tag.equals("JobID") == true)
          jobID = r.getElementText();
        else if (tag.equals("TaskID") == true)
          taskID = Integer.parseInt(r.getElementText());
      }
    }
    if (jobID == null) throw new Exception("missing JobID in CollectTask");
    if (taskID < 0) throw new Exception("missing TaskID in CollectTask");

    synchronized(master)
    {
      JobHandle job = jobs.jobs.get(jobID);
      TaskHandle task = null;
      if (job != null) task = job.tasks.tasks.get(new Integer(taskID));
      boolean appendData = false;

      String message = "<TaskReport>\n" +
                       "  <TaskID>" + taskID + "</TaskID>\n" +
                       "  <Result>";
      if ((job == null) || (task == null))
        message += "missing</Result>\n";
      else if (task.state != TaskHandle.TaskState.finished)
        message += task.stateAsString() + "</Result>\n";
      else
      {
        if ("ok".equals(task.result))
        {
          message += "ok</Result>\n";
          appendData = true;
        }
        else
          message += "failed</Result>\n" + "  <ErrorMessage>" + task.errorMessage + "</ErrorMessage>\n";
        job.tasks.removeTask(taskID);
        job.myTasks.remove(new Integer(taskID));
      }
      message += "</TaskReport>";

      Message.write(os, message.getBytes());
      if (appendData)
        Message.writeObjectIfNotNull(os, task.outputData);
      master.logger.m2c(message);
      master.reporting.doIt();
    }
  } 
}
