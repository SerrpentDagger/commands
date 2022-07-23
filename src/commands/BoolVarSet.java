package commands;

public class BoolVarSet extends VarSet
{
	public final boolean bool;

	public BoolVarSet(boolean bool, String var, ScajlVariable set)
	{
		super(var, set);
		this.bool = bool;
	}
	
}
