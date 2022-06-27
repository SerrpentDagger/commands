package commands;

public class StackEntry
{
	public final int from;
	public final Label to;
	public final boolean returns;
	
	public StackEntry(int from, Label to, boolean returns)
	{
		this.from = from;
		this.to = to;
		this.returns = returns;
	}
}
