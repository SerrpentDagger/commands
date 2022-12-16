package commands;

public class Variable
{
	public final ScajlVariable var;
	public final String name;
	
	public Variable(ScajlVariable var, String name)
	{
		this.var = var;
		if (Scajl.ILLEGAL_VAR_MATCHER.matcher(name).matches())
			this.name = null;
		else
			this.name = name;
	}
}
