package commands.libs;

import java.util.Arrays;
import java.util.Comparator;

import annotations.Desc;

@Desc("A utility class to provide functions that deal with arrays.")
public abstract class Array
{
	private Array() {}
	
	@Desc("Sort the array with the given comparator and return the result.")
	public static <T> T[] sort(T[] arr, Comparator<T> comp)
	{
		Arrays.sort(arr, comp);
		return arr;
	}
	
	public static double[] sort(double[] arr)
	{
		Arrays.sort(arr);
		return arr;
	}
	
	public static int[] sort(int[] arr)
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
	public static Object[] flip(Object[] arr)
	{
		Object[] out = new Object[arr.length];
		for (int i = 0; i < arr.length; i++)
			out[out.length - 1 - i] = arr[i];
		return out;
	}
	
	public static String[] flip(String[] arr)
	{
		String[] out = new String[arr.length];
		for (int i = 0; i < arr.length; i++)
			out[out.length - 1 - i] = arr[i];
		return out;
	}
}
