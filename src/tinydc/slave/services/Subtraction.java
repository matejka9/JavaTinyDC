package tinydc.slave.services;

import tinydc.common.servicesdata.*;

public class Subtraction extends Service
{
  private static final String version = "1.0";

  public String getServiceVersion()
  {
    return version;
  }

  public ServiceOutput start(ServiceInput input) throws Exception
  {
    SubtractionInput in = (SubtractionInput)input;
    SubtractionOutput out = new SubtractionOutput();

    out.result = in.a - in.b;
    Thread.sleep((int)(out.result * 1000.0));
    return out;
  }
}
