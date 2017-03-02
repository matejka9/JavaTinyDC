package tinydc.common.servicesdata;

import java.io.Serializable;

public class ServiceInput implements Serializable
{
  public boolean initializeFromConsole()
  {
    System.out.println("This service does not support initialization from console.");
    return false;
  }

  public boolean initializeDefault()
  {
    System.out.println("This service does not support default initialization.");
    return false;
  }
}
