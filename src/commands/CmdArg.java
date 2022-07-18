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
	static final HashMap<Class<?>, LinkedHashMap<String, CmdArg<?>>> ARGS = new HashMap<>();
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
		LinkedHashMap<String, CmdArg<?>> bin = ARGS.get(toClass);
		if (bin != null)
		{
			if (!bin.containsKey(arg.regId))
				bin.put(arg.regId, arg);
		}
		else
		{
			LinkedHashMap<String, CmdArg<?>> mp = new LinkedHashMap<>();
			mp.put(arg.regId, arg);
			ARGS.put(toClass, mp);
		}
		return arg;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> CmdArg<T> getArgFor(Class<T> cls)
	{
		LinkedHashMap<String, CmdArg<?>> bin = ARGS.get(cls);
		if (bin == null || bin.isEmpty())
		{
			if (cls.isArray())
			{
				CmdArg<?> componentArg = getArgFor(cls.componentType());
				if (componentArg == null)
					return null;
				return (CmdArg<T>) arrayOf(componentArg).reg();
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
	private String regId = "";
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
	
	public CmdArg<T> reg() { return reg(regId); }
	public CmdArg<T> reg(int i) { return reg("" + i); }
	public CmdArg<T> reg(String regId)
	{
		this.regId = regId;
		reg(this, cls);
		return this;
	}
	public String getRegId() { return regId; }
	
	public abstract T parse(String trimmed);
	public T parse(String[] tokens, int off)
	{
		String str = "";
		int count = tokenCount();
		if (count > 0)
		{
			for (int i = 0; i < tokenCount(); i++)
			{
				str += tokens[off + i] + " ";
			}
		}
		else if (count == -1)
		{
			int arrCount = 0;
			for (int i = off; i < tokens.length && (i == off || arrCount > 0); i++)
			{
				arrCount += Script.arrPreFrom(tokens[i]).length();
				arrCount -= Script.arrPostFrom(tokens[i]).length();
				str += tokens[i];
			}
		}
		else if (count == -2)
		{
			for (int i = off; i < tokens.length; i++)
				str += tokens[i] + " ";
		}
		return parse(str.trim());
	}
	
	public String unparse(T obj)
	{
		return obj.toString();
	}
	
	///////////////////////
	
	public static final CmdArg<Library> LIBRARY = new CmdArg<Library>("Library", Library.class)
	{
		@Override
		public Library parse(String trimmed)
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
		public Object parse(String trimmed)
		{
			String[] tokens = Script.tokensOf(trimmed);
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
		public Color parse(String trimmed)
		{
			Double r, g, b;
			String[] tokens = Script.tokensOf(trimmed);
			r = DOUBLE.parse(tokens, 0);
			g = DOUBLE.parse(tokens, 1);
			b = DOUBLE.parse(tokens, 2);
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
		public Long parse(String trimmed)
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
		public Integer parse(String trimmed)
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
		public Byte parse(String trimmed)
		{
			Integer i = INT.parse(trimmed);
			return i == null ? null : (byte) (int) i;
		}
	}.reg();
	
	public static final CmdArg<Short> SHORT = new CmdArg<Short>("Short", Short.class)
	{
		@Override
		public Short parse(String trimmed)
		{
			Integer i = INT.parse(trimmed);
			return i == null ? null : (short) (int) i;
		}
	}.reg();
	
	public static final CmdArg<Double> DOUBLE_POSITIVE = new CmdArg<Double>("PositiveDouble", Double.class)
	{
		@Override
		public Double parse(String trimmed)
		{
			Double doub = DOUBLE.parse(trimmed);
			if (doub != null && doub >= 0)
				return doub;
			return null;
		}
	};
	
	public static final CmdArg<Double> DOUBLE = new CmdArg<Double>("Double", Double.class)
	{	
		@Override
		public Double parse(String trimmed)
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
		public Double parse(String trimmed)
		{
			String[] tokens = Script.tokensOf(trimmed);
			Double a = DOUBLE.parse(tokens[0]);
			Double b = DOUBLE.parse(tokens[2]);
			Oper op = DoubleExp.Oper.parse(tokens[1]);
			if (ArrayUtils.contains(ArrayUtils.of(a, b, op), null))
				return null;
			return op.eval(a, b);
		}
	}.reg(1);
	
	public static final CmdArg<Float> FLOAT = new CmdArg<Float>("Float", Float.class)
	{	
		@Override
		public Float parse(String trimmed)
		{
			Double d = DOUBLE.parse(trimmed);
			return d == null ? null : (float) (double) d;
		}
	}.reg();
	
	public static final CmdArg<Character> CHARACTER = new CmdArg<Character>("Character", Character.class)
	{
		@Override
		public Character parse(String trimmed)
		{
			CmdString str = new CmdString(trimmed);
			if (str.unraw.length() > 1)
				return null;
			return str.unraw.charAt(0);
		}
	}.reg();
	
	public static final CmdArg<String> TOKEN = new CmdArg<String>("Token", String.class)
	{
		@Override
		public String parse(String trimmed)
		{
			return trimmed;
		}
	};
	
	public static final CmdArg<String> TYPE = new CmdArg<String>("Type", String.class)
	{
		@Override
		public String parse(String trimmed)
		{
			String type = TOKEN.parse(trimmed);
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
		public ObjectType<?> parse(String trimmed)
		{
			String[] tokens = Script.tokensOf(trimmed);
			if (!Script.isType(tokens[1]))
				return null;
			ScriptObject<?> so = Script.getType(tokens[1]);
			return so.getObjectType(tokens[0]);
		}
	};
	
	public static final CmdArg<CmdString> STRING = new CmdArg<CmdString>("String", CmdString.class)
	{
		@Override
		public CmdString parse(String trimmed)
		{
			return new CmdString(trimmed);
		}
		
		@Override
		public String unparse(CmdString obj)
		{
			return obj.raw;
		};
	}.reg();
	
	public static final CmdArg<String> EXPOSED_STRING = new CmdArg<String>("String", String.class)
	{
		@Override
		public String parse(String trimmed)
		{
			return STRING.parse(trimmed).unraw;
		}
		
		@Override
		public String unparse(String obj)
		{
			return obj;
		}
	}.reg();
	
	public static final CmdArg<Boolean> BOOLEAN = new CmdArg<Boolean>("Boolean", Boolean.class)
	{
		@Override
		public Boolean parse(String trimmed)
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
		public BooleanThen parse(String trimmed)
		{
			String[] tokens = Script.tokensOf(trimmed);
			BooleanThen b = new BooleanThen(BOOLEAN.parse(tokens, 0), TOKEN.parse(tokens, 1));
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
		public BooleanExp parse(String trimmed)
		{
			try
			{
				String[] tokens = Script.tokensOf(trimmed);
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
		public VarSet parse(String trimmed)
		{
			String v;
			CmdString s;
			String[] tokens = Script.tokensOf(trimmed);
			v = TOKEN.parse(tokens, 0);
			s = STRING.parse(tokens, 1);
			if (v == null || s == null)
				return null;
			return new VarSet(v, s);
		}
		
		@Override
		public String unparse(VarSet obj)
		{
			return Script.tokenize(obj.var, obj.set.raw);
		};
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
		public BoolVarSet parse(String trimmed)
		{
			Boolean b;
			String v;
			CmdString s;
			String[] tokens = Script.tokensOf(trimmed);
			b = BOOLEAN.parse(tokens, 0);
			v = TOKEN.parse(tokens, 1);
			s = STRING.parse(tokens, 2);
			if (b == null || v == null || s == null)
				return null;
			return new BoolVarSet(b, v, s);
		}
		
		@Override
		public String unparse(BoolVarSet obj)
		{
			return Script.tokenize("" + obj.bool, obj.var, obj.set.raw);
		};
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
		public IntVarSet parse(String trimmed)
		{
			Integer i;
			String v;
			CmdString s;
			String[] tokens = Script.tokensOf(trimmed);
			i = INT.parse(tokens, 0);
			v = TOKEN.parse(tokens, 1);
			s = STRING.parse(tokens, 2);
			if (i == null || v == null || s == null)
				return null;
			return new IntVarSet(i, v, s);
		}
		
		@Override
		public String unparse(IntVarSet obj)
		{
			return Script.tokenize("" + obj.i, obj.var, obj.set.raw);
		};
	}.reg();

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
		public IntVarSet parse(String trimmed)
		{
			Integer i;
			String v;
			CmdString s;
			String[] tokens = Script.tokensOf(trimmed);
			v = TOKEN.parse(tokens, 0);
			i = INT.parse(tokens, 1);
			s = STRING.parse(tokens, 2);
			if (i == null || v == null || s == null)
				return null;
			return new IntVarSet(i, v, s);
		}
		
		@Override
		public String unparse(IntVarSet obj)
		{
			return Script.tokenize(obj.var, "" + obj.i, obj.set.raw);
		};
	}.reg(1);
	
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
		public VarIntTok parse(String trimmed)
		{
			String v;
			Integer i;
			String t;
			String[] tokens = Script.tokensOf(trimmed);
			v = TOKEN.parse(tokens, 0);
			i = INT.parse(tokens, 1);
			t = TOKEN.parse(tokens, 2);
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
	
	private static <X> CmdArg<X> unwrappedOf(CmdArg<?> wrappedArg, Class<X> toPrim)
	{
		return new CmdArg<X>(toPrim.getSimpleName(), toPrim)
		{
			@SuppressWarnings("unchecked")
			@Override
			public X parse(String trimmed)
			{
				Object obj = wrappedArg.parse(trimmed);
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
			public X parse(String trimmed)
			{
				if (!trimmed.startsWith("" + Script.ARR_S) || !trimmed.endsWith("" + Script.ARR_E))
					return null;
				String[] tokens = Script.arrayElementsOf(trimmed);
				X arr = (X) Array.newInstance(prim, tokens.length);
				for (int i = 0; i < tokens.length; i++)
					Array.set(arr, i, wrappedArg.parse(tokens[i].trim()));
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
	
	public static <X> CmdArg<X[]> arrayOf(CmdArg<X> arg)
	{
		@SuppressWarnings("unchecked")
		CmdArg<X[]> array = new CmdArg<X[]>(Script.ARR_S + arg.type + Script.ARR_E, (Class<X[]>) Array.newInstance(arg.cls, 0).getClass())
		{
			@Override
			public X[] parse(String trimmed)
			{
				if (!trimmed.startsWith("" + Script.ARR_S) || !trimmed.endsWith("" + Script.ARR_E))
					return null;
				String[] tokens = Script.arrayElementsOf(trimmed);
				int count = arg.tokenCount();
				if (tokens.length % count != 0)
					return null;
				
				X first;
				String trm = "";
				for (int i = 0; i < count; i++)
					trm += tokens[i] + " ";
				first = arg.parse(trm.trim());
				
				if (first == null)
					return null;
				
				int xLen = tokens.length / count;
				@SuppressWarnings("unchecked")
				X[] arr = (X[]) Array.newInstance(first.getClass(), xLen);
				arr[0] = first;
				for (int i = count; i < tokens.length; i += count)
				{
					trm = "";
					for (int j = i; j < i + count; j++)
						trm += tokens[j] + " ";
					X val = arg.parse(trm.trim());
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
		
		return array;
	}
}
