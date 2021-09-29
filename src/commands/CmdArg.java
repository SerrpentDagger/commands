package commands;

import java.awt.Color;
import java.lang.reflect.Array;

import commands.BooleanExp.Comp;

public abstract class CmdArg<T>
{
	public final String type;
	public final Class<T> cls;
	public CmdArg(String type, Class<T> cls)
	{
		this.type = type;
		this.cls = cls;
		if (tokenCount() < 0)
			throw new IllegalStateException("Only positive token counts can be parsed.");
		if (type.split(" ").length != tokenCount())
			throw new IllegalStateException("CmdArg type names should correspond with their token counts.");
	}
	
	public boolean rawToken(int ind) { return false; }
	public int tokenCount() { return 1; }
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
	
	///////////////////////
	
	public static final CmdArg<Command> COMMAND = new CmdArg<Command>("Command", Command.class)
	{
		@Override
		public Command parse(String trimmed)
		{
			return Script.get(trimmed);
		}
	};
	
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
	};
	
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
	};
	
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
	};
	
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
	
	public static final CmdArg<String> SCRIPT_OBJECT = new CmdArg<String>("Object", String.class)
	{
		@Override
		public String parse(String trimmed)
		{
			return TOKEN.parse(trimmed);
		}
	};
	
	public static final CmdArg<CmdString> STRING = new CmdArg<CmdString>("String", CmdString.class)
	{
		@Override
		public CmdString parse(String trimmed)
		{
			return new CmdString(trimmed);
		}
	};
	
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
	};
	
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
	};
	
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
	};
	
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
	};
	
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
	};
	
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
	};

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
	};
	
	////////////////////////////////////////
	
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
		};
		
		return array;
	}
}
