package tinydc.master;

import java.util.*;

public class JobsManagement
{
  TreeMap<String,JobHandle> jobs;
  Master master;
  TasksManagement tasks;

  public JobsManagement(Master master, TasksManagement tasks)
  {
    jobs = new TreeMap<String,JobHandle>();
    this.master = master;
    this.tasks = tasks;
  }

  public JobHandle getOrAdd(String jobID)
  {
    JobHandle job = jobs.get(jobID);
    if (job == null)
    {
      job = new JobHandle(tasks);
      jobs.put(jobID, job);
    }
    return job;
  }
}
