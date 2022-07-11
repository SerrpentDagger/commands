package commands;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import arrays.AUtils;
import commands.CmdArg.TypeArg;
import commands.Command.RunnableCommand;
import commands.Scope.SNode;
import utilities.ArrayUtils;
import utilities.StringUtils;

public class Script
{
	private static final LinkedHashMap<String, Command> CMDS = new LinkedHashMap<String, Command>();
	private static final HashMap<String, ScriptObject<?>> OBJECTS = new HashMap<String, ScriptObject<?>>();
	private static final HashMap<String, Library> LIBS = new HashMap<>();
	
	public static final String COMMENT = "//";
	public static final char COMMENT_CHAR = '/';
	
	public static final String LABEL = "--", SCOPED_LABEL = "~~";
	public static final String LABEL_REG = LABEL + "|" + SCOPED_LABEL;
	public static final String LABEL_ACC_FOR_DOWN = "|", LABEL_ACC_TO_UP = "^";
	public static final String[] VALID_LABEL_MODS = new String[] { LABEL_ACC_FOR_DOWN, LABEL_ACC_TO_UP };
	public static final String LABEL_MODS_REG = "[" + quote(LABEL_ACC_FOR_DOWN) + quote(LABEL_ACC_TO_UP) + "]+";
	public static final Pattern LABEL_MODS_PATTERN = Pattern.compile(LABEL_MODS_REG);
	
	public static final String PREVIOUS = "PREV";
	public static final String PARENT = "PARENT";
	public static final String NULL = "null";
	public static final String FALSE = "false", TRUE = "true";
	
	public static final String STORE = "->";
	public static final String INLINE_IF = "?";
	public static final String INLINE_ELSE = ":";
	public static final String MEMBER_ACCESS = ".";
	public static final char ARR_S = '[', ARR_E = ']', ARR_ACCESS = '.', ARR_SEP = ';';
	public static final String ARR_LEN = "len";
	public static final char STRING_CHAR = '"';
	public static final char ESCAPE_CHAR = '\\';
	public static final char HELP_CHAR = '?';
	public static final char VAR_ARG_ARRAY = '#';
	public static final String END_SCRIPT = "==";
	public static final String VAR_ARG_STR = "" + VAR_ARG_ARRAY;
	public static final String HELP_CHAR_STR = "" + HELP_CHAR;
	public static final String INDEX = "INDEX";
	public static final int NO_LABEL = -2;
	public static final Label GLOBAL = new Label("GLOBAL", 0, false, true, false);
	public static final char UNRAW = '%', RAW = '$', REF = '@';
	
	public static final String ILLEGAL_VAR_REG_EX = "[^\\w\\-]";
	public static final Pattern ILLEGAL_VAR_MATCHER = Pattern.compile(ILLEGAL_VAR_REG_EX);
	
	public static final String BOOL = "boolean", VOID = "void", TOKEN = "token", TOKEN_ARR = ARR_S + TOKEN + ARR_E, STRING = "String", INT = "int", DOUBLE = "double", VALUE = "Value", OBJECT = "Object";
	
	//////////////// Files
	public static final String HIDDEN_SCRIPT = "--";
	private static final String SEP = File.separator;
	private static String SCRIPT_PATH = "scajl" + SEP;
	private static String SCRIPT_EXT = ".scajl";
	////////////////
	
	public int parseLine = -1;
	private final Scope scope = new Scope();
	private final HashMap<String, Label> labels = new HashMap<String, Label>();
	private final ArrayDeque<StackEntry> stack = new ArrayDeque<StackEntry>();
	private final ArrayDeque<StackEntry> popped = new ArrayDeque<StackEntry>();
	private Script parent = null;
	public String path;
	public String name = "BASE";
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
	private Consumer<Throwable> exceptionCallback = (exc) ->
	{
		errorCallback.accept("Exception encountered at line: " + (parseLine + 1) + "\n" + exc.toString());
	};
	private UserReqType userRequestType = (ctx, vars, type, prompt) ->
	{
		for (String var : vars)
		{
			printCallback.accept(var + "?: ");
			String in = keyIn.next();
			in = in.trim();
			if (!ctx.putVarType(var, in, type, prompt))
				return false;
		}
		return true;
	};
	
	public static Command add(String name, String ret, String desc, CmdArg<?>... args)
	{
		Command cmd = new Command(name, ret, desc, args);
		if (CMDS.put(name, cmd) != null)
			throw new IllegalArgumentException("Cannot register two commands to the same name: " + name);
		return cmd;
	}
	public static Command overload(String name, Command other, String differ, ArgTransform transform, CmdArg<?>... args)
	{
		return add(name, other.getReturn(), "Overload of " + other.getName() + ": " + other.getDescription() + " - " + differ, args).setFunc((ctx, objs) ->
		{
			return other.func.cmd(ctx, transform.transform(objs));
		});
	}
	
