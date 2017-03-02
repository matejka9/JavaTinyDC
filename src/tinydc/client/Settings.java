package tinydc.client;

import java.io.*;
import java.util.*;
import javax.xml.stream.*;

import tinydc.common.*;

public class Settings
{
  public static final String clientSettingsFileName = "ClientConfig.xml";

  public int masterWaitingForClientsTCPPort;
  public String masterIP;
  public ArrayList<String> services;

  public Settings()
  {
    masterIP = Defaults.masterIP;
    masterWaitingForClientsTCPPort = Defaults.masterWaitingForClientsTCPPort;
    services = new ArrayList<String>();
  }

  public void readFromXMLFileOrThrow() throws XMLStreamException, FileNotFoundException
  {
    XMLInputFactory f = XMLInputFactory.newInstance();
    XMLStreamReader r = f.createXMLStreamReader(new FileReader(clientSettingsFileName));

    while (r.hasNext() == true)
    {
      r.next();
      if (r.isStartElement() == false) continue;
      String tag = r.getLocalName();
      if (tag.equals("MasterWaitingForClientsTCPPort") == true)
        masterWaitingForClientsTCPPort = Integer.parseInt(r.getElementText());
      else if (tag.equals("MasterIP") == true)
        masterIP = r.getElementText();
      else if (tag.equals("Service") == true)
        services.add(r.getElementText());
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
      System.err.println("warning: there was a problem reading file " + clientSettingsFileName);
      System.err.println("         Default settings will be used instead");
      e.printStackTrace();
    }
    catch (FileNotFoundException e)
    {
      System.err.println("warning: the master settings file " + clientSettingsFileName + " was not found");
      System.err.println("         Default settings will be used instead");
    }
  }
}
