package tinydc.slave;

import java.util.*;

import tinydc.slave.services.*;

public class Services
{
  TreeMap<String,Service> services;

  public Services(List<String> activeServices)
  {
    services = new TreeMap<String,Service>();
    for (String s : activeServices)
      try
      {
        services.put(s, (Service) Class.forName("tinydc.slave.services." + s).newInstance());
      }
      catch (ClassNotFoundException e)
      {
        System.err.println("Warning: class for the service " + s + " mentioned in configuration file not found.");
      }
      catch (InstantiationException e)
      {
        System.err.println("Warning: could not instantiate the service " + s + " mentioned in configuration file.");
      }
      catch (IllegalAccessException e)
      {
        System.err.println("Warning: illegal access for service " + s + " mentioned in configuration file not found.");
      }
  }
}
