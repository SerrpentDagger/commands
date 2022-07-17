package commands;

public class StackEntry
{
	public final int from;
	public final Label to;
	
	public StackEntry(int from, Label to)
	{
		this.from = from;
		this.to = to;
	}
}
