package commands.libs;

import java.util.Random;

import commands.Script;

public class BuiltInLibs
{
	public static void load()
	{
		Script.add("Math", () -> Script.expose(Math.class, true));
		Script.add("Random", () -> Script.expose(Random.class, true));
	}
}
