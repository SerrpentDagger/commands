package commands;

import java.awt.Color;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

import commands.BooleanExp.Comp;
import commands.DoubleExp.Oper;
import utilities.ArrayUtils;

public abstract class CmdArg<T>
{
	static final HashMap<Class<?>, LinkedHashMap<Integer, CmdArg<?>>> ARGS = new HashMap<>();
	private static final HashMap<Class<?>, Class<?>> WRAP_PRIMITIVE = new HashMap<>();
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
				CmdArg<?> componentArg = getArgFor(cls.componentType());
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
		public String castAndUnparse(Object obj) { return arg.unparse((TY) obj); }
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
			rt[j] = (this.rawToken(j) ? Script.RAW : "") + rt[j];
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
	
	public abstract T parse(String trimmed, String[] tokens, Script ctx);
	public T parse(String[] tokens, ScajlVariable[] vars, int off, Script ctx)
	{
		return parse(tokens, off, ctx);
	}
	public T parse(String[] tokens, int off, Script ctx)
	{
		String str = "";
		int count = tokenCount();
		String[] toks = new String[count];
		for (int i = 0; i < count; i++)
		{
			str += tokens[off + i] + (i == count - 1 ? "" : " ");
			toks[i] = tokens[off + i];
		}
		return parse(str, toks, ctx);
	}
	public T parse(String trimmed)
	{
		if (tokenCount() > 1)
			throw new IllegalStateException("Attempt to parse multitoken arg without providing token array.");
		return parse(trimmed, null, null);
	}
	
	public String unparse(T obj)
	{
		return obj.toString();
	}
	
	/////////////////////////
	
	public static abstract class VarCmdArg<T> extends CmdArg<T>
	{
		public VarCmdArg(String type, Class<T> cls)
		{
			super(type, cls);
		}

		@Override
		public String getInfoString()
		{
			return super.getInfoString() + "*";
		}
		
		@Override
		public T parse(String trimmed, String[] tokens, Script ctx)
		{
			throw new UnsupportedOperationException("This VarCmdArg cannot parse without access to the ScajlVariable sources.");
		}
		
		@Override
		public abstract T parse(String[] tokens, ScajlVariable[] vars, int off, Script ctx);
		
		@Override
		public VarCmdArg<T> reg()
		{
			return (VarCmdArg<T>) super.reg();
		}
	}
	
