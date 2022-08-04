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
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

import arrays.AUtils;
import commands.CmdArg.TypeArg;
import commands.CmdArg.VarCmdArg;
import commands.Command.CommandResult;
import commands.Command.RunnableCommand;
import commands.ParseTracker.BoxTracker;
import commands.ParseTracker.DelimTracker;
import commands.ParseTracker.MultiTracker;
import commands.ParseTracker.RepeatTracker;
import commands.ParseTracker.WrapTracker;
import commands.ScajlVariable.*;
import commands.Scope.SNode;
import commands.libs.Bool;
import commands.libs.BuiltInLibs;
import mod.serpentdagger.artificialartificing.utils.group.MixedPair;
import utilities.ArrayUtils;
import utilities.StringUtils;

public class Script
{
	private static final LinkedHashMap<String, Command> CMDS = new LinkedHashMap<String, Command>();
	private static final HashMap<String, ScriptObject<?>> OBJECTS = new HashMap<String, ScriptObject<?>>();
	private static final HashMap<String, Library> LIBS = new HashMap<>();
	
	public static final String COMMENT = "//";
	public static final char COMMENT_CHAR = '/';
	public static final String MULTILINE_COMMENT_START = "<<", MULTILINE_COMMENT_END = ">>";
	public static final char LINE_MERGE = '+';
	
	public static final String LABEL = "--", SCOPED_LABEL = "~~";
	public static final String LABEL_REG = LABEL + "|" + SCOPED_LABEL;
	public static final String LABEL_ACC_FOR_DOWN = "|", LABEL_ACC_TO_UP = "^";
	public static final String[] VALID_LABEL_MODS = new String[] { LABEL_ACC_FOR_DOWN, LABEL_ACC_TO_UP };
	public static final String LABEL_MODS_REG = "[" + quote(LABEL_ACC_FOR_DOWN) + quote(LABEL_ACC_TO_UP) + "]+";
	public static final Pattern LABEL_MODS_PATTERN = Pattern.compile(LABEL_MODS_REG);
	
	public static final String PREVIOUS = "PREV";
	public static final String PARENT = "PARENT";
	public static final String NULL = "null";
	public static final SVVal NULLV = ScajlVariable.NULL;
	public static final SVVal FALSE = new SVVal("false", null), TRUE = new SVVal("true", null);
	
	public static final String STORE = "->";
	public static final String INLINE_IF = "?";
	public static final String INLINE_SEP = ":";
	public static final String MEMBER_ACCESS = ".";
	public static final char ARR_S = '[', ARR_E = ']', ARR_ACCESS = '.', ARR_SEP = ';';
	public static final char TOK_S = '(', TOK_E = ')', SCOPE_S = '{', SCOPE_E = '}';
	public static final char MAP_KEY_EQ = '=';
	public static final String ARR_LEN = "len", ARR_SELF = "up";
	public static final char STRING_CHAR = '"';
	public static final char ESCAPE_CHAR = '\\';
	public static final char HELP_CHAR = '?';
	public static final char VAR_ARG_ARRAY = '#';
	public static final String END_SCRIPT = "==";
	public static final String VAR_ARG_STR = "" + VAR_ARG_ARRAY;
	public static final String HELP_CHAR_STR = "" + HELP_CHAR;
	public static final String INDEX = "INDEX";
	public static final int NO_LABEL = -2;
	public static final Label GLOBAL = new Label("GLOBAL", -1, false, true, false);
	public static final char UNRAW = '%', RAW = '$', REF = '@', RAW_CONTENTS = '&', UNPACK = '^', NO_UNPACK = '|';
	/** unraw, raw, ref, rcont, unpack, no_unpack */
	public static final String[] VALID_VAR_MODS = new String[] { "" + UNRAW, "" + RAW, "" + REF, "" + RAW_CONTENTS, "" + UNPACK, "" + NO_UNPACK };
	
	public static final String ILLEGAL_VAR_REG_EX = ".*[^\\w\\-]+.*";
	public static final Pattern ILLEGAL_VAR_MATCHER = Pattern.compile(ILLEGAL_VAR_REG_EX);
	
	public static final String LEGAL_ANON_SCOPE_REG_EX = "[\\{\\}]" + LABEL_MODS_REG.substring(0, LABEL_MODS_REG.length() - 1) + "*";
	public static final Pattern LEGAL_ANON_SCOPE_MATCHER = Pattern.compile(LEGAL_ANON_SCOPE_REG_EX);
	
	public static final String PARENTH_REG_EX = "[" + quote(TOK_S) + quote(TOK_E) + "]";
	public static final Pattern PARENTH_MATCHER = Pattern.compile(PARENTH_REG_EX);
	
	public static final String BOOL = "boolean", VOID = "void", TOKEN = "token", TOKEN_ARR = ARR_S + TOKEN + ARR_E, STRING = "String", INT = "int", DOUBLE = "double", VALUE = "Value", OBJECT = "Object";
	
	//////////////// Files
	public static final String HIDDEN_SCRIPT = "--";
	private static final String SEP = File.separator;
	private static String SCRIPT_PATH = "scajl" + SEP;
	private static String SCRIPT_EXT = ".scajl";
	////////////////
	
	public int parseLine = -1;
	protected final Scope scope = new Scope();
	private final HashMap<String, Label> labels = new HashMap<String, Label>();
	private final ArrayDeque<StackEntry> stack = new ArrayDeque<StackEntry>();
	private StackEntry popped = null;
	private Script parent = null;
	public String path;
	public String name = "BASE";
	public final String[] lines;
	public final HashMap<Integer, Label> anonScope;
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
			if (!ctx.putVarType(var, in.trim(), type, prompt))
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
	
	private static String uniqueName(String desired, Map<String, ?> from)
	{
		String actual = desired;
		int i = 2;
		while (from.containsKey(actual))
			actual = desired + i++;
		return actual;
	}
	private static String upperFirstChar(String toUpper)
	{
		return Character.toUpperCase(toUpper.charAt(0)) + toUpper.substring(1);
	}
	private static <T> CmdArg<T> getArgFor(Class<T> cl, ExpPredicate filter, ClsPredicate clFilter, boolean recursive)
	{
		CmdArg<T> out = CmdArg.getArgFor(cl);
		if (out == null && recursive)
			return expose(cl, filter, clFilter, recursive).argOf();
		return out;
	}
	private static <T> TypeArg<T> getTypeArgFor(Class<T> cl, ExpPredicate filter, ClsPredicate clFilter, boolean recursive)
	{
		TypeArg<T> out = CmdArg.getTypeArgFor(cl);
		if (out.arg == null && recursive)
		{
			expose(cl, filter, clFilter, recursive);
			return CmdArg.getTypeArgFor(cl);
		}
		return out;
	}
	
