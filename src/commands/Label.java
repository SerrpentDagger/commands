package commands;

public class Label
{
	public final int line;
	public final boolean isScoped;
	public final String name;
	
	public Label(String name, int line, boolean scoped)
	{
		this.name = name;
		this.line = line;
		this.isScoped = scoped;
	}
}
