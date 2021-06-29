package commands;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Scanner;

import commands.Command.RunnableCommand;

public class Script
{
	private static final HashMap<String, Command> CMDS = new HashMap<String, Command>();
	public static final String COMMENT = "//";
	public static final String LABEL = "--";
	public static final String PREVIOUS = "PREV";
	public static final String NULL = "null";
	public static final String END_FLAG = "END";
	public static final String STORE = "->";
	public static final char ARR_S = '[', ARR_E = ']';
	public static final String INDEX = "INDEX";
	public static final int NO_LABEL = -2;
	
	public int parseLine = -1;
	private final HashMap<String, String> vars = new HashMap<String, String>();
	private final HashMap<String, Integer> labels = new HashMap<String, Integer>();
	private final ArrayDeque<Integer> stack = new ArrayDeque<Integer>();
	public String path;
	public final String[] lines;
	private Scanner scan;
	
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
	
	public static final Command VAR = add("var", CmdArg.TOKEN, CmdArg.STRING).setFunc((ctx, objs) ->
	{
		ctx.putVar((String) objs[0], (String) objs[1]);
		return (String) objs[1];
	}).rawVars(0);
	public static final Command PRINT = add("print", CmdArg.STRING).setFunc((ctx, objs) ->
	{
		String str = (String) objs[0];
		System.out.println(str);
		return str;
	});
	public static final Command KEY_IN = add("key_in", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		System.out.println((String) objs[0] + "?");
		String next = ctx.scan.next();
		ctx.putVar((String) objs[0], next);
		return next;
	});
	public static final Command ADD = add("add", CmdArg.greedyArray(CmdArg.DOUBLE)).setFunc((ctx, objs) ->
	{
		return "" + operate(0, (Object[]) objs[0], (all, next) -> all + next);
	});
	public static final Command SUB = add("sub", CmdArg.greedyArray(CmdArg.DOUBLE)).setFunc((ctx, objs) ->
	{
		Object[] arr = (Object[]) objs[0];
		return "" + operate((double) arr[0] * 2, arr, (all, next) -> all - next);
	});
	public static final Command MULT = add("mult", CmdArg.greedyArray(CmdArg.DOUBLE)).setFunc((ctx, objs) ->
	{
		return "" + operate(1, (Object[]) objs[0], (all, next) -> all * next);
	});
	public static final Command DIVI = add("divi", CmdArg.greedyArray(CmdArg.DOUBLE)).setFunc((ctx, objs) ->
	{
		Object[] arr = (Object[]) objs[0];
		return "" + operate((double) arr[0] * (double) arr[0], arr, (all, next) -> all / next);
	});
	public static final Command INCREMENT = add("inc", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return "" + ((double) objs[0] + 1);
	});
	public static final Command DECREMENT = add("dec", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return "" + ((double) objs[0] - 1);
	});
	public static final Command NEGATE = add("negate", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return "" + (-(double) objs[0]);
	});
	public static final Command COMPARE = add("compare", CmdArg.BOOLEAN_EXP).setFunc((ctx, objs) ->
	{
		return "" + ((BooleanExp) objs[0]).eval();
	});
	public static final Command IF_THEN_ELSE = add("if", CmdArg.greedyArray(CmdArg.BOOLEAN_THEN)).setFunc((ctx, objs) ->
	{
		BooleanThen[] ba = (BooleanThen[]) objs[0];
		for (BooleanThen b : ba)
			if (b.bool)
				return b.then;
		return NULL;
	});
	public static final Command FOR = add("for", CmdArg.INT, CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		int count = (int) objs[0];
		String label = (String) objs[1];
		int line = ctx.getLabelLine(label);
		if (line == NO_LABEL)
			ctx.parseExcept("Invalid label specification", ctx.parseLine, label, "No label found.");
		for (int i = 0; i < count; i++)
		{
			ctx.putVar(INDEX, "" + i);
			ctx.runFrom(line);
		}
		return ctx.getVar(PREVIOUS);
	});
	public static final Command WHILE = add("while", CmdArg.BOOLEAN, CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		boolean bool = (boolean) objs[0];
		int wL = ctx.parseLine;
		int line = ctx.getLabelLine((String) objs[1]);
		if (line == NO_LABEL)
			ctx.parseExcept("Invalid label specification", ctx.parseLine, (String) objs[1], "No label found.");
		if (bool)
		{
			ctx.runFrom(line);
			ctx.parseLine = wL - 1;
		}
		return ctx.getVar(PREVIOUS);
	});
	public static final Command GOTO = add("goto", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String label = (String) objs[0];
		int line = ctx.getLabelLine(label);
		if (line == NO_LABEL)
			ctx.parseExcept("Invalid label specification", ctx.parseLine, label, "No label found.");
		
		ctx.pushStack(line);
		
		return "" + line;
	});
	public static final Command RETURN = add("return").setFunc((ctx, objs) ->
	{
		return "" + ctx.popStack();
	});
	
	public static double operate(double start, Object[] numArray, Operator op)
	{
		double all = start;
		for (Object doub : numArray)
			all = op.operate(all, (double) doub);
		return all;
	}
	public static interface Operator
	{
		public double operate(double all, double next);
	}
	
	/////////////////////////////////////////////
	
	public static String[] tokensOf(String str)
	{
		return str.trim().split(" ");
	}
	public static String firstToken(String line)
	{
		String first = "";
		for (int i = 0; i < line.length() && line.charAt(i) != ' '; i++)
			first += line.charAt(i);
		return first;
	}
	
	public String varParse(Command cmd, int index, String token)
	{
		if (cmd.rawVar[index])
			return token.trim();
		String var = getVar(token);
		if (var != null)
			return arrPreFrom(token) + var.trim() + arrPostFrom(token);
		else
			return token.trim();
	}
	private String arrPreFrom(String token)
	{
		String pre = "";
		for (int i = 0; i < token.length(); i++)
		{
			if (token.charAt(i) == ARR_S)
				pre += ARR_S;
			else
				return pre;
		}
		return pre;
	}
	private String arrPostFrom(String token)
	{
		String post = "";
		for (int i = token.length() - 1; i >= 0; i--)
		{
			if (token.charAt(i) == ARR_S)
				post += ARR_S;
			else
				return post;
		}
		return post;
	}
	
	public RunnableCommand parse(String line)
	{
		String[] tokens = line.split(" ");
		int tks = tokens.length;
		if (tks > 0)
		{
			String first = tokens[0];
			String[] storing = first.split(STORE);
			String name = storing[0];
			Command cmd = CMDS.get(name);
			if (cmd != null)
			{
				CmdArg<?>[] args = cmd.args;
				Object[] objs = new Object[args.length];
				int tok = 1;
				for (int argInd = 0; argInd < args.length; argInd++)
				{
					CmdArg<?> arg = args[argInd];
					int count = arg.tokenCount();
					String trimmed = "";
				
					for (int tokInArg = 0; tokInArg < count; tokInArg++)
					{
						if (tks <= tok)
							parseExcept("Missing tokens", parseLine, line, name + " requires " + tks + " args.");

						trimmed += varParse(cmd, argInd, tokens[tok]) + " ";
						tok++;
					}
					if (count == -1)
					{
						if (tokens.length == tok)
							parseExcept("Missing array tokens", parseLine, line, name);
						if (!tokens[tok].startsWith("" + ARR_S))
							parseExcept("Argument must be an array", parseLine, line, tokens[tok]);
						int arrCount = 0;
						do
						{
							if (tokens[tok].startsWith("" + ARR_S))
								arrCount++;
							if (tokens[tok].endsWith("" + ARR_E))
								arrCount--;
							String var = varParse(cmd, argInd, tokens[tok]);
							trimmed += var + " ";
							tok++;
						} while (arrCount > 0);
					}
					if (count == -2)
					{
						if (tokens.length == tok)
							parseExcept("Missing array tokens", parseLine, line, name);
						while (tok < tks)
						{
							String var = varParse(cmd, argInd, tokens[tok]);
							trimmed += var + " ";
							tok++;
						}
					}
					
					trimmed = trimmed.trim();
					Object obj = arg.parse(trimmed);
					if (obj == null)
						parseExcept("Invalid token resolution", parseLine, trimmed, tokens[tok - 1]);
					objs[argInd] = obj;
				}

				if (tok != tks && !tokens[tok].startsWith(COMMENT))
					parseExcept("Unused token", parseLine, tokens[tok], tokens[tok]);
				
				RunnableCommand run = new RunnableCommand(cmd, objs);
				return run;
			}
			else
				parseExcept("Unknown command", parseLine, name);
		}
		return null;
	}
	
	private void parseExcept(String preAt, int line, String postLine)
	{
		parseExcept(preAt, line, postLine, null);
	}
	
	private void parseExcept(String preAt, int line, String postLine, String extra)
	{
		throw new CommandParseException(preAt + " at line " + (line + 1) + ": " + postLine + ", from: " + lines[line]
				+ (extra == null ? "" : ", extra info: " + extra));
	}
	
	////////////////////////////////////////////////
	
	public Script(String str)
	{
		this(new Scanner(str));
	}
	
	public Script(Scanner scan)
	{
		path = null;
		String str = "";
		int num = 0;
		while (scan.hasNextLine())
		{
			String line = scan.nextLine();
			if (line.startsWith(LABEL))
			{
				String[] tokens = tokensOf(line);
				putLabel(tokens[0], num);
			}
			
			str += line + "\n";
			num++;
		}
		lines = str.split("\n");
		scan.close();
	}
	
	public Script(File script) throws FileNotFoundException
	{
		this(new Scanner(script));
		this.path = script.getAbsolutePath();
	}
	
	public void run()
	{
		scan = new Scanner(System.in);
		runFrom(-1);
		scan.close();
	}
	public void runFrom(int start)
	{
		if (start != -1)
			pushStack(start);
		else
			parseLine = 0;
		if (scan == null)
			scan = new Scanner(System.in);
		while(parseLine < lines.length && parseLine != -1)
		{
			String line = lines[parseLine];
			if (line.startsWith(COMMENT) || line.isEmpty() || line.startsWith(LABEL))
			{
				parseLine++;
				continue;
			}
			String first = firstToken(line);
			String[] storing = first.split(STORE);
			RunnableCommand cmd = parse(line);
			String out;
			putVar(PREVIOUS, out = cmd.run(this));
			for (int i = 1; i < storing.length; i++)
				putVar(storing[i], out);
			if (parseLine != -1)
				parseLine++;
		}
		parseLine = -1;
	}
	
	public void putVar(String name, String val)
	{
		try
		{
			Double.parseDouble(name);
			parseExcept("Numerical variable name", parseLine, name);
		}
		catch (NumberFormatException e)
		{}
		vars.put(name, val);
	}
	
	public String getVar(String name)
	{
		return vars.get(arrayTrim(name));
	}
	private String arrayTrim(String token)
	{
		String str = token;
		if (str.startsWith("" + ARR_S))
			str = str.substring(1);
		if (str.endsWith("" + ARR_E))
			str = str.substring(0, str.length() - 1);
		return str;
	}
	
	public void putLabel(String label, int line)
	{
		labels.put(label.replaceFirst(LABEL, ""), line);
	}
	public int getLabelLine(String label)
	{
		Integer line = labels.get(label.replaceFirst(LABEL, ""));
		if (line == null)
			return NO_LABEL;
		return line;
	}
	
	private void pushStack(int to)
	{
		stack.push(parseLine);
		parseLine = to;
	}
	
	private int popStack()
	{
		if (stack.isEmpty())
		{
			return parseLine = -1;
		}
		parseLine = stack.pop();
		return parseLine;
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
