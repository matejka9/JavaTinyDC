package tinydc.master;

import java.util.*;

public class TasksManagement
{
  TreeMap<Integer,TaskHandle> tasks;
  Master master;
  SlavesManagement slaves;

  TasksManagement(Master master, SlavesManagement slaves)
  {
     tasks = new TreeMap<Integer,TaskHandle>();
     this.master = master;
     this.slaves = slaves;
  }

  void queueTask(TaskHandle task)
  {
    task.state = TaskHandle.TaskState.queued;
    tasks.put(task.taskID, task);
  }

  void cancelTask(int taskID)
  {
    TaskHandle task = tasks.get(new Integer(taskID));
    if (task.slave != null) slaves.cancelTask(task.slave, taskID);
    removeTask(taskID);
    master.reporting.tasksCancelled++;
  }

  void removeTask(int taskID)
  {
    tasks.remove(taskID);
    master.reschedule();
  }
  
}
