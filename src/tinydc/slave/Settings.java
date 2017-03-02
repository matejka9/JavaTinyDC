package tinydc.slave;

import java.io.*;
import java.util.*;
import javax.xml.stream.*;

import tinydc.common.*;

public class Settings
{
  public static final String slaveSettingsFileName = "SlaveConfig.xml";

  public int masterWaitingForSlavesUDPPort;
  public int masterWaitingForSlavesTCPPort;
  public int slavesWaitingForMasterTCPPort;
  public int slaveStatusPeriod;
  public String masterIP;

  public List<String> services;

  public Settings()
  {
    masterIP = Defaults.masterIP;
    masterWaitingForSlavesUDPPort = Defaults.masterWaitingForSlavesUDPPort;
    masterWaitingForSlavesTCPPort = Defaults.masterWaitingForSlavesTCPPort;
    slavesWaitingForMasterTCPPort = Defaults.slavesWaitingForMasterTCPPort;
    slaveStatusPeriod = Defaults.slaveStatusPeriod;
    services = new Stack<String>();
  }

  public void readFromXMLFileOrThrow() throws XMLStreamException, FileNotFoundException
  {
    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new FileReader(slaveSettingsFileName));

    while (r.hasNext() == true)
    {
      r.next();
      if (r.isStartElement() == false) continue;
      String tag = r.getLocalName();
      if (tag.equals("MasterWaitingForSlavesUDPPort") == true)
        masterWaitingForSlavesUDPPort = Integer.parseInt(r.getElementText());
      else if (tag.equals("MasterWaitingForSlavesTCPPort") == true)
        masterWaitingForSlavesTCPPort = Integer.parseInt(r.getElementText());
      else if (tag.equals("SlavesWaitingForMasterTCPPort") == true)
        slavesWaitingForMasterTCPPort = Integer.parseInt(r.getElementText());
      else if (tag.equals("MasterIP") == true)
        masterIP = r.getElementText();
      else if (tag.equals("Service") == true)
        services.add(r.getElementText());
      else if (tag.equals("SlaveStatusPeriod") == true)
        slaveStatusPeriod = Integer.parseInt(r.getElementText());
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
      System.err.println("Warning: there was a problem reading file " + slaveSettingsFileName);
      System.err.println("         Default settings will be used instead");
      e.printStackTrace();
    }
    catch (FileNotFoundException e)
    {
      System.err.println("Warning: the master settings file " + slaveSettingsFileName + " was not found");
      System.err.println("         Default settings will be used instead");
    }
  }
}
