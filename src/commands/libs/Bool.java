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

@Desc("A simple container for booleans that can be stored in Java objects like ArrayLists.")
public class Bool implements ScajlClone<Bool>
{
	private boolean val = false;
	
	public Bool(boolean val)
	{
		this.val = val;
	}
	
	public Bool and(Bool other)
	{
		val = val && other.val;
		return this;
	}
	
	public Bool or(Bool other)
	{
		val = val || other.val;
		return this;
	}
	
	public Bool xor(Bool other)
	{
		val = val ^ other.val;
		return this;
	}
	
	public Bool not()
	{
		val = !val;
		return this;
	}
	
	public Bool neither(Bool other)
	{
		val = !(val || other.val);
		return this;
	}
	
	public Bool set(boolean in)
	{
		val = in;
		return this;
	}
	
	public boolean get()
	{
		return val;
	}

	@Override
	public Bool sjClone()
	{
		return new Bool(val);
	}
}
