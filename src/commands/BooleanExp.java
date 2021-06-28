package commands;

public class BooleanExp
{
	private final double a, b;
	private final Comp comp;
	
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
		EQUAL((a, b) -> a == b),
		LESS((a, b) -> a < b),
		LESS_EQ((a, b) -> a <= b),
		MORE((a, b) -> a > b),
		MORE_EQ((a, b) -> a >= b);
		
		////////////
		
		private Bool bool;
		Comp(Bool bool)
		{
			this.bool = bool;
		}
		
		public boolean eval(double a, double b)
		{
			return bool.eval(a, b);
		}
		
		///////////
		
		private static interface Bool
		{
			boolean eval(double a, double b);
		}
	}
}
