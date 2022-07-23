package commands;

public class IntVarSet extends VarSet
{
	public final int i;

	public IntVarSet(int i, String var, ScajlVariable set)
	{
		super(var, set);
		this.i = i;
	}
	
}
