/**
 * This file is part of Scajl, which is a scripting language for Java applications.
 * Copyright (c) 2023, SerpentDagger (MRRH) <serpentdagger.contact@gmail.com>.
 * 
 * Scajl is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 * 
 * Scajl is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with Scajl.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package commands;

public class Command
{
	private boolean isVarArgs = false, isDisabled = false;
	boolean[] rawArg;
	boolean[] nullable;
	String name;
	String ret;
	String desc;
	CmdArg<?>[] args;
	CmdArg<?> variadic = null;
	CmdFunc func;
	
	protected Command(String name, String ret, String desc, CmdArg<?>... args)
	{
		if (name.split("\\s").length != 1)
			throw new IllegalArgumentException("Command names must not include white space: '" + name + "'");
		this.name = name;
		this.args = args;
		this.ret = ret;
		this.desc = desc;
		rawArg = new boolean[args.length];
		nullable = new boolean[args.length];
	}
	
	public Command setFunc(CmdFunc func)
	{
		this.func = func;
		return this;
	}
	
	public Command setVarArgs()
	{
		isVarArgs = true;
		variadic = CmdArg.arrayOf(args[args.length - 1]);
		return this;
	}
	
	public boolean isVarArgs()
	{
		return isVarArgs;
	}
	
	public Command setDisabled(boolean disabled)
	{
		isDisabled = disabled;
		return this;
	}
	
	public boolean isDisabled()
	{
		return isDisabled;
	}
	
	public Command rawArg(int... indices)
	{
		for (int i : indices)
			rawArg[i] = true;
		return this;
	}
	
	public Command nullable(int... indices)
	{
		for (int i : indices)
			nullable[i] = true;
		return this;
	}
	
	public boolean rawToken(int arg, int tok)
	{
		return rawArg[arg] || args[arg].rawToken(tok);
	}
	
	public boolean nullableArg(int arg)
	{
		return nullable[arg];
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
			str += (nullableArg(i) ? "!" : "") + args[i].getInfoString() + (i == args.length - 1 ? "" : ", ");
		
		if (isVarArgs)
			str += "...";
		
		return str;
	}
	
	public String getInfoString()
	{
		return this.getName() + " | Args: " + this.getArgInfo() + ", Return: " + this.getReturn() + ", Desc: " + this.getDescription();
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
		
		public ScajlVariable run(Scajl ctx)
		{
			return cmd.func.cmd(ctx, objs);
		}
		
		public String getInput()
		{
			return inputArgs;
		}
	}
	
	public static class CommandResult
	{
		public final ScajlVariable output;
		public final boolean shouldBreak;
		
		public CommandResult(ScajlVariable out, boolean brk)
		{
			output = out;
			shouldBreak = brk;
		}
	}
	
	@FunctionalInterface
	public static interface CmdFunc
	{
		public ScajlVariable cmd(Scajl ctx, Object... args);
	}
}
