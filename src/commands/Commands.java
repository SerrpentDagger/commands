package commands;

import java.util.HashMap;
import java.util.Scanner;

import commands.Command.RunnableCommand;

public class Commands
{
	private static final HashMap<String, Command> CMDS = new HashMap<String, Command>();
	public static final String COMMENT = "//";
	public static final String PREVIOUS = "PREV";
	
	public static Command get(String name)
	{
		return CMDS.get(name);
	}
	
	public static Command add(String name, CmdArg<?>... args)
	{
		Command cmd = new Command(name, args);
		if (CMDS.put(name, cmd) != null)
			throw new IllegalArgumentException("Cannot register two commands to the same name.");
		return cmd;
	}
	
	/////////////////////////////////////////////
	
	public static final Command VAR = add("var", CmdArg.STRING, CmdArg.STRING).setFunc((args) ->
	{
		CmdArg.putVar((String) args[0], (String) args[1]);
		return (String) args[1];
	});
	public static final Command PRINT = add("print").setFunc((args) ->
	{
		String prev = CmdArg.getVar(PREVIOUS);
		prev = prev == null ? "null" : prev;
		System.out.println(prev);
		return prev;
	});
	public static final Command ADD = add("add", CmdArg.DOUBLE, CmdArg.DOUBLE).setFunc((objs) ->
	{
		return "" + ((double) objs[0] + (double) objs[1]);
	});
	
	/////////////////////////////////////////////
	
	public static RunnableCommand parse(String line, int lineNum)
	{
		String[] tokens = line.split(" ");
		int tks = tokens.length;
		if (tks > 0)
		{
			String name = tokens[0];
			Command cmd = CMDS.get(name);
			if (cmd != null)
			{
				CmdArg<?>[] args = cmd.args;
				Object[] objs = new Object[args.length];
				int tok = 1;
				for (int i = 0; i < args.length; i++)
				{
					CmdArg<?> arg = args[0];
					int count = arg.tokenCount();
					String trimmed = "";
					for (int j = 0;
							j < count ||
								(count < 0 && tks > tok) ||
								(count == 0 && tks > tok && (tok == tks - 1 || arg.parse(tokens[j + 1]) != null));
							j++)
					{
						if (tks <= tok)
							throw new CommandParseException("Missing tokens at line " + lineNum + ": " + line);
						
						String var = CmdArg.getVar(tokens[tok]);
						if (var != null)
							trimmed += var.trim();
						else
							trimmed += tokens[tok];
						trimmed += " ";
						
						tok++;
					}
					
					Object obj = arg.parse(trimmed.trim());
					if (obj == null)
						throw new CommandParseException("Invalid tokens at line " + lineNum + ": " + trimmed);
					objs[i] = obj;
				}

				if (tok != tks && !tokens[tok].startsWith(COMMENT))
					throw new CommandParseException("Unused token at line " + lineNum + ": " + tokens[tok]);
				
				RunnableCommand run = new RunnableCommand(cmd, objs);
				return run;
			}
			else
				throw new CommandParseException("Unknown command at line " + lineNum + ": " + name);
		}
		return null;
	}
	
	public static void run(Scanner fileScan)
	{
		int lineNum = 0;
		while(fileScan.hasNextLine())
		{
			lineNum++;
			String line = fileScan.nextLine();
			if (line.startsWith(COMMENT))
				continue;
			CmdArg.putVar(PREVIOUS, parse(line, lineNum).run());
		}
	}
	
	public static class CommandParseException extends RuntimeException
	{
		private static final long serialVersionUID = 1L;
		public CommandParseException(String name)
		{
			super(name);
		}
	}
}
