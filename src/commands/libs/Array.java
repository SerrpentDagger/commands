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

import java.util.Arrays;
import java.util.Comparator;

import annotations.Desc;
import commands.ScajlVariable;
import utilities.ArrayUtils;

@Desc("A utility class to provide functions that deal with arrays.")
public abstract class Array
{
	private Array() {}
	
	@Desc("Sort the array with the given comparator and return the result.")
	public static ScajlVariable[] sort(ScajlVariable[] arr, Comparator<ScajlVariable> comp)
	{
		Arrays.sort(arr, comp);
		return arr;
	}
	
	public static double[] sort(double[] arr)
	{
		Arrays.sort(arr);
		return arr;
	}

	public static String[] sort(String[] arr)
	{
		Arrays.sort(arr);
		return arr;
	}
	
	@Desc("Flip the ordering of the given array and return the result.")
	public static ScajlVariable[] flip(ScajlVariable[] arr)
	{
		ScajlVariable[] out = new ScajlVariable[arr.length];
		for (int i = 0; i < arr.length; i++)
			out[out.length - 1 - i] = arr[i];
		return out;
	}
	
	public static ScajlVariable[] append(ScajlVariable[] arr, ScajlVariable val)
	{
		return ArrayUtils.append(arr, val);
	}
}
