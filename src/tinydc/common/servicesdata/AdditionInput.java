package tinydc.common.servicesdata;

import java.io.*;

public class AdditionInput extends ServiceInput
{
  public double a;
  public double b;

  public boolean initializeFromConsole()
  {
    try
    {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      System.out.println("Addition service requires two double values a,b.");
      System.out.print("a=");
      a = Double.parseDouble(in.readLine());
      System.out.print("b=");
      b = Double.parseDouble(in.readLine());
      return true;
    }
    catch (IOException e)
    {
      System.err.println("Error reading input " + e.getMessage());
      return false;
    }
  }

  public boolean initializeDefault()
  {
    a = 1.0;
    b = 2.0;
    return true;
  }

}
