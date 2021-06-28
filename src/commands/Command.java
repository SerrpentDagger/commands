package commands;

public class Command
{
	String name;
	CmdArg<?>[] args;
	CmdFunc func;
	
	protected Command(String name, CmdArg<?>... args)
	{
		this.name = name;
		this.args = args;
	}
	
	public Command setFunc(CmdFunc func)
	{
		this.func = func;
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
		
		public String run()
		{
			return cmd.func.cmd(objs);
		}
	}
	
	@FunctionalInterface
	public static interface CmdFunc
	{
		public String cmd(Object... args);
	}
}
