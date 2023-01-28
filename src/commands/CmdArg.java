package commands;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.function.IntPredicate;

import commands.BooleanExp.Comp;
import commands.DoubleExp.Oper;
import commands.ScajlVariable.SVArray;
import commands.ScajlVariable.SVExec;
import commands.ScajlVariable.SVJavObj;
import commands.ScajlVariable.SVTokGroup;
import utilities.ArrayUtils;
import utilities.MapUtils;
import utilities.StringUtils;

public abstract class CmdArg<T>
{
	static final HashMap<Class<?>, LinkedHashMap<Integer, CmdArg<?>>> ARGS = new HashMap<>();
	private static final HashMap<Class<?>, Class<?>> WRAP_PRIMITIVE = new HashMap<>();
	private static final HashSet<Method> OBJECT_METHODS = new HashSet<>();
	static
	{
		WRAP_PRIMITIVE.put(boolean.class, Boolean.class);
		WRAP_PRIMITIVE.put(byte.class, Byte.class);
		WRAP_PRIMITIVE.put(char.class, Character.class);
		WRAP_PRIMITIVE.put(double.class, Double.class);
		WRAP_PRIMITIVE.put(float.class, Float.class);
		WRAP_PRIMITIVE.put(int.class, Integer.class);
		WRAP_PRIMITIVE.put(long.class, Long.class);
		WRAP_PRIMITIVE.put(short.class, Short.class);
		WRAP_PRIMITIVE.put(void.class, Void.class);
		
		for (Method m : Object.class.getMethods())
			OBJECT_METHODS.add(m);
	}
	public static Class<?> wrap(Class<?> cl) { return WRAP_PRIMITIVE.get(cl); }
	
	///////////

	public static <T> CmdArg<T> reg(CmdArg<T> arg, Class<T> toClass)
	{
		LinkedHashMap<Integer, CmdArg<?>> bin = ARGS.get(toClass);
		if (bin != null)
		{
			if (!bin.containsKey(arg.tokenCount()))
				bin.put(arg.tokenCount(), arg);
		}
		else
		{
			LinkedHashMap<Integer, CmdArg<?>> mp = new LinkedHashMap<>();
			mp.put(arg.tokenCount(), arg);
			ARGS.put(toClass, mp);
		}
		return arg;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> CmdArg<T> getArgFor(Class<T> cls)
	{
		LinkedHashMap<Integer, CmdArg<?>> bin = ARGS.get(cls);
		if (bin == null || bin.isEmpty())
		{
			if (cls.isArray())
			{
				CmdArg<?> componentArg = getArgFor(cls.getComponentType());
				if (componentArg == null)
					return null;
				return (CmdArg<T>) arrayOf(componentArg);
			}
			else if (cls.isPrimitive())
			{
				CmdArg<?> wrapped = getArgFor(WRAP_PRIMITIVE.get(cls));
				if (wrapped == null)
					return null;
				return unwrappedOf(wrapped, cls);
			}
			return null;
		}
		Iterator<CmdArg<?>> it = bin.values().iterator();
		return (CmdArg<T>) it.next();
	}
	
	@SuppressWarnings("unchecked")
	public static <T> CmdArg<T> getArgForCount(CmdArg<T> defalt, int writtenTokenCount)
	{
		if (defalt.tokenCount() == writtenTokenCount)
			return defalt;
		LinkedHashMap<Integer, CmdArg<?>> bin = ARGS.get(defalt.cls);
		if (bin == null || bin.isEmpty())
			return null;
		return (CmdArg<T>) bin.get(writtenTokenCount);
	}
	
	public static <T> TypeArg<T> getTypeArgFor(Class<T> cls)
	{
		return new TypeArg<T>(cls, getArgFor(cls));
	}
	
	public static class TypeArg<TY>
	{
		public final Class<TY> cl;
		public final CmdArg<TY> arg;
		public TypeArg(Class<TY> type, CmdArg<TY> arg) { this.cl = type; this.arg = arg; }
		@SuppressWarnings("unchecked")
		public ScajlVariable castAndUnparse(Object obj, Scajl ctx) { return obj == null ? ScajlVariable.NULL : arg.unparse((TY) obj, ctx); }
	}

	/////////////////////////////////////////
	
	public final String type;
	public final Class<T> cls;
	public CmdArg(String type, Class<T> cls)
	{
		this.type = type;
		this.cls = cls;
		if (tokenCount() < 0)
			throw new IllegalStateException("Only positive token counts can be parsed.");
	}
	
	public String getInfoString()
	{
		String inf = "";
		String[] rt = this.type.split(" ");
		for (int j = 0; j < rt.length; j++)
		{
			rt[j] = (this.rawToken(j) ? Scajl.RAW : "") + rt[j];
			inf += rt[j] + (j == rt.length - 1 ? "" : " ");
		}
		return inf;
	}
	
	public boolean rawToken(int ind) { return false; }
	public int tokenCount() { return 1; }
	
	public CmdArg<T> reg()
	{
		reg(this, cls);
		return this;
	}
	
	public abstract T parse(ScajlVariable[] vars, int off, Scajl ctx);
	public T parse(ScajlVariable var, Scajl ctx)
	{
		if (tokenCount() > 1)
			throw new IllegalStateException("Attempt to parse multitoken arg without providing variable array.");
		return parse(new ScajlVariable[] { var }, 0, ctx);
	}
	
	public ScajlVariable unparse(T obj, Scajl ctx)
	{
		return unparse(obj);
	}
	
	public ScajlVariable unparse(T obj)
	{
		return Scajl.objOf(obj);
	}
	
	/////////////////////////
		
	public static abstract class PrefCmdArg<T> extends CmdArg<T>
	{
		private final LinkedHashMap<String, CmdArg<T>> prefixes = new LinkedHashMap<>();
		private int tokenCount = 0;
		
		public PrefCmdArg(String type, Class<T> cls)
		{
			super(type, cls);
		}
		
		public PrefCmdArg<T> add(String prefix, CmdArg<T> toAdd)
		{
			if (prefixes.isEmpty())
				tokenCount = toAdd.tokenCount() + 1;
			if (toAdd.tokenCount() != tokenCount() - 1)
				throw new IllegalArgumentException("Added CmdArg must have matching tokenCount.");
			prefixes.put(prefix.toUpperCase(), toAdd);
			return this;
		}
		
		@Override
		public String getInfoString()
		{
			String inf = "";
			Iterator<Entry<String, CmdArg<T>>> it = prefixes.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<String, CmdArg<T>> ent = it.next();
				inf += Scajl.RAW + ent.getKey() + Scajl.RAW + " " + ent.getValue().getInfoString();
				if (it.hasNext())
					inf += " (or) ";
			}
			return inf;
		}
		
		@Override
		public int tokenCount()
		{
			return tokenCount;
		}
		
		@Override
		public boolean rawToken(int ind)
		{
			return ind == 0;
		}
		
		@Override
		public T parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			CmdArg<T> arg = prefixes.get(vars[0].val(ctx).toUpperCase());
			if (arg == null)
				return null;
			return arg.parse(vars, 1, ctx);
		}
		
