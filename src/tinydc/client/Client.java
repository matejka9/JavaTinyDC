package tinydc.client;

import java.io.*;
import java.util.*;
import java.net.*;
import javax.xml.stream.*;

import tinydc.common.servicesdata.*;
import tinydc.common.*;

public class Client extends Thread
{
  private boolean consoleMenu;
  private String jobID;
  private static final String NOJOBID = "(none)";

  private Settings settings;

  private Socket masterSocket;
  private InputStream masterInputStream;
  private OutputStream masterOutputStream;

  private String taskResult;
  private String errorMessage;

  public Client(String args[])
  {
    jobID = NOJOBID;
    settings = new Settings();
    settings.readFromXMLFile();
    parseArguments(args);
  }

  public static void main(String args[])
  {
    Client c = new Client(args);
    c.start();
  }

  private void parseArguments(String args[])
  {
    if (args.length == 0) consoleMenu = true;
    else consoleMenu = false;

    //TODO: parse your arguments here
    for (int i = 0; i < args.length; i++)
    {

    }
  }

  public void run()
  {
    if (consoleMenu) consoleMenu();
    //TODO: other ways to run the client
  }

  private void consoleMenu()
  {
    int choice = 0;
    int taskID;

    try 
    {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      do
      {
        printMenu();
        try
        {
          choice = Integer.parseInt(in.readLine());
        }
        catch (NumberFormatException e)
        {
          continue;
        }
        finally
        {
          System.out.println("");
        }
        if ((choice > SETJOBID) && (jobID.equals("(none)")))
        {
          System.out.println("Please set the job id first.");
          continue;
        }
        switch (choice)
        {
          case SETJOBID:      System.out.print("Enter new job id: ");
                              jobID = in.readLine();
                              break;
          case SUBMITNEWTASK: TaskService service = readServiceFromConsole();
                              if (service != null)
                              {
                                taskID = submitClientTask(jobID, service);
                                System.out.println("Submitted new task identified as taskID=" + taskID);
                              }
                              break;
          case GETTASKSTATUS: System.out.print("Enter taskID: ");
                              taskID = Integer.parseInt(in.readLine());
                              String status = clientTaskRequest("status", taskID);
                              System.out.println("Task " + taskID + " status: " + status);
                              break;
          case CANCELTASK:    System.out.print("Task to cancel has taskID: ");
                              taskID = Integer.parseInt(in.readLine());
                              String wasCancelled = clientTaskRequest("cancel", taskID);
                              System.out.println("Request to cancel task sent, response: task " + wasCancelled);
                              break;
          case COLLECTTASK:   System.out.print("Task to collect has taskID: ");
                              taskID = Integer.parseInt(in.readLine());
                              ServiceOutput output = collectTask(taskID);
                              System.out.println("Task result: " + taskResult);
                              if ("failed".equals(taskResult))
                                System.out.println("Error: " + errorMessage);
                              if (output != null) output.printToConsole();
                              break;
          case LISTTASKS:     TreeMap<Integer,String> tasks = getAllTasksForCurrentJob();
                              System.out.println("List of tasks for job " + jobID + "\n- - - - - - - - - -");
                              for (Map.Entry<Integer,String> te : tasks.entrySet())
                                System.out.println(te.getKey() + ": " + te.getValue());
                              System.out.println("- - - - - - - - - -");
                              break;
        }
      } while (choice != EXIT);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  private TaskService readServiceFromConsole()
  {
    int n;
    int selectedService;

    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    System.out.println("Select required service:\n");
    n = 1;
    for (String s:settings.services)
      System.out.println(" " + (n++) + ") " + s);
    System.out.println(" 0) cancel\n");
    System.out.print("> ");
    try
    {
      selectedService = Integer.parseInt(in.readLine());
      System.out.println("");
    }
    catch (Exception e)
    {
      selectedService = 0;
    }
    if ((selectedService < 1) || (selectedService >= n))
       return null;
    
    TaskService service = new TaskService();
    service.name = settings.services.get(selectedService - 1);
    System.out.print("Required version for the service [ENTER=*]: ");
    String fileName = null;
    try
    {
      service.version = in.readLine();
      if (service.version.length() == 0) service.version = "*";
      System.out.print("File name with binary input data: [ENTER=enter from console]: ");
      fileName = in.readLine();
    }
    catch (Exception e)
    {
      System.out.println("Error. Try again.");
      return null;
    }
    if (fileName.length() != 0)
    {
      try
      {
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileName));
        service.input = (ServiceInput) ois.readObject();
        ois.close();
      }
      catch (Exception e)
      {
        System.out.println("Problem reading data from the specfied file: " + e.getMessage());
        return null;
      }
    }
    else
    {
      try
      {
        service.input = (ServiceInput) Class.forName("tinydc.common.servicesdata." + service.name + "Input").newInstance();
      }
      catch (Exception e)
      {
        System.out.println("Could not find or initialize input data class for service " + service.name);
        System.out.println("Please correct your client configuration.");
        return null;
      }

      if (service.input.initializeFromConsole() == false)
        if (service.input.initializeDefault() == false)
        {
          System.out.println("Sorry, a task without data cannot be submitted.");
          return null;
        }
    }
    return service;
  }

