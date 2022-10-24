package commands.libs;

import annotations.Desc;
import annotations.ScajlClone;

@Desc("A simple container for booleans that can be stored in Java objects like ArrayLists.")
public class Bool implements ScajlClone<Bool>
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

	@Override
	public Bool sjClone()
	{
		return new Bool(val);
	}
}
