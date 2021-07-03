package commands;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import commands.Command.RunnableCommand;

public class Script
{
	private static final HashMap<String, Command> CMDS = new HashMap<String, Command>();
	public static final String COMMENT = "//";
	public static final String LABEL = "--";
	public static final String PREVIOUS = "PREV";
	public static final String NULL = "null";
	public static final String FALSE = "false", TRUE = "true";
	public static final String END_FLAG = "END";
	public static final String STORE = "->";
	public static final char ARR_S = '[', ARR_E = ']';
	public static final String INDEX = "INDEX";
	public static final int NO_LABEL = -2;
	public static final char UNRAW = '%', RAW = '$';
	
	public int parseLine = -1;
	private final HashMap<String, String> vars = new HashMap<String, String>();
	private final HashMap<String, Integer> labels = new HashMap<String, Integer>();
	private final ArrayDeque<Integer> stack = new ArrayDeque<Integer>();
	public String path;
	public final String[] lines;
	private Scanner keyIn;
	private final Robot rob;
	private Consumer<String> errorCallback = (err) -> System.err.println(err);
	private BiConsumer<CommandParseException, String> parseExceptionCallback = (exc, err) -> { exc.printStackTrace(); };
	private Consumer<Exception> exceptionCallback = (exc) ->
	{
		System.err.println("Exception encountered at line: " + (parseLine + 1));
		exc.printStackTrace();
	};
	private Consumer<String[]> userRequest = (vars) ->
	{
		for (String var : vars)
			putVar(var, keyIn.next());
	};
	private UserReqType userRequestType = (vars, type, prompt) ->
	{
		for (String var : vars)
		{
			String in = keyIn.next().trim();
			if (!putVarType(var, in, type, prompt))
				return false;
		}
		return true;
	};
	
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
	
	public static String[] getAllCmds()
	{
		return CMDS.keySet().toArray(new String[CMDS.size()]);
	}
	
	public static CmdArg<?>[][] getAllCmdArgs()
	{
		Set<Entry<String, Command>> entries = CMDS.entrySet();
		@SuppressWarnings("unchecked")
		Entry<String, Command>[] entA = (Entry<String, Command>[]) Array.newInstance(Entry.class, entries.size());
		
		CmdArg<?>[][] sets = new CmdArg<?>[entries.size()][];
		for (int i = 0; i < sets.length; i++)
		{
			Command cmd = entA[i].getValue();
			sets[i] = cmd.args;
		}
		
		return sets;
	}
	
	public static String[][] cmdTypePairs()
	{
		Set<Entry<String, Command>> entries = CMDS.entrySet();
		String[][] str = new String[entries.size()][];
		
		AtomicInteger i = new AtomicInteger(0);
		entries.forEach((ent) ->
		{
			str[i.get()] = new String[] { ent.getKey(), ent.getValue().getArgInfo() };
			i.incrementAndGet();
		});
		
		return str;
	}
	
	/////////////////////////////////////////////
	
