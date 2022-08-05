package commands.libs;

public class Num extends Number
{
/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private double val;
	public Num(double val)
	{
		this.val = val;
	}
	
	public Num add(Num other)
	{
		val += other.val;
		return this;
	}
	
	public Num sub(Num other)
	{
		val -= other.val;
		return this;
	}
	
	public Num mult(Num other)
	{
		val *= other.val;
		return this;
	}
	
	public Num divi(Num other)
	{
		val /= other.val;
		return this;
	}
	
	public Num set(double val)
	{
		this.val = val;
		return this;
	}
	
	public double get()
	{
		return val;
	}
	
	public Num operate(Num other, Operation op)
	{
		val = op.op(val, other.val);
		return this;
	}
	
	public static interface Operation
	{
		public double op(double a, double b);
	}

	@Override
	public int intValue()
	{
		return (int) val;
	}

	@Override
	public long longValue()
	{
		return (long) val;
	}

	@Override
	public float floatValue()
	{
		return (float) val;
	}

	@Override
	public double doubleValue()
	{
		return val;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		return (obj != null) && (obj instanceof Number) && ((Number) obj).doubleValue() == val;
	}
	
	@Override
	public String toString()
	{
		return Double.toString(val);
	}
}
