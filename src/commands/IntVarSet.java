package commands;

public class IntVarSet extends VarSet
{
	public final int i;

	public IntVarSet(int i, String var, CmdString set)
	{
		super(var, set);
		this.i = i;
	}
	
}
