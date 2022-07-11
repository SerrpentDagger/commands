package commands;

public class BooleanExp
{
	public final double a, b;
	public final Comp comp;
	
	public BooleanExp(double a, double b, Comp comp)
	{
		this.a = a;
		this.b = b;
		this.comp = comp;
	}
	
	public boolean eval()
	{
		return comp.eval(a, b);
	}
	
	public static enum Comp
	{
		EQUAL("==", (a, b) -> a == b),
		NOT_EQUAL("!=", (a, b) -> a != b),
		LESS("<", (a, b) -> a < b),
		LESS_EQ("<=", (a, b) -> a <= b),
		MORE(">", (a, b) -> a > b),
		MORE_EQ(">=", (a, b) -> a >= b);
		
		////////////
		
		public final Bool bool;
		public final String display;
		Comp(String display, Bool bool)
		{
			this.display = display;
			this.bool = bool;
		}
		
		public boolean eval(double a, double b)
		{
			return bool.eval(a, b);
		}
		
		public static Comp parse(String str)
		{
			switch(str)
			{
				case "=":
					return EQUAL;
				case "==":
					return EQUAL;
				case "!=":
					return NOT_EQUAL;
				case "!":
					return NOT_EQUAL;
				case "<":
					return LESS;
				case "<=":
					return LESS_EQ;
				case ">":
					return MORE;
				case ">=":
					return MORE_EQ;
			}
			return null;
		}
		
		///////////
		
		private static interface Bool
		{
			boolean eval(double a, double b);
		}
	}
}