	private static Command[] expose(Member m, ScriptObject<?> to, ExpPredicate filter, ClsPredicate clFilter, boolean recursive)
	{
		if (m instanceof Executable)
			return new Command[] { expose((Executable) m, to, filter, clFilter, recursive) };
		else
			return exposeGetterSetterFor((Field) m, to, filter, clFilter, recursive);
	}
	private static Command[] exposeGetterSetterFor(Field f, ScriptObject<?> to, ExpPredicate filter, ClsPredicate clFilter, boolean recursive)
	{
		String fieldName = f.getName();
		String displayName = f.getDeclaringClass().getCanonicalName() + "." + fieldName;
		
		int mods = f.getModifiers();
		boolean makeSetter = !Modifier.isFinal(mods);
		boolean isInst = !Modifier.isStatic(mods);
		
		Command[] out = new Command[2];
		
		HashMap<String, Command> map = to == null ? CMDS : to.getMemberCommandMap();
		String gName = uniqueName("get" + upperFirstChar(fieldName), map);
		String sName = uniqueName("set" + upperFirstChar(fieldName), map);
		
		Class<?> type = f.getType();
		TypeArg<?> typeArg = getTypeArgFor(type, filter, clFilter, recursive);
		Class<?> declType = f.getDeclaringClass();
		CmdArg<?> declArg = getArgFor(declType, filter, clFilter, recursive);
		
		if (typeArg.arg == null)
			throw new IllegalStateException("Unable to automatically expose field '" + displayName + "' due to lack of registered"
					+ " CmdArg for class: " + type.getCanonicalName());
		if (isInst && declArg == null)
			throw new IllegalStateException("Unable to automatically expose instance field '" + displayName + "' due to lack of registered"
					+ " CmdArg for declaring class: " + declType.getCanonicalName());

		String typeString = typeArg.arg.type;
		
		String gDesc = "Getter for the field '" + displayName + "'";
		String sDesc = "Setter for the field '" + displayName + "'";
		
		CmdArg<?>[] sArgs = new CmdArg<?>[] { typeArg.arg };
		CmdArg<?>[] gArgs = new CmdArg<?>[] {};
		if (isInst)
		{
			sArgs = AUtils.extendPre(sArgs, 1, (i) -> declArg);
			gArgs = AUtils.extendPre(gArgs, 1, (i) -> declArg);
		}
		
		out[0] = to == null ? add(gName, typeString, gDesc, gArgs) : to.add(gName, typeString, gDesc, gArgs); // Getter
		out[0].setFunc((ctx, objs) ->
		{
			Object ret = null;
			
			try
			{
				ret = f.get(isInst ? objs[0] : null);
			}
			catch (IllegalArgumentException | IllegalAccessException err)
			{
				ctx.parseExcept("Exception occurred during invocation of auto-exposed getter for field '" + displayName + "'", "Exception follows in log.");
				err.printStackTrace();
			}

			if (ret == null)
				return NULLV;
			return typeArg.castAndUnparse(ret);
		});
		if (makeSetter)
		{
			out[1] = to == null ? add(sName, VOID, sDesc, sArgs) : to.add(sName, VOID, sDesc, sArgs);
			out[1].setFunc((ctx, objs) ->
			{
				try
				{
					if (isInst)
						f.set(objs[0], objs[1]);
					else
						f.set(null, objs[0]);
				}
				catch (IllegalArgumentException | IllegalAccessException err)
				{
					ctx.parseExcept("Exception occurred during invocation of auto-exposed setter for field '" + displayName + "'", "Exception follows in log.");
					err.printStackTrace();
				}
				return ctx.prev();
			});
		}
		
		return out;
	}
	
	public static Command expose(Executable e) { return expose(e, null, SAFE_EXPOSE_FILTER, SAFE_CLASS_EXPOSE_FILTER, false); }
	private static Command expose(Executable e, ScriptObject<?> to, ExpPredicate filter, ClsPredicate clFilter, boolean recursive)
	{
		boolean isM = e instanceof Method;
		final Method m = isM ? (Method) e : null;
		final Constructor<?> c = isM ? null : (Constructor<?>) e;
		
		HashMap<String, Command> map = to == null ? CMDS : to.getMemberCommandMap();
		String name = uniqueName(isM ? e.getName() : "new", map);

		Class<?>[] types = e.getParameterTypes();
		for (int i = 0; i < types.length; i++)
			if (types[i].isPrimitive())
				types[i] = CmdArg.wrap(types[i]);
		boolean isInst = isM && !Modifier.isStatic(e.getModifiers());
		if (isInst)
			types = AUtils.extendPre(types, 1, (i) -> e.getDeclaringClass());
		boolean varArgs = e.isVarArgs();
		if (varArgs)
			types[types.length - 1] = types[types.length - 1].getComponentType();
		
		String params = StringUtils.toString(types, (cl) -> cl.getSimpleName(), "(", ", ", "");
		if (varArgs)
			params += "...";
		params += ")";
		String displayName = e.getDeclaringClass().getCanonicalName() + "." + e.getName() + params;
		
		CmdArg<?>[] args = new CmdArg<?>[types.length];
		for (int i = 0; i < types.length; i++)
		{
			args[i] = getArgFor(types[i], filter, clFilter, recursive);
			if (args[i] == null)
				throw new IllegalStateException("Unable to automatically expose method '" + displayName + "' due to lack of registered"
						+ " CmdArg for class: " + types[i].getCanonicalName());
		}
		
		Class<?> retType = isM ? m.getReturnType() : c.getDeclaringClass();
		if (retType.isPrimitive())
			retType = CmdArg.wrap(retType);
		TypeArg<?> retTypeArg = getTypeArgFor(retType, filter, clFilter, recursive);
		boolean isVoid = retType.equals(Void.TYPE);
		
		if (retTypeArg.arg == null)
			throw new IllegalStateException("Unable to automatically expose method '" + displayName + "' due to lack of registered"
					+ " CmdArg for return type of class: " + retType.getCanonicalName());
		
		String ret = retTypeArg.arg.type;
		
		String desc = "Direct exposition of method: " + displayName;
		
		Command cmd = to == null ? add(name, ret, desc, args) : to.add(name, ret, desc, args);
		if (varArgs)
			cmd.setVarArgs();
		cmd.setFunc((ctx, objs) ->
		{
			Object out = null;
			try
			{
				if (isM)
					if (isInst)
					{
						Object[] objs2 = new Object[objs.length - 1];
						for (int i = 1; i < objs.length; i++)
							objs2[i - 1] = objs[i];
						out = m.invoke(objs[0], objs2);
					}
					else
						out = m.invoke(null, objs);
				else
					out = c.newInstance(objs);
			}
			catch (IllegalAccessException | InvocationTargetException | InstantiationException | IllegalArgumentException err)
			{
				err.printStackTrace();
				ctx.parseExcept("Exception occurred during invocation of auto-exposed executable '" + displayName + "'", "Exception follows in log.");
			}
			if (isVoid)
				return ctx.prev();
			if (out == null)
				return NULLV;
			return retTypeArg.castAndUnparse(out);
		});
		
		return cmd;
	}
	private static String getSortingName(Member m)
	{
		if (m instanceof Executable)
		{
			Executable e = (Executable) m;
			return e.getDeclaringClass().getCanonicalName() + "." + e.getName() +
					StringUtils.toString(e.getParameterTypes(), (cl) -> cl.getCanonicalName(), "(", ", ", ")"
					+ " " + getReturnType(e).getCanonicalName());
		}
		else
		{
			Field f = (Field) m;
			return f.getDeclaringClass().getCanonicalName() + "." + f.getName();
		}
	}
	
