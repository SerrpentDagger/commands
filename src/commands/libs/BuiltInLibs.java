package commands.libs;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
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
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import annotations.ScajlClone;
import commands.CmdArg;
import commands.Script;
import commands.ScriptObject;
import jbuilder.JBuilder;
import jbuilder.JBuilder.OnClose;
import main.Timer;
import utilities.Parallelizer;
import utilities.Parallelizer.ByThread;
import utilities.Parallelizer.ThreadFill;

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
		Script.exposeAll(toExp, Script.SAFE_CLASS_EXPOSE_FILTER,
				(member, rec) -> member instanceof Field && Script.SAFE_EXPOSE_FILTER.test(member, rec), false);
		
		ScriptObject<Num> num = Script.expose(Num.class, true);
		num.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> new Num((Double) objs[0]), num.argOf(), CmdArg.DOUBLE), "N"));
		
		ScriptObject<Bool> bool = Script.expose(Bool.class, true);
		bool.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> new Bool((Boolean) objs[0]), bool.argOf(), CmdArg.BOOLEAN), "B"));
		
		ScriptObject<Str> str = Script.expose(Str.class, true);
		str.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> new Str((String) objs[0]), str.argOf(), CmdArg.STRING), "S"));
		
		ScriptObject<Complex> complex = Script.expose(Complex.class, true);
		complex.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> Complex.ofABi(0, (Double) objs[0]), complex.argOf(), CmdArg.DOUBLE), "Imaj")
				.add("Real", CmdArg.inlineOf((objs) -> Complex.ofABi((Double) objs[0], 0), complex.argOf(), CmdArg.DOUBLE)));
		complex.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> Complex.ofABi((Double) objs[0], (Double) objs[1]), complex.argOf(), CmdArg.DOUBLE, CmdArg.DOUBLE), "ReIm")
				.add("MgAn", CmdArg.inlineOf((objs) -> Complex.ofRTheta((Double) objs[0], (Double) objs[1]), complex.argOf(), CmdArg.DOUBLE, CmdArg.DOUBLE)));
		
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
			Script.expose(BigInteger.class, true);
			ScajlClone.reg(BigInteger.class, (bi) -> new BigInteger(bi.toByteArray()));
			Script.expose(BigDecimal.class, true);
			ScajlClone.reg(BigDecimal.class, (bd) -> new BigDecimal(bd.unscaledValue(), bd.scale()));
		});
		Script.add("Array", () -> Script.expose(Array.class, true));
		Script.add("JBuilder", () -> 
		{
			CmdArg.funcInterfaceOf(OnClose.class, (match) -> (b) -> match.run(b));
			ScriptObject<?>[] exp = Script.exposeDeclaredBy
			(
					Font.class,
					Component.class,
					JTextField.class,
					JButton.class,
					JToggleButton.class,
					JComboBox.class,
					FlowLayout.class,
					GridLayout.class,
					JBuilder.class
			);
			Script.exposeMethodsByName(JComponent.class, exp[exp.length - 1], false, "setFont");
		});
		Script.add("Timer", () -> Script.expose(Timer.class, true));
		Script.add("Multithread", () ->
		{
			CmdArg.funcInterfaceOf(ThreadFill.class, (match) -> (th) -> match.run(th));
			CmdArg.funcInterfaceOf(ByThread.class, (match) -> (ind) -> match.run(ind));
			Script.expose(Parallelizer.class, false);
		});
	}
}