	public static Command expose(Method m) { return expose(m, null); }
	public static Command expose(Method m, ScriptObject<?> to)
	{
		String originalName = m.getName();
		String name = originalName;
		int n = 2;
		HashMap<String, Command> map = to == null ? CMDS : to.getMemberCommandMap();
		while (map.containsKey(name))
			name = originalName + n++;
		
		Class<?> retType = m.getReturnType();
		if (retType.isPrimitive())
			retType = CmdArg.wrap(retType);
		TypeArg<?> retTypeArg = CmdArg.getTypeArgFor(retType);
		String ret = retType.getSimpleName();
		boolean isVoid = retType.equals(Void.TYPE);
		Class<?>[] types = m.getParameterTypes();
		for (int i = 0; i < types.length; i++)
			if (types[i].isPrimitive())
				types[i] = CmdArg.wrap(types[i]);
		boolean isInst = !Modifier.isStatic(m.getModifiers());
		if (isInst)
			types = AUtils.extendPre(types, 1, (i) -> m.getDeclaringClass());
		boolean varArgs = m.isVarArgs();
		if (varArgs)
			types[types.length - 1] = types[types.length - 1].getComponentType();
		
		String params = StringUtils.toString(types, (cl) -> cl.getSimpleName(), "(", ", ", "");
		if (varArgs)
			params += "...";
		params += ")";
		String displayName = m.getDeclaringClass().getCanonicalName() + "." + originalName + params;
		
		CmdArg<?>[] args = new CmdArg<?>[types.length];
		for (int i = 0; i < types.length; i++)
		{
			args[i] = CmdArg.getArgFor(types[i]);
			if (args[i] == null)
				throw new IllegalStateException("Unable to automatically expose method '" + displayName + "' due to lack of registered"
						+ " CmdArg for class: " + types[i].getCanonicalName());
		}
		if (retTypeArg.arg == null)
			throw new IllegalStateException("Unable to automatically expose method '" + displayName + "' due to lack of registered"
					+ " CmdArg for return type of class: " + retType.getCanonicalName());
		
		String desc = "Direct exposition of method: " + displayName;
		
		Command cmd = to == null ? add(name, ret, desc, args) : to.add(name, ret, desc, args);
		if (varArgs)
			cmd.setVarArgs();
		cmd.setFunc((ctx, objs) ->
		{
			Object out = null;
			try
			{
				if (isInst)
					out = m.invoke(objs[0], ArrayUtils.remove(objs, objs[0]));
				else
					out = m.invoke(null, objs);
			}
			catch (IllegalAccessException | InvocationTargetException e)
			{
				ctx.parseExcept("Exception occurred during invocation of auto-exposed method '" + displayName + "'", "Exception follows in log.");
				e.printStackTrace();
			}
			if (isVoid)
				return ctx.prev();
			if (out == null)
				return NULL;
			return retTypeArg.castAndUnparse(out);
		});
		
		return cmd;
	}
	private static String getSortingName(Method m)
	{
		return m.getDeclaringClass().getCanonicalName() + "." + m.getName() +
				StringUtils.toString(m.getParameterTypes(), (cl) -> cl.getCanonicalName(), "(", ", ", ")"
				+ " " + m.getReturnType().getCanonicalName());
	}
	
	public static Command[] exposeAll(Method[] methods, ScriptObject<?> to, Predicate<Method> iff)
	{
		Arrays.sort(methods, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(getSortingName(a), getSortingName(b)));
		
		ArrayList<Command> out = new ArrayList<>();
		for (Method m : methods)
			if (iff.test(m))
				out.add(expose(m, to));
		return out.toArray(new Command[out.size()]);
	}
	
	public static <SO> ScriptObject<SO> add(String type, String desc, Class<SO> cl, CmdArg<?>... constArgs)
	{
		ScriptObject<SO> so = new ScriptObject<SO>(type, desc, cl, constArgs);
		if (OBJECTS.put(type, so) != null)
			throw new IllegalArgumentException("Cannot register two ScriptObject types of the same name: " + type);
		
		newObjOf(so);
		
		return so;
	}
	
	public static <SO> ScriptObject<SO> add(ScriptObject<SO> so)
	{
		if (OBJECTS.put(so.getTypeName(), so) != null)
			throw new IllegalArgumentException("Cannot register two ScriptObject types of the same name: " + so.getTypeName());
		return so;
	}
	
	public static Library add(String name, Runnable load)
	{
		Library lib = new Library(name, load);
		LIBS.put(name, lib);
		return lib;
	}
	
	public static boolean canAutoExpose(Method m)
	{
		boolean good = true;
		Class<?> ret = m.getReturnType();
		good = CmdArg.getArgFor(ret) != null;
		Class<?>[] params = m.getParameterTypes();
		for (int i = 0; good && i < params.length; i++)
			good = CmdArg.getArgFor(params[i]) != null;
		return good;
	}
	
	public static ScriptObject<?>[] getAllTypes()
	{
		return OBJECTS.values().toArray(new ScriptObject<?>[OBJECTS.size()]);
	}
	
	public static ScriptObject<?> getType(String name)
	{
		return OBJECTS.get(name);
	}
	