  private int submitClientTask(String jobID, TaskService service) throws Exception
  {
    String message = "<ClientTask>\n" +
                     "  <JobID>" + jobID + "</JobID>\n" +
                     "  <Service version=\"" + service.version + "\">" + service.name + "</Service>\n" +
                     "</ClientTask>";
    byte [] packet = message.getBytes();

    connectToMaster();

      Message.write(masterOutputStream, packet);
      Message.writeObjectIfNotNull(masterOutputStream, service.input);
      
      packet = Message.read(masterInputStream);
      int taskID = processConfirm(packet);

    disconnectFromMaster();
    return taskID;
  }

  private void connectToMaster() throws Exception
  {
    masterSocket = new Socket(settings.masterIP, settings.masterWaitingForClientsTCPPort);
    masterOutputStream = masterSocket.getOutputStream();
    masterInputStream = masterSocket.getInputStream();
  }
  
  private int processConfirm(byte [] packet) throws Exception
  {
    int taskID = -1;
    boolean jobIDVerified = false;
            
    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new ByteArrayInputStream(packet));
    if (r.hasNext() != true)
      throw new Exception("Incorrect message from master");
    r.next();
    if (r.getLocalName().equals("ConfirmClientTask"))
    {
      while (r.hasNext())
      {
        r.next();
        if (r.isStartElement() == true)
        {
          String tag = r.getLocalName();
      	  if (tag.equals("TaskID") == true)
            taskID = Integer.parseInt(r.getElementText());
          else if (tag.equals("JobID") == true)
          {
            if (!jobID.equals(r.getElementText()))
              throw new Exception("Incorrect jobID in master response");
            else
              jobIDVerified = true;
          }
        }
      }
      if (!jobIDVerified) throw new Exception("Missing jobID in master response");
      if (taskID < 0) throw new Exception("Missing TaskID in master request");
      return taskID;
    }
    throw new Exception("Unexpected response from master: " + r.getLocalName());
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
      System.out.println("Problem closing connection to master.");
      e.printStackTrace();
    }
  }

  private String clientTaskRequest(String request, int taskID) throws Exception
  {
    String message = "<ClientTaskRequest>\n" +
                     "  <JobID>" + jobID + "</JobID>\n" +
                     "  <TaskID>" + taskID + "</TaskID>\n" +
                     "  <Operation>" + request + "</Operation>\n" +
                     "</ClientTaskRequest>";
    byte [] packet = message.getBytes();

    connectToMaster();
      Message.write(masterOutputStream, packet);
      packet = Message.read(masterInputStream);
      String status = processTaskStatus(packet, taskID);
    disconnectFromMaster();
    return status;
  }

  private String processTaskStatus(byte [] packet, int taskID) throws Exception
  {
    boolean taskIDVerified = false;
    String status = null;

    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new ByteArrayInputStream(packet));
    if (r.hasNext() != true)
      throw new Exception("Incorrect message from master");
    r.next();
    if (r.getLocalName().equals("MasterTaskStatus"))
    {
      while (r.hasNext())
      {
        r.next();
        if (r.isStartElement() == true)
        {
          String tag = r.getLocalName();
      	  if (tag.equals("Status") == true)
            status = r.getElementText();
          else if (tag.equals("TaskID") == true)
          {
            if (taskID != Integer.parseInt(r.getElementText()))
              throw new Exception("Incorrect taskID in master response");
            else
              taskIDVerified = true;
          }
        }
      }
      if (taskIDVerified == false) throw new Exception("Missing taskID in task status");
      if (status == null) throw new Exception("Missing status in task status");
      return status;
    }
    throw new Exception("Unexpected response from Master: " + r.getLocalName());
  }

  private ServiceOutput collectTask(int taskID) throws Exception
  {
    ServiceOutput output = null;

    String collectTask = "<CollectTask>\n" +
                         "  <JobID>" + jobID + "</JobID>\n" + 
                         "  <TaskID>" + taskID + "</TaskID>\n" +
                         "</CollectTask>";
    byte [] packet = collectTask.getBytes();
    connectToMaster();
      Message.write(masterOutputStream, packet);
      packet = Message.read(masterInputStream);
      processTaskReport(packet, taskID);
      if ("ok".equals(taskResult))
      {
        ObjectInputStream ois = new ObjectInputStream(masterInputStream);
        output = (ServiceOutput) ois.readObject();
        ois.close();

        long tm = System.currentTimeMillis();
        Message.writeObjectIfNotNull(new FileOutputStream(jobID + "." + taskID + "." +
                                          tm + "." + output.getClass().getSimpleName()),
                                     output);
      }
    disconnectFromMaster();
    return output;
  }

  private void processTaskReport(byte [] packet, int taskID) throws Exception
  {
    boolean taskIDVerified = false;
    taskResult = null;
    errorMessage = null;

    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new ByteArrayInputStream(packet));
    if (r.hasNext() != true)
      throw new Exception("Incorrect message from master");
    r.next();
    if (r.getLocalName().equals("TaskReport"))
    {
      while (r.hasNext())
      {
        r.next();
        if (r.isStartElement() == true)
        {
          String tag = r.getLocalName();
      	  if (tag.equals("Result") == true)
            taskResult = r.getElementText();
          else if (tag.equals("ErrorMessage") == true)
            errorMessage = r.getElementText();
          else if (tag.equals("TaskID") == true)
          {
            if (taskID != Integer.parseInt(r.getElementText()))
              throw new Exception("Incorrect taskID in task report");
            else
              taskIDVerified = true;
          }
        }
      }
      if (taskIDVerified == false) throw new Exception("Missing taskID in task report");
      if (taskResult == null) throw new Exception("Missing status in task report");
      return;
    }
    throw new Exception("Unexpected response from Master: " + r.getLocalName());
  }

  private TreeMap<Integer,String> getAllTasksForCurrentJob() throws Exception
  {
    connectToMaster();
      String message = "<ClientTaskList>\n" +
                       "  <JobID>" + jobID + "</JobID>\n" +
                       "</ClientTaskList>";
      byte [] packet = message.getBytes();
      Message.write(masterOutputStream, packet);
      packet = Message.read(masterInputStream);
    disconnectFromMaster();
    return processTaskList(packet);
  }

  private TreeMap<Integer,String> processTaskList(byte [] packet) throws Exception
  {
    boolean jobIDVerified = false;
    TreeMap<Integer,String> taskList = new TreeMap<Integer,String>();
    int taskID = -1;
    String taskStatus = null;

    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new ByteArrayInputStream(packet));
    if (r.hasNext() != true)
      throw new Exception("Incorrect message from master");
    r.next();
    if (r.getLocalName().equals("MasterTaskList"))
    {
      while (r.hasNext())
      {
        r.next();
        if (r.isStartElement() == true)
        {
          String tag = r.getLocalName();
          if (tag.equals("TaskID") == true)
            taskID = Integer.parseInt(r.getElementText());
          else if (tag.equals("Status") == true)
            taskStatus = r.getElementText();
          else if (tag.equals("JobID") == true)
          {
            if (!jobID.equals(r.getElementText()))
              throw new Exception("Incorrect JobID in task list");
            else
              jobIDVerified = true;
          }
        }
        else if (r.isEndElement() == true)
        {
          String tag = r.getLocalName();
          if (tag.equals("MasterTaskStatus"))
          {
            if ((taskID < 0) || (taskStatus == null))
              throw new Exception("Error parsing task list");
            taskList.put(new Integer(taskID), taskStatus);
            taskID = -1;
            taskStatus = null;
          }
        }
      }
      if (jobIDVerified == false) throw new Exception("Missing jobID in task list");
      return taskList;
    }
    throw new Exception("Unexpected response from Master: " + r.getLocalName());
  }

  private static final int SETJOBID = 1;
  private static final int SUBMITNEWTASK = 2;
  private static final int GETTASKSTATUS = 3;
  private static final int CANCELTASK = 4;
  private static final int COLLECTTASK = 5;
  private static final int LISTTASKS = 6;
  private static final int EXIT = 0;

  private void printMenu()
  {
    System.out.print("\nTinyDC client, version " + Defaults.version + "\n\n ");
    System.out.print("current job: " + jobID + "\n\n ");
    System.out.print(SETJOBID + ") change job\n ");
    System.out.print(SUBMITNEWTASK + ") submit new task\n ");
    System.out.print(GETTASKSTATUS + ") get status of a submitted task\n ");
    System.out.print(CANCELTASK + ") cancel a submitted task\n ");
    System.out.print(COLLECTTASK + ") collect finished task\n ");
    System.out.print(LISTTASKS + ") list my submitted task\n ");
    System.out.print(EXIT + ") exit\n\n");
    System.out.print("> ");
  }

}