	public static abstract class PrefCmdArg<T> extends CmdArg<T>
	{
		private final HashMap<String, CmdArg<T>> prefixes = new HashMap<>();
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
		public T parse(String trimmed, String[] tokens, Script ctx)
		{
			CmdArg<T> arg = prefixes.get(tokens[0].toUpperCase());
			if (arg == null)
				return null;
			return arg.parse(tokens, 1, ctx);
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
		public Library parse(String trimmed, String[] tokens, Script ctx)
		{
			return Script.getLibrary(trimmed);
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
		public Object parse(String trimmed, String[] tokens, Script ctx)
		{
			if (!Script.isType(tokens[1]))
				return null;
			ScriptObject<?> so = Script.getType(tokens[1]);
			return so.getObject(tokens[0]);
		}
		
		@Override
		public String unparse(Object obj)
		{
			TypeArg<?> typeArg = getTypeArgFor(obj.getClass());
			if (typeArg.arg == null || typeArg.arg == this)
				return Script.NULL;
			return typeArg.castAndUnparse(obj);
		}
	}.reg();

	public static final CmdArg<Color> COLOR = new CmdArg<Color>("Red Green Blue", Color.class)
	{
		@Override
		public int tokenCount()
		{
			return 3;
		}
		
		@Override
		public Color parse(String trimmed, String[] tokens, Script ctx)
		{
			Double r, g, b;
			r = DOUBLE.parse(tokens, 0, ctx);
			g = DOUBLE.parse(tokens, 1, ctx);
			b = DOUBLE.parse(tokens, 2, ctx);
			if (r == null || g == null || b == null)
				return null;
			return new Color(r.floatValue(), g.floatValue(), b.floatValue());
		}
		
		@Override
		public String unparse(Color obj)
		{
			return Script.tokenize(obj.getRed(), obj.getGreen(), obj.getBlue());
		};
	}.reg();
	
	public static final CmdArg<Long> LONG = new CmdArg<Long>("Long", Long.class)
	{
		@Override
		public Long parse(String trimmed, String[] tokens, Script ctx)
		{
			try
			{
				return Long.parseLong(trimmed);
			}
			catch (NumberFormatException e)
			{}
			return null;
		}
	}.reg();
	
	public static final CmdArg<Integer> INT = new CmdArg<Integer>("Integer", Integer.class)
	{
		@Override
		public Integer parse(String trimmed, String[] tokens, Script ctx)
		{
			try
			{
				return Math.round(Math.round(Double.parseDouble(trimmed)));
			}
			catch (NumberFormatException e)
			{}
			return null;
		}
	}.reg();
	
	public static final CmdArg<Byte> BYTE = new CmdArg<Byte>("Byte", Byte.class)
	{
		@Override
		public Byte parse(String trimmed, String[] tokens, Script ctx)
		{
			Integer i = INT.parse(trimmed, tokens, ctx);
			return i == null ? null : (byte) (int) i;
		}
	}.reg();
	
	public static final CmdArg<Short> SHORT = new CmdArg<Short>("Short", Short.class)
	{
		@Override
		public Short parse(String trimmed, String[] tokens, Script ctx)
		{
			Integer i = INT.parse(trimmed, tokens, ctx);
			return i == null ? null : (short) (int) i;
		}
	}.reg();
	
	public static final CmdArg<Double> DOUBLE_POSITIVE = new CmdArg<Double>("PositiveDouble", Double.class)
	{
		@Override
		public Double parse(String trimmed, String[] tokens, Script ctx)
		{
			Double doub = DOUBLE.parse(trimmed, tokens, ctx);
			if (doub != null && doub >= 0)
				return doub;
			return null;
		}
	};
	
	public static final CmdArg<Double> DOUBLE = new CmdArg<Double>("Double", Double.class)
	{	
		@Override
		public Double parse(String trimmed, String[] tokens, Script ctx)
		{
			try
			{
				return Double.parseDouble(trimmed);
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
		public Double parse(String trimmed, String[] tokens, Script ctx)
		{
			Double a = DOUBLE.parse(tokens, 0, ctx);
			Double b = DOUBLE.parse(tokens, 2, ctx);
			Oper op = DoubleExp.Oper.parse(tokens[1]);
			if (ArrayUtils.contains(ArrayUtils.of(a, b, op), null))
				return null;
			return op.eval(a, b);
		}
	}.reg();
	
	public static final CmdArg<Float> FLOAT = new CmdArg<Float>("Float", Float.class)
	{	
		@Override
		public Float parse(String trimmed, String[] tokens, Script ctx)
		{
			Double d = DOUBLE.parse(trimmed, tokens, ctx);
			return d == null ? null : (float) (double) d;
		}
	}.reg();
	
	public static final CmdArg<Character> CHARACTER = new CmdArg<Character>("Character", Character.class)
	{
		@Override
		public Character parse(String trimmed, String[] tokens, Script ctx)
		{
			if (trimmed.length() > 1)
				return null;
			return trimmed.charAt(0);
		}
	}.reg();
	
	public static final CmdArg<String> TOKEN = new CmdArg<String>("Token", String.class)
	{
		@Override
		public String parse(String trimmed, String[] tokens, Script ctx)
		{
			if (tokens.length > 1)
				return null;
			return tokens[0];
		}
	};
	
	public static final CmdArg<String> TYPE = new CmdArg<String>("Type", String.class)
	{
		@Override
		public String parse(String trimmed, String[] tokens, Script ctx)
		{
			String type = TOKEN.parse(trimmed, tokens, ctx);
			if (Script.isType(type))
				return type;
			return null;
		}
	};
	
	@SuppressWarnings("rawtypes")
	public static final CmdArg<ObjectType> SCRIPT_OBJECT = new CmdArg<ObjectType>("Object Type", ObjectType.class)
	{
		@Override
		public int tokenCount()
		{
			return 2;
		}
		
		@Override
		public ObjectType<?> parse(String trimmed, String[] tokens, Script ctx)
		{
			if (!Script.isType(tokens[1]))
				return null;
			ScriptObject<?> so = Script.getType(tokens[1]);
			return so.getObjectType(tokens[0]);
		}
	};
	
	public static final CmdArg<String> STRING = new CmdArg<String>("String", String.class)
	{
		@Override
		public String parse(String trimmed, String[] tokens, Script ctx)
		{
			return trimmed;
		}
		
		@Override
		public String unparse(String obj)
		{
			return Script.STRING_CHAR + obj + Script.STRING_CHAR;
		};
	}.reg();
	
	public static final CmdArg<Boolean> BOOLEAN = new CmdArg<Boolean>("Boolean", Boolean.class)
	{
		@Override
		public Boolean parse(String trimmed, String[] tokens, Script ctx)
		{
			switch (trimmed)
			{
				case "true":
					return true;
				case "false":
					return false;
				default:
					return null;
			}
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
		public BooleanThen parse(String trimmed, String[] tokens, Script ctx)
		{
			BooleanThen b = new BooleanThen(BOOLEAN.parse(tokens, 0, ctx), TOKEN.parse(tokens, 1, ctx));
			return b;
		}
		
		@Override
		public String unparse(BooleanThen obj)
		{
			return Script.tokenize("" + obj.bool, obj.then);
		};
	}.reg();
	
	public static final CmdArg<BooleanExp> BOOLEAN_EXP = new CmdArg<BooleanExp>("Double Comparator Double", BooleanExp.class)
	{
		@Override
		public int tokenCount()
		{
			return 3;
		}
		
		@Override
		public BooleanExp parse(String trimmed, String[] tokens, Script ctx)
		{
			try
			{
				double a = Double.parseDouble(tokens[0]);
				Comp comp = Comp.parse(tokens[1].toUpperCase());
				if (comp == null)
					return null;
				double b = Double.parseDouble(tokens[2]);
				return new BooleanExp(a, b, comp);
			}
			catch (NumberFormatException e)
			{}
			return null;
		}
		
		@Override
		public String unparse(BooleanExp obj)
		{
			return Script.tokenize("" + obj.a, obj.comp.display, "" + obj.b);
		};
	}.reg();
	
	public static final VarCmdArg<VarSet> VAR_SET = new VarCmdArg<VarSet>("VarName Value", VarSet.class)
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
		public VarSet parse(String[] tokens, ScajlVariable[] vars, int off, Script ctx)
		{
			String v;
			ScajlVariable s;
			v = TOKEN.parse(tokens, off, ctx);
			s = vars[off + 1];
			if (v == null || s == null)
				return null;
			return new VarSet(v, s);
		}
		
		@Override
		public String unparse(VarSet obj)
		{
			return Script.tokenize(obj.var, obj.set.raw());
		};
	}.reg();
	
	public static final VarCmdArg<BoolVarSet> BOOL_VAR_SET = new VarCmdArg<BoolVarSet>("Boolean VarName Value", BoolVarSet.class)
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
		public BoolVarSet parse(String[] tokens, ScajlVariable[] vars, int off, Script ctx)
		{
			Boolean b;
			String v;
			ScajlVariable s;
			b = BOOLEAN.parse(tokens, off, ctx);
			v = TOKEN.parse(tokens, off + 1, ctx);
			s = vars[off + 1];
			if (b == null || v == null || s == null)
				return null;
			return new BoolVarSet(b, v, s);
		}
		
		@Override
		public String unparse(BoolVarSet obj)
		{
			return Script.tokenize("" + obj.bool, obj.var, obj.set.raw());
		}
	}.reg();
	
	public static final VarCmdArg<IntVarSet> INT_VAR_SET = new VarCmdArg<IntVarSet>("Integer VarName Value", IntVarSet.class)
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
		public IntVarSet parse(String[] tokens, ScajlVariable[] vars, int off, Script ctx)
		{
			Integer i;
			String v;
			ScajlVariable s;
			i = INT.parse(tokens, off, ctx);
			v = TOKEN.parse(tokens, off + 1, ctx);
			s = vars[off + 2];
			if (i == null || v == null || s == null)
				return null;
			return new IntVarSet(i, v, s);
		}
		
		@Override
		public String unparse(IntVarSet obj)
		{
			return Script.tokenize("" + obj.i, obj.var, obj.set.raw());
		};
	}.reg();

	public static final VarCmdArg<IntVarSet> VAR_INT_SET = new VarCmdArg<IntVarSet>("VarName Integer Value", IntVarSet.class)
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
		public IntVarSet parse(String[] tokens, ScajlVariable[] vars, int off, Script ctx)
		{
			Integer i;
			String v;
			ScajlVariable s;
			v = TOKEN.parse(tokens, off, ctx);
			i = INT.parse(tokens, off + 1, ctx);
			s = vars[off + 2];
			if (i == null || v == null || s == null)
				return null;
			return new IntVarSet(i, v, s);
		}
		
		@Override
		public String unparse(IntVarSet obj)
		{
			return Script.tokenize(obj.var, "" + obj.i, obj.set.raw());
		};
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
		public VarIntTok parse(String trimmed, String[] tokens, Script ctx)
		{
			String v;
			Integer i;
			String t;
			v = TOKEN.parse(tokens, 0, ctx);
			i = INT.parse(tokens, 1, ctx);
			t = TOKEN.parse(tokens, 2, ctx);
			if (i == null || v == null || t == null)
				return null;
			return new VarIntTok(v, i, t);
		}
		
		@Override
		public String unparse(VarIntTok obj)
		{
			return Script.tokenize(obj.var, "" + obj.i, obj.tok);
		};
	}.reg();
	
