package tinydc.master;

import java.io.*;
import java.util.*;

import tinydc.common.*;

public class Reporting
{
  int tasksSubmitted = 0;
  int tasksOK = 0;
  int tasksFailed = 0;
  int tasksCancelled = 0;

  private final Master master;
  private String runningSince;

  private String htmlString;

  Reporting(Master master)
  {
    this.master = master;
    runningSince = Calendar.getInstance().getTime().toString();
  }

  void doIt()
  {
    synchronized(master)
    {
      prepareHtmlReport();
    }
    try
    {
      saveHTML(master.settings.htmlReportFileName);
    }
    catch (Exception e)
    {
      master.logger.dbg("could not save HTML report: " + e.getMessage());
    }
  }

  private void prepareHtmlReport()
  {
    StringBuffer htmlDocument = new StringBuffer( 
     "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
     "<html xmlns=\"http://www.w3.org/1999/xhtml\">" +
     "<head>\n" +
     "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />" +
     "<title>TinyDC Master Status Report</title>\n " +
     "</head>\n<body>\n<h3><font face=\"Arial,Verdana,Helvetica\">TinyDC Master version " + Defaults.version +
       " running since " + runningSince + "</font></h3>\n" +
     "<font face=\"Arial,Verdana,Helvetica\">" +
     "(<font size=\"-1\">updated every " + master.settings.htmlReportFrequency + "s, current time: " + Calendar.getInstance().getTime().toString() + "</font>)<br/><br/>" +
     "List of active tasks:<br/><br/><pre>\n" +
     " taskID     state     slaveID    jobID\n" +
     "---------------------------------------------------------\n");

    for (Map.Entry<String,JobHandle> j : master.jobsManagement.jobs.entrySet())
      for (Map.Entry<Integer,TaskHandle> t : j.getValue().myTasks.entrySet())
      {
        int taskID = t.getKey();
        TaskHandle task = t.getValue();
        String slaveID = (task.slave == null) ? "           " : (String.format("%11d", task.slave.slaveID));
        htmlDocument.append(
         String.format("%7d %9s %s    %s\n", taskID, task.stateAsString(), slaveID, j.getKey()));
      }  
    htmlDocument.append("</pre><br/>List of active slaves:<br/><br/><pre>\n" +
                              " slaveID               IP    since last status     taskID\n" +
                              "---------------------------------------------------------\n");

    for (Map.Entry<Integer,SlaveHandle> s : master.slavesManagement.slaves.entrySet())
    {
      int slaveID = s.getKey();
      SlaveHandle slave = s.getValue();
      String taskID = (slave.task == null) ? "           " : (String.format("%11d", slave.task.taskID));
      htmlDocument.append(String.format("%8d %16s %11d s  %s\n", slaveID, slave.ip.toString(),
                                          (System.currentTimeMillis() - slave.lastStatus) / 1000,
                                          taskID));
    }

    htmlDocument.append("</pre><br/><br/>");

    htmlDocument.append("Total tasks:<br/><pre>\n");
    htmlDocument.append("&nbsp;&nbsp;submitted: " + tasksSubmitted + "\n");
    htmlDocument.append("&nbsp;&nbsp;       ok: " + tasksOK + "\n");
    htmlDocument.append("&nbsp;&nbsp;   failed: " + tasksFailed + "\n");
    htmlDocument.append("&nbsp;&nbsp;cancelled: " + tasksCancelled + "\n");
    htmlDocument.append("&nbsp;&nbsp;   queued: " +
                        (tasksSubmitted - (tasksOK + tasksFailed + tasksCancelled)) + "\n");

    htmlDocument.append("</pre></font></body></html>");

    htmlString = htmlDocument.toString();
  }

  private void saveHTML(String fileName) throws Exception
  {
    try
    {
      FileWriter ow = new FileWriter(fileName);
      ow.write(htmlString);
      ow.close();
    }
    catch (IOException e)
    {
      throw new Exception("problem saving HTML report to " + fileName + ": " + e.getMessage());
    }
  }
}
