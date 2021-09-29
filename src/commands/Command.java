package commands;

public class Command
{
	private boolean isVarArgs = false;
	boolean[] rawArg;
	String name;
	String ret;
	String desc;
	CmdArg<?>[] args;
	CmdFunc func;
	
	protected Command(String name, String ret, String desc, CmdArg<?>... args)
	{
		this.name = name;
		this.args = args;
		this.ret = ret;
		this.desc = desc;
		rawArg = new boolean[args.length];
	}
	
	public Command setFunc(CmdFunc func)
	{
		this.func = func;
		return this;
	}
	
	public Command setVarArgs()
	{
		isVarArgs = true;
		return this;
	}
	
	public boolean isVarArgs()
	{
		return isVarArgs;
	}
	
	public Command rawArg(int... indices)
	{
		for (int i : indices)
			rawArg[i] = true;
		return this;
	}
	
	public boolean rawToken(int arg, int tok)
	{
		return rawArg[arg] || args[arg].rawToken(tok);
	}
	
	public String getName()
	{
		return name;
	}
	
	public String getReturn()
	{
		return ret;
	}
	
	public String getDescription()
	{
		return desc;
	}
	
	public String getArgInfo()
	{
		String str = "";
		for (int i = 0; i < args.length; i++)
		{
			String[] rt = args[i].type.split(" ");
			for (int j = 0; j < rt.length; j++)
			{
				rt[j] = (args[i].rawToken(j) ? Script.RAW : "") + rt[j];
				str += rt[j] + (j == rt.length - 1 ? "" : " ");
			}
			str += (i == args.length - 1 ? "" : ", ");
		}
		
		if (isVarArgs)
			str += "...";
		
		return str;
	}
	
	public static class RunnableCommand
	{
		private final Object[] objs;
		private final String inputArgs;
		private final Command cmd;
		
		public RunnableCommand(Command cmd, String input, Object... args)
		{
			this.cmd = cmd;
			objs = args;
			inputArgs = input;
		}
		
		public String run(Script ctx)
		{
			return cmd.func.cmd(ctx, objs);
		}
		
		public String getInput()
		{
			return inputArgs;
		}
	}
	
	@FunctionalInterface
	public static interface CmdFunc
	{
		public String cmd(Script ctx, Object... args);
	}
}
