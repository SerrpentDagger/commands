package commands.libs;

import annotations.Desc;
import annotations.ScajlClone;
import utilities.StringUtils;

@Desc("A simple container for Strings that can be stored in Java objects like ArrayLists.")
public class Str implements ScajlClone<Str>
{
	public String val;
	
	public Str()
	{
		val = "";
	}
	
	public Str(String val)
	{
		this.val = val;
	}
	
	public Str(Str other)
	{
		this.val = other.val;
	}
	
	public Str add(String other)
	{
		val += other;
		return this;
	}
	
	public Str add(Str other)
	{
		val += other.val;
		return this;
	}
	
	public Str set(String to)
	{
		val = to;
		return this;
	}
	
	public Str mult(double by)
	{
		val = StringUtils.mult(val, by);
		return this;
	}
	
	public Str divi(double by)
	{
		val = StringUtils.divi(val, by);
		return this;
	}
	
	public Str flip()
	{
		val = StringUtils.flip(val);
		return this;
	}
	
	public Str padTo(int length)
	{
		val = StringUtils.padTo(val, length);
		return this;
	}
	
	public String get()
	{
		return val;
	}

	@Override
	public Str sjClone()
	{
		return new Str(val);
	}
}
