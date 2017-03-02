package tinydc.common.servicesdata;

import java.io.Serializable;

public class ServiceOutput implements Serializable
{
  public void printToConsole()
  {
    System.out.println("Some binary data of format " + getClass().getSimpleName());
  }
}
