package commands.libs;

import java.util.Arrays;
import java.util.Comparator;

public abstract class Array
{
	private Array() {}
	
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
