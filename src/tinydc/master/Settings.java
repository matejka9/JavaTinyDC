package tinydc.master;

import java.io.*;
import javax.xml.stream.*;

import tinydc.common.*;

public class Settings
{
  public int masterWaitingForSlavesUDPPort;
  public int masterWaitingForSlavesTCPPort;
  public int masterWaitingForClientsTCPPort;
  public int slavesWaitingForMasterTCPPort;
  public int masterRemoveInactiveSlavesDelay;
  String masterMessageLogFileName;
  boolean masterMessageLogOn;
  boolean masterDebuggingOn;
  String htmlReportFileName;
  int htmlReportFrequency;

  public static final String masterSettingsFileName = "MasterConfig.xml";

  private Master master;

  public Settings(Master master)
  {
    masterWaitingForSlavesUDPPort = Defaults.masterWaitingForSlavesUDPPort;
    masterWaitingForSlavesTCPPort = Defaults.masterWaitingForSlavesTCPPort;
    masterWaitingForClientsTCPPort = Defaults.masterWaitingForClientsTCPPort;
    slavesWaitingForMasterTCPPort = Defaults.slavesWaitingForMasterTCPPort;
    masterRemoveInactiveSlavesDelay = Defaults.masterRemoveInactiveSlavesDelay;
    htmlReportFileName = Defaults.htmlReportFileName;
    htmlReportFrequency = Defaults.htmlReportFrequency;
    masterMessageLogFileName = Defaults.masterMessageLogFileName;
    masterMessageLogOn = Defaults.masterMessageLogOn;
    masterDebuggingOn = Defaults.masterDebuggingOn;

    this.master = master;
  }

  public void readFromXMLFileOrThrow() throws XMLStreamException, FileNotFoundException
  {
    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new FileReader(masterSettingsFileName));

    while (r.hasNext() == true)
    {
      r.next();
      if (r.isStartElement() == false) continue;
      String tag = r.getLocalName();
      if (tag.equals("MasterWaitingForSlavesUDPPort") == true)
        masterWaitingForSlavesUDPPort = Integer.parseInt( r.getElementText() );
      else if (tag.equals("MasterWaitingForSlavesTCPPort") == true)
        masterWaitingForSlavesTCPPort = Integer.parseInt( r.getElementText() );
      else if (tag.equals("MasterWaitingForClientsTCPPort") == true)
        masterWaitingForClientsTCPPort = Integer.parseInt( r.getElementText() );
      else if (tag.equals("SlavesWaitingForMasterTCPPort") == true)
        slavesWaitingForMasterTCPPort = Integer.parseInt( r.getElementText() );
      else if (tag.endsWith("MasterRemoveInactiveSlavesDelay") == true)
        masterRemoveInactiveSlavesDelay = Integer.parseInt( r.getElementText() );
      else if (tag.endsWith("HtmlReportFileName") == true)
        htmlReportFileName = r.getElementText();
      else if (tag.endsWith("HtmlReportFrequency") == true)
        htmlReportFrequency = Integer.parseInt( r.getElementText() );
      else if (tag.endsWith("MasterMessageLogFileName") == true)
        masterMessageLogFileName = r.getElementText();
      else if (tag.endsWith("MasterMessageLogOn") == true)
        masterMessageLogOn = Boolean.parseBoolean( r.getElementText() );
      else if (tag.endsWith("MasterDebuggingOn") == true)
        masterDebuggingOn = Boolean.parseBoolean( r.getElementText() );
    }
  }

  public void readFromXMLFile()
  {
    try
    {
      readFromXMLFileOrThrow();
    }
    catch (XMLStreamException e)
    {
        System.err.println("there was a problem reading file " + masterSettingsFileName + e.getMessage());
        System.err.println("default settings will be used instead");
    }
    catch (FileNotFoundException e)
    {
        System.err.println("warning: the master settings file " + masterSettingsFileName + " was not found");
        System.err.println("default settings will be used instead");
    }
  }
}
