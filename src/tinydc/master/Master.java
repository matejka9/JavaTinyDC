package tinydc.master;

import java.util.*;

public class Master extends Thread
{
  private TasksManagement tasksManagement;
  private ClientsManagement clientsManagement;

  JobsManagement jobsManagement;
  SlavesManagement slavesManagement;
  
  final Settings settings;
  final Reporting reporting;
  final MessageLogger logger;

  public Master()
  {
    settings = new Settings(this);
    settings.readFromXMLFile();

    reporting = new Reporting(this);
    logger = new MessageLogger(settings.masterMessageLogFileName);
    if (settings.masterMessageLogOn) logger.turnOn();
    if (settings.masterDebuggingOn) logger.turnDbgOn();

    slavesManagement = new SlavesManagement(this);
    tasksManagement = new TasksManagement(this, slavesManagement);
    jobsManagement = new JobsManagement(this, tasksManagement);
    clientsManagement = new ClientsManagement(this, jobsManagement);
  }

  public static void main(String args[])
  {
    new Master().start();
  }

  public void run()
  {
    do {
      reporting.doIt();
      try { Thread.sleep(1000 * settings.htmlReportFrequency); }
      catch (InterruptedException e) {}
    } while (true);
  }

  void reschedule()
  {
    boolean repeatReschedule;

    slavesManagement.removeInactiveSlaves();

    do {
      repeatReschedule = false;
      // taskID is chronological, i.e. we avoid starvation
      for (Map.Entry<Integer,TaskHandle> t : tasksManagement.tasks.entrySet())
      {
        TaskHandle task = t.getValue();
        if (task.state == TaskHandle.TaskState.queued)
        {
          SlaveHandle freeSlave = slavesManagement.findFreeSlaveForTask(task);
          if (freeSlave != null) 
            if (freeSlave.submitTask(task) == false)
              repeatReschedule = true;
        }
      }
      if (repeatReschedule) 
        try { Thread.sleep(100); } catch (InterruptedException e) {}
    } while (repeatReschedule);
    reporting.doIt();
  }
}
