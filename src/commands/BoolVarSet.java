package commands;

public class BoolVarSet extends VarSet
{
	public final boolean bool;

	public BoolVarSet(boolean bool, String var, CmdString set)
	{
		super(var, set);
		this.bool = bool;
	}
	
}
