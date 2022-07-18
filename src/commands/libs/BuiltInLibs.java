package commands.libs;

import java.util.Random;

import commands.CmdArg;
import commands.CmdString;
import commands.Script;
import commands.ScriptObject;

public class BuiltInLibs
{
	public static void load()
	{
		Script.expose(Object.class, false);
		
		ScriptObject<Num> num = Script.expose(Num.class, true);
		num.setDescription("A simple container for numbers that can be stored in Java objects like ArrayLists.");
		num.add((objs) -> new Num((Double) objs[0]), CmdArg.DOUBLE);
		
		ScriptObject<Bool> bool = Script.expose(Bool.class, true);
		bool.setDescription("A simple container for booleans that can be stored in Java objects like ArrayLists.");
		bool.add((objs) -> new Bool((Boolean) objs[0]), CmdArg.BOOLEAN);
		
		ScriptObject<Str> str = Script.expose(Str.class, true);
		str.setDescription("A simple container for Strings that can be stored in Java objects like ArrayLists.");
		str.add((objs) -> new Str(((CmdString) objs[0]).unraw), CmdArg.STRING);
		
		Script.add("Math", () -> Script.expose(Math.class, true));
		Script.add("Random", () -> Script.expose(Random.class, true));
	}
}
