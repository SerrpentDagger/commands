/**
 * This file is part of Scajl, which is a scripting language for Java applications.
 * Copyright (c) 2023, SerpentDagger (MRRH) <serpentdagger.contact@gmail.com>.
 * 
 * Scajl is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 * 
 * Scajl is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with Scajl.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package commands.libs;

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

import annotations.ScajlClone;
import commands.CmdArg;
import commands.Scajl;
import commands.Script;
import commands.ScriptObject;

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
		
	
		Scajl.expose(Object.class, false);
		Class<?>[] toExp = new Class[]
		{
			Integer.class,
			Double.class,
			String.class
		};
		Scajl.exposeAll(toExp, Scajl.SAFE_CLASS_EXPOSE_FILTER,
				(member, rec) -> member instanceof Field && Scajl.SAFE_EXPOSE_FILTER.test(member, rec), false);
		
		ScriptObject<Num> num = Scajl.expose(Num.class, true);
		num.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> new Num((Double) objs[0]), num.argOf(), CmdArg.DOUBLE), "N"));
		
		ScriptObject<Bool> bool = Scajl.expose(Bool.class, true);
		bool.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> new Bool((Boolean) objs[0]), bool.argOf(), CmdArg.BOOLEAN), "B"));
		
		ScriptObject<Str> str = Scajl.expose(Str.class, true);
		str.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> new Str((String) objs[0]), str.argOf(), CmdArg.STRING), "S"));
		
		ScriptObject<Complex> complex = Scajl.expose(Complex.class, true);
		complex.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> Complex.ofABi(0, (Double) objs[0]), complex.argOf(), CmdArg.DOUBLE), "Imaj")
				.add("Real", CmdArg.inlineOf((objs) -> Complex.ofABi((Double) objs[0], 0), complex.argOf(), CmdArg.DOUBLE)));
		complex.add(CmdArg.prefixedOf(CmdArg.inlineOf((objs) -> Complex.ofABi((Double) objs[0], (Double) objs[1]), complex.argOf(), CmdArg.DOUBLE, CmdArg.DOUBLE), "ReIm")
				.add("MgAn", CmdArg.inlineOf((objs) -> Complex.ofRTheta((Double) objs[0], (Double) objs[1]), complex.argOf(), CmdArg.DOUBLE, CmdArg.DOUBLE)));
		
		Scajl.add("Math", () -> Scajl.expose(Math.class, true));
		Scajl.add("Random", () -> Scajl.expose(Random.class, true));
		Scajl.add("String", () ->
		{
			Scajl.expose(Matcher.class, true);
			Scajl.expose(Pattern.class, true);
			Scajl.expose(String.class, false);
		});
		Scajl.add("Numbers", () ->
		{
			Scajl.expose(Integer.class, false);
			Scajl.expose(Double.class, false);
			Scajl.expose(BigInteger.class, true);
			ScajlClone.reg(BigInteger.class, (bi) -> new BigInteger(bi.toByteArray()));
			Scajl.expose(BigDecimal.class, true);
			ScajlClone.reg(BigDecimal.class, (bd) -> new BigDecimal(bd.unscaledValue(), bd.scale()));
		});
		Scajl.add("Array", () -> Scajl.expose(Array.class, true));
/*		Scajl.add("JBuilder", () -> 
		{
			CmdArg.funcInterfaceOf(OnClose.class, (match) -> (b) -> match.run(b));
			ScriptObject<?>[] exp = Scajl.exposeDeclaredBy
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
			Scajl.exposeMethodsByName(JComponent.class, exp[exp.length - 1], false, "setFont");
		});*/
//		Scajl.add("Timer", () -> Scajl.expose(Timer.class, true));
/*		Scajl.add("Multithread", () ->
		{
			CmdArg.funcInterfaceOf(ThreadFill.class, (match) -> (th) -> match.run(th));
			CmdArg.funcInterfaceOf(ByThread.class, (match) -> (ind) -> match.run(ind));
			Scajl.expose(Parallelizer.class, false);
		});*/
		
		Scajl.expose(Script.class, false);
	}
}
