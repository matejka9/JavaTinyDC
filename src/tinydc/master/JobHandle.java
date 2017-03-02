package tinydc.master;

import java.util.*;
import tinydc.common.servicesdata.*;

public class JobHandle
{
  TasksManagement tasks;
  TreeMap<Integer,TaskHandle> myTasks;

  public JobHandle(TasksManagement tasks)
  {
    this.tasks = tasks;
    myTasks = new TreeMap<Integer,TaskHandle>();
  }

  int newTask(String serviceName, String serviceVersion, ServiceInput taskInputData)
  {
    TaskHandle task = new TaskHandle(serviceName, serviceVersion, taskInputData);
    tasks.queueTask(task);
    myTasks.put(new Integer(task.taskID), task);
    return task.taskID;
  }

  void cancelTask(int taskID)
  {
    tasks.cancelTask(taskID);
    myTasks.remove(new Integer(taskID));
  }

}
