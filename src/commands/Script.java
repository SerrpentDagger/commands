package commands;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import commands.Command.RunnableCommand;

public class Script
{
	private static final LinkedHashMap<String, Command> CMDS = new LinkedHashMap<String, Command>();
	private static final HashMap<String, ScriptObject<?>> OBJECTS = new HashMap<String, ScriptObject<?>>();
	public static final String COMMENT = "//";
	public static final char COMMENT_CHAR = '/';
	public static final String LABEL = "--";
	public static final String PREVIOUS = "PREV";
	public static final String PARENT = "PARENT";
	public static final String NULL = "null";
	public static final String FALSE = "false", TRUE = "true";
	public static final String STORE = "->";
	public static final char ARR_S = '[', ARR_E = ']', ARR_ACCESS = '.', ARR_SEP = ';';
	public static final String ARR_LEN = "len";
	public static final char STRING_CHAR = '"';
	public static final char ESCAPE_CHAR = '\\';
	public static final char VAR_ARG_ARRAY = '#';
	public static final String INDEX = "INDEX";
	public static final int NO_LABEL = -2;
	public static final char UNRAW = '%', RAW = '$';
	
	public static final String BOOL = "boolean", VOID = "void", TOKEN = "token", STRING = "String", INT = "int", DOUBLE = "double", VALUE = "Value", OBJECT = "Object";
	
	public int parseLine = -1;
	private final HashMap<String, String> vars = new HashMap<String, String>();
	private final HashMap<String, Integer> labels = new HashMap<String, Integer>();
	private final ArrayDeque<StackEntry> stack = new ArrayDeque<StackEntry>();
	private final ArrayDeque<StackEntry> popped = new ArrayDeque<StackEntry>();
	private Script parent = null;
	public String path;
	public String name = NULL;
	public final String[] lines;
	private Scanner keyIn;
	public final Robot rob;
	private AtomicBoolean forceKill = new AtomicBoolean(false);
	private Consumer<String> printCallback = (str) -> System.out.println(str);
	private BiConsumer<String, String> prevCallback = (cmd, prv) -> {};
	private Consumer<String> errorCallback = (err) -> printCallback.accept(err);
	private BiConsumer<CommandParseException, String> parseExceptionCallback = (exc, err) -> { exc.printStackTrace(); };
	private Debugger debugger = (cmd, args, ret) -> {};
	private Debugger oldDebug = debugger;
	private boolean printingDebug = false;
	private Consumer<Exception> exceptionCallback = (exc) ->
	{
		errorCallback.accept("Exception encountered at line: " + (parseLine + 1) + "\r\n" + exc.toString());
	};
	private UserReqType userRequestType = (ctx, vars, type, prompt) ->
	{
		for (String var : vars)
		{
			String in = keyIn.next().trim();
			if (!ctx.putVarType(var, in, type, prompt))
				return false;
		}
		return true;
	};
	
	public static Command get(String name)
	{
		return CMDS.get(name);
	}
	
	public static Command add(String name, String ret, String desc, CmdArg<?>... args)
	{
		Command cmd = new Command(name, ret, desc, args);
		if (CMDS.put(name, cmd) != null)
			throw new IllegalArgumentException("Cannot register two commands to the same name.");
		return cmd;
	}
	
	public static <SO> ScriptObject<SO> add(String type, String desc, Class<SO> cl, CmdArg<?>... constArgs)
	{
		ScriptObject<SO> so = new ScriptObject<SO>(type, desc, cl, constArgs);
		if (OBJECTS.put(type, so) != null)
			throw new IllegalArgumentException("Cannot register two ScriptObject types of the same name.");
		
		newObjOf(so);
		
		return so;
	}
	
