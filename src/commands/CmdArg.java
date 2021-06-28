package commands;

import java.util.HashMap;

public abstract class CmdArg<T>
{
	public int tokenCount() { return 1; }
	public abstract T parse(String trimmed);
	
	private static final HashMap<String, String> VARS = new HashMap<String, String>();
	
	public static void putVar(String name, String val)
	{
		VARS.put(name, val);
	}
	
	public static String getVar(String name)
	{
		return VARS.get(name);
	}
	
	///////////////////////
	
	public static final CmdArg<Command> COMMAND = new CmdArg<Command>()
	{
		@Override
		public Command parse(String trimmed)
		{
			return Commands.get(trimmed);
		}
	};
	
	public static final CmdArg<Integer> INT = new CmdArg<Integer>()
	{
		@Override
		public Integer parse(String trimmed)
		{
			try
			{
				return Math.round(Math.round(Double.parseDouble(trimmed)));
			}
			catch (NumberFormatException e)
			{}
			return null;
		}
	};
	
	public static final CmdArg<Double> DOUBLE = new CmdArg<Double>()
	{	
		@Override
		public Double parse(String trimmed)
		{
			try
			{
				return Double.parseDouble(trimmed);
			}
			catch (NumberFormatException e)
			{}
			return null;
		}
	};
	
	public static final CmdArg<String> STRING = new CmdArg<String>()
	{
		@Override
		public String parse(String trimmed)
		{
			return trimmed;
		}
	};
	
	public static final CmdArg<int[]> INT_ARRAY = new CmdArg<int[]>()
	{
		@Override
		public int tokenCount()
		{
			return -1;
		}
		
		@Override
		public int[] parse(String trimmed)
		{
			try
			{
				String[] tokens = trimmed.split(" ");
				int[] vals = new int[tokens.length];
				for (int i = 0; i < tokens.length; i++)
				{
					vals[i] = Math.round(Math.round(Double.parseDouble(tokens[i])));
				}
			}
			catch (NumberFormatException e)
			{}
			return null;
		}
	};
	
	public static final CmdArg<double[]> DOUBLE_ARRAY = new CmdArg<double[]>()
	{
		@Override
		public int tokenCount()
		{
			return -1;
		}
		
		@Override
		public double[] parse(String trimmed)
		{
			try
			{
				String[] tokens = trimmed.split(" ");
				double[] vals = new double[tokens.length];
				for (int i = 0; i < tokens.length; i++)
				{
					vals[i] = Double.parseDouble(tokens[i]);
				}
			}
			catch (NumberFormatException e)
			{}
			return null;
		}
	};
}
