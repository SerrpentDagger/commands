package commands.libs;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import commands.CmdArg;
import commands.Script;
import commands.ScriptObject;
import jbuilder.JBuilder;
import main.Timer;

public class BuiltInLibs
{
	public static void load()
	{
		CmdArg.funcInterfaceOf(Runnable.class, (match) -> () -> match.run());
		CmdArg.funcInterfaceOf(Function.class, (match) -> (a) -> match.run(a));
		CmdArg.funcInterfaceOf(IntFunction.class, (match) -> (a) -> match.run(a));
		CmdArg.funcInterfaceOf(BiFunction.class, (match) -> (a, b) -> match.run(a, b));
		CmdArg.funcInterfaceOf(Comparator.class, (match) -> (a, b) -> (int) match.run(a, b));
		CmdArg.funcInterfaceOf(Supplier.class, (match) -> () -> match.run());
		CmdArg.funcInterfaceOf(Consumer.class, (match) -> (a) -> match.run(a));
		CmdArg.funcInterfaceOf(BiConsumer.class, (match) -> (a, b) -> match.run(a, b));
		CmdArg.funcInterfaceOf(Predicate.class, (match) -> (a) -> (boolean) match.run(a));
		CmdArg.funcInterfaceOf(BiPredicate.class, (match) -> (a, b) -> (boolean) match.run(a, b));
		CmdArg.funcInterfaceOf(BinaryOperator.class, (match) -> (a, b) -> match.run(a, b));
	
		Script.expose(Object.class, false);
		Class<?>[] toExp = new Class[]
		{
			Integer.class,
			Double.class,
			String.class
		};
//		Script.exposeAll(toExp, Script.SAFE_CLASS_EXPOSE_FILTER,
//				(member, rec) -> member instanceof Field && Script.SAFE_EXPOSE_FILTER.test(member, rec), false);
		
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
		Script.add("String", () ->
		{
			Script.expose(Matcher.class, true);
			Script.expose(Pattern.class, true);
			Script.expose(String.class, false);
		});
		Script.add("Numbers", () ->
		{
			Script.expose(Integer.class, false);
			Script.expose(Double.class, false);
		});
		Script.add("Array", () -> Script.expose(Array.class, true));
		Script.add("JBuilder", () -> 
		{
			Script.exposeDeclaredBy
			(
					Component.class,
					JTextField.class,
					JButton.class,
					JToggleButton.class,
					JComboBox.class,
					FlowLayout.class,
					GridLayout.class,
					JBuilder.class
			);
		});
		Script.add("Timer", () -> Script.expose(Timer.class, true));
	}
}
