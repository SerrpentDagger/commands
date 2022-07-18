package commands.libs;

public class Bool
{
	private boolean val = false;
	
	public Bool(boolean val)
	{
		this.val = val;
	}
	
	public Bool and(Bool other)
	{
		val = val && other.val;
		return this;
	}
	
	public Bool or(Bool other)
	{
		val = val || other.val;
		return this;
	}
	
	public Bool xor(Bool other)
	{
		val = val ^ other.val;
		return this;
	}
	
	public Bool not()
	{
		val = !val;
		return this;
	}
	
	public Bool neither(Bool other)
	{
		val = !(val || other.val);
		return this;
	}
	
	public Bool set(boolean in)
	{
		val = in;
		return this;
	}
	
	public boolean get()
	{
		return val;
	}
}
