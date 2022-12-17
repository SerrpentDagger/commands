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
