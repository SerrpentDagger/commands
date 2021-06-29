package commands;

public class Command
{
	boolean[] rawVar;
	String name;
	CmdArg<?>[] args;
	CmdFunc func;
	
	protected Command(String name, CmdArg<?>... args)
	{
		this.name = name;
		this.args = args;
		rawVar = new boolean[args.length];
	}
	
	public Command setFunc(CmdFunc func)
	{
		this.func = func;
		return this;
	}
	
	public Command rawVars(int... indices)
	{
		for (int i : indices)
			rawVar[i] = true;
		return this;
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
