package commands.libs;

import java.util.Random;

import commands.CmdArg;
import commands.Script;
import commands.ScriptObject;

public class BuiltInLibs
{
	public static void load()
	{
		Script.expose(Object.class, false);
		
		ScriptObject<Num> num = Script.expose(Num.class, true);
		num.setDescription("A simple container for numbers that can be stored in Java objects like ArrayLists.");
		num.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> new Num((Double) objs[0]), num.argOf(), CmdArg.DOUBLE), "N"));
		
		ScriptObject<Bool> bool = Script.expose(Bool.class, true);
		bool.setDescription("A simple container for booleans that can be stored in Java objects like ArrayLists.");
		bool.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> new Bool((Boolean) objs[0]), bool.argOf(), CmdArg.BOOLEAN), "B"));
		
		ScriptObject<Str> str = Script.expose(Str.class, true);
		str.setDescription("A simple container for Strings that can be stored in Java objects like ArrayLists.");
		str.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> new Str((String) objs[0]), str.argOf(), CmdArg.STRING), "S"));
		
		Script.add("Math", () -> Script.expose(Math.class, true));
		Script.add("Random", () -> Script.expose(Random.class, true));
	}
}
