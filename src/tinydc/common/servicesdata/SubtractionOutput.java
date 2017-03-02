package tinydc.common.servicesdata;

public class SubtractionOutput extends ServiceOutput
{
  public double result;

  public void printToConsole()
  {
    System.out.println("Subtraction service output:");
    System.out.println("  a+b=" + result);
  }
}