	public static final Command VAR = add("var", CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		VarSet var = (VarSet) objs[0];
		ctx.putVar(var.var, var.set);
		return var.set;
	});
	public static final Command IS_VAR = add("is_var", CmdArg.greedyArray(CmdArg.TOKEN)).setFunc((ctx, objs) ->
	{
		String[] vars = (String[]) objs[0];
		for (String var : vars)
			if (ctx.getVar(var) == null)
				return FALSE;
		return TRUE;
	});
	public static final Command IS_NUMBER = add("is_number", CmdArg.greedyArray(CmdArg.TOKEN)).setFunc((ctx, objs) ->
	{
		String[] vars = (String[]) objs[0];
		for (String var : vars)
		{
			String val = ctx.getVar(var);
			if (val == null || CmdArg.DOUBLE.parse(val) == null)
				return FALSE;
		}
		return TRUE;
	});
	public static final Command PRINT = add("print", CmdArg.STRING_GREEDY).setFunc((ctx, objs) ->
	{
		String str = CmdArg.arrayTokenTo((String[]) objs[0]);
		System.out.println(str);
		return str;
	});
	public static final Command KEY_IN = add("key_in", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		System.out.println((String) objs[0] + "?");
		String next = ctx.keyIn.next();
		ctx.putVar((String) objs[0], next);
		return next;
	});
	public static final Command USER_REQ = add("user_req", CmdArg.greedyArray(CmdArg.TOKEN)).setFunc((ctx, objs) ->
	{
		ctx.userRequest.accept((String[]) objs[0]);
		return ctx.prev();
	});
	
	public static final Command USER_REQ_INT = userReqType("user_req_int", CmdArg.INT, "integer");
	public static final Command USER_REQ_DOUBLE = userReqType("user_req_double", CmdArg.DOUBLE, "double");
	public static final Command USER_REQ_STRING = userReqType("user_req_string", CmdArg.STRING_GREEDY, "text");
	public static final Command USER_REQ_BOOL = userReqType("user_req_bool", CmdArg.BOOLEAN, "boolean");
	public static final Command USER_REQ_TOKEN = userReqType("user_req_token", CmdArg.TOKEN, "token");
	
	public static final Command CONCAT = add("concat", CmdArg.greedyArray(CmdArg.STRING)).setFunc((ctx, objs) ->
	{
		String out = "";
		for (String[] tokArr : (String[][]) objs[0])
		{
			out += CmdArg.arrayTokenTo(tokArr);
		}
		return out;
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
			ctx.parseExcept("Invalid label specification", label, "No label found.");
		for (int i = 0; i < count; i++)
		{
			ctx.putVar(INDEX, "" + i);
			ctx.runFrom(line);
		}
		return ctx.prev();
	});
	public static final Command WHILE = add("while", CmdArg.BOOLEAN, CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		boolean bool = (boolean) objs[0];
		int wL = ctx.parseLine;
		int line = ctx.getLabelLine((String) objs[1]);
		if (line == NO_LABEL)
			ctx.parseExcept("Invalid label specification", (String) objs[1], "No label found.");
		if (bool)
		{
			ctx.runFrom(line);
			ctx.parseLine = wL - 1;
		}
		return ctx.prev();
	});
	public static final Command GOTO = add("goto", CmdArg.TOKEN, CmdArg.greedyArray(CmdArg.VAR_SET)).setFunc((ctx, objs) ->
	{
		String label = (String) objs[0];
		int line = ctx.getLabelLine(label);
		if (line == NO_LABEL)
			ctx.parseExcept("Invalid label specification", label, "No label found.");
		
		for (int i = 0; i < objs.length; i++)
		{
			VarSet var = ((VarSet[]) objs[1])[i];
			ctx.putVar(var.var, var.set);
		}
		
		ctx.pushStack(line);
		
		return "" + line;
	});
	public static final Command RETURN = add("return").setFunc((ctx, objs) ->
	{
		return "" + ctx.popStack();
	});
	public static final Command SLEEP = add("sleep", CmdArg.INT).setFunc((ctx, objs) ->
	{
		try
		{
			Thread.sleep((int) objs[0]);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		return "" + (int) objs[0];
	});

	public static final Command MOUSE_MOVE = add("mouse_move", CmdArg.INT, CmdArg.INT).setFunc((ctx, objs) ->
	{
		ctx.rob.mouseMove((int) objs[0], (int) objs[1]);
		return ctx.prev();
	});
	
	public static final Command KEY_PRESS = keyCommand("key_press", (key, ctx, objs) ->
	{
		ctx.rob.keyPress(key);
	}, CmdArg.TOKEN);
	
	public static final Command KEY_RELEASE = keyCommand("key_release", (key, ctx, objs) ->
	{
		ctx.rob.keyRelease(key);
	}, CmdArg.TOKEN);
	
	public static final Command KEY = keyCommand("key", (key, ctx, objs) ->
	{
		ctx.rob.keyPress(key);
		ctx.rob.delay((int) objs[1]);
		ctx.rob.keyRelease(key);
	}, CmdArg.TOKEN, CmdArg.DOUBLE);
	
	private static Command keyCommand(String name, KeyFunc k, CmdArg<?>... args)
	{
		return add(name, args).setFunc((ctx, objs) ->
		{
			try
			{
				String key = (String) objs[0];
				Field field = keyCode(key);
				k.key(field.getInt(field), ctx, objs);
				return "" + true;
			}
			catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e)
			{
				e.printStackTrace();
				return "" + false;
			}
		});
	}
	private static Field keyCode(String letter) throws NoSuchFieldException
	{
		return KeyEvent.class.getField("VK_" + letter.toUpperCase());
	}
	@FunctionalInterface
	private static interface KeyFunc
	{
		public void key(int key, Script ctx, Object[] objs);
	}
	
	private static double operate(double start, Object[] numArray, Operator op)
	{
		double all = start;
		for (Object doub : numArray)
			all = op.operate(all, (double) doub);
		return all;
	}
	@FunctionalInterface
	private static interface Operator
	{
		public double operate(double all, double next);
	}
	
	private static Command userReqType(String name, CmdArg<?> type, String userPromptType)
	{
		return add(name, CmdArg.greedyArray(CmdArg.TOKEN)).setFunc((ctx, objs) ->
		{
			if (ctx.userRequestType.reqType((String[]) objs[0], type, userPromptType))
				return TRUE;
			else
				return FALSE;
		});
	}
	@FunctionalInterface
	private static interface UserReqType
	{
		public boolean reqType(String[] vars, CmdArg<?> type, String userPromptType);
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
	
	///////////
	
	public String varParse(Command cmd, int argInd, int tokInd, String token)
	{
		String trm = varTrim(token);
		boolean unraw = token.charAt(0) == UNRAW;
		boolean raw = token.charAt(0) == RAW;
		if (raw || (!unraw && (cmd.rawArg[argInd] || cmd.rawToken(tokInd))))
			return trm;
		String var = getVar(trm);
		if (unraw)
			return varParse(cmd, argInd, tokInd, var);
		if (var != null)
			return arrPreFrom(token) + var + arrPostFrom(token);
		else
			return trm;
	}
	public static String arrPreFrom(String token)
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
	public static String arrPostFrom(String token)
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
	private String varTrim(String str)
	{
		return str.trim().replaceFirst("" + UNRAW, "").replaceFirst("" + RAW, "");
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
				String token = "";
				for (int argInd = 0; argInd < args.length; argInd++)
				{
					CmdArg<?> arg = args[argInd];
					int count = arg.tokenCount();
					String trimmed = "";
				
					for (int tokInArg = 0; tokInArg < count; tokInArg++)
					{
						if (tks <= tok)
							parseExcept("Missing tokens", line, name + " requires " + tks + " args.");

						trimmed += varParse(cmd, argInd, tok, token = tokens[tok]) + " ";
						tok++;
					}
					if (count == -1)
					{
						if (tokens.length == tok)
							parseExcept("Missing array tokens", line, name);
						if (!(token = tokens[tok]).startsWith("" + ARR_S))
							parseExcept("Argument must be an array", line, token);
						int arrCount = 0;
						do
						{
							if (token.startsWith("" + ARR_S))
								arrCount++;
							if (token.endsWith("" + ARR_E))
								arrCount--;
							String var = varParse(cmd, argInd, tok, token);
							trimmed += var + " ";
							tok++;
						} while (arrCount > 0);
					}
					else if (count == -2)
					{
						if (tokens.length == tok)
							parseExcept("Missing array tokens", line, name);
						while (tok < tks)
						{
							String var = varParse(cmd, argInd, tok, token = tokens[tok]);
							trimmed += var + " ";
							tok++;
						}
					}
					
					trimmed = trimmed.trim();
					Object obj = arg.parse(trimmed);
					if (obj == null)
						parseExcept("Invalid token resolution", trimmed, token);
					objs[argInd] = obj;
				}

				if (tok != tks && !tokens[tok].startsWith(COMMENT))
					parseExcept("Unused token", tokens[tok], token);
				
				RunnableCommand run = new RunnableCommand(cmd, objs);
				return run;
			}
			else
				parseExcept("Unknown command", name);
		}
		return null;
	}
	
	public void error(String message)
	{
		this.errorCallback.accept(message);
	}
	
	public void parseExcept(String preAt, String postLine)
	{
		parseExcept(preAt, postLine, null);
	}
	
	public void parseExcept(String preAt, String postLine, String extra)
	{
		throw new CommandParseException(preAt + " at line " + (parseLine + 1) + ": " + postLine + ", from: " + lines[parseLine]
				+ (extra == null ? "" : ", extra info: " + extra));
	}
	
	////////////////////////////////////////////////
	
	public Script(String str) throws AWTException
	{
		this(new Scanner(str));
	}
	
	public Script(Scanner scan) throws AWTException
	{
		rob = new Robot();
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
	
	public Script(File script) throws FileNotFoundException, AWTException
	{
		this(new Scanner(script));
		this.path = script.getAbsolutePath();
	}
	
	public void run()
	{
		try
		{
			keyIn = new Scanner(System.in);
			runFrom(-1);
			keyIn.close();
		}
		catch (CommandParseException e)
		{
			this.parseExceptionCallback.accept(e, e.getMessage());
		}
		catch (Exception e)
		{
			this.exceptionCallback.accept(e);
		}
	}
	public void runFrom(int start)
	{
		if (start != -1)
			pushStack(start);
		else
			parseLine = 0;
		if (keyIn == null)
			keyIn = new Scanner(System.in);
		while(parseLine < lines.length && parseLine != -1)
		{
			String line = lines[parseLine];
			System.out.print("Parsing: " + line);
			if (line.startsWith(COMMENT) || line.isEmpty() || line.startsWith(LABEL))
			{
				parseLine++;
				continue;
			}
			String first = firstToken(line);
			String[] storing = first.split(STORE);
			RunnableCommand cmd = parse(line);
			String out;
			System.out.println(" ... Done");
			putVar(PREVIOUS, out = cmd.run(this));
			for (int i = 1; i < storing.length; i++)
				putVar(storing[i], out);
			if (parseLine != -1)
				parseLine++;
		}
		parseLine = -1;
	}
	public void goTo(String label)
	{
		int line = getLabelLine(label);
		if (line == NO_LABEL)
			parseExcept("Invalid label specification", label, "No label found.");
		
		pushStack(line);
	}
	
	public boolean putVarType(String name, String val, CmdArg<?> type, String prompt)
	{
		if (type.parse(val) == null)
		{
			error("The input \"" + val + "\" for variable \"" + name + "\" is invalid for type \"" + prompt + "\".");
			return false;
		}
		else
		{
			putVar(name, val);
			return true;
		}
	}
	
	public void putVar(String name, String val)
	{
		try
		{
			Double.parseDouble(name);
			parseExcept("Numerical variable name", name);
		}
		catch (NumberFormatException e)
		{}
		vars.put(name, val);
	}
	
	public String getVar(String name)
	{
		return vars.get(arrayTrim(name));
	}
	public String prev()
	{
		return vars.get(PREVIOUS);
	}
	public static String arrayTrim(String token)
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
	
	///////////////////////
	
	public void setErrorCallback(Consumer<String> callback)
	{
		this.errorCallback = callback;
	}
	
	public void setExceptionCallback(Consumer<Exception> callback)
	{
		this.exceptionCallback = callback;
	}
	
	public void setParseExceptionCallback(BiConsumer<CommandParseException, String> callback)
	{
		this.parseExceptionCallback = callback;
	}
	
	public void setUserReqestType(UserReqType reqType)
	{
		this.userRequestType = reqType;
	}
	
	//////////////////////
	
	public static class CommandParseException extends RuntimeException
	{
		private static final long serialVersionUID = 1L;
		public CommandParseException(String name)
		{
			super(name);
		}
	}
}