		@Override
		public PrefCmdArg<T> reg()
		{
			if (prefixes.isEmpty())
				throw new IllegalStateException("Cannot register PrefCmdArg before assigning at least one contained CmdArg with which tokenCount can be determined.");
			return (PrefCmdArg<T>) super.reg();
		}
	}
	
	@FunctionalInterface
	public interface ObjConstruct<T>
	{
		public T construct(Object[] objs);
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	
	public static final CmdArg<Library> LIBRARY = new CmdArg<Library>("Library", Library.class)
	{
		@Override
		public Library parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			return Scajl.getLibrary(vars[off].val(ctx));
		}	
	}.reg();
	
	public static final CmdArg<Object> OBJECT = new CmdArg<Object>("Object Type", Object.class)
	{
		@Override
		public int tokenCount()
		{
			return 2;
		}
		
		@Override
		public Object parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			String type = vars[off + 1].val(ctx);
			if (!Scajl.isType(type))
				return null;
			if (!(vars[off] instanceof SVJavObj))
				return null;
			SVJavObj jObj = (SVJavObj) vars[off];
			ScriptObject<?> so = Scajl.getType(type);
			Object[] types = jObj.value;
			CmdArg<?> arg = so.argOf();
			for (Object obj : types)
				if (arg.cls.isAssignableFrom(obj.getClass()))
					return obj;
			
			return null;
		}
		
		@Override
		public ScajlVariable unparse(Object obj, Scajl ctx)
		{
			TypeArg<?> typeArg = getTypeArgFor(obj.getClass());
			if (typeArg.arg == null || typeArg.arg == this)
				return Scajl.objOf(obj);
			return typeArg.castAndUnparse(obj, ctx);
		}
	}.reg();
	
	public static final CmdArg<Object> OBJECT_NO_MULTICLASS = new CmdArg<Object>("Object", Object.class)
	{
		@Override
		public Object parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			if (!(vars[off] instanceof SVJavObj))
				return null;
			SVJavObj jObj = (SVJavObj) vars[off];
			Object[] types = jObj.value;
			if (types.length > 1)
			{
				ctx.parseExcept("Invalid Object resolution", "The provided Object is multiclassed, so a specific Type must be given");
				return null;
			}
			return types[0];
		}
		
		@Override
		public ScajlVariable unparse(Object obj)
		{
			return OBJECT.unparse(obj);
		}
	}.reg();
	
	public static final CmdArg<Long> LONG = new CmdArg<Long>("Long", Long.class)
	{
		@Override
		public Long parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Double d = DOUBLE.parse(vars, off, ctx);
			return d == null ? null : (long) (double) d;
		}
	}.reg();
	
	public static final CmdArg<Integer> INT = new CmdArg<Integer>("Integer", Integer.class)
	{
		@Override
		public Integer parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Double d = DOUBLE.parse(vars, off, ctx);
			return d == null ? null : (int) (double) d;
		}
	}.reg();
	
	public static final CmdArg<Integer> INT_POSITIVE = new CmdArg<Integer>("PositiveInteger", Integer.class)
	{
		@Override
		public Integer parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Integer i = INT.parse(vars, off, ctx);
			if (i != null && i >= 0)
				return i;
			return null;
		}
	};
	
	public static final CmdArg<Integer> INT_EXP = new CmdArg<Integer>("Integer Operation Integer", Integer.class)
	{
		@Override
		public int tokenCount()
		{
			return 3;
		}
		
		@Override
		public Integer parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Double d = DOUBLE_EXPRESSION.parse(vars, off, ctx);
			return d == null ? null : (int) (double) d; // TODO: No longer rounded? Inte bra?
		}
	}.reg();
	
	public static final CmdArg<Byte> BYTE = new CmdArg<Byte>("Byte", Byte.class)
	{
		@Override
		public Byte parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Double d = DOUBLE.parse(vars, off, ctx);
			return d == null ? null : (byte) (double) d;
		}
	}.reg();
	
	public static final CmdArg<Short> SHORT = new CmdArg<Short>("Short", Short.class)
	{
		@Override
		public Short parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Double d = DOUBLE.parse(vars, off, ctx);
			return d == null ? null : (short) (double) d;
		}
	}.reg();
	
	public static final CmdArg<Void> VOID = new CmdArg<Void>("Void", Void.class)
	{
		@Override
		public Void parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			return null;
		}
		
		@Override
		public ScajlVariable unparse(Void obj, Scajl ctx)
		{
			return ctx.prev();
		};
	}.reg();
	
	public static final CmdArg<Double> DOUBLE_POSITIVE = new CmdArg<Double>("PositiveDouble", Double.class)
	{
		@Override
		public Double parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Double doub = DOUBLE.parse(vars, off, ctx);
			if (doub != null && doub >= 0)
				return doub;
			return null;
		}
	};
	
	public static final CmdArg<Double> DOUBLE = new CmdArg<Double>("Double", Double.class)
	{	
		@Override
		public Double parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			try
			{
				return vars[off].valueD(ctx);
			}
			catch (NumberFormatException e)
			{}
			return null;
		}
	}.reg();
	
	public static final CmdArg<Double> DOUBLE_EXPRESSION = new CmdArg<Double>("Double Operation Double", Double.class)
	{
		@Override
		public int tokenCount()
		{
			return 3;
		}
		
		@Override
		public Double parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Double a = DOUBLE.parse(vars, off, ctx);
			Double b = DOUBLE.parse(vars, off + 2, ctx);
			Oper op = DoubleExp.Oper.parse(vars[off + 1].val(ctx));
			if (ArrayUtils.contains(ArrayUtils.of(a, b, op), null))
				return null;
			return op.eval(a, b);
		}
	}.reg();
	
	public static final CmdArg<Float> FLOAT = new CmdArg<Float>("Float", Float.class)
	{	
		@Override
		public Float parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Double d = DOUBLE.parse(vars, off, ctx);
			return d == null ? null : (float) (double) d;
		}
	}.reg();
	
	public static final CmdArg<Character> CHARACTER = new CmdArg<Character>("Character", Character.class)
	{
		@Override
		public Character parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			String ch = vars[off].val(ctx);
			if (ch.length() > 1)
				return null;
			return ch.charAt(0);
		}
	}.reg();
	
	public static final CmdArg<String> TOKEN = new CmdArg<String>("Token", String.class)
	{
		@Override
		public String parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			String tk = vars[off].val(ctx);
			String[] tokens = Scajl.tokensOf(tk);
			if (tokens.length > 1)
				return null;
			return tokens[0];
		}
	};
	
	public static final CmdArg<String> TYPE = new CmdArg<String>("Type", String.class)
	{
		@Override
		public String parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			String type = TOKEN.parse(vars, off, ctx);
			if (Scajl.isType(type))
				return type;
			return null;
		}
	};
	
	public static final CmdArg<ScajlVariable> SCAJL_VARIABLE = new CmdArg<ScajlVariable>("Variable", ScajlVariable.class)
	{
		@Override
		public ScajlVariable parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			return vars[off];
		}
		
		@Override
		public ScajlVariable unparse(ScajlVariable obj)
		{
			return obj;
		}
	}.reg();
	
	public static final CmdArg<ScajlVariable> PATTERN = new CmdArg<ScajlVariable>("Pattern", ScajlVariable.class)
	{
		@Override
		public ScajlVariable parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			return SCAJL_VARIABLE.parse(vars, off, ctx);
		}
		
		@Override
		public ScajlVariable unparse(ScajlVariable obj)
		{
			return SCAJL_VARIABLE.unparse(obj);
		}
	};
	
	public static final CmdArg<SVArray> SVARRAY = new CmdArg<SVArray>("Array", SVArray.class)
	{
		@Override
		public SVArray parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			return vars[off] instanceof SVArray ? (SVArray) vars[off] : null;
		}

		@Override
		public ScajlVariable unparse(SVArray obj)
		{
			return obj;
		}
	}.reg();
	
	public static final CmdArg<SVJavObj> SVJAVOBJ = new CmdArg<SVJavObj>("Object", SVJavObj.class)
	{
		@Override
		public SVJavObj parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			return vars[off] instanceof SVJavObj ? (SVJavObj) vars[off] : null;
		}
		
		@Override
		public ScajlVariable unparse(SVJavObj obj)
		{
			return obj;
		}
	};
	
	public static final CmdArg<SVExec> SVEXEC = new CmdArg<SVExec>("Executable", SVExec.class)
	{
		@Override
		public SVExec parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			if (vars[off] instanceof SVExec)
				return (SVExec) vars[off];
			Label lab = LABEL.parse(vars, off, ctx);
			if (lab == null)
				return null;
			String callLab = "" + Scajl.SCOPE_S +  Scajl.CALL.name + " " + lab.name + Scajl.SCOPE_E;
			return new SVExec("" + Scajl.REF + callLab, callLab, null, ctx);
		}
		
		@Override
		public ScajlVariable unparse(SVExec obj)
		{
			return obj;
		}
	};
	
	public static final CmdArg<String> STRING = new CmdArg<String>("String", String.class)
	{
		@Override
		public String parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			return vars[off].val(ctx);
		}
		
		@Override
		public ScajlVariable unparse(String obj)
		{
			return Scajl.strOf(obj);
		};
	}.reg();
	static
	{
		for (int i = 2; i < 6; i++)
		{
			final int ii = i;
			new CmdArg<String>(StringUtils.mult("String ", i).trim(), String.class)
			{
				@Override
				public int tokenCount()
				{
					return ii;
				}
			
				@Override
				public String parse(ScajlVariable[] vars, int off, Scajl ctx)
				{
					String s = "";
					for (int i = 0; i < tokenCount(); i++)
						s += vars[i].val(ctx);
					return s;
				}
				
				@Override
				public ScajlVariable unparse(String obj)
				{
					return STRING.unparse(obj);
				};
			}.reg();
		}
	}
	
	public static final CmdArg<Boolean> BOOLEAN = new CmdArg<Boolean>("Boolean", Boolean.class)
	{
		@Override
		public Boolean parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			try
			{
				return vars[off].valueB(ctx);
			}
			catch (NumberFormatException e)
			{}
			return null;
		}
	}.reg();
		
	public static final CmdArg<BooleanThen> BOOLEAN_THEN = new CmdArg<BooleanThen>("Boolean Then", BooleanThen.class)
	{
		@Override
		public int tokenCount()
		{
			return 2;
		}

		@Override
		public BooleanThen parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Boolean b = BOOLEAN.parse(vars, off, ctx);
			if (b == null)
				return null;
			return new BooleanThen(b, vars[off + 1]);
		}
	}.reg();
	
	public static final CmdArg<BooleanExp> BOOLEAN_EXP_OBJ = new CmdArg<BooleanExp>("Double Comparator Double", BooleanExp.class)
	{
		@Override
		public int tokenCount()
		{
			return 3;
		}
		
		@Override
		public BooleanExp parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Double a = DOUBLE.parse(vars, off, ctx);
			if (a == null) return null;
			Comp comp = Comp.parse(vars[off + 1].val(ctx).toUpperCase());
			if (comp == null)
				return null;
			Double b = DOUBLE.parse(vars, off + 2, ctx);
			return b == null ? null : new BooleanExp(a, b, comp);
		}
		
		@Override
		public ScajlVariable unparse(BooleanExp obj)
		{
			return Scajl.tokenize("" + obj.a, obj.comp.display, "" + obj.b);
		}
	}.reg();
	
	public static final CmdArg<Label> LABEL = new CmdArg<Label>("Label", Label.class)
	{
		@Override
		public Label parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			return ctx.getLabel(vars[off].val(ctx));
		}
	};
	
	public static final CmdArg<Variable> VARIABLE = new CmdArg<Variable>("Variable", Variable.class)
	{
		@Override
		public boolean rawToken(int ind)
		{
			return ind == 0;
		}
		
		@Override
		public Variable parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			ScajlVariable var = vars[off];
			Variable out = new Variable(var, TOKEN.parse(vars, off, ctx));
			if (out.name == null)
				return null;
			return out;
		}
	}.reg();
	
	public static final CmdArg<Object[]> VAR_PATTERN = (CmdArg<Object[]>) CmdArg.combine(VARIABLE, PATTERN);
	
	public static final CmdArg<VarSet> VAR_SET = new CmdArg<VarSet>("VarName Value", VarSet.class)
	{
		@Override
		public boolean rawToken(int ind)
		{
			return ind == 0;
		}
		
		@Override
		public int tokenCount()
		{
			return 2;
		}
		
		@Override
		public VarSet parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			String v;
			ScajlVariable s;
			v = vars[off].val(ctx);
			s = vars[off + 1];
			if (v == null || s == null)
				return null;
			return new VarSet(v, s);
		}
	}.reg();
	
	public static final CmdArg<BoolVarSet> BOOL_VAR_SET = new CmdArg<BoolVarSet>("Boolean VarName Value", BoolVarSet.class)
	{
		@Override
		public boolean rawToken(int ind)
		{
			return ind == 1;
		}
		
		@Override
		public int tokenCount()
		{
			return 3;
		}
		
		@Override
		public BoolVarSet parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Boolean b;
			String v;
			ScajlVariable s;
			b = BOOLEAN.parse(vars, off, ctx);
			v = TOKEN.parse(vars, off + 1, ctx);
			s = vars[off + 1];
			if (b == null || v == null || s == null)
				return null;
			return new BoolVarSet(b, v, s);
		}
	}.reg();
	
	public static final CmdArg<IntVarSet> INT_VAR_SET = new CmdArg<IntVarSet>("Integer VarName Value", IntVarSet.class)
	{
		@Override
		public boolean rawToken(int ind)
		{
			return ind == 1;
		}
		
		@Override
		public int tokenCount()
		{
			return 3;
		}
		
		@Override
		public IntVarSet parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Integer i;
			String v;
			ScajlVariable s;
			i = INT.parse(vars, off, ctx);
			v = TOKEN.parse(vars, off + 1, ctx);
			s = vars[off + 2];
			if (i == null || v == null || s == null)
				return null;
			return new IntVarSet(i, v, s);
		}
	}.reg();
	
	public static CmdArg<Object[]> combine(CmdArg<?>... args)
	{
		String name = "";
		int count = args.length;
		
		for (CmdArg<?> arg : args)
			name += arg.type + " ";
		name = name.substring(0, name.length() - 1);
		IntPredicate rawToken = (ind) ->
		{
			for (int i = 0; i < args.length; i++)
			{
				if (ind < args[i].tokenCount())
					return args[i].rawToken(ind);
				ind -= args[i].tokenCount();
			}
			return false;

		};
		return new CmdArg<Object[]>(name, Object[].class)
				{
					@Override
					public boolean rawToken(int ind)
					{
						return rawToken.test(ind);
					}
					
					@Override
					public int tokenCount()
					{
						return count;
					}
			
					@Override
					public Object[] parse(ScajlVariable[] vars, int off, Scajl ctx)
					{
						Object[] out = new Object[count];
						for (int i = off; i < count;)
						{
							out[i - off] = args[i - off].parse(vars, off + i, ctx);
							i += args[i - off].tokenCount();
						}
						if (ArrayUtils.contains(out, null))
							return null;
						return out;
					}
				};
	}

	public static final CmdArg<IntVarSet> VAR_INT_SET = new CmdArg<IntVarSet>("VarName Integer Value", IntVarSet.class)
	{
		@Override
		public boolean rawToken(int ind)
		{
			return ind == 0;
		}
		
		@Override
		public int tokenCount()
		{
			return 3;
		}
		
		@Override
		public IntVarSet parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			Integer i;
			String v;
			ScajlVariable s;
			v = TOKEN.parse(vars, off, ctx);
			i = INT.parse(vars, off + 1, ctx);
			s = vars[off + 2];
			if (i == null || v == null || s == null)
				return null;
			return new IntVarSet(i, v, s);
		}
	};
	
	public static final CmdArg<VarIntTok> VAR_INT_TOK = new CmdArg<VarIntTok>("VarName Integer Token", VarIntTok.class)
	{
		@Override
		public boolean rawToken(int ind)
		{
			return ind == 0;
		}
		
		@Override
		public int tokenCount()
		{
			return 3;
		}
		
		@Override
		public VarIntTok parse(ScajlVariable[] vars, int off, Scajl ctx)
		{
			String v;
			Integer i;
			String t;
			v = TOKEN.parse(vars, 0, ctx);
			i = INT.parse(vars, 1, ctx);
			t = TOKEN.parse(vars, 2, ctx);
			if (i == null || v == null || t == null)
				return null;
			return new VarIntTok(v, i, t);
		}
	}.reg();
	
	public static final CmdArg<int[]> INT_ARR = arrayOfPrimitives(int[].class);
	public static final CmdArg<short[]> SHORT_ARR = arrayOfPrimitives(short[].class);
	public static final CmdArg<byte[]> BYTE_ARR = arrayOfPrimitives(byte[].class);
	public static final CmdArg<double[]> DOUB_ARR = arrayOfPrimitives(double[].class);
	public static final CmdArg<float[]> FLOAT_ARR = arrayOfPrimitives(float[].class);
	public static final CmdArg<boolean[]> BOOL_ARR = arrayOfPrimitives(boolean[].class);
	public static final CmdArg<char[]> CHAR_ARR = arrayOfPrimitives(char[].class);
	
	////////////////////////////////////////
	
	public static <X> CmdArg<X> funcInterfaceOf(ScriptObject<X> scajlType, FITransformer<X> transformer)
	{
		return funcInterfaceOf(scajlType.getTypeName(), scajlType.argOf().cls, transformer);
	}
	public static <X> CmdArg<X> funcInterfaceOf(Class<X> funcInt, FITransformer<X> transformer)
	{
		return funcInterfaceOf(funcInt.getSimpleName(), funcInt, transformer);
	}
	/**
	 * @param <X>
	 * @param typeName
	 * @param funcInt The Class of the functional interface to be exposed.
	 * @param transformer An {@linkplain FITransformer} to produce a valid X function.
	 * @return New, registerd CmdArg.
	 * 
	 * Example usage:
<pre> 
...

&#64;FunctionalInterface
public static interface Pos3dVal
{
   public double val(Pos3d pos);
}

...

CmdArg.funcInterfaceOf(Pos3dVal.class, (matching) -> (pos) -> (double) matching.run(pos));
</pre> 
	 */
	public static <X> CmdArg<X> funcInterfaceOf(String typeName, Class<X> funcInt, FITransformer<X> transformer)
	{
		Method[] methods = funcInt.getMethods();
		int absCount = 0;
		Method abs = null;
		for (Method method : methods)
			if (Modifier.isAbstract(method.getModifiers()) && MapUtils.getFirst(OBJECT_METHODS.iterator(), (objM) ->
			{
				Parameter[] m = null, o = null;
				boolean match = method.getName().equals(objM.getName())
						&& method.getReturnType().equals(objM.getReturnType())
						&& (m = method.getParameters()).length == (o = objM.getParameters()).length;
				if (match)
					for (int i = 0; match && i < m.length; i++)
						match = m[i].getType().equals(o[i].getType());
				return match;
			}) == null)
			{
				abs = method;
				absCount++;
			}
		if (!funcInt.isInterface() || absCount != 1)
			throw new IllegalArgumentException("Class specified for funcInterfaceOf is not a functional interface: " + funcInt.getCanonicalName());
		final Method fiM = abs;
		Parameter[] fiParams = fiM.getParameters();
		Class<?> retType = fiM.getReturnType();
		if (retType.isPrimitive())
			retType = wrap(retType);
		CmdArg<?> retArg = getArgFor(retType);
		boolean isVoid = retType.equals(Void.class);
		if (retArg == null && !isVoid)
			throw new IllegalStateException("Unable to produce functional interface CmdArg due to missing CmdArg for return type: " + retType.getCanonicalName());
		int pCount = fiParams.length;
		Class<?>[] paramTypes = new Class<?>[pCount];
		TypeArg<?>[] paramArgs = new TypeArg<?>[pCount];
		String[] paramNames = new String[pCount];
		for (int i = 0; i < pCount; i++)
		{
			paramTypes[i] = fiParams[i].getType();
			paramArgs[i] = getTypeArgFor(paramTypes[i]);
			if (paramArgs[i] == null || paramArgs[i].arg == null)
				throw new IllegalStateException("Unable to produce functional interface CmdArg due to missing CmdArg for parameter type: " + paramTypes[i].getCanonicalName());
			paramNames[i] = fiParams[i].getName().toUpperCase();
		}
		
		typeName += "(";
		for (int i = 0; i < pCount; i++)
			typeName += "'" + paramArgs[i].arg.type + "':" + paramNames[i] + (i == pCount - 1 ? " " : ", ");
		typeName += "-> " + retArg.type + ")";
		
		CmdArg<X> arg = new CmdArg<X>(typeName, funcInt)
		{
			@Override
			public X parse(ScajlVariable[] vars, int off, Scajl ctx)
			{
				SVExec exec = SVEXEC.parse(vars, off, ctx);
				return transformer.transform((objs) ->
				{
					if (objs.length != pCount)
						throw new IllegalArgumentException("Args provided in FITransformer do not match the requested functional interface method signature.");
					VarSet[] sets = new VarSet[pCount];
					for (int i = 0; i < objs.length; i++)
						sets[i] = new VarSet(paramNames[i], paramArgs[i].castAndUnparse(objs[i], ctx));
					
					for (VarSet set : sets)
						ctx.putVar(set.var, set.set);
					exec.eval(ctx);
					
					if (isVoid)
						return null;
					ScajlVariable[] outVars = new ScajlVariable[] { ctx.prev() };
					Object out;
					out = retArg.parse(outVars, 0, ctx);
					if (out == null)
						ctx.parseExcept("Invalid return value: " + outVars[0].raw(), "The functional interface expects a return of type: " + retArg.type);
					return out;
				});
			}
			
			@Override
			public ScajlVariable unparse(X obj)
			{
				return Scajl.objOf(obj);
			};
		}.reg();
		
		return arg;
	}
	@FunctionalInterface
	public static interface FIMatcher
	{
		public Object run(Object... objs);
	}
	/**
	 * @param fromMatchingSignature
	 * @return An X of format: <p><code>(Xarg1, Xarg2, Xarg3...) -> (Xreturn) fromMatchingSignature.run(Xarg1, Xarg2, Xarg3...)</code>
	 */
	@FunctionalInterface
	public static interface FITransformer<X>
	{
		/**
		 * @param fromMatchingSignature
		 * @return An X of format: <p><code>(Xarg1, Xarg2, Xarg3...) -> (Xreturn) fromMatchingSignature.run(Xarg1, Xarg2, Xarg3...)</code>
		 */
		public X transform(FIMatcher fromMatchingSignature);
	}
	
	@SuppressWarnings("unchecked")
	public static <X> PrefCmdArg<X> prefixedOf(CmdArg<X> arg, String prefix)
	{
		LinkedHashMap<Integer, CmdArg<?>> bin = ARGS.get(arg.cls);
		int tc = arg.tokenCount() + 1;
		PrefCmdArg<X> pref = null;
		if (bin != null)
			pref = (PrefCmdArg<X>) bin.get(tc);
		
		if (pref == null)
		{
			pref = new PrefCmdArg<X>(prefix + " " + arg.type, arg.cls)
			{
				@Override
				public ScajlVariable unparse(X obj, Scajl ctx)
				{
					return arg.unparse(obj, ctx);
				}
			};
			pref.add(prefix, arg).reg();
		}
		else
			pref.add(prefix, arg);
		return pref;
	}
	
	public static <T> CmdArg<T> inlineOf(ObjConstruct<T> construct, CmdArg<T> original, CmdArg<?>... args)
	{
		int tokenCount = 0;
		String format = "";
		for (CmdArg<?> arg : args)
		{
			tokenCount += arg.tokenCount();
			format += arg.type + " ";
		}
		format.trim();
		final int tc = tokenCount;
		
		CmdArg<T> inline = new CmdArg<T>(format, original.cls)
		{
			@Override
			public int tokenCount()
			{
				return tc;
			}
		
			@Override
			public boolean rawToken(int ind)
			{
				for (int i = 0; i < args.length; i++)
				{
					if (ind < args[i].tokenCount())
						return args[i].rawToken(ind);
					ind -= args[i].tokenCount();
				}
				return false;
			}
			
			@Override
			public T parse(ScajlVariable[] vars, int off, Scajl ctx)
			{
				Object[] objs = new Object[args.length];
				int t = 0;
				for (int a = 0; a < args.length; a++)
				{
					objs[a] = args[a].parse(vars, t, ctx);
					t += args[a].tokenCount();
					if (objs[a] == null)
						return null;
				}
				
				return construct.construct(objs);
			}
			
			@Override
			public ScajlVariable unparse(T obj, Scajl ctx)
			{
				return original.unparse(obj, ctx);
			}
		};
		
		return inline.reg();
	}
	
	private static <X> CmdArg<X> unwrappedOf(CmdArg<?> wrappedArg, Class<X> toPrim)
	{
		return new CmdArg<X>(toPrim.getSimpleName(), toPrim)
		{
			@SuppressWarnings("unchecked")
			@Override
			public X parse(ScajlVariable[] vars, int off, Scajl ctx)
			{
				Object obj = wrappedArg.parse(vars, off, ctx);
				if (obj == null)
					return null;
				return (X) obj;
			}
		}.reg();
	}
	
	private static <X> CmdArg<X> arrayOfPrimitives(Class<X> primArray)
	{
		Class<?> prim = primArray.getComponentType();
		Class<?> wrapped = WRAP_PRIMITIVE.get(prim);
		CmdArg<?> wrappedArg = getArgFor(wrapped);
		@SuppressWarnings("unchecked")
		CmdArg<X> array = new CmdArg<X>(Scajl.ARR_S + prim.getSimpleName() + Scajl.ARR_E, primArray)
		{
			@Override
			public X parse(ScajlVariable[] vars, int off, Scajl ctx)
			{
				if (!(vars[off] instanceof SVArray))
					return null;
				SVArray array = (SVArray) vars[off];
				ScajlVariable[] elements = array.getArray();
				X arr = (X) Array.newInstance(prim, elements.length);
				for (int i = 0; i < elements.length; i++)
				{
					Object val = parseArrayElement(wrappedArg, elements[i].eval(ctx), array.noUnpack, ctx);
					if (val == null)
						return null;
					Array.set(arr, i, val);
				}
				return arr;
			}
			
			@Override
			public ScajlVariable unparse(X obj)
			{
				int len = Array.getLength(obj);
				ScajlVariable[] elements = new ScajlVariable[len];
				for (int i = 0; i < len; i++)
					elements[i] = Scajl.valOf("" + Array.get(obj, i));
				return Scajl.arrOf(elements);
			};
		}.reg();
		return array;
	}
	
	@SuppressWarnings("unchecked")
	public static <X> CmdArg<X[]> arrayOf(CmdArg<X> arg)
	{
		Class<X[]> arrClass = (Class<X[]>) Array.newInstance(arg.cls, 0).getClass();
		LinkedHashMap<Integer, CmdArg<?>> arrayBin = ARGS.get(arrClass);
		
		CmdArg<X[]> array = arrayBin == null ? null : (CmdArg<X[]>) arrayBin.get(1);
		if (array == null)
		{
			array = new CmdArg<X[]>(Scajl.ARR_S + arg.type + Scajl.ARR_E, arrClass)
			{
				@Override
				public X[] parse(ScajlVariable[] vars, int off, Scajl ctx)
				{
					if (!(vars[off] instanceof SVArray))
						return null;
					SVArray array = (SVArray) vars[off];
					ScajlVariable[] elements = array.getArray();
					int xLen = elements.length;
					
					X[] arr = (X[]) Array.newInstance(arg.cls, xLen);
					for (int i = 0; i < elements.length; i++)
					{
						ScajlVariable elm = elements[i].eval(ctx);
						X val = parseArrayElement(arg, elm, array.noUnpack, ctx);
						if (val == null)
							return null;
						arr[i] = val;
					}
					return arr;
				}
				
				@Override
				public ScajlVariable unparse(X[] obj, Scajl ctx)
				{
					ScajlVariable[] elements = new ScajlVariable[obj.length];
					for (int i = 0; i < obj.length; i++)
						elements[i] = arg.unparse(obj[i], ctx);
					return Scajl.arrOf(elements);
				}
			}.reg();
		}
		
		return array;
	}
	private static <X> X parseArrayElement(CmdArg<X> arg, ScajlVariable elm, boolean noUnpack, Scajl ctx)
	{
		ScajlVariable[] eVars;
		if (!noUnpack && elm instanceof SVTokGroup  && !((SVTokGroup) elm).noUnpack && !arg.cls.isAssignableFrom(SVTokGroup.class))
			eVars = ((SVTokGroup) elm).getArray();
		else
			eVars = new ScajlVariable[] { elm };
		CmdArg<X> elmArg = getArgForCount(arg, eVars.length);
		if (elmArg == null)
		{
			ctx.parseExcept("Invalid array token resolution", "The format '" + arg.type + "' requires " + arg.tokenCount() + " tokens, but " + eVars.length + " have been provided. Tokens are separated by spaces, and automatically unpacked when parsing an Array unless that Array was declared with the " + Scajl.NO_UNPACK + " modifier.");
			return null;
		}
		X val = elmArg.parse(eVars, 0, ctx);
		if (val == null)
			ctx.parseExcept("Invalid Array element resolution", "Expected type '" + elmArg.type + "'", "Input tokens: " + StringUtils.toString(eVars, (v) -> v.toString(), "'", " ", "'"));
		return val;
	}
}
