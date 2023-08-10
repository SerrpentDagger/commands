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

import annotations.Desc;
import annotations.ScajlClone;

@Desc("A simple container for numbers that can be stored in Java objects like ArrayLists.")
public class Num extends Number implements ScajlClone<Num>
{
	private static final long serialVersionUID = 1L;
	private double val;
	public Num(double val)
	{
		this.val = val;
	}
	
	public Num add(Num other)
	{
		val += other.val;
		return this;
	}
	
	public Num sub(Num other)
	{
		val -= other.val;
		return this;
	}
	
	public Num mult(Num other)
	{
		val *= other.val;
		return this;
	}
	
	public Num divi(Num other)
	{
		val /= other.val;
		return this;
	}
	
	public Num set(double val)
	{
		this.val = val;
		return this;
	}
	
	public double get()
	{
		return val;
	}
	
	public Num operate(Num other, Operation op)
	{
		val = op.op(val, other.val);
		return this;
	}
	
	public static interface Operation
	{
		public double op(double a, double b);
	}

	@Override
	public int intValue()
	{
		return (int) val;
	}

	@Override
	public long longValue()
	{
		return (long) val;
	}

	@Override
	public float floatValue()
	{
		return (float) val;
	}

	@Override
	public double doubleValue()
	{
		return val;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		return (obj != null) && (obj instanceof Number) && ((Number) obj).doubleValue() == val;
	}
	
	@Override
	public String toString()
	{
		return Double.toString(val);
	}

	@Override
	public Num sjClone()
	{
		return new Num(val);
	}
}
