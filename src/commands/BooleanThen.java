package commands;

public class BooleanThen
{
	public final boolean bool;
	public final ScajlVariable then;
	
	public BooleanThen(boolean bool, ScajlVariable then)
	{
		this.bool = bool;
		this.then = then;
	}
}
