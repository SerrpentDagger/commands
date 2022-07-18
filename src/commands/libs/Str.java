package commands.libs;

public class Str
{
	public String val;
	
	public Str(String val)
	{
		this.val = val;
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
	
	public String get()
	{
		return val;
	}
}
