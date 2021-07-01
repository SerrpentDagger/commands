package commands;

import java.awt.Color;
import java.lang.reflect.Array;

import commands.BooleanExp.Comp;

public abstract class CmdArg<T>
{
	public final String type;
	public CmdArg(String type)
	{
		this.type = type;
	}
	
	public boolean rawToken(int ind) { return false; }
	public int tokenCount() { return 1; }
	public abstract T parse(String trimmed);
	public T parse(String[] tokens, int off)
	{
		String str = "";
		for (int i = 0; i < tokenCount(); i++)
		{
			str += tokens[off + i] + " ";
		}
		return parse(str.trim());
	}
	
	///////////////////////
	
	public static final CmdArg<Command> COMMAND = new CmdArg<Command>("Command")
	{
		@Override
		public Command parse(String trimmed)
		{
			return Script.get(trimmed);
		}
	};
	
	public static final CmdArg<Color> COLOR = new CmdArg<Color>("Color")
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
	
	public static final CmdArg<Integer> INT = new CmdArg<Integer>("Integer")
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
	
	public static final CmdArg<Double> DOUBLE = new CmdArg<Double>("Double")
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
	
	public static final CmdArg<String> TOKEN = new CmdArg<String>("Token")
	{
		@Override
		public String parse(String trimmed)
		{
			return trimmed;
		}
	};
	
	public static final CmdArg<String> STRING = new CmdArg<String>("String")
	{
		@Override
		public int tokenCount()
		{
			return -2;
		}
		
		@Override
		public String parse(String trimmed)
		{
			return trimmed;
		}
	};
	
	public static final CmdArg<Boolean> BOOLEAN = new CmdArg<Boolean>("Boolean")
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
	
	public static final CmdArg<BooleanThen> BOOLEAN_THEN = new CmdArg<BooleanThen>("Boolean Then")
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
	
	public static final CmdArg<BooleanExp> BOOLEAN_EXP = new CmdArg<BooleanExp>("Double Comparator Double")
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
	
	public static final CmdArg<VarSet> VAR_SET = new CmdArg<VarSet>("VarName Token")
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
			String v, s;
			String[] tokens = Script.tokensOf(trimmed);
			v = TOKEN.parse(tokens, 0);
			s = TOKEN.parse(tokens, 1);
			if (v == null || s == null)
				return null;
			return new VarSet(v, s);
		}
	};
	
	////////////////////////////////////////
	
	public static <X> CmdArg<X[]> greedyArray(CmdArg<X> arg)
	{
		CmdArg<X[]> array = arrayOf(arg);
		CmdArg<X[]> greedy = new CmdArg<X[]>(arg.type + "...")
		{
			@Override
			public int tokenCount()
			{
				return -2;
			}
			
			@Override
			public X[] parse(String trimmed)
			{
				return array.parse(Script.ARR_S + trimmed + Script.ARR_E);
			}
		};
		
		return greedy;
	}
	
	public static <X> CmdArg<X[]> arrayOf(CmdArg<X> arg)
	{
		CmdArg<X[]> array = new CmdArg<X[]>(Script.ARR_S + arg.type + Script.ARR_E)
		{
			@Override
			public int tokenCount()
			{
				return -1;
			}
			
			@Override
			public X[] parse(String trimmed)
			{
				String str = trimmed.substring(1, trimmed.length() - 1);
				String[] tokens = Script.tokensOf(str);
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
