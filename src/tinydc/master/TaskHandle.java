package tinydc.master;

import java.util.*;
import tinydc.common.servicesdata.*;

public class TaskHandle
{
  public enum TaskState {queued, running, finished};
  TaskState state;
  int taskID;
  String serviceName;
  String serviceVersion;
  ServiceInput inputData;
  ServiceOutput outputData;
  String result;
  String errorMessage;
  SlaveHandle slave;

  HashSet<Integer> slavesThatRejected;

  private static int newTaskID = 1;

  public TaskHandle(String serviceName, String serviceVersion, ServiceInput inputData)
  {
    this.taskID = newTaskID++;
    this.serviceName = serviceName;
    this.serviceVersion = serviceVersion;
    this.inputData = inputData;

    state = TaskState.queued;
    slavesThatRejected = new HashSet<Integer>();
  }

  public String stateAsString()
  {
    switch (state)
    {
      case queued: return "queued";
      case running: return "running";
      case finished: return "finished";
    }
    return "other";
  }
}
