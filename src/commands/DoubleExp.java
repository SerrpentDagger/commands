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

package commands;

public class DoubleExp
{
	public final double a, b;
	public final Oper op;
	
	public DoubleExp(double a, double b, Oper op)
	{
		this.a = a;
		this.b = b;
		this.op = op;
	}
	
	public double eval()
	{
		return op.eval(a, b);
	}
	
	public static enum Oper
	{
		ADD("+", (a, b) -> a + b),
		SUB("-", (a, b) -> a - b),
		MULT("*", (a, b) -> a * b),
		DIVI("/", (a, b) -> a / b),
		POW("**", (a, b) -> Math.pow(a, b)),
		ROOT("/*", (a, b) -> Math.pow(a, 1d / b)),
		MOD("%", (a, b) -> a % b);
		
		////////////////
		
		public final DoubOp op;
		public final String display;
		Oper(String display, DoubOp op)
		{
			this.display = display;
			this.op = op;
		}
		
		public double eval(double a, double b)
		{
			return op.eval(a, b);
		}
		
		public static Oper parse(String str)
		{
			for (Oper op : Oper.values())
				if (op.display.equals(str))
					return op;
			return null;
		}
		
		//////////////
		
		private static interface DoubOp
		{
			double eval(double a, double b);
		}
	}
}