	public static final CmdArg<int[]> INT_ARR = arrayOfPrimitives(int[].class);
	public static final CmdArg<short[]> SHORT_ARR = arrayOfPrimitives(short[].class);
	public static final CmdArg<byte[]> BYTE_ARR = arrayOfPrimitives(byte[].class);
	public static final CmdArg<double[]> DOUB_ARR = arrayOfPrimitives(double[].class);
	public static final CmdArg<float[]> FLOAT_ARR = arrayOfPrimitives(float[].class);
	public static final CmdArg<boolean[]> BOOL_ARR = arrayOfPrimitives(boolean[].class);
	public static final CmdArg<char[]> CHAR_ARR = arrayOfPrimitives(char[].class);
	
	////////////////////////////////////////
	
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
				public String unparse(X obj)
				{
					return arg.unparse(obj);
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
			public T parse(String trimmed, String[] tokens, Script ctx)
			{
				Object[] objs = new Object[args.length];
				int t = 0;
				for (int a = 0; a < args.length; a++)
				{
					String trmd = "";
					for (int i = 0; i < args[a].tokenCount(); i++)
						trmd += tokens[t + i] + (i == args[a].tokenCount() - 1 ? "" : " ");
					t += args[a].tokenCount();
					objs[a] = args[a].parse(trmd);
					if (objs[a] == null)
						return null;
				}
				
				return construct.construct(objs);
			}
			
			@Override
			public String unparse(T obj)
			{
				return original.unparse(obj);
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
			public X parse(String trimmed, String[] tokens, Script ctx)
			{
				Object obj = wrappedArg.parse(trimmed, tokens, ctx);
				if (obj == null)
					return null;
				return (X) obj;
			}
		}.reg();
	}
	
