package commands;

import commands.Label.LabelTree;

public class StackEntry
{
	public final int from;
	public final LabelTree to;
	
	public StackEntry(int from, LabelTree to)
	{
		this.from = from;
		this.to = to;
	}
}