	public static Command[] getAllCommands()
	{
		return CMDS.values().toArray(new Command[CMDS.size()]);
	}
	
	public static String[] getAllCmds()
	{
		return CMDS.keySet().toArray(new String[CMDS.size()]);
	}
	
	public static Library getLibrary(String name)
	{
		return LIBS.get(name);
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
	public static final Command VAR_ARRAY = add("var_array", VOID, "Sets the variable to an array of the given length, filled with the given value.", CmdArg.VAR_INT_SET).setFunc((ctx, objs) ->
	{
		IntVarSet[] sets = (IntVarSet[]) objs[0];
		for (IntVarSet set : sets)
		{
			String[] s = new String[set.i];
			Arrays.setAll(s, (i) -> set.set.unraw);
			ctx.putVar(set.var, toArrayString(s));
		}
		
		return ctx.prev();
	}).setVarArgs();
	public static final Command VAR_ARRAY_FILL = add("var_array_fill", VOID, "Sets the variable to an array of the given length, filled with the return value of excecution of the given label for each index.", CmdArg.VAR_INT_TOK).setFunc((ctx, objs) ->
	{
		VarIntTok[] sets = (VarIntTok[]) objs[0];
		for (VarIntTok set : sets)
		{
			int count = set.i;
			String label = set.tok;
			Label lab = ctx.getLabel(label);
			if (lab == null)
				ctx.parseExcept("Invalid label specification", label, "No label found.");
			String[] elements = new String[count];
			for (int i = 0; i < count; i++)
			{
				ctx.putVar(INDEX, "" + i);
				ctx.subRunFrom(lab);
				elements[i] = ctx.prev();
			}
			ctx.putVar(set.var, toArrayString(elements));
		}
		return ctx.prev();
	}).setVarArgs();
	public static final Command VAR_TO_INDEX = add("var_to_index", VOID, "Sets the value at an index in specified array.", CmdArg.VAR_INT_SET).setFunc((ctx, objs) ->
	{
		IntVarSet[] sets = (IntVarSet[]) objs[0];
		
		for (int i = 0; i < sets.length; i++)
		{
			String arr = sets[i].var;
			if (!ctx.isArray(arr))
				ctx.parseExcept("Attempt to set index value of non-array.", arr);
			
			String[] elements = arrayElementsOf(ctx.getVar(arr));
			CmdString val = sets[i].set;
			int index = sets[i].i;
			if (index < 0)
				elements = AUtils.extendPre(elements, -index, (ind) -> ind == 0 ? val.unraw : NULL);
			else if (index >= elements.length)
				elements = AUtils.extendPost(elements, 1 + index - elements.length, (ind) -> ind == index ? val.unraw : NULL);
			else
				elements[sets[i].i] = val.unraw;
			
			ctx.putVar(arr, toArrayString(elements));
		}
		
		return ctx.prev();
	}).setVarArgs();
	public static final Command MAKE_GLOBAL = add("make_global", VOID, "Makes each variable token global in scope.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String[] vars = (String[]) objs[0];
		for (String var : vars)
			ctx.scope.makeGlobal(var);
		
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
		
		return "" + OBJECTS.get(type).isObject((String) objs[1]);
	});
	public static final Command DESTROY = add("dest_obj", BOOL, "Destroys the Object of the given Type. Returns true if object existed.", CmdArg.TYPE, CmdArg.SCRIPT_OBJECT).setFunc((ctx, objs) ->
	{
		String type = (String) objs[0];
		if (!OBJECTS.containsKey(type))
			ctx.parseExcept("Unrecognized type", type);
		
		return "" + (OBJECTS.get(type).objs.remove((String) objs[1]) != null);
	});
	public static final Command OBJ_STRING = add("obj_string", STRING, "Returns the string representation of the object of the given type.", CmdArg.OBJECT).setFunc((ctx, objs) ->
	{
		return objs[0].toString();
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
	public static final Command IMPORT = add("import", VOID, "Loads the given library by name.", CmdArg.LIBRARY).setFunc((ctx, objs) ->
	{
		for (Library lib : (Library[]) objs[0])
			lib.load();
		return ctx.prev();
	}).setVarArgs();
	public static final Command IS_LOADED = add("is_loaded", BOOL, "Returns true if all given libraries are loaded.", CmdArg.LIBRARY).setFunc((ctx, objs) ->
	{
		boolean bool = true;
		for (Object obj : objs)
			if (!(bool = ((Library) obj).isLoaded()))
				break;
		return "" + bool;
	});
	public static final Command PRINT = add("print", STRING, "Prints and returns the supplied value.", CmdArg.STRING).setFunc((ctx, objs) ->
	{
		CmdString[] strs = (CmdString[]) objs[0];
		String out = "";
		for (CmdString str : strs)
			out += str.unraw;
		ctx.printCallback.accept(out);
		return out;
	}).setVarArgs();
	public static final Command PRINT_VARS = add("print_all_vars", VOID, "Prints all variables and their values.").setFunc((ctx, objs) ->
	{
		ctx.printCallback.accept("----------------");
		AtomicInteger lastLvl = new AtomicInteger(-1);
		ctx.scope.forEach((lvl, label, var, val) ->
		{
			String spacer = StringUtils.mult("  ", lvl);
			if (lastLvl.get() != lvl)
			{
				ctx.printCallback.accept(StringUtils.mult("~~", lvl + 2) + label.name + ":");
				lastLvl.set(lvl);
			}
			ctx.printCallback.accept(spacer + " " + var + "=" + val);
		});
		ctx.printCallback.accept("----------------");
		return ctx.prev();
	});
	public static final Command PRINT_COMMANDS = add("print_all_cmds", VOID, "Prints all commands and their info.").setFunc((ctx, objs) ->
	{
		ctx.printCallback.accept("--- Commands ---");
		CMDS.values().forEach((cmd) -> ctx.printCallback.accept("   " + cmd.getInfoString()));
		ctx.printCallback.accept("--- Types ---");
		OBJECTS.values().forEach((obj) -> ctx.printCallback.accept("   " + obj.getInfoString()));
		ctx.printCallback.accept("--- Libraries ---");
		LIBS.values().forEach((lib) -> ctx.printCallback.accept("   " + lib.getInfoString()));
		return ctx.prev();
	});
	public static final Command PRINT_DEBUG = add("print_debug", VOID, "Sets whether or not debug information should be printed for every line execution.", CmdArg.BOOLEAN).setFunc((ctx, objs) ->
	{
		Boolean bool = (Boolean) objs[0];
		
		ctx.printDebug(bool);
		
		return ctx.prev();
	});
	public static final Command PRINT_OBJS = add("print_all_objs", VOID, "Prints all the objects of all the types.").setFunc((ctx, objs) ->
	{
		OBJECTS.forEach((type, so) ->
		{
			ctx.printCallback.accept(type + ":");
			so.objs.forEach((k, obj) ->
			{
				ctx.printCallback.accept("    " + k + "=" + obj.toString());
			});
		});
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
	public static final Command ARR_MERGE = add("array_merge", TOKEN_ARR, "Merges arrays one onto the other in the order provided.", CmdArg.arrayOf(CmdArg.STRING)).setFunc((ctx, objs) ->
	{
		String out = "" + ARR_S;
		CmdString[][] arrays = (CmdString[][]) objs[0];
		for (int i = 0; i < arrays.length; i++)
			for (int j = 0; j < arrays[i].length; j++)
				out += arrays[i][j].raw + (i == arrays.length - 1 && j == arrays[i].length - 1 ? "" : ARR_SEP + " ");
		
		return out + ARR_E;
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
	public static final Command FOR = add("for", VOID, "Excecutes the given token label the given number of times.", CmdArg.INT, CmdArg.TOKEN, CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		int count = (int) objs[0];
		String label = (String) objs[1];
		Label lab = ctx.getLabel(label);
		if (lab == null)
			ctx.parseExcept("Invalid label specification", label, "No label found.");
		for (int i = 0; i < count; i++)
		{
			ctx.putVar(INDEX, "" + i);
			ctx.subRunFrom(lab, (VarSet[]) objs[2]);
		}
		return ctx.prev();
	}).setVarArgs();
	public static final Command WHILE = add("while", VOID, "While the boolean token (0) is true, excecutes the token label (1). Sets variables as provided before each run (2...).", CmdArg.TOKEN, CmdArg.TOKEN, CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		String[] bool = new String[] { (String) objs[0] };
		int wL = ctx.parseLine;
		Label lab = ctx.getLabel((String) objs[1]);
		if (lab == null)
			ctx.parseExcept("Invalid label specification", (String) objs[1], "No label found.");
		int ind = 0;
		while (ctx.valParse(CmdArg.BOOLEAN, bool, ctx.lines[wL]))
		{
			ctx.putVar(INDEX, "" + ind);
			ind++;
			ctx.subRunFrom(lab, (VarSet[]) objs[2]);
		}
		return ctx.prev();
	}).rawArg(0).setVarArgs();
	public static final Command GOTO = add("goto", VOID, "Excecutes the given token label in a new stack entry. Sets variables to values provided.", CmdArg.TOKEN, CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		String label = (String) objs[0];
		if (label.equals(NULL))
			return ctx.prev();
		
		Label lab = ctx.getLabel(label);
		if (lab == null)
			ctx.parseExcept("Invalid label specification", label, "No label found.");
		
		ctx.runFrom(lab, (VarSet[]) objs[1]);
		// ctx.pushStack(line, true);
		
		return ctx.prev();
	}).setVarArgs();
	public static final Command SKIPTO = add("skipto", VOID, "Skips excecution to the given token label, without modifying the stack or scope.", CmdArg.TOKEN, CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		String label = (String) objs[0];
		if (label.equals(NULL))
			return ctx.prev();
		
		Label lab = ctx.getLabel(label);
		if (lab == null)
			ctx.parseExcept("Invalid label specification", label, "No label found.");
		
		for (int i = 0; i < ((VarSet[]) objs[1]).length; i++)
		{
			VarSet var = ((VarSet[]) objs[1])[i];
			ctx.putVar(var.var, var.set.raw);
		}
		
		ctx.parseLine = lab.line;
		
		return ctx.prev();
	}).setVarArgs();
	public static final Command RETURN = add("return", VOID, "Marks the end of a label or code section. If present, will set PREV to argument, or array of arguments if more than one is provided.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String[] rets = (String[]) objs[0];
		SNode last = ctx.popStack();
		if (rets.length == 0)
			return last.get(PREVIOUS);
		else if (rets.length == 1)
			return rets[0];
		else
		{
			String out = "" + ARR_S;
			for (int i = 0; i < rets.length; i++)
				out += rets[i] + (i == rets.length - 1 ? ARR_E : " " + ARR_SEP);
			return out;
		}
	}).setVarArgs();
	public static final Command RUN_SCRIPT = add("run_script", Script.VOID, "Runs the given script. Booleans determine whether variables in this script will be given to other before being run, and whether variables in other will be pulled to this script once finished.", CmdArg.STRING, CmdArg.BOOLEAN, CmdArg.BOOLEAN, CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		CmdString name = (CmdString) objs[0];
		File scr = getScriptFile(name.unraw);
		if (scr == null)
			ctx.parseExcept("Specified script does not exist", name.unraw);
		
		Script script;
		String out = ctx.prev();
		try
		{
			script = new Script(scr);
			ctx.transferCallbacks(script);
			if ((boolean) objs[1])
				script.integrateVarsFrom(ctx);
			
			script.putVar(Script.PARENT, ctx.name);
			script.name = name.unraw;
			script.run((VarSet[]) objs[3]);
			
			if ((boolean) objs[2])
				ctx.integrateVarsFrom(script);
			
			out = script.prev();
		}
		catch (FileNotFoundException | AWTException e)
		{
			ctx.getExceptionCallback().accept(e);
		}
		return out;
	}).setVarArgs();
	public static final Command RUNSCR = overload("runscr", RUN_SCRIPT, "Push and pull are assumed false.", (objs) ->
	{
		return new Object[] { objs[0], false, false, objs[1] };
	}, CmdArg.STRING, CmdArg.VAR_SET).setVarArgs();
	public static final Command RUN_SCRIPT_LABEL = add("run_script_label", Script.VOID, "Runs the given script from the given label. Booleans are the same as those of 'run_script'.", CmdArg.STRING, CmdArg.STRING, CmdArg.BOOLEAN, CmdArg.BOOLEAN, CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		CmdString name = (CmdString) objs[0];
		File scr = getScriptFile(name.unraw);
		if (scr == null)
			ctx.parseExcept("Specified script does not exist", name.unraw);
		
		Script script;
		String out = ctx.prev();
		try
		{
			script = new Script(scr);
			ctx.transferCallbacks(script);
			if ((boolean) objs[2])
				script.integrateVarsFrom(ctx);
			
			CmdString label = (CmdString) objs[1];
			Label lab = script.getLabel(label.unraw);
			if (lab != null)
			{
				script.putVar(Script.PARENT, ctx.name);
				script.name = name.unraw;
				script.subRunFrom(lab, (VarSet[]) objs[4]);
				if ((boolean) objs[3])
					ctx.integrateVarsFrom(script);
				
				out = script.prev();
			}
			else
				ctx.parseExcept("Specified label does not exist", label.unraw, "Script: " + name.unraw);
		}
		catch (FileNotFoundException | AWTException e)
		{
			ctx.getExceptionCallback().accept(e);
		}
		return out;
	}).setVarArgs();
	public static final Command RUNLAB = overload("runlab", RUN_SCRIPT_LABEL, "Push and pull are assumed false.", (objs) ->
	{
		return new Object[] { objs[0], objs[1], false, false, objs[2] };
	}, CmdArg.STRING, CmdArg.STRING, CmdArg.VAR_SET).setVarArgs();
	public static final Command EXIT = add("exit", VOID, "Exits the script runtime.").setFunc((ctx, objs) ->
	{
		ctx.forceKill.set(true);
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
	
	public static final Command GET_MOUSE_POS = add("get_mouse_pos", arrayReturnDispl(INT), "Gets and returns the position of the mouse pointer.").setFunc((ctx, objs) ->
	{
		Point p = MouseInfo.getPointerInfo().getLocation();
		return toArrayString(new String[] { "" + p.x, "" + p.y });
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
	
	public static final Command AUTO_DELAY = add("set_robot_delay", INT, "Sets the automatic delay after robot operations (default 300 ms). Returns the old delay.", CmdArg.INT).setFunc((ctx, objs) ->
	{
		int a = ctx.rob.getAutoDelay();
		ctx.rob.setAutoDelay((int) objs[0]);
		return "" + a;
	});
	
	public static final Command GET_AUTO_DELAY = add("get_robot_delay", INT, "Gets the automatic delay after robot operations.").setFunc((ctx, objs) ->
	{
		return "" + ctx.rob.getAutoDelay();
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
		}).setVarArgs().rawArg(0);
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
	@FunctionalInterface
	public static interface ArgTransform
	{
		public Object[] transform(Object[] args);
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
		return scriptObj.add("new", tn, "Constructs a new " + tn + ": " + scriptObj.getDescription(), args).setFunc((ctx, objs) ->
		{
			String name = (String) objs[0];
			Object[] constObjs = new Object[objs.length - 1];
			for (int i = 1; i < objs.length; i++)
				constObjs[i - 1] = objs[i];
			
			scriptObj.construct(ctx, name, constObjs);
			
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
	public static boolean isType(String token)
	{
		return OBJECTS.containsKey(token);
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
			boolean inWord = parseChar != ',' && parseChar != ARR_SEP;
			boolean buildingElement = inQuote || inArray || inWord;
			
			if (buildingElement)
				ele += parseChar;
			else if (!ele.isEmpty())
			{
				elements.add(ele.trim());
				ele = "";
			}
			if (i == str.length() - 1 && !ele.isEmpty())
				elements.add(ele.trim());
		}
		
		return elements.toArray(new String[elements.size()]);
	}
	public static String toArrayString(String[] elements)
	{
		String out = "" + ARR_S;
		for (int i = 0; i < elements.length; i++)
			out += elements[i] + (i == elements.length - 1 ? "" : ARR_SEP + " ");
		return out + ARR_E;
	}
	
	public static String tokenize(String... objs)
	{
		return StringUtils.toString(objs, "", " ", "");
	}
	
	public static String tokenize(double... nums)
	{
		return StringUtils.toString(nums, "", " ", "");
	}
	
	public static String arrayReturnDispl(String of)
	{
		return ARR_S + of + ARR_E;
	}
	
	///////////
	
	private String arrayReplace(Command cmd, int argInd, int tokInd, String array)
	{
		String[] elements = arrayElementsOf(array);
		String replaced = "" + ARR_S;
		
		for (int i = 0; i < elements.length; i++)
			replaced += varParse(cmd, argInd, tokInd, elements[i]) + (i == elements.length - 1 ? "" : ARR_SEP + " ");
		
		return replaced + ARR_E;
	}
	
	public <T> T valParse(CmdArg<T> arg, String[] tokens, String line)
	{
		int count = arg.tokenCount();
		if (tokens.length != count)
			parseExcept("Invalid token count", line, "Argument requires " + count + " tokens, but " + tokens.length + " have been provided. Tokens are separated by spaces.");
	
		String trimmed = "";
		for (int tokInArg = 0; tokInArg < count; tokInArg++)
			trimmed += varParse(null, 0, tokInArg, tokens[tokInArg]) + " ";
		
		trimmed = trimmed.trim();
		T obj = arg.parse(trimmed);
		
		if (obj == null)
			parseExcept("Invalid token resolution", trimmed, "Expected type: " + arg.type);
		
		return obj;
	}
	
	public String varParse(Command cmd, int argInd, int tokInd, String token)
	{
		String trm = varTrim(token);
		
		boolean array = isArray(token);
		if (!array && CmdArg.DOUBLE.parse(trm) != null)
			return trm;
		
		boolean unraw = token.charAt(0) == UNRAW || token.charAt(0) == VAR_ARG_ARRAY;
		boolean raw = token.charAt(0) == RAW;
		
		if (raw || (!unraw && (cmd != null && cmd.rawToken(argInd, tokInd)))) // If raw, return what's written.
			return trm;
		
		if (array)
		{
			String got = getVar(trm);
			String var = arrPreFrom(trm) + got + arrPostFrom(trm);
			return got == null ? arrayReplace(cmd, argInd, tokInd, trm) : var;
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
		if (var != null && !raw) // If not raw, it is a request to use the contents of the variable.
			return var;
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
			if (token.charAt(i) == ARR_E)
				post += ARR_E;
			else
				return post;
		}
		return post;
	}
	private static String varTrim(String str)
	{
		return replaceIfFirst(replaceIfFirst(replaceIfFirst(str.trim(), UNRAW, ""), RAW, ""), VAR_ARG_STR, "");
//		return str.trim().replaceFirst(quote(UNRAW), "").replaceFirst(quote(RAW), "").replaceFirst(quote(VAR_ARG_STR), "");
	}
	private static String getTrim(String str)
	{
		String trm = varTrim(str);
		if (!(trm.startsWith("" + ARR_S) && trm.endsWith("" + ARR_E)))
				trm = trm.replaceAll(quote(ARR_S), "").replaceAll(quote(ARR_E), "");
		return trm;
	}
	private static String quote(String str)
	{
		return Pattern.quote(str);
	}
	private static String quote(char chr)
	{
		return quote("" + chr);
	}
	private static String replaceIfFirst(String str, char find, String rep)
	{
		return replaceIfFirst(str, "" + find, rep);
	}
	private static String replaceIfFirst(String str, String find, String rep)
	{
		if (str.isEmpty())
			return str;
		if (str.startsWith(find))
			return str.replaceFirst(quote(find), rep);
		return str;
	}
	
	public static File getScriptFile(String unraw)
	{
		File scr = new File(SCRIPT_PATH + StringUtils.endWith(StringUtils.startWithout(unraw, HIDDEN_SCRIPT), SCRIPT_EXT));
		if (!scr.exists())
		{
			scr = new File(SCRIPT_PATH + StringUtils.endWith(StringUtils.startWith(unraw, HIDDEN_SCRIPT), SCRIPT_EXT));
			if (!scr.exists())
				return null;
		}
		return scr;
	}
	
	private boolean breakIf = true;
	private RunnableCommand parse(String line, CmdHead head)
	{
		String[] argStrs = argsOf(line);
		if (line.length() > 0)
		{
			Command cmd = getCommand(head);
			if (cmd != null)
			{
				if (cmd.isDisabled())
					parseExcept("Disabled command", cmd.name);
				boolean inlIfVal = head.isInlineIf ? valParse(CmdArg.BOOLEAN, new String[] { head.inlineIf }, line) : true;
				breakIf = breakIf && head.isInlineElse;
				if (!breakIf && inlIfVal)
				{
					breakIf = true;
					CmdArg<?>[] args = cmd.args;
					boolean varArgs = cmd.isVarArgs();
					boolean varArgArray = argStrs.length > 0 && argStrs[argStrs.length - 1].startsWith(VAR_ARG_STR);
					if (varArgArray && !varArgs)
						parseExcept("Var-Arg array cannot be specified for non-var-arg commands", line, argStrs[argStrs.length]);
					if (argStrs.length != args.length && !(varArgs && argStrs.length >= args.length - 1 && !varArgArray))
						parseExcept("Invalid argument count", line, head.name + " requires " + args.length + " args, but " + argStrs.length + " have been provided. Args are separated by commas.");
					
					Object[] objs = new Object[args.length];
					if (varArgs)
						objs[objs.length - 1] = Array.newInstance(args[args.length - 1].cls, argStrs.length - args.length + 1);
					
					String input = "";
					for (int argInd = 0; argInd < argStrs.length; argInd++)
					{
						boolean atVA = varArgs && argInd >= args.length - 1;
						boolean firstVarArg = varArgs && argInd == args.length - 1;
						
						String[] tokens = tokensOf(argStrs[argInd]);
						
						if (varArgArray && !firstVarArg)
							parseExcept("Invalid argument count", line, "If var-arg arguments are specified as an array, the array must be the last argument");
						
						CmdArg<?> arg = args[Math.min(argInd, args.length - 1)];
						//HashSet<CmdArg<?>> bin = ARGS.getBin(arg.cls);
						//Iterator<CmdArg<?>> binIt = bin == null ? null : bin.iterator();
						
						Object obj = null;
						
					/*	do
						{
							
						} while (obj == null && binIt != null && binIt.hasNext());*/
						String trimmed = "";
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
								parseExcept("Invalid token resolution", trimmed, "Expected type: " + arg.type);
							
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
			}
			else
				parseExcept("Unknown command", head.name);
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
			if (line.startsWith(LABEL) || line.startsWith(SCOPED_LABEL))
			{
				putLabel(firstToken(line), num);
			}
			else if (line.startsWith(END_SCRIPT))
				break;
			else if (line.trim().endsWith(END_SCRIPT))
			{
				str += StringUtils.endWithout(line, END_SCRIPT);
				break;
			}
			
			str += line.trim() + "\n";
			num++;
		}
		lines = str.split("\n");
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
	public void run(VarSet... varSets)
	{
		try
		{
			keyIn = new Scanner(System.in);
			runFrom(GLOBAL, varSets);
		}
		catch (CommandParseException e)
		{
			this.parseExceptionCallback.accept(e, e.getMessage());
		}
		catch (Throwable e)
		{
			e.printStackTrace();
			this.exceptionCallback.accept(e);
		}
	}
	public void subRunFrom(Label start, VarSet... varSets)
	{
		runFrom(start, false, varSets);
	}
	private void runFrom(Label start, VarSet... varSets)
	{
		runFrom(start, true, varSets);
	}
	private void runFrom(Label label, boolean returns, VarSet... varSets)
	{
		pushStack(label, returns);
		for (VarSet var : varSets)
			putVar(var.var, var.set.raw);
		if (keyIn == null)
			keyIn = new Scanner(System.in);
		
		while(parseLine < lines.length && !stack.isEmpty() && !forceKill.get())
		{
			String line = lines[parseLine];
			if (line.startsWith(COMMENT) || line.isEmpty() || line.startsWith(LABEL) || line.startsWith(SCOPED_LABEL))
			{
				parseLine++;
				continue;
			}
			else if (line.trim().equals(HELP_CHAR_STR))
			{
				PRINT_COMMANDS.func.cmd(this, (Object[]) null);
				parseLine++;
				continue;
			}
			String first = firstToken(line);
			CmdHead head = new CmdHead(first);
			if (head.printHelp)
			{
				Command command = getCommand(head);
				if (command == null)
				{
					ScriptObject<?> so = OBJECTS.get(head.name);
					if (so == null)
						parseExcept("Unrecognized command for help request", "Cannot display help text");
					else
					{
						String str = "";
						Command[] cmds = so.getMemberCommands();
						for (int i = 0; i < cmds.length; i++)
							str += "    " + cmds[i].getInfoString() + "\n";
						printCallback.accept("Type: " + so.getTypeName() + ", Desc: " + so.getDescription() + "\n  Cmds:\n" + str);
					}
				}
				else
					printCallback.accept(command.getInfoString());
			}
			else
			{
				RunnableCommand cmd = parse(line, head);
				if (cmd != null)
				{
					String out;
					putVar(PREVIOUS, out = cmd.run(this));
					for (int i = 0; i < head.storing.length; i++)
						putVar(head.storing[i], out);
					prevCallback.accept(head.name, out);
					debugger.info(head.name, cmd.getInput(), out);
					if (!popped.isEmpty() && !popped.pop().returns)
						break;
				}
			}
			parseLine++;
		}
	}
	public void goTo(String label)
	{
		Label lab = getLabel(label);
		if (lab == null)
			parseExcept("Invalid label specification", label, "No label found.");
		
		pushStack(lab, true);
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
	
	public static Command getCommand(CmdHead head)
	{
		if (!head.isMemberCmd)
			return CMDS.get(head.name);
		ScriptObject<?> parent = OBJECTS.get(head.parentPath[0]);
		for (int i = 1; i < head.parentPath.length - 1; i++)
		{
			parent = parent.getSub(head.parentPath[i]);
			if (parent == null)
				return null;
		}
		return parent.getMemberCmd(head.name);
	}
	
	public void putVar(String name, String val)
	{
//		if (name.contains("" + ARR_S) || name.contains("" + ARR_E) || name.contains("" + STRING_CHAR) || name.contains("" + ESCAPE_CHAR))
		if (ILLEGAL_VAR_MATCHER.matcher(name).find())
			parseExcept("Illegal characters in variable name", name);
		try
		{
			Double.parseDouble(name);
			parseExcept("Numerical variable name", name);
		}
		catch (NumberFormatException e)
		{}
		scope.put(name, val);
	}
	
	public void integrateVarsFrom(Script other)
	{
		scope.integrateFrom(other.scope);
	}
	
	public String getVar(String name)
	{
		String trm = getTrim(name);
		String got = scope.get(trm);
		boolean ref = got != null && got.startsWith("" + REF);
		if (ref)
			return getVar(got.substring(1));
		
		if (got != null && got.startsWith("" + ARR_S) && got.endsWith("" + ARR_E))
		{
			String[] elements = arrayElementsOf(got);
			String out = "" + ARR_S;
			for (int i = 0; i < elements.length; i++)
			{
				if (elements[i].startsWith("" + REF))
					elements[i] = getVar(elements[i].substring(1));
				out += elements[i] + (i == elements.length - 1 ? "" : ARR_SEP + " ");
			}
			out += ARR_E;
			return out;
		}
		
		return got;
	}
	public String prev()
	{
		return getVar(PREVIOUS);
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
	
	public static boolean[] prefixModsFrom(String test, String[] valid)
	{
		boolean[] out = new boolean[valid.length];
		int startsWith = -1;
		while ((startsWith = startsWithOneOf(test, valid)) != -1)
		{
			out[startsWith] = true;
			test = test.replaceFirst(quote(valid[startsWith]), "");
		}
		return out;
	}
	private static int startsWithOneOf(String test, String[] valid)
	{
		for (int i = 0; i < valid.length; i++)
			if (test.startsWith(valid[i]))
				return i;
		return -1;
	}
	
	private void putLabel(String label, int line)
	{
		Label lab = new Label(label, line);
		labels.put(lab.name, lab);
	}
	
	public Label getLabel(String label)
	{
		return labels.get(label.replaceFirst(LABEL_REG, ""));
	}
	
	private void pushStack(Label to, boolean returns)
	{
		stack.push(new StackEntry(parseLine, to, returns));
		parseLine = to.line;
		if (to.isScoped)
			scope.push(to);
	}
	
	private SNode popStack()
	{
		StackEntry ent = stack.pop();
		popped.push(ent);
		parseLine = ent.from;
		if (ent.to.isScoped)
			return scope.pop();
		return scope.getLast();
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
	
	public void setExceptionCallback(Consumer<Throwable> callback)
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
			debugger = (cmd, args, ret) -> { printCallback.accept("      --  " + cmd + "(" + args + ") -> " + ret); };
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
	
	public Consumer<Throwable> getExceptionCallback()
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
		to.keyIn = keyIn;
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
