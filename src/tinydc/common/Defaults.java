package tinydc.common;

public class Defaults
{
  public static final String version = "0.001";

  public static final int masterWaitingForSlavesUDPPort = 3141;
  public static final int masterWaitingForSlavesTCPPort = 3142;
  public static final int masterWaitingForClientsTCPPort = 3143;
  public static final int slavesWaitingForMasterTCPPort = 3144;
  public static final int masterRemoveInactiveSlavesDelay = 120;

  public static final int htmlReportFrequency = 30;
  public static final String htmlReportFileName = "tinydc.html";

  public static final String masterMessageLogFileName = "messagesAtMaster.log";
  public static final boolean masterMessageLogOn = false;
  public static final boolean masterDebuggingOn = false;
  
  public static final String masterIP = "127.0.0.1";

  public static final int slaveStatusPeriod = 60;
  public static final int maxDatagramSize = 4096;
}
