package commands;

public class StackEntry
{
	public final int from;
	public final int to;
	public final boolean returns;
	
	public StackEntry(int from, int to, boolean returns)
	{
		this.from = from;
		this.to = to;
		this.returns = returns;
	}
}
