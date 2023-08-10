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
import utilities.StringUtils;

@Desc("A simple container for Strings that can be stored in Java objects like ArrayLists.")
public class Str implements ScajlClone<Str>
{
	public String val;
	
	public Str()
	{
		val = "";
	}
	
	public Str(String val)
	{
		this.val = val;
	}
	
	public Str(Str other)
	{
		this.val = other.val;
	}
	
	public Str add(String other)
	{
		val += other;
		return this;
	}
	
	public Str add(Str other)
	{
		val += other.val;
		return this;
	}
	
	public Str set(String to)
	{
		val = to;
		return this;
	}
	
	public Str mult(double by)
	{
		val = StringUtils.mult(val, by);
		return this;
	}
	
	public Str divi(double by)
	{
		val = StringUtils.divi(val, by);
		return this;
	}
	
	public Str flip()
	{
		val = StringUtils.flip(val);
		return this;
	}
	
	public Str padTo(int length)
	{
		val = StringUtils.padTo(val, length);
		return this;
	}
	
	public String get()
	{
		return val;
	}

	@Override
	public Str sjClone()
	{
		return new Str(val);
	}
	
	@Override
	public String toString()
	{
		return val;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		return obj instanceof Str && val.equals(((Str) obj).val);
	}
}