	public static Command[] getAllCommands()
	{
		return CMDS.values().toArray(new Command[CMDS.size()]);
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
	
	public static final Command VAR = add("var", VOID, "Sets a variable to a value.", CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		VarSet[] vars = (VarSet[]) objs[0];
		for (VarSet var : vars)
			ctx.putVar(var.var, var.set.raw);
		return ctx.prev();
	}).setVarArgs();
	public static final Command VAR_IF = add("var_if", VOID, "If the boolean is true, sets the variable to the value.", CmdArg.BOOL_VAR_SET).setFunc((ctx, objs) ->
	{
		BoolVarSet[] vars = (BoolVarSet[]) objs[0];
		for (BoolVarSet var : vars)
			if (var.bool)
				ctx.putVar(var.var, var.set.raw);
		return ctx.prev();
	}).setVarArgs();
	public static final Command VAR_IF_NOT = add("var_if_not", VOID, "If the boolean is false, sets the variable to the value.", CmdArg.BOOL_VAR_SET).setFunc((ctx, objs) ->
	{
		BoolVarSet[] vars = (BoolVarSet[]) objs[0];
		for (BoolVarSet var : vars)
			if (!var.bool)
				ctx.putVar(var.var, var.set.raw);
		return ctx.prev();
	}).setVarArgs();
	public static final Command VAR_IF_NOT_VAR = add("var_if_not_var", VOID, "Sets a variable to a value, if the variable does not already exist.", CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		VarSet[] vars = (VarSet[]) objs[0];
		for (VarSet var : vars)
			if (ctx.getVar(var.var) == null)
				ctx.putVar(var.var, var.set.raw);
		return ctx.prev();
	}).setVarArgs();
	public static final Command IS_VAR = add("is_var", BOOL, "Checks whether or not the token is a variable.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String[] vars = (String[]) objs[0];
		for (String var : vars)
			if (ctx.getVar(var) == null)
				return FALSE;
		return TRUE;
	}).rawArg(0).setVarArgs();
	public static final Command IS_NUMBER = add("is_number", BOOL, "Checks whether or not the token is a number.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String[] vars = (String[]) objs[0];
		for (String var : vars)
		{
			String val = ctx.getVar(var);
			if (val == null || CmdArg.DOUBLE.parse(val) == null)
				return FALSE;
		}
		return TRUE;
	}).rawArg(0).setVarArgs();
	public static final Command IS_ARRAY = add("is_array", BOOL, "Checks whether or not the token is an array.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String var = (String) objs[0];
		if (!ctx.isArray(var))
			return FALSE;
		return TRUE;
	}).rawArg(0);
	public static final Command IS_TYPE = add("is_type", BOOL, "Checks whether or not the token represents a recognized type name.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		return "" + OBJECTS.containsKey(objs[0]);
	});
	public static final Command IS_OBJ = add("is_obj", BOOL, "Checks whether the token is a valid Object in the given Type.", CmdArg.TYPE, CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String type = (String) objs[0];
		if (!OBJECTS.containsKey(type))
			ctx.parseExcept("Unrecognized type", type);
		
		return "" + OBJECTS.get(type).objs.containsKey(objs[1]);
	});
	public static final Command DESTROY = add("dest_obj", BOOL, "Destroys the Object of the given Type. Returns true if object existed.", CmdArg.TYPE, CmdArg.SCRIPT_OBJECT).setFunc((ctx, objs) ->
	{
		String type = (String) objs[0];
		if (!OBJECTS.containsKey(type))
			ctx.parseExcept("Unrecognized type", type);
		
		return "" + (OBJECTS.get(type).objs.remove((String) objs[1]) != null);
	});
	public static final Command GET_PARENT = add("get_parent", STRING, "Returns the name of the parent script # levels up, where 0 would target this script.", CmdArg.INT).setFunc((ctx, objs) ->
	{
		Script p = ctx;
		int count = (Integer) objs[0];
		for (int i = 0; i < count; i++)
		{
			p = p.parent;
			if (p == null)
				return NULL;
		}
		return p.name == null ? NULL : p.name;
	});
	public static final Command PRINT = add("print", STRING, "Prints and returns the supplied value.", CmdArg.STRING).setFunc((ctx, objs) ->
	{
		CmdString str = (CmdString) objs[0];
		ctx.printCallback.accept(str.unraw);
		return str.raw;
	});
	public static final Command PRINT_VARS = add("print_all_vars", VOID, "Prints all variables and their values.").setFunc((ctx, objs) ->
	{
		ctx.vars.forEach((var, val) ->
		{
			ctx.printCallback.accept(var + "=" + val);
		});
		return ctx.prev();
	});
	public static final Command PRINT_DEBUG = add("print_debug", VOID, "Sets whether or not debug information should be printed for every line execution.", CmdArg.BOOLEAN).setFunc((ctx, objs) ->
	{
		Boolean bool = (Boolean) objs[0];
		
		ctx.printDebug(bool);
		
		return ctx.prev();
	});
	public static final Command KEY_IN = add("key_in", VALUE, "Fills the variables with user-keyboard-input.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String[] vars = (String[]) objs[0];
		String next = NULL;
		for (String var : vars)
		{
			System.out.println(var + "?");
			next = ctx.keyIn.next();
			ctx.putVar(var, next);
		}
		return next;
	}).rawArg(0).setVarArgs();
	public static final Command USER_REQ = userReqType("user_req", CmdArg.STRING, "String");
	public static final Command USER_REQ_INT = userReqType("user_req_int", CmdArg.INT, "integer");
	public static final Command USER_REQ_DOUBLE = userReqType("user_req_double", CmdArg.DOUBLE, "double");
	public static final Command USER_REQ_STRING = userReqType("user_req_string", CmdArg.STRING, "text");
	public static final Command USER_REQ_BOOL = userReqType("user_req_bool", CmdArg.BOOLEAN, "boolean");
	public static final Command USER_REQ_TOKEN = userReqType("user_req_token", CmdArg.TOKEN, "token");
	
	public static final Command CONCAT = add("concat", STRING, "Concatinates and returns the argument Strings.", CmdArg.STRING).setFunc((ctx, objs) ->
	{
		String out = "\"";
		for (CmdString tokArr : (CmdString[]) objs[0])
		{
			out += tokArr.unraw;
		}
		return out + "\"";
	}).setVarArgs();
	public static final Command ADD = add("add", DOUBLE, "Adds and returns the argument numbers.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return "" + operate(0, (Object[]) objs[0], (all, next) -> all + next);
	}).setVarArgs();
	public static final Command SUB = add("sub", DOUBLE, "Subtracts and returns the argument numbers.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		Object[] arr = (Object[]) objs[0];
		return "" + operate((double) arr[0] * 2, arr, (all, next) -> all - next);
	}).setVarArgs();
	public static final Command MULT = add("mult", DOUBLE, "Multiplies and returns the argument numbers.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return "" + operate(1, (Object[]) objs[0], (all, next) -> all * next);
	}).setVarArgs();
	public static final Command DIVI = add("divi", DOUBLE, "Divides and returns the argument numbers.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		Object[] arr = (Object[]) objs[0];
		return "" + operate((double) arr[0] * (double) arr[0], arr, (all, next) -> all / next);
	}).setVarArgs();
	public static final Command MODULO = add("mod", DOUBLE, "Returns A % B.", CmdArg.DOUBLE, CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return "" + (Double) objs[0] % (Double) objs[1];
	});
	public static final Command INCREMENT = add("inc", DOUBLE, "Returns the increment of the argument number.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return "" + ((double) objs[0] + 1);
	});
	public static final Command DECREMENT = add("dec", DOUBLE, "Returns the decriment of the argument number.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return "" + ((double) objs[0] - 1);
	});
	public static final Command FLOOR = add("floor", INT, "Returns the largest integer less than or equal to this double.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return "" + Math.floor((double) objs[0]);
	});
	public static final Command CEIL = add("ceil", INT, "Returns the smallest integer greater than or equal to this double.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return "" + Math.ceil((double) objs[0]);
	});
	public static final Command NEGATE = add("negate", DOUBLE, "Returns the negation of the argument number.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return "" + (-(double) objs[0]);
	});
	public static final Command NOT = add("not", BOOL, "Return the boolean inverse of the argument.", CmdArg.BOOLEAN).setFunc((ctx, objs) ->
	{
		return "" + (!(boolean) objs[0]);
	});
	public static final Command OR = add("or", BOOL, "Return true if any argument is true.", CmdArg.BOOLEAN).setFunc((ctx, objs) ->
	{
		Boolean[] ors = (Boolean[]) objs[0];
		boolean out = false;
		for (boolean b : ors)
			out = out || b;
		return "" + out;
	}).setVarArgs();
	public static final Command AND = add("and", BOOL, "Return true if every argument is true.", CmdArg.BOOLEAN).setFunc((ctx, objs) ->
	{
		Boolean[] ands = (Boolean[]) objs[0];
		boolean out = true;
		for (boolean b : ands)
			out = out && b;
		return "" + out;
	}).setVarArgs();
	public static final Command COMPARE = add("compare", BOOL, "Returns the evaluation of the boolean expression.", CmdArg.BOOLEAN_EXP).setFunc((ctx, objs) ->
	{
		return "" + ((BooleanExp) objs[0]).eval();
	});
	public static final Command STRING_MATCH = add("string_match", BOOL, "Returns true if the Strings match.", CmdArg.STRING).setFunc((ctx, objs) ->
	{
		CmdString[] strs = (CmdString[]) objs[0];
		boolean equal = true;
		for (int i = 1; i < strs.length; i++)
			equal = equal && strs[i].unraw.equals(strs[0].unraw);
		return "" + equal;
	}).setVarArgs();
	public static final Command IF_THEN_ELSE = add("if", TOKEN, "Iterates through the arguments, and returns the first token that has a true boolean, or null.", CmdArg.BOOLEAN_THEN).setFunc((ctx, objs) ->
	{
		BooleanThen[] ba = (BooleanThen[]) objs[0];
		for (BooleanThen b : ba)
			if (b.bool)
				return b.then;
		return NULL;
	}).setVarArgs();
	public static final Command FOR = add("for", VOID, "Excecutes the given token label the given number of times.", CmdArg.INT, CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		int count = (int) objs[0];
		String label = (String) objs[1];
		int line = ctx.getLabelLine(label);
		if (line == NO_LABEL)
			ctx.parseExcept("Invalid label specification", label, "No label found.");
		for (int i = 0; i < count; i++)
		{
			ctx.putVar(INDEX, "" + i);
			ctx.subRunFrom(line);
		}
		return ctx.prev();
	});
	public static final Command WHILE = add("while", VOID, "While the boolean is true, excecutes the token label.", CmdArg.BOOLEAN, CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		boolean bool = (boolean) objs[0];
		int wL = ctx.parseLine;
		int line = ctx.getLabelLine((String) objs[1]);
		if (line == NO_LABEL)
			ctx.parseExcept("Invalid label specification", (String) objs[1], "No label found.");
		if (bool)
		{
			ctx.subRunFrom(line);
			ctx.parseLine = wL - 1;
		}
		return ctx.prev();
	});
	public static final Command GOTO = add("goto", INT, "Excecutes the given token label, and returns the line number of that label.", CmdArg.TOKEN, CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		String label = (String) objs[0];
		if (label.equals(NULL))
			return "" + ctx.parseLine;
		
		int line = ctx.getLabelLine(label);
		if (line == NO_LABEL)
			ctx.parseExcept("Invalid label specification", label, "No label found.");
		
		for (int i = 0; i < ((VarSet[]) objs[1]).length; i++)
		{
			VarSet var = ((VarSet[]) objs[1])[i];
			ctx.putVar(var.var, var.set.raw);
		}
		
		ctx.pushStack(line, true);
		
		return "" + line;
	}).setVarArgs();
	public static final Command SKIPTO = add("skipto", INT, "Skips excecution to the given token label, without modifying the stack.", CmdArg.TOKEN, CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		String label = (String) objs[0];
		if (label.equals(NULL))
			return "" + ctx.parseLine;
		
		int line = ctx.getLabelLine(label);
		if (line == NO_LABEL)
			ctx.parseExcept("Invalid label specification", label, "No label found.");
		
		for (int i = 0; i < ((VarSet[]) objs[1]).length; i++)
		{
			VarSet var = ((VarSet[]) objs[1])[i];
			ctx.putVar(var.var, var.set.raw);
		}
		
		ctx.parseLine = line;
		
		return "" + line;
	}).setVarArgs();
	public static final Command RETURN = add("return", VOID, "Marks the end of a label or code section.").setFunc((ctx, objs) ->
	{
		ctx.popStack();
		return ctx.prev();
	});
	public static final Command SLEEP = add("sleep", VOID, "Sleep the given number of milliseconds.", CmdArg.INT).setFunc((ctx, objs) ->
	{
		try
		{
			Thread.sleep((int) objs[0]);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		return ctx.prev();
	});

	public static final Command MOUSE_MOVE = add("mouse_move", VOID, "Move mouse to the X, Y supplied.", CmdArg.INT, CmdArg.INT).setFunc((ctx, objs) ->
	{
		ctx.rob.mouseMove((int) objs[0], (int) objs[1]);
		return ctx.prev();
	});
	
	public static final Command MOUSE_PRESS = mouseCommand("mouse_press", "Presses the mouse button specified by a number > 0.", (mouse, ctx, objs) ->
	{
		ctx.rob.mousePress(mouse);
	}, CmdArg.INT);
	
	public static final Command MOUSE_RELEASE = mouseCommand("mouse_release", "Releases the mouse button specified by a number > 0.", (mouse, ctx, objs) ->
	{
		ctx.rob.mouseRelease(mouse);
	}, CmdArg.INT);
	
	public static final Command MOUSE = mouseCommand("mouse", "Presses the mouse button for the specified time, then releases it.", (mouse, ctx, objs) ->
	{
		ctx.rob.mousePress(mouse);
		ctx.rob.delay((int) objs[1]);
		ctx.rob.mouseRelease(mouse);
	}, CmdArg.INT, CmdArg.INT);
	
	public static final Command MOUSE_CLICK = mouseCommand("mouse_click_at", "Clicks the given mouse button at the given position.", (mouse, ctx, objs) ->
	{
		ctx.rob.mouseMove((int) objs[1], (int) objs[2]);
		ctx.rob.mousePress(mouse);
		ctx.rob.mouseRelease(mouse);
	}, CmdArg.INT, CmdArg.INT, CmdArg.INT);
	
	public static final Command KEY_PRESS = keyCommand("key_press", "Presses the key down (does not release automatically).", (key, ctx, objs) ->
	{
		ctx.rob.keyPress(key);
	}, CmdArg.TOKEN);
	
	public static final Command KEY_RELEASE = keyCommand("key_release", "Releases the key.", (key, ctx, objs) ->
	{
		ctx.rob.keyRelease(key);
	}, CmdArg.TOKEN);
	
	public static final Command KEY = keyCommand("key", "Presses the key for the given # of ms, then releases it.", (key, ctx, objs) ->
	{
		ctx.rob.keyPress(key);
		ctx.rob.delay((int) objs[1]);
		ctx.rob.keyRelease(key);
	}, CmdArg.TOKEN, CmdArg.INT);
	
	public static final Command AUTO_DELAY = add("set_robot_delay", VOID, "Sets the automatic delay after robot operations (default 300 ms).", CmdArg.INT).setFunc((ctx, objs) ->
	{
		ctx.rob.setAutoDelay((int) objs[0]);
		return ctx.prev();
	});
	
	private static Command mouseCommand(String name, String desc, KeyFunc m, CmdArg<?>... args)
	{
		return add(name, BOOL, desc, args).setFunc((ctx, objs) ->
		{
			try
			{
				int button = MouseEvent.getMaskForButton((int) objs[0]);
				m.key(button, ctx, objs);
				return TRUE;
			}
			catch (IllegalArgumentException e)
			{
				ctx.parseExcept("Invalid mouse button", "" + (int) objs[0], "The button ID must be between 1 and the number of buttons on your mouse, inclusive.");
				return FALSE;
			}
		});
	}
	private static Command keyCommand(String name, String desc, KeyFunc k, CmdArg<?>... args)
	{
		return add(name, BOOL, desc, args).setFunc((ctx, objs) ->
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
				ctx.exceptionCallback.accept(e);
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
		return add(name, BOOL, "Requests user input of type: " + userPromptType + ", returns true on successful input.", CmdArg.TOKEN).setFunc((ctx, objs) ->
		{
			if (ctx.userRequestType.reqType(ctx, (String[]) objs[0], type, userPromptType))
				return TRUE;
			else
				return FALSE;
		}).setVarArgs();
	}
	@FunctionalInterface
	public static interface UserReqType
	{
		public boolean reqType(Script ctx, String[] vars, CmdArg<?> type, String userPromptType);
	}
	@FunctionalInterface
	public static interface Debugger
	{
		public void info(String command, String args, String retrn);
	}
	
	///////////////
	
	public static <O> Command newObjOf(ScriptObject<O> scriptObj)
	{
		CmdArg<?>[] constArgs = scriptObj.getConstArgs();
		CmdArg<?>[] args = new CmdArg<?>[constArgs.length + 1];
		args[0] = CmdArg.TOKEN;
		for (int i = 0; i < constArgs.length; i++)
			args[i + 1] = constArgs[i];
		String tn = scriptObj.getTypeName();
		return add("new_" + scriptObj.getCommandName(), tn, "Constructs a new " + tn + ": " + scriptObj.getDescription(), args).setFunc((ctx, objs) ->
		{
			String name = (String) objs[0];
			Object[] constObjs = new Object[objs.length - 1];
			for (int i = 1; i < objs.length; i++)
				constObjs[i - 1] = objs[i];
			
			scriptObj.construct(name, constObjs);
			
			return name;
		});
	}
	
	/////////////////////////////////////////////
	
	public boolean isArray(String token)
	{
		String var = getVar(token);
		if (var != null)
			return isArray(var);
		
		return token.startsWith("" + ARR_S) && token.endsWith("" + ARR_E);
	}
	public boolean isString(String token)
	{
		String var = getVar(token);
		if (var != null)
			return isString(var);
		
		return token.startsWith("" + STRING_CHAR) && token.endsWith("" + STRING_CHAR);
	}
	
	public static String[] argsOf(String line)
	{
		line = line.trim();
		String str = "";
		for (int i = 0; i < line.length(); i++)
		{
			char parse = line.charAt(i);
			if (parse == ' ')
				break;
			else
				str += parse;
		}
		line = line.replaceFirst(quote(str), "");
		
		ArrayList<String> args = new ArrayList<String>();
		
		String arg = "";
		int quotes = 0;
		int comment = 0;
		for (int i = 0; i < line.length(); i++)
		{
			if (comment == 2)
				break;
			
			boolean escaped = i > 0 && line.charAt(i - 1) == ESCAPE_CHAR;
			char parseChar = line.charAt(i);
			
			if (!escaped)
			{
				quotes += sameChar(parseChar, STRING_CHAR);
				if (quotes % 2 != 1)
				{
					int com = sameChar(parseChar, COMMENT_CHAR);
					comment = com == 0 ? 0 : comment + com;
					if (comment == 2)
						arg = arg.substring(0, arg.length() - 1);
				}
			}
			
			boolean inQuote = quotes % 2 == 1;
			boolean commented = comment == 2;
			boolean buildingArg = !commented && (inQuote || parseChar != ',');
			
			if (buildingArg)
				arg += parseChar;
			else if (!arg.trim().isEmpty())
			{
				args.add(arg.trim());
				arg = "";
			}
			if (i == line.length() - 1)
				args.add(arg.trim());
		}
		
		return args.toArray(new String[args.size()]);
	}
	public static String[] arrAccOf(String token)
	{
		ArrayList<String> access = new ArrayList<String>();
		
		String str = token.trim();
		
		String acc = "";
		int quotes = 0;
		int arraysDeep = 0;
		for (int i = 0; i < str.length(); i++)
		{
			boolean escaped = i > 0 && str.charAt(i - 1) == ESCAPE_CHAR;
			char parse = str.charAt(i);
			
			if (!escaped)
			{
				quotes += sameChar(parse, STRING_CHAR);
				arraysDeep += sameChar(parse, ARR_S);
				arraysDeep -= sameChar(parse, ARR_E);
			}
			
			boolean inQuote = quotes % 2 == 1;
			boolean inArray = arraysDeep > 0;
			boolean inWord = parse != ARR_ACCESS;
			boolean buildingAcc = inQuote || inArray || inWord;
			
			if (buildingAcc)
				acc += parse;
			else if (!acc.isEmpty())
			{
				access.add(acc);
				acc = "";
			}
			if (i == str.length() - 1 && !acc.isEmpty())
				access.add(acc);
		}
		
		return access.toArray(new String[access.size()]);
	}
	public static String[] tokensOf(String line)
	{
		ArrayList<String> tokens = new ArrayList<String>();
		
		String str = line.trim();
		
		String tok = "";
		int quotes = 0;
		int arraysDeep = 0;
		for (int i = 0; i < str.length(); i++)
		{
			boolean escaped = i > 0 && str.charAt(i - 1) == ESCAPE_CHAR;
			char parseChar = str.charAt(i);
			
			if (!escaped)
			{
				quotes += sameChar(parseChar, STRING_CHAR);
				arraysDeep += sameChar(parseChar, ARR_S);
				arraysDeep -= sameChar(parseChar, ARR_E);
			}
			
			boolean inQuote = quotes % 2 == 1;
			boolean inArray = arraysDeep > 0;
			boolean inWord = parseChar != ' ' && parseChar != ',' && parseChar != ARR_SEP;
			boolean buildingToken = inQuote || inArray || inWord;
			
			if (buildingToken)
				tok += parseChar;
			else if (!tok.isEmpty())
			{
				tokens.add(tok);
				tok = "";
			}
			if (i == str.length() - 1 && !tok.isEmpty())
				tokens.add(tok);
		}
		
		return tokens.toArray(new String[tokens.size()]);
	}
	private static int sameChar(char a, char b) { return a == b ? 1 : 0; }
	public static String firstToken(String line)
	{
		String first = "";
		for (int i = 0; i < line.length() && line.charAt(i) != ' '; i++)
			first += line.charAt(i);
		return first;
	}
	public static String[] arrayElementsOf(String array)
	{
		ArrayList<String> elements = new ArrayList<String>();
		String str = arrayTrim(array);
		
		String ele = "";
		int quotes = 0;
		int arraysDeep = 0;
		for (int i = 0; i < str.length(); i++)
		{
			boolean escaped = i > 0 && str.charAt(i - 1) == ESCAPE_CHAR;
			char parseChar = str.charAt(i);
			
			if (!escaped)
			{
				quotes += sameChar(parseChar, STRING_CHAR);
				if (quotes % 2 != 1)
				{
					arraysDeep += sameChar(parseChar, ARR_S);
					arraysDeep -= sameChar(parseChar, ARR_E);
				}
			}
			
			boolean inQuote = quotes % 2 == 1;
			boolean inArray = arraysDeep > 0;
			boolean inWord = parseChar != ' ' && parseChar != ',' && parseChar != ARR_SEP;
			boolean buildingElement = inQuote || inArray || inWord;
			
			if (buildingElement)
				ele += parseChar;
			else if (!ele.isEmpty())
			{
				elements.add(ele);
				ele = "";
			}
			if (i == str.length() - 1 && !ele.isEmpty())
				elements.add(ele);
		}
		
		return elements.toArray(new String[elements.size()]);
	}
	
	///////////
	
	private String arrayReplace(Command cmd, int argInd, int tokInd, String array)
	{
		String inner = arrayTrim(array);
		String[] innerTokens = tokensOf(inner);
		String replaced = "" + ARR_S;
		
		for (int i = 0; i < innerTokens.length; i++)
			replaced += varParse(cmd, argInd, tokInd, innerTokens[i]) + (i == innerTokens.length - 1 ? "" : ARR_SEP + " ");
		
		return replaced + ARR_E;
	}
	
	public String varParse(Command cmd, int argInd, int tokInd, String token)
	{
		String trm = varTrim(token);
		
		boolean array = isArray(token);
		if (CmdArg.DOUBLE.parse(trm) != null && !array)
			return trm;
		
		boolean unraw = token.charAt(0) == UNRAW;
		boolean raw = token.charAt(0) == RAW;
		
		if (raw || (!unraw && (cmd.rawToken(argInd, tokInd)))) // If raw, return what's written.
			return trm;
		
		if (array)
		{
			String var = getVar(trm);
			return arrayReplace(cmd, argInd, tokInd, var == null ? trm : var);
		}
		
		String[] arrAcc = arrAccOf(trm);
		if (arrAcc.length > 1)
		{
			if (!isArray(arrAcc[0]))
				parseExcept("Array access attempted on non-array variable", arrAcc[0]);
			
			String[] elements = arrayElementsOf(varParse(cmd, argInd, tokInd, arrAcc[0]));
			
			if (arrAcc[1].equals(ARR_LEN))
				return "" + elements.length;
			
			Integer i = CmdArg.INT.parse(varParse(cmd, argInd, tokInd, arrAcc[1]));
			if (i == null)
				parseExcept("Null index passed to array", arrAcc[0], arrAcc[1]);
			try
			{
				String extra = "";
				for (int j = 2; j < arrAcc.length; j++)
					extra += ARR_ACCESS + arrAcc[j];
				return varParse(cmd, argInd, tokInd, elements[i] + extra);
			}
			catch (IndexOutOfBoundsException e)
			{
				parseExcept("Index out of bounds", arrAcc[0], "" + i);
			}
		}
		
		String var = getVar(trm); // trm is not an array.
		if (var != null && !raw) // If not raw, it is a request to use the contents of the variable. This may parse nested variables.
			return varParse(cmd, argInd, tokInd, var);
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
	private static String varTrim(String str)
	{
		return str.trim().replaceFirst(quote(UNRAW), "").replaceFirst(quote(RAW), "");
	}
	private static String getTrim(String str)
	{
		return varTrim(str).replaceAll(quote(ARR_S), "").replaceAll(quote(ARR_E), "");
	}
	private static String quote(String str)
	{
		return Pattern.quote(str);
	}
	private static String quote(char chr)
	{
		return quote("" + chr);
	}
	
	public RunnableCommand parse(String line)
	{
		String[] argStrs = argsOf(line);
		if (line.length() > 0)
		{
			String first = firstToken(line);
			String[] storing = first.split(STORE);
			String name = storing[0];
			Command cmd = CMDS.get(name);
			if (cmd != null)
			{
				CmdArg<?>[] args = cmd.args;
				boolean varArgs = cmd.isVarArgs();
				boolean varArgArray = false;
				if (argStrs.length != args.length && !(varArgs && argStrs.length >= args.length - 1))
					parseExcept("Invalid argument count", line, name + " requires " + args.length + " args, but " + argStrs.length + " have been provided. Args are separated by commas.");
				
				Object[] objs = new Object[args.length];
				if (varArgs)
					objs[objs.length - 1] = Array.newInstance(args[args.length - 1].cls, argStrs.length - args.length + 1);
				
				String input = "";
				for (int argInd = 0; argInd < argStrs.length; argInd++)
				{
					CmdArg<?> arg = args[Math.min(argInd, args.length - 1)];
					boolean atVA = varArgs && argInd >= args.length - 1;
					boolean firstVarArg = varArgs && argInd == args.length - 1;
					
					String[] tokens = tokensOf(argStrs[argInd]);
					
					varArgArray = varArgArray || (firstVarArg && tokens.length == 1 && isArray(varTrim(tokens[0])));
					if (varArgArray && !firstVarArg)
						parseExcept("Invalid argument count", line, "If var-arg arguments are specified as an array, the array must be the last argument");
					
					String trimmed = "";
					Object obj;
					if (!varArgArray)
					{
						int count = arg.tokenCount();
						if (tokens.length != count)
							parseExcept("Invalid token count", line, "Argument " + (argInd + 1) + " requires " + count + " tokens, but " + tokens.length + " have been provided. Tokens are separated by spaces.");
					
						for (int tokInArg = 0; tokInArg < count; tokInArg++)
							trimmed += varParse(cmd, Math.min(argInd, args.length - 1), tokInArg, tokens[tokInArg]) + " ";
						
						trimmed = trimmed.trim();
						obj = arg.parse(trimmed);
						if (obj == null)
							parseExcept("Invalid token resolution", trimmed);
						
						if (!atVA)
							objs[argInd] = obj;
						else
							((Object[]) objs[objs.length - 1])[argInd - args.length + 1] = obj;
					}
					else
					{
						trimmed = varParse(cmd, args.length - 1, 0, tokens[0]);
						obj = CmdArg.arrayOf(arg).parse(trimmed);
						if (obj == null)
							parseExcept("Invalid var-arg array resolution", trimmed, tokens[0]);
						
						objs[objs.length - 1] = obj;
					}
					input += trimmed + (argInd == argStrs.length - 1 ? "" : ", ");
				}

				RunnableCommand run = new RunnableCommand(cmd, input, objs);
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
				+ (extra == null ? "." : ", extra info: " + extra));
	}
	
	////////////////////////////////////////////////
	
	public Script(String str) throws AWTException
	{
		this(new Scanner(str));
	}
	
	public Script(Scanner scan) throws AWTException
	{
		rob = new Robot();
		rob.setAutoDelay(170);
		path = null;
		String str = "";
		int num = 0;
		while (scan.hasNextLine())
		{
			String line = scan.nextLine();
			if (line.startsWith(LABEL))
			{
				putLabel(firstToken(line), num);
			}
			
			str += line + "\n";
			num++;
		}
		lines = str.split("\n");
		scan.close();
		putVar(PARENT, NULL);
	}
	
	public Script(File script) throws FileNotFoundException, AWTException
	{
		this(new Scanner(script));
		this.path = script.getAbsolutePath();
	}
	
	public synchronized void setForceKill(AtomicBoolean bool)
	{
		forceKill = bool;
	}
	public synchronized void forceKill()
	{
		forceKill.set(true);
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
	public void subRunFrom(int start)
	{
		runFrom(start, false);
	}
	private void runFrom(int start)
	{
		runFrom(start, true);
	}
	private void runFrom(int start, boolean returns)
	{
		if (start != -1)
			pushStack(start, returns);
		else
			pushStack(0, returns);
		if (keyIn == null)
			keyIn = new Scanner(System.in);
		
		while(parseLine < lines.length && !stack.isEmpty() && !forceKill.get())
		{
			String line = lines[parseLine];
		//	System.out.print("Parsing: " + line);
			if (line.startsWith(COMMENT) || line.isEmpty() || line.startsWith(LABEL))
			{
				parseLine++;
				continue;
			}
			String first = firstToken(line);
			String[] storing = first.split(STORE);
			RunnableCommand cmd = parse(line);
			String out;
	//		System.out.println(" ... Done");
			putVar(PREVIOUS, out = cmd.run(this));
			for (int i = 1; i < storing.length; i++)
				putVar(storing[i], out);
			prevCallback.accept(storing[0], out);
			debugger.info(storing[0], cmd.getInput(), out);
			if (!popped.isEmpty() && !popped.pop().returns)
				break;
			parseLine++;
		}
	}
	public void goTo(String label)
	{
		int line = getLabelLine(label);
		if (line == NO_LABEL)
			parseExcept("Invalid label specification", label, "No label found.");
		
		pushStack(line, true);
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
		if (name.contains("" + ARR_S) || name.contains("" + ARR_E) || name.contains("" + STRING_CHAR) || name.contains("" + ESCAPE_CHAR))
			parseExcept("Illegal characters in variable name", name);
		try
		{
			Double.parseDouble(name);
			parseExcept("Numerical variable name", name);
		}
		catch (NumberFormatException e)
		{}
		vars.put(name, val);
	}
	
	public void integrateVarsFrom(Script other)
	{
		other.vars.forEach((var, val) ->
		{
			if (!var.equals(PARENT))
				vars.put(var, val);
		});
	}
	
	public String getVar(String name)
	{
		return vars.get(getTrim(name));
	}
	public String prev()
	{
		return vars.get(PREVIOUS);
	}
	public static String arrayTrim(String token)
	{
		String str = token.trim();
		if (str.startsWith("" + ARR_S))
			str = str.substring(1);
		if (str.endsWith("" + ARR_E))
			str = str.substring(0, str.length() - 1);
		return str;
	}
	public static String stringTrim(String string)
	{
		String str = string.trim();
		if (str.startsWith("" + STRING_CHAR) && str.endsWith("" + STRING_CHAR))
			return str.substring(1, str.length() - 1);
		else
			return str;
	}
	
	public void putLabel(String label, int line)
	{
		labels.put(label.replaceFirst(quote(LABEL), ""), line);
	}
	public int getLabelLine(String label)
	{
		Integer line = labels.get(label.replaceFirst(quote(LABEL), ""));
		if (line == null)
			return NO_LABEL;
		return line;
	}
	
	private void pushStack(int to, boolean returns)
	{
		stack.push(new StackEntry(parseLine, to, returns));
		parseLine = to;
	}
	
	private void popStack()
	{
		StackEntry ent = stack.pop();
		popped.push(ent);
		parseLine = ent.from;
		return;
	}
	
	///////////////////////
	
	public void setPrintCallback(Consumer<String> callback)
	{
		this.printCallback = callback;
	}
	
	public void setPrevCallback(BiConsumer<String, String> callback)
	{
		this.prevCallback = callback;
	}
	
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
	
	public void setDebugger(Debugger debug)
	{
		debugger = debug;
		oldDebug = debug;
		printingDebug = false;
	}
	
	public void printDebug(boolean bool)
	{
		printingDebug = bool;
		if (bool)
		{
			oldDebug = debugger;
			debugger = (cmd, args, ret) -> { printCallback.accept(cmd + "(" + args + ") -> " + ret); };
		}
		else
		{
			debugger = oldDebug;
		}
	}
	
	public Consumer<String> getPrintCallback()
	{
		return printCallback;
	}
	
	public BiConsumer<String, String> getPrevCallback()
	{
		return prevCallback;
	}
	
	public Consumer<String> getErrorCallback()
	{
		return errorCallback;
	}
	
	public Consumer<Exception> getExceptionCallback()
	{
		return exceptionCallback;
	}
	
	public BiConsumer<CommandParseException, String> getParseExceptionCallback()
	{
		return parseExceptionCallback;
	}
	
	public UserReqType getUserReqType()
	{
		return userRequestType;
	}
	
	public boolean printingDebug()
	{
		return printingDebug;
	}
	
	public Debugger getDebugger()
	{
		return debugger;
	}
	
	public void transferCallbacks(Script to)
	{
		to.setPrintCallback(getPrintCallback());
		to.setErrorCallback(getErrorCallback());
		to.setExceptionCallback(getExceptionCallback());
		to.setParseExceptionCallback(getParseExceptionCallback());
		to.setDebugger(getDebugger());
		to.setPrevCallback(getPrevCallback());
		to.setForceKill(forceKill);
		to.setUserReqestType(getUserReqType());
	}
	
	public Script setParent(Script parent)
	{
		this.parent = parent;
		return this;
	}
	
	public Script getParent()
	{
		return parent;
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
