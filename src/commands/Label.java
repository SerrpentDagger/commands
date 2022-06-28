package commands;

import java.util.regex.Matcher;

public class Label
{
	public final int line;
	public final boolean isScoped, isAccessible, getsAccess;
	public final String name;
	
	public Label(String labelText, int line)
	{
		isScoped = labelText.startsWith(Script.SCOPED_LABEL);
		labelText = labelText.replaceFirst(Script.LABEL_REG, "").trim();
		boolean[] labelMods = Script.prefixModsFrom(labelText, Script.VALID_LABEL_MODS);
		isAccessible = labelMods[0];
		getsAccess = labelMods[1];
		Matcher match = Script.LABEL_MODS_PATTERN.matcher(labelText);
		name = match.replaceFirst("");
		this.line = line;
	}
	
	public Label(String name, int line, boolean scoped, boolean accessible, boolean getsAccess)
	{
		this.name = name;
		this.line = line;
		this.isScoped = scoped;
		this.isAccessible = accessible;
		this.getsAccess = getsAccess;
	}
}
