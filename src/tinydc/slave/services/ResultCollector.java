package tinydc.slave.services;

import tinydc.common.servicesdata.*;

public interface ResultCollector
{
  public void taskCompleted(ServiceOutput outputData);
  public void taskFailed(String error);
}
