package commands;

public class CmdString
{
	public final String raw;
	public final String unraw;
	
	public CmdString(String raw)
	{
		this.raw = raw;
		unraw = Script.stringTrim(raw);
	}
}
