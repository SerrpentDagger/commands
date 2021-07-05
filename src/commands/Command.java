package commands;

public class Command
{
	private boolean isVarArgs = false;
	boolean[] rawArg;
	String name;
	CmdArg<?>[] args;
	CmdFunc func;
	
	protected Command(String name, CmdArg<?>... args)
	{
		this.name = name;
		this.args = args;
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
	
	public String getArgInfo()
	{
		String str = "";
		for (int i = 0; i < args.length; i++)
			str += args[i].type + (i == args.length - 1 ? "" : ", ");
		
		if (isVarArgs)
			str += "...";
		
		return str;
	}
	
	public static class RunnableCommand
	{
		private final Object[] objs;
		private final Command cmd;
		
		public RunnableCommand(Command cmd, Object... args)
		{
			this.cmd = cmd;
			objs = args;
		}
		
		public String run(Script ctx)
		{
			return cmd.func.cmd(ctx, objs);
		}
	}
	
	@FunctionalInterface
	public static interface CmdFunc
	{
		public String cmd(Script ctx, Object... args);
	}
}
