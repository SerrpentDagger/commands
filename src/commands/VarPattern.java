package commands;

public class VarPattern
{
	public final ScajlVariable var, pattern;
	public final String name;
	
	public VarPattern(ScajlVariable var, ScajlVariable pattern, String name)
	{
		this.var = var;
		this.pattern = pattern;
		if (Script.ILLEGAL_VAR_MATCHER.matcher(name).matches())
			this.name = null;
		else
			this.name = name;
	}
}