	private static <X> CmdArg<X> arrayOfPrimitives(Class<X> primArray)
	{
		Class<?> prim = primArray.componentType();
		Class<?> wrapped = WRAP_PRIMITIVE.get(prim);
		CmdArg<?> wrappedArg = getArgFor(wrapped);
		@SuppressWarnings("unchecked")
		CmdArg<X> array = new CmdArg<X>(Script.ARR_S + prim.getSimpleName() + Script.ARR_E, primArray)
		{
			@Override
			public X parse(String trimmed, String[] tokns, Script ctx)
			{
				if (!trimmed.startsWith("" + Script.ARR_S) || !trimmed.endsWith("" + Script.ARR_E))
					return null;
				String[] tokens = Script.arrayElementsOf(trimmed);
				X arr = (X) Array.newInstance(prim, tokens.length);
				for (int i = 0; i < tokens.length; i++)
					Array.set(arr, i, wrappedArg.parse(tokens[i].trim(), null, ctx));
				return arr;
			}
			
			@Override
			public String unparse(X obj)
			{
				String str = "" + Script.ARR_S;
				int len = Array.getLength(obj);
				for (int i = 0; i < len; i++)
					str += Array.get(obj, i) + (i == len - 1 ? "" : Script.ARR_SEP + " ");
				return str + Script.ARR_E;
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
			array = new CmdArg<X[]>(Script.ARR_S + arg.type + Script.ARR_E, arrClass)
			{
				//TODO: Array should be able to parse using differen CmdArgs.
				@Override
				public X[] parse(String trimmed, String[] tokens, Script ctx)
				{
					if (!trimmed.startsWith("" + Script.ARR_S) || !trimmed.endsWith("" + Script.ARR_E))
						return null;
					int count = arg.tokenCount();
					if (tokens.length % count != 0)
						return null;
					
					X first;
					first = arg.parse(tokens, 0, ctx);
					
					if (first == null)
						return null;
					
					int xLen = tokens.length / count;
					X[] arr = (X[]) Array.newInstance(first.getClass(), xLen);
					arr[0] = first;
					for (int i = count; i < tokens.length; i += count)
					{
						X val = arg.parse(tokens, i, ctx);
						if (val == null)
							return null;
						arr[i / count] = val;
					}
					return arr;
				}
				
				@Override
				public String unparse(X[] obj)
				{
					String[] elements = new String[obj.length];
					for (int i = 0; i < obj.length; i++)
						elements[i] = arg.unparse(obj[i]);
					return Script.toArrayString(elements);
				}
			}.reg();
		}
		
		return array;
	}
}