	public static final ExpPredicate SAFE_EXPOSE_FILTER = (ex, rec) ->
	{
		int mods = ex.getModifiers();
		return Modifier.isPublic(mods)
				&& !ex.isSynthetic()
				&& Script.canAutoExpose(ex, rec);
	};
	public static final ClsPredicate SAFE_CLASS_EXPOSE_FILTER = (cl, rec) ->
	{
		int mods = cl.getModifiers();
		return Modifier.isPublic(mods)
				&& !cl.isAnonymousClass()
				&& !cl.isHidden()
				&& !cl.isLocalClass()
				&& cl.getSuperclass() != null
				&& cl != Class.class;
	};
	public static Command[] exposeAll(Member[] members, ScriptObject<?> to, ExpPredicate iff)
	{
		return exposeAll(members, to, iff, SAFE_CLASS_EXPOSE_FILTER, false);
	}
	public static Command[] exposeAll(Member[] members, ScriptObject<?> to, ExpPredicate iff, ClsPredicate clIf, boolean recursive)
	{
		Arrays.sort(members, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(getSortingName(a), getSortingName(b)));
		ArrayList<Command> out = new ArrayList<>();
		for (Member m : members)
			if (iff.test(m, recursive))
				out.addAll(Arrays.asList(expose(m, to, iff, clIf, recursive)));
		return out.toArray(new Command[out.size()]);
	}
	public static ScriptObject<?>[] exposeAll(Class<?>[] classes, ClsPredicate iff, ExpPredicate memberIf, boolean recursive)
	{
		Arrays.sort(classes, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getCanonicalName(), b.getCanonicalName()));
		ArrayList<ScriptObject<?>> out = new ArrayList<>();
		for (Class<?> cl : classes)
			if (iff.test(cl, recursive))
				out.add(expose(cl, memberIf, iff, recursive));
		return out.toArray(new ScriptObject<?>[out.size()]);
	}
	public static <T> ScriptObject<T> expose(Class<T> cl, boolean recursive)
	{
		return expose(cl, SAFE_EXPOSE_FILTER, SAFE_CLASS_EXPOSE_FILTER, recursive);
	}
	@SuppressWarnings("unchecked")
	public static <T> ScriptObject<T> expose(Class<T> cl, ExpPredicate memberIf, ClsPredicate classIf, boolean recursive)
	{
		String desired = cl.getSimpleName();
		String name = desired;
		int i = 2;
		while (OBJECTS.containsKey(name))
		{
			ScriptObject<?> test = OBJECTS.get(name);
			if (test.argOf().cls.equals(cl))
				return (ScriptObject<T>) test;
			name = desired + i++;
		}
		
		String desc = "Direct exposition of class: " + cl.getCanonicalName();
		
		ScriptObject<T> so = new ScriptObject<>(name, desc, cl);
		Script.add(so);
		so.argOf().reg();
		if (recursive)
		{
			ScriptObject<?> sub = so;
			Class<?> subCl = cl;
			exposeAll(subCl.getClasses(), classIf, memberIf, recursive);
			exposeAll(subCl.getInterfaces(), classIf, memberIf, recursive);
			Class<?> sup = cl.getSuperclass();
			if (sup != null && (sup == Object.class || classIf.test(sup, recursive)))
			{
				MixedPair<ScriptObject<Object>, ScriptObject<Object>> supSub = ScriptObject.castHirearchy(expose(sup, memberIf, classIf, recursive), sub);
				ScriptObject.makeSub(supSub.b(), supSub.a());
			}
		}
		
		Script.exposeAll(cl.getConstructors(), so, memberIf, classIf, recursive);
		Script.exposeAll(cl.getMethods(), so, memberIf, classIf, recursive);
		Script.exposeAll(cl.getFields(), so, memberIf, classIf, recursive);

		return so;
	}
	
	public static <SO> ScriptObject<SO> add(String type, String desc, Class<SO> cl)
	{
		ScriptObject<SO> so = new ScriptObject<SO>(type, desc, cl);
		if (OBJECTS.put(type, so) != null)
			throw new IllegalArgumentException("Cannot register two ScriptObject types of the same name: " + type);
		
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
	
	public static boolean canAutoExpose(Member m, boolean recursive)
	{
		boolean good = true;
		if (m instanceof Executable)
		{
			Executable e = (Executable) m;
			Class<?> ret = getReturnType(e);
			good = CmdArg.getArgFor(ret) != null;
			Class<?>[] params = e.getParameterTypes();
			for (int i = 0; good && i < params.length; i++)
				good = CmdArg.getArgFor(params[i]) != null || (recursive && params[i].getConstructors().length > 0);
		}
		else
		{
			Field f = (Field) m;
			Class<?> ret = f.getType();
			good = CmdArg.getArgFor(ret) != null || (recursive && ret.getConstructors().length > 0);
		}
		return good;
	}
	private static Class<?> getReturnType(Executable e)
	{
		return (e instanceof Method) ? ((Method) e).getReturnType() : ((Constructor<?>) e).getDeclaringClass();
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
			ctx.putVar(var.var, var.set);
		return ctx.prev();
	}).setVarArgs();
	public static final Command VAR_IF = add("var_if", VOID, "If the boolean is true, sets the variable to the value.", CmdArg.BOOL_VAR_SET).setFunc((ctx, objs) ->
	{
		BoolVarSet[] vars = (BoolVarSet[]) objs[0];
		for (BoolVarSet var : vars)
			if (var.bool)
				ctx.putVar(var.var, var.set);
		return ctx.prev();
	}).setVarArgs();
	public static final Command VAR_IF_NOT = add("var_if_not", VOID, "If the boolean is false, sets the variable to the value.", CmdArg.BOOL_VAR_SET).setFunc((ctx, objs) ->
	{
		BoolVarSet[] vars = (BoolVarSet[]) objs[0];
		for (BoolVarSet var : vars)
			if (!var.bool)
				ctx.putVar(var.var, var.set);
		return ctx.prev();
	}).setVarArgs();
	public static final Command VAR_IF_NOT_VAR = add("var_if_not_var", VOID, "Sets a variable to a value, if the variable does not already exist.", CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		VarSet[] vars = (VarSet[]) objs[0];
		for (VarSet var : vars)
			if (!ScajlVariable.isVar(var.var, ctx))
				ctx.putVar(var.var, var.set);
		return ctx.prev();
	}).setVarArgs();
	public static final Command VAR_ARRAY = add("var_array", VOID, "Sets the variable to an array of the given length, filled with the given value.", CmdArg.VAR_INT_SET).setFunc((ctx, objs) ->
	{
		IntVarSet[] sets = (IntVarSet[]) objs[0];
		for (IntVarSet set : sets)
		{
			ScajlVariable[] arr = new ScajlVariable[set.i];
			Arrays.setAll(arr, (i) -> set.set);
			
			ctx.putVar(set.var, new SVArray(arr, null));
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
			ScajlVariable[] elements = new ScajlVariable[count];
			for (int i = 0; i < count; i++)
			{
				ctx.putVar(INDEX, numOf(i));
				ctx.runFrom(lab);
				elements[i] = ctx.getVar(PREVIOUS, false, null);
			}
			ctx.putVar(INDEX, numOf(count));
			ctx.putVar(set.var, new SVArray(elements, null));
		}
		return ctx.prev();
	}).setVarArgs();
	public static final Command MAKE_GLOBAL = add("make_global", VOID, "Makes each variable token global in scope.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String[] vars = (String[]) objs[0];
		for (String var : vars)
			ctx.scope.makeGlobal(var);
		
		return ctx.prev();
	}).rawArg(0).setVarArgs();
	public static final Command IS_VAR = add("is_var", BOOL, "Checks whether or not the token is a variable.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String[] vars = (String[]) objs[0];
		for (String var : vars)
			if (!ScajlVariable.isVar(var, ctx))
				return FALSE;
		return TRUE;
	}).rawArg(0).setVarArgs();
	public static final Command IS_NUMBER = add("is_number", BOOL, "Checks whether or not the token is a number.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String[] vars = (String[]) objs[0];
		for (String var : vars)
		{
			ScajlVariable val = ctx.getVar(var, false, null);
			if (val == null || CmdArg.DOUBLE.parse(val.val(ctx)) == null)
				return FALSE;
		}
		return TRUE;
	}).rawArg(0).setVarArgs();
	public static final Command IS_ARRAY = add("is_array", BOOL, "Checks whether or not the token is an array.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String[] vars = (String[]) objs[0];
		for (String var : vars)
			if (!(ScajlVariable.getVar(var, false, ctx) instanceof SVArray))
				return FALSE;
		return TRUE;
	}).rawArg(0).setVarArgs();
	public static final Command IS_TYPE = add("is_type", BOOL, "Checks whether or not the token represents a recognized type name.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		for (String var : (String[]) objs[0])
			if (!OBJECTS.containsKey(var))
				return FALSE;
		return TRUE;
	}).setVarArgs();
	public static final Command IS_OBJ = add("is_obj", BOOL, "Checks whether the token is a valid Object in the given Type.", CmdArg.TYPE, CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String type = (String) objs[0];
		if (!OBJECTS.containsKey(type))
			ctx.parseExcept("Unrecognized type", type);
		
		return boolOf(OBJECTS.get(type).isObject((String) objs[1]));
	});
	public static final Command MERGE = add("merge_obj", TOKEN, "Merges the the given Object variables into a single multiclassed Object. If there is more than one value specified for a given Type hirearchy, the last provided will overwrite the previous ones.", CmdArg.SVJAVOBJ).setFunc((ctx, objs) ->
	{
		SVJavObj[] jObjs = (SVJavObj[]) objs[0];
		if (jObjs.length == 0)
			return NULLV;
		if (jObjs.length == 1)
			return jObjs[0];
		
		int count = 0;
		for (SVJavObj jObj : jObjs)
			count += jObj.value.length;
		Object[] merged = new Object[count];
		for (SVJavObj jObj : jObjs)
			for (Object obj : jObj.value)
			{
				Class<?> objCl = obj.getClass();
				AUtils.replaceFirst(merged, (i, current) ->
				{
					if (current == null)
						return obj;
					Class<?> curCl = current.getClass();
					return curCl.isAssignableFrom(objCl) || objCl.isAssignableFrom(curCl) ? obj : current;
				});
			}
		return new SVJavObj(null, null, null, AUtils.trim(merged));
	}).setVarArgs();
	public static final Command GET_PARENT = add("get_parent", STRING, "Returns the name of the parent script # levels up, where 0 would target this script.", CmdArg.INT).setFunc((ctx, objs) ->
	{
		Script p = ctx;
		int count = (Integer) objs[0];
		for (int i = 0; i < count; i++)
		{
			p = p.parent;
			if (p == null)
				return NULLV;
		}
		return p.name == null ? NULLV : valOf(p.name);
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
		return boolOf(bool);
	});
	public static final Command PRINT = add("print", STRING, "Prints and returns the supplied value.", CmdArg.STRING).setFunc((ctx, objs) ->
	{
		String[] strs = (String[]) objs[0];
		String out = "";
		for (String str : strs)
			out += str;
		ctx.printCallback.accept(out);
		return valOf(out);
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
		ctx.printCallback.accept("--- Object Hirearchy ---");
		ctx.printCallback.accept(OBJECTS.get("Object").hirearchyString());
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
	public static final Command KEY_IN = add("key_in", VOID, "Fills the variables with user-keyboard-input.", CmdArg.TOKEN).setFunc((ctx, objs) ->
	{
		String[] vars = (String[]) objs[0];
		String next = NULL;
		for (String var : vars)
		{
			System.out.println(var + "?");
			next = ctx.keyIn.next();
			ctx.putVar(var, valOf(next));
		}
		return ctx.prev();
	}).rawArg(0).setVarArgs();
	public static final Command USER_REQ = userReqType("user_req", CmdArg.STRING, "String");
	public static final Command USER_REQ_INT = userReqType("user_req_int", CmdArg.INT, "integer");
	public static final Command USER_REQ_DOUBLE = userReqType("user_req_double", CmdArg.DOUBLE, "double");
	public static final Command USER_REQ_STRING = userReqType("user_req_string", CmdArg.STRING, "text");
	public static final Command USER_REQ_BOOL = userReqType("user_req_bool", CmdArg.BOOLEAN, "boolean");
	public static final Command USER_REQ_TOKEN = userReqType("user_req_token", CmdArg.TOKEN, "token");
	
	public static final Command CONCAT = add("concat", STRING, "Concatinates and returns the argument Strings.", CmdArg.STRING).setFunc((ctx, objs) ->
	{
		String out = "";
		for (String tokArr : (String[]) objs[0])
			out += tokArr;
		return strOf(out);
	}).setVarArgs();
	public static final Command ARR_MERGE = add("merge_array", TOKEN_ARR, "Merges arrays one onto the other in the order provided.", CmdArg.arrayOf(CmdArg.SVARRAY)).setFunc((ctx, objs) ->
	{
		SVArray[] arrays = (SVArray[]) objs[0];
		int count = 0;
		for (SVArray arr : arrays)
			count += arr.getArray().length;
		ScajlVariable[] outArr = new ScajlVariable[count];
		int j = 0;
		for (int i = 0; i < arrays.length; i++)
		{
			ScajlVariable[] from = arrays[i].getArray();
			ArrayUtils.fillFrom(outArr, from, j, 0, from.length);
			j += from.length;
		}
		return arrOf(outArr);
	}).setVarArgs();
	public static final Command ADD = add("add", DOUBLE, "Adds and returns the argument numbers.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return numOf(operate(0, (Object[]) objs[0], (all, next) -> all + next));
	}).setVarArgs();
	public static final Command SUB = add("sub", DOUBLE, "Subtracts and returns the argument numbers.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		Object[] arr = (Object[]) objs[0];
		return numOf(operate((double) arr[0] * 2, arr, (all, next) -> all - next));
	}).setVarArgs();
	public static final Command MULT = add("mult", DOUBLE, "Multiplies and returns the argument numbers.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return numOf(operate(1, (Object[]) objs[0], (all, next) -> all * next));
	}).setVarArgs();
	public static final Command DIVI = add("divi", DOUBLE, "Divides and returns the argument numbers.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		Object[] arr = (Object[]) objs[0];
		return numOf(operate((double) arr[0] * (double) arr[0], arr, (all, next) -> all / next));
	}).setVarArgs();
	public static final Command MODULO = add("mod", DOUBLE, "Returns A % B.", CmdArg.DOUBLE, CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return numOf((Double) objs[0] % (Double) objs[1]);
	});
	public static final Command INCREMENT = add("inc", DOUBLE, "Returns the increment of the argument number.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return numOf(((double) objs[0] + 1));
	});
	public static final Command DECREMENT = add("dec", DOUBLE, "Returns the decriment of the argument number.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return numOf(((double) objs[0] - 1));
	});
	public static final Command FLOOR = add("floor", INT, "Returns the largest integer less than or equal to this double.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return numOf(Math.floor((double) objs[0]));
	});
	public static final Command CEIL = add("ceil", INT, "Returns the smallest integer greater than or equal to this double.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return numOf(Math.ceil((double) objs[0]));
	});
	public static final Command NEGATE = add("negate", DOUBLE, "Returns the negation of the argument number.", CmdArg.DOUBLE).setFunc((ctx, objs) ->
	{
		return numOf((-(double) objs[0]));
	});
	public static final Command NOT = add("not", BOOL, "Return the boolean inverse of the argument.", CmdArg.BOOLEAN).setFunc((ctx, objs) ->
	{
		return boolOf((!(boolean) objs[0]));
	});
	public static final Command OR = add("or", BOOL, "Return true if any argument is true.", CmdArg.BOOLEAN).setFunc((ctx, objs) ->
	{
		Boolean[] ors = (Boolean[]) objs[0];
		boolean out = false;
		for (boolean b : ors)
			out = out || b;
		return boolOf(out);
	}).setVarArgs();
	public static final Command AND = add("and", BOOL, "Return true if every argument is true.", CmdArg.BOOLEAN).setFunc((ctx, objs) ->
	{
		Boolean[] ands = (Boolean[]) objs[0];
		boolean out = true;
		for (boolean b : ands)
			out = out && b;
		return boolOf(out);
	}).setVarArgs();
	public static final Command COMPARE = add("compare", BOOL, "Returns the evaluation of the boolean expression.", CmdArg.BOOLEAN_EXP).setFunc((ctx, objs) ->
	{
		return boolOf(((BooleanExp) objs[0]).eval());
	});
	public static final Command STRING_MATCH = add("string_match", BOOL, "Returns true if the Strings match.", CmdArg.STRING).setFunc((ctx, objs) ->
	{
		String[] strs = (String[]) objs[0];
		boolean equal = true;
		for (int i = 1; i < strs.length; i++)
			equal = equal && strs[i].equals(strs[0]);
		return boolOf(equal);
	}).setVarArgs();
	public static final Command IF_THEN_ELSE = add("if", TOKEN, "Iterates through the arguments, and returns the first token that has a true boolean, or null.", CmdArg.BOOLEAN_THEN).setFunc((ctx, objs) ->
	{
		BooleanThen[] ba = (BooleanThen[]) objs[0];
		for (BooleanThen b : ba)
			if (b.bool)
				return b.then;
		return NULLV;
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
			ctx.putVar(INDEX, numOf(i));
			ctx.runFrom(lab, (VarSet[]) objs[2]);
		}
		ctx.putVar(INDEX, numOf(count));
		return ctx.prev();
	}).setVarArgs();
	public static final Command WHILE = add("while", VOID, "While the boolean token (0) is true, excecutes the token label (1). Sets variables as provided before each run (2...).", CmdArg.TOKEN, CmdArg.TOKEN, CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		int wL = ctx.parseLine;
		Label lab = ctx.getLabel((String) objs[1]);
		if (lab == null)
			ctx.parseExcept("Invalid label specification", (String) objs[1], "No label found.");
		int ind = 0;
		while (ctx.valParse(CmdArg.BOOLEAN, ctx.lines[wL], null, (String) objs[0]))
		{
			ctx.putVar(INDEX, numOf(ind));
			ind++;
			ctx.runFrom(lab, (VarSet[]) objs[2]);
		}
		ctx.putVar(INDEX, numOf(ind));
		return ctx.prev();
	}).rawArg(0).setVarArgs();
	public static final Command CALL = add("call", VOID, "Excecutes the given token label in a new stack entry. Sets variables to values provided.", CmdArg.TOKEN, CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		String label = (String) objs[0];
		if (label.equals(NULL))
			return ctx.prev();
		
		Label lab = ctx.getLabel(label);
		if (lab == null)
			ctx.parseExcept("Invalid label specification", label, "No label found.");
		
		ctx.runFrom(lab, (VarSet[]) objs[1]);
		
		return ctx.prev();
	}).setVarArgs();
	public static final Command GOTO = overload("goto", CALL, "No difference, but exists for temporary backwards compatibility.", (objs) -> objs, CALL.args).setVarArgs();
	public static final Command RETURN = add("return", "Value", "Marks the end of a label or code section. If present, will set PREV to argument, or array of arguments if more than one is provided.", CmdArg.SCAJL_VARIABLE).setFunc((ctx, objs) ->
	{
		ScajlVariable[] rets = (ScajlVariable[]) objs[0];
		for (int i = 0; i < rets.length; i++)
			rets[i] = rets[i].eval(ctx);
		SNode last = ctx.popStack();
		if (rets.length == 0)
			return last.get(PREVIOUS);
		else if (rets.length == 1)
			return rets[0];
		else
			return arrOf(rets);
	}).setVarArgs();
	public static final Command ECHO = add("echo", VOID, "Sets PREV to argument, or array of arguments if more than one is provided.", CmdArg.SCAJL_VARIABLE).setFunc((ctx, objs) ->
	{
		ScajlVariable[] rets = (ScajlVariable[]) objs[0];
		for (int i = 0; i < rets.length; i++)
			rets[i] = rets[i].eval(ctx);
		if (rets.length == 0)
			return ctx.prev();
		else if (rets.length == 1)
			return rets[0];
		else
			return arrOf(rets);
	}).setVarArgs();
	public static final Command RUN_SCRIPT = add("run_script", Script.VOID, "Runs the given script. Booleans determine whether variables in this script will be given to other before being run, and whether variables in other will be pulled to this script once finished.", CmdArg.STRING, CmdArg.BOOLEAN, CmdArg.BOOLEAN, CmdArg.VAR_SET).setFunc((ctx, objs) ->
	{
		String name = (String) objs[0];
		File scr = getScriptFile(name);
		if (scr == null)
			ctx.parseExcept("Specified script does not exist", name);
		
		Script script;
		ScajlVariable out = ctx.prev();
		try
		{
			script = new Script(scr);
			ctx.transferCallbacks(script);
			if ((boolean) objs[1])
				script.integrateVarsFrom(ctx);
			
			script.putVar(Script.PARENT, valOf(ctx.name));
			script.name = name;
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
		String name = (String) objs[0];
		File scr = getScriptFile(name);
		if (scr == null)
			ctx.parseExcept("Specified script does not exist", name);
		
		Script script;
		ScajlVariable out = ctx.prev();
		try
		{
			script = new Script(scr);
			ctx.transferCallbacks(script);
			if ((boolean) objs[2])
				script.integrateVarsFrom(ctx);
			
			String label = (String) objs[1];
			Label lab = script.getLabel(label);
			if (lab != null)
			{
				script.putVar(Script.PARENT, valOf(ctx.name));
				script.name = name;
				script.runFrom(lab, (VarSet[]) objs[4]);
				if ((boolean) objs[3])
					ctx.integrateVarsFrom(script);
				
				out = script.prev();
			}
			else
				ctx.parseExcept("Specified label does not exist", label, "Script: " + name);
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
		return arrOf(p.x, p.y);
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
		return numOf(a);
	});
	
	public static final Command GET_AUTO_DELAY = add("get_robot_delay", INT, "Gets the automatic delay after robot operations.").setFunc((ctx, objs) ->
	{
		return numOf(ctx.rob.getAutoDelay());
	});
	
	static
	{
		BuiltInLibs.load();
	}
	
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
				return TRUE;
			}
			catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e)
			{
				ctx.exceptionCallback.accept(e);
				return FALSE;
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
	
	@FunctionalInterface
	public static interface ExpPredicate
	{
		public boolean test(Member e, boolean recursive);
	}
	
	@FunctionalInterface
	public static interface ClsPredicate
	{
		public boolean test(Class<?> cl, boolean recursive);
	}
	
	/////////////////////////////////////////////
	
/*	private boolean varCheck(String token, Predicate<String> check)
	{
		if (token.charAt(0) == RAW)
			return check.test(token.substring(1));
		String var = getVar(token);
		if (var != null)
			return check.test(var);
		return check.test(token);
	}
	public boolean isArray(String token)
	{
		return varCheck(token, (str) -> str.startsWith("" + ARR_S) && str.endsWith("" + ARR_E));
	}*/
//	public boolean isExecutable(String token)
//	{
//		return varCheck(token, (str) -> str.startsWith("" + SCOPE_S) && str.endsWith("" + SCOPE_E));
/*		if (token.charAt(0) == RAW)
			return false;
		String var = getVar(token);
		if (var != null)
			return isExecutable(var);
		return token.startsWith("" + SCOPE_S) && token.endsWith("" + SCOPE_E);*/
//	}
//	public boolean isString(String token)
//	{
//		return varCheck(token, (str) -> str.startsWith("" + STRING_CHAR) && token.endsWith("" + STRING_CHAR));
//		return token.startsWith("" + STRING_CHAR) && token.endsWith("" + STRING_CHAR);
//	}
	public static boolean isType(String token)
	{
		return OBJECTS.containsKey(token);
	}
	
	private static final DelimTracker QTRACK = new DelimTracker(STRING_CHAR, ESCAPE_CHAR);
	private static final BoxTracker
			ARRTRACK = new BoxTracker(ARR_S, ARR_E, ESCAPE_CHAR),
			PARTRACK = new BoxTracker(TOK_S, TOK_E, ESCAPE_CHAR),
			CURLTRACK = new BoxTracker(SCOPE_S, SCOPE_E, ESCAPE_CHAR);
	private static final WrapTracker MCOMTRACK = new WrapTracker(MULTILINE_COMMENT_START, MULTILINE_COMMENT_END, ESCAPE_CHAR);
	private static final RepeatTracker LCOMTRACK = new RepeatTracker(COMMENT_CHAR, ESCAPE_CHAR, 2);
	private static final MultiTracker COMTRACK = new MultiTracker(MCOMTRACK, LCOMTRACK);
	private static final MultiTracker SYNTRACK = new MultiTracker(ARRTRACK, CURLTRACK, PARTRACK);
	
	private static boolean multilineComment = false;
	private static String stripComments(String line)
	{
		String str = "";
		QTRACK.reset();
		COMTRACK.resetIf((tracker) -> tracker != MCOMTRACK || !multilineComment);
		for (int i = 0; i < line.length(); i++)
		{
			char parse = line.charAt(i);
			QTRACK.track(parse);
			
			COMTRACK.track(parse, QTRACK.inside());
			
			if (!(multilineComment = MCOMTRACK.inside() || MCOMTRACK.wasInside()))
			{
				if (LCOMTRACK.inside())
					return str.substring(0, str.length() - 1);
				str += parse;
			}
			else if (!MCOMTRACK.wasInside())
				str = str.substring(0, str.length() - 1);
		}
		return str.trim();
	}
	private static boolean syntaxCheck(String line)
	{
		if (LEGAL_ANON_SCOPE_MATCHER.matcher(line).matches())
			return true;
		QTRACK.reset();
		SYNTRACK.reset();
		for (int i = 0; i < line.length(); i++)
		{
			char parse = line.charAt(i);
			QTRACK.track(parse);
			SYNTRACK.track(parse, QTRACK.inside());
		}
		return !(QTRACK.inside() || SYNTRACK.insideOne());
	}
	public static String[] syntaxedSplit(String toSplit, char delim)
	{
		return syntaxedSplit(toSplit, "" + delim);
	}
	public static String[] syntaxedSplit(String toSplit, String delim)
	{
		return syntaxedSplit(toSplit, quote(delim), delim.length(), -1);
	}
	public static String[] syntaxedSplit(String toSplit, String delim, int limit)
	{
		return syntaxedSplit(toSplit, quote(delim), delim.length(), limit);
	}
	public static boolean syntaxedContains(String toCheck, String regEx, int trackLength)
	{
		Pattern pat = Pattern.compile(regEx);
		QTRACK.reset();
		SYNTRACK.reset();
		
		String recent = "";
		for (int i = 0; i < toCheck.length(); i++)
		{
			char parse = toCheck.charAt(i);
			
			QTRACK.track(parse);
			SYNTRACK.track(parse, QTRACK.inside());
			
			recent += parse;
			if (recent.length() > trackLength)
				recent = recent.substring(1);
			if (!QTRACK.inside() && !SYNTRACK.insideOne() && pat.matcher(recent).matches())
				return true;
		}
		return false;
	}
	public static String[] syntaxedSplit(String toSplit, String regEx, int trackLength, int limit)
	{
		Pattern pat = Pattern.compile(regEx);
		QTRACK.reset();
		SYNTRACK.reset();
		
		ArrayList<String> out = new ArrayList<>();
		
		String building = "";
		String recent = "";
		int found = 1;
		for (int i = 0; i < toSplit.length(); i++)
		{
			char parse = toSplit.charAt(i);
			
			QTRACK.track(parse);
			SYNTRACK.track(parse, QTRACK.inside());
			
			boolean push = false;
			recent += parse;
			if (recent.length() > trackLength)
				recent = recent.substring(1);
			if ((limit > 0 && found >= limit) || !pat.matcher(recent).matches() || QTRACK.inside() || SYNTRACK.insideOne())
			{
				building += parse;
				
				if (i == toSplit.length() - 1)
					push = true;
			}
			else
			{
				push = true;
				building = building.substring(0, building.length() - trackLength + 1);
			}
			
			if (push && !((building = building.trim()).isEmpty()))
			{
				out.add(building);
				building = "";
				found++;
			}
		}
		return out.toArray(new String[out.size()]);
	}
	public static SVVal boolOf(boolean bool)
	{
		return bool ? TRUE : FALSE;
	}
	public static SVVal numOf(double num)
	{
		return new SVVal(num, null);
	}
	public static SVVal valOf(String val)
	{
		return new SVVal(val, null);
	}
	public static SVString strOf(String str)
	{
		return new SVString(str, str, null);
	}
	public static ScajlVariable objOf(Object obj)
	{
		return obj == null ? NULLV : new SVJavObj(obj);
	}
	public static SVArray arrOf(ScajlVariable... arr)
	{
		return new SVArray(null, null, arr, false, null);
	}
	public static SVArray arrOf(String... arr)
	{
		ScajlVariable[] svarr = new ScajlVariable[arr.length];
		for (int i = 0; i < arr.length; i++)
			svarr[i] = valOf(arr[i]);
		return arrOf(svarr);
	}
	public static SVArray arrOf(double... arr)
	{
		ScajlVariable[] svarr = new ScajlVariable[arr.length];
		for (int i = 0; i < arr.length; i++)
			svarr[i] = numOf(arr[i]);
		return arrOf(svarr);
	}
	public static String[] argsOf(String line)
	{
		String[] spl = syntaxedSplit(line.trim(), "\\s", 1, 2);
		if (spl.length == 2)
			line = spl[1];
		else
			return new String[0];
			
		ArrayList<String> args = new ArrayList<String>();
		
		String arg = "";
		QTRACK.reset();
		SYNTRACK.reset();
		for (int i = 0; i < line.length(); i++)
		{
			char parseChar = line.charAt(i);
			
			QTRACK.track(parseChar);
			SYNTRACK.track(parseChar, QTRACK.inside());
			boolean buildingArg = (QTRACK.inside() || SYNTRACK.insideOne() || parseChar != ',');
			
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
		QTRACK.reset();
		SYNTRACK.reset();
		for (int i = 0; i < str.length(); i++)
		{
			char parse = str.charAt(i);
			
			QTRACK.track(parse);
			SYNTRACK.track(parse, QTRACK.inside());
			
			boolean inWord = parse != ARR_ACCESS;
			boolean buildingAcc = inWord || QTRACK.inside() || SYNTRACK.insideOne();
			
			if (buildingAcc)
			{
				if (!(PARTRACK.justEntered() || PARTRACK.justLeft()))
					acc += parse;
			}
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
		QTRACK.reset();
		SYNTRACK.reset();
		for (int i = 0; i < str.length(); i++)
		{
			char parseChar = str.charAt(i);

			QTRACK.track(parseChar);
			SYNTRACK.track(parseChar, QTRACK.inside());
			
			boolean inWord = parseChar != ' ' && parseChar != ',' && parseChar != ARR_SEP;
			boolean buildingToken = inWord || QTRACK.inside() || SYNTRACK.insideOne();
			
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
	public static String firstToken(String line)
	{
		return syntaxedSplit(line, "\\s", 1, 2)[0];
	}
	public static String[] arrayElementsOf(String array)
	{
		String str = arrayTrim(array);
		return syntaxedSplit(str, "" + ARR_SEP);
	}
	public static String toArrayString(String[] elements)
	{
		String out = "" + ARR_S;
		for (int i = 0; i < elements.length; i++)
			out += elements[i] + (i == elements.length - 1 ? "" : ARR_SEP + " ");
		return out + ARR_E;
	}
	
	private static boolean startsWith(String str, char c)
	{
		return str.length() > 0 && str.charAt(0) == c;
	}
	private static boolean endsWith(String str, char c)
	{
		return str.length() > 0 && str.charAt(str.length() - 1) == c;
	}
	
	public static String unpack(String token)
	{
		if (startsWith(token, ARR_S))
			return unpack(token, ARR_S, ARR_E);
		if (startsWith(token, STRING_CHAR))
			return unpack(token, STRING_CHAR, STRING_CHAR);
		if (startsWith(token, TOK_S))
			return unpack(token, TOK_S, TOK_E);
		if (startsWith(token, SCOPE_S))
			return unpack(token, SCOPE_S, SCOPE_E);
		return token;
	}
	
	private static String unpack(String toUnp, char start, char end)
	{
		if (startsWith(toUnp, start) && endsWith(toUnp, end))
			return toUnp.substring(1, toUnp.length() - 1);
		return toUnp;
	}
	
	public static ScajlVariable tokenize(String... objs)
	{
		return valOf(StringUtils.toString(objs, "" + TOK_S, " ", "" + TOK_E));
	}
	
	public static ScajlVariable tokenize(double... nums)
	{
		return valOf(StringUtils.toString(nums, "" + TOK_S, " ", "" + TOK_E));
	}
	
	public static String arrayReturnDispl(String of)
	{
		return ARR_S + of + ARR_E;
	}
	
	///////////
	
	public ScajlVariable getVar(String input, boolean rawDefault, SVMember selfCtx)
	{
		return ScajlVariable.getVar(input, rawDefault, this, selfCtx);
	}
	
	public void putVar(String input, ScajlVariable var)
	{
		ScajlVariable.putVar(input, var, this);
	}
	
	public <T> T valParse(CmdArg<T> arg, String line, SVMember selfCtx, String... tokens)
	{
		return valParse(arg, line, (i) -> false, selfCtx, tokens);
	}
	public <T> T valParse(CmdArg<T> arg, String line, IntPredicate tokenRawDefault, SVMember selfCtx, String... tokens)
	{
		int count = arg.tokenCount();

		MixedPair<String[], ScajlVariable[]> tokVarPair = tokVarPair(tokenRawDefault, arg instanceof VarCmdArg, selfCtx, (Object[]) tokens);
		tokens = tokVarPair.a();
		ScajlVariable[] vars = tokVarPair.b();
		
		if (tokens.length != count)
			parseExcept("Invalid token count", line, "Argument requires " + count + " tokens, but " + tokens.length + " have been provided. Tokens are separated by spaces.");
		
		T obj = arg.parse(tokens, vars, 0, this);
		
		if (obj == null)
			parseExcept("Invalid token resolution", StringUtils.toString(tokens, "", " ", ""), "Expected type: " + arg.type);
		
		return obj;
	}
	public MixedPair<String[], ScajlVariable[]> tokVarPair(IntPredicate tokenRawDefault, boolean unresolved, SVMember selfCtx, Object... preParse)
	{
		ScajlVariable[] vars = new ScajlVariable[preParse.length];
		String[] tokens = new String[preParse.length];
		for (int i = 0; i < vars.length; i++)
		{
			if (preParse[i] instanceof String)
			{
				ScajlVariable var = getVar((String) preParse[i], tokenRawDefault.test(i), selfCtx);
				vars[i] = var;
				tokens[i] = !unresolved ? vars[i].val(this) : vars[i].raw();
			}
			else if (preParse[i] instanceof ScajlVariable)
			{
				vars[i] = (ScajlVariable) preParse[i];
				tokens[i] = vars[i].raw();
			}
			else
				throw new IllegalArgumentException("Non-String, non-ScajlVariable parameter passed as preParsed values.");
		}
		return new MixedPair<>(tokens, vars);
	}
	
	private static String quote(String str)
	{
		return Pattern.quote(str);
	}
	private static String quote(char chr)
	{
		return quote("" + chr);
	}
	public static String[] replaceTokenWith(String[] tokens, String[] toInsert, int index, AtomicInteger newInd)
	{
		String[] tokensExt = new String[tokens.length - 1 + toInsert.length];
		ArrayUtils.fillFrom(tokensExt, tokens, 0, 0, index);
		ArrayUtils.fillFrom(tokensExt, toInsert, index, 0, toInsert.length);
		if (index != tokens.length - 1)
			ArrayUtils.fillFrom(tokensExt, tokens, index + toInsert.length, index + 1, tokens.length - (index + 1));
		newInd.set(index + toInsert.length);
		return tokensExt;
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
	
	
	private RunnableCommand parse(String line, CmdHead head, Bool breakIf, SVMember selfCtx)
	{
		String[] argStrs = argsOf(line);
		if (line.length() > 0)
		{
			Command cmd = getCommand(head);
			if (cmd != null)
			{
				if (cmd.isDisabled())
					parseExcept("Disabled command", cmd.name);
				breakIf.set(breakIf.get() && head.isInlineElse);
				if (!breakIf.get() && (head.isInlineIf ? valParse(CmdArg.BOOLEAN, line, selfCtx, head.inlineIf) : true))
				{
					breakIf.set(true);
					CmdArg<?>[] args = cmd.args;
					boolean varArgs = cmd.isVarArgs();
					boolean varArgArray = argStrs.length > 0 && argStrs[argStrs.length - 1].startsWith(VAR_ARG_STR);
					if (varArgArray)
						argStrs[argStrs.length - 1] = argStrs[argStrs.length - 1].substring(1);
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
						int varArgInd = Math.min(argInd, args.length - 1);
						
						if (varArgArray && !firstVarArg)
							parseExcept("Invalid argument count", line, "If var-arg arguments are specified as an array, the array must be the last argument");
						
						CmdArg<?> arg = args[varArgInd];
						final CmdArg<?> origArg = arg;
						
						String[] tokenSource = tokensOf(argStrs[argInd]);
						String tokenSourceStr = StringUtils.toString(tokenSource, "", " ", "");
						Object[] preParse = ScajlVariable.preParse(tokenSource, this);
						
						Object obj = null;
						
						String trimmed = "";
						if (!varArgArray)
						{
							arg = CmdArg.getArgForCount(arg, preParse.length);
							if (arg == null)
								parseExcept("Invalid token count for CmdArg format '" + origArg.type + "'", line, "Format requires " + origArg.tokenCount() + " tokens, but " + preParse.length + " have been provided. Tokens are separated by spaces. From tokens: " + tokenSourceStr);
							
							final CmdArg<?> aarg = arg;
							MixedPair<String[], ScajlVariable[]> tokVarPair = tokVarPair((i) -> cmd.rawArg[varArgInd] || aarg.rawToken(i), aarg instanceof VarCmdArg, selfCtx, preParse);
							String[] tokens = tokVarPair.a();
							trimmed = StringUtils.toString(tokens, "", " ", "");
							ScajlVariable[] vars = tokVarPair.b();
							
							obj = arg.parse(tokens, vars, 0, this);
							
							if (obj == null && !cmd.nullableArg(varArgInd))
								parseExcept("Invalid token resolution", trimmed, "Expected type: " + arg.type + ". From tokens: " + tokenSourceStr);
							
							if (!atVA)
								objs[argInd] = obj;
							else
								((Object[]) objs[objs.length - 1])[argInd - args.length + 1] = obj;
						}
						else
						{
							final CmdArg<?> arrArg = cmd.variadic;
							MixedPair<String[], ScajlVariable[]> tokVarPair = tokVarPair((i) -> false, true, selfCtx, preParse);
							String[] tokens = tokVarPair.a();
							ScajlVariable[] vars = tokVarPair.b();
							trimmed = tokens[0];
							obj = arrArg.parse(tokens, vars, 0, this);
							if (obj == null && !cmd.nullableArg(argInd))
								parseExcept("Invalid var-arg array resolution", trimmed, tokens[0]);
							
							objs[objs.length - 1] = obj;
						}
						if (obj != null)
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
	
	public String getParseExceptString(String preAt, String postLine, String extra)
	{
		return preAt + " at line " + (parseLine + 1) + ": " + postLine + ", from: " + lines[parseLine]
				+ (extra == null ? "." : ", extra info: " + extra);
	}
	
	public void parseExcept(String preAt, String postLine, String extra)
	{
		throw new CommandParseException(getParseExceptString(preAt, postLine, extra));
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
		int anonScopeId = 0;
		Pattern noScope = Pattern.compile("\\" + SCOPE_S + "+");
		multilineComment = false;
		ArrayList<Integer> merges = new ArrayList<Integer>();
		anonScope = new HashMap<>();
		while (scan.hasNextLine())
		{
			String line = stripComments(scan.nextLine());
			if (line.startsWith(LABEL) || line.startsWith(SCOPED_LABEL))
				putLabel(firstToken(line), num);
			else if (startsWith(line, SCOPE_S))
			{
				Label anon = new Label(SCOPED_LABEL + noScope.matcher(line).replaceFirst("") + "ANON" + anonScopeId++, num);
				labels.put(anon.name, anon);
				anonScope.put(num, anon);
			}
			else if (line.startsWith(END_SCRIPT))
				break;
			else if (line.endsWith(END_SCRIPT))
			{
				str += StringUtils.endWithout(line, END_SCRIPT);
				break;
			}
			else if (line.startsWith("" + LINE_MERGE))
				merges.add(num);
			
			str += line.trim() + "\n";
			num++;
		}
		lines = str.split("\n");
		for (int i = merges.size() - 1; i >= 0; i--)
		{
			int m = merges.get(i);
			String toMerge = lines[m].replaceFirst(quote(LINE_MERGE), "");
			if (m > 0)
			{
				lines[m - 1] += " " + toMerge.trim();
				lines[m] = "";
			}
			else
				lines[m] = toMerge.trim();
		}
		for (int i = 0; i < lines.length; i++)
			if (!syntaxCheck(lines[i]))
				throw new CommandParseException("Invalid syntax at line " + (i + 1) + ": " + lines[i] + ". Unfinished delimiter.");

		putVar(PARENT, ScajlVariable.NULL);
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
	private int labelsDeep = 0;
	private void runFrom(Label label, VarSet... varSets)
	{
		pushStack(label);
		for (VarSet var : varSets)
			putVar(var.var, var.set);
		if (keyIn == null)
			keyIn = new Scanner(System.in);
		
		Bool breakIf = new Bool(false);
		while(parseLine < lines.length && !stack.isEmpty() && !forceKill.get())
		{
			String line = lines[parseLine];
			if (line.startsWith(LABEL) || line.startsWith(SCOPED_LABEL))
				labelsDeep++;
			else if (labelsDeep == 0)
			{
				if (line.equals(HELP_CHAR_STR))
					PRINT_COMMANDS.func.cmd(this, (Object[]) null);
				else if (startsWith(line, SCOPE_S) && LEGAL_ANON_SCOPE_MATCHER.matcher(line).matches())
					scope.push(anonScope.get(parseLine));
				else if (endsWith(line, SCOPE_E) && LEGAL_ANON_SCOPE_MATCHER.matcher(line).matches())
					scope.pop();
				else
				{
					CommandResult res = runExecutable(line, breakIf, null);
					if (res.shouldBreak)
						break;
				}
			}
			else if (new CmdHead(firstToken(line)).name.equals(RETURN.name))
				labelsDeep--;
			parseLine++;
		}
	}
	protected CommandResult runExecutable(String executableLine, SVMember selfCtx)
	{
		return runExecutable(executableLine, new Bool(false), selfCtx);
	}
	private CommandResult runExecutable(String executableLine, Bool breakIf, SVMember selfCtx)
	{
		String line = executableTrim(executableLine);
		if (line.isEmpty() || line.startsWith(LABEL) || line.startsWith(SCOPED_LABEL))
			return new CommandResult(prev(), false);
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
					printCallback.accept("--- Type Hirearchy ---\n" + so.hirearchyString() + "\n--- Commands ---\n" + str);
				}
			}
			else
				printCallback.accept(command.getInfoString());
		}
		else
		{
			int fur = 1;
			boolean whil = false;
			if (head.isInlineFor)
				fur = valParse(CmdArg.INT, line, selfCtx, head.inlineFor);
			if (head.isInlineWhile)
				whil = valParse(CmdArg.BOOLEAN, line, selfCtx, head.inlineWhile);
			if (head.isInlineFor || head.isInlineWhile)
				putVar(INDEX, numOf(0));
			for (int f = 0; f < fur || (head.isInlineWhile && whil);)
			{
				RunnableCommand cmd = parse(line, head, breakIf, selfCtx);
				if (cmd != null)
				{
					ScajlVariable out;
					putVar(PREVIOUS, out = cmd.run(this));
					for (int i = 0; i < head.storing.length; i++)
						putVar(head.storing[i], out);
					String raw = out.raw();
					prevCallback.accept(head.name, raw);
					debugger.info(head.name, cmd.getInput(), raw);
				}
				if (popped != null) // Popped isn't empty -> something returned. Old stack doesn't return to anything -> end script.
				{
					popped = null;
					return new CommandResult(prev(), true);
				}
				f++;
				if (head.isInlineFor || head.isInlineWhile)
					putVar(INDEX, numOf(f));				
				if (head.isInlineWhile)
					whil = valParse(CmdArg.BOOLEAN, line, selfCtx, head.inlineWhile);
			}
			if (head.isInlineFor)
				putVar(INDEX, numOf(fur));
		}
		return new CommandResult(prev(), false);
	}
	public void goTo(String label)
	{
		Label lab = getLabel(label);
		if (lab == null)
			parseExcept("Invalid label specification", label, "No label found.");
		
		pushStack(lab);
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
			putVar(name, valOf(val));
			return true;
		}
	}
	
	public Command getCommand(CmdHead head)
	{
		if (!head.isMemberCmd)
			return CMDS.get(head.name);
		ScriptObject<?> parent = OBJECTS.get(head.parentPath[0]);
		if (parent == null)
			return null;
		for (int i = 1; i < head.parentPath.length - 1; i++)
		{
			parent = parent.getSub(head.parentPath[i]);
			if (parent == null)
				return null;
		}
		return parent.getMemberCmd(head.name);
	}
	
	public void integrateVarsFrom(Script other)
	{
		scope.integrateFrom(other.scope);
	}
	
	public ScajlVariable prev()
	{
		return getVar(PREVIOUS, false, null);
	}
	public static String arrayTrim(String token)
	{
		return unpack(token.trim(), ARR_S, ARR_E);
	}
	public static String executableTrim(String token)
	{
		return unpack(token.trim(), SCOPE_S, SCOPE_E);
	}
	public static String stringTrim(String string)
	{
		String str = string.trim();
		if (str.startsWith("" + STRING_CHAR) && str.endsWith("" + STRING_CHAR))
			return str.substring(1, str.length() - 1);
		else
			return str;
	}
	public static String tokTrim(String token)
	{
		return StringUtils.startWithout(StringUtils.endWithout(token.trim(), "" + TOK_E), "" + TOK_S);
	}
	
	public static MixedPair<boolean[], String> prefixModsFrom(String test, String[] valid)
	{
		boolean[] out = new boolean[valid.length];
		int startsWith = -1;
		while ((startsWith = startsWithOneOf(test, valid)) != -1)
		{
			out[startsWith] = true;
			test = test.replaceFirst(quote(valid[startsWith]), "");
		}
		return new MixedPair<boolean[], String>(out, test);
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
	
	private void pushStack(Label to)
	{
		stack.push(new StackEntry(parseLine, to));
		parseLine = to.line + 1;
		if (to.isScoped)
			scope.push(to);
	}
	
	private SNode popStack()
	{
		StackEntry ent = stack.pop();
		popped = ent;
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
