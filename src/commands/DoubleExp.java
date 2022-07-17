package commands;

public class DoubleExp
{
	public final double a, b;
	public final Oper op;
	
	public DoubleExp(double a, double b, Oper op)
	{
		this.a = a;
		this.b = b;
		this.op = op;
	}
	
	public double eval()
	{
		return op.eval(a, b);
	}
	
	public static enum Oper
	{
		ADD("+", (a, b) -> a + b),
		SUB("-", (a, b) -> a - b),
		MULT("*", (a, b) -> a * b),
		DIVI("/", (a, b) -> a / b),
		POW("**", (a, b) -> Math.pow(a, b)),
		ROOT("/*", (a, b) -> Math.pow(a, 1d / b)),
		MOD("%", (a, b) -> a % b);
		
		////////////////
		
		public final DoubOp op;
		public final String display;
		Oper(String display, DoubOp op)
		{
			this.display = display;
			this.op = op;
		}
		
		public double eval(double a, double b)
		{
			return op.eval(a, b);
		}
		
		public static Oper parse(String str)
		{
			for (Oper op : Oper.values())
				if (op.display.equals(str))
					return op;
			return null;
		}
		
		//////////////
		
		private static interface DoubOp
		{
			double eval(double a, double b);
		}
	}
}
