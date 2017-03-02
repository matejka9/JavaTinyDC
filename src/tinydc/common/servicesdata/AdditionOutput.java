package tinydc.common.servicesdata;

public class AdditionOutput extends ServiceOutput
{
  public double result;

  public void printToConsole()
  {
    System.out.println("Addition service output:");
    System.out.println("  a+b=" + result);
  }
}
