package tinydc.slave.services;

import tinydc.common.servicesdata.*;

public abstract class Service implements Runnable
{
  private ResultCollector slave;
  private ServiceInput inputData;
  private Thread thread;

  protected boolean stopping;

  public String getServiceName() { return this.getClass().getSimpleName(); }
  public abstract String getServiceVersion();
  public boolean canRunAlsoTasksOf(String version) { return false; }
  public abstract ServiceOutput start(ServiceInput input) throws Exception;

  public void runTask(ResultCollector slave, ServiceInput inputData)
  {
    stopping = false;
    this.slave = slave;
    this.inputData = inputData;
    thread = new Thread(this);
    thread.start();
  }

  public void stopTask() throws Exception
  {
    stopping = true;
    Thread.sleep(1000);
    thread.interrupt();
  }

  public void run()
  {
    try
    {
      ServiceOutput outputData = start(inputData);
      slave.taskCompleted(outputData);
    }
    catch (Exception e)
    {
      slave.taskFailed(e.getMessage());
    }
  }
}
