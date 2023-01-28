package annotations;

import java.util.HashMap;

public interface ScajlClone<T>
{
	public T sjClone();

	////////////////
	
	@NoExpose
	static final HashMap<Class<?>, ScajlCloner<?>> REGISTRY = new HashMap<>();
	
	public static boolean isSC(Class<?> cls)
	{
		return REGISTRY.get(cls) != null || ScajlClone.class.isAssignableFrom(cls);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T tryClone(T obj)
	{
		if (obj instanceof ScajlClone<?>)
			return (T) ((ScajlClone<?>) obj).sjClone();
		ScajlCloner<T> cloner = (ScajlCloner<T>) REGISTRY.get(obj.getClass());
		if (cloner != null)
			return cloner.sjClone(obj);
		return obj;
	}
	
	public static <T> void reg(Class<T> cls, ScajlCloner<T> cloner)
	{
		REGISTRY.put(cls, cloner);
	}
	
	public static void unsup(String name)
	{
		throw new UnsupportedOperationException('\'' + name + "' is unsupported on given Variable(s).");
	}
	
	@FunctionalInterface
	public static interface ScajlCloner<T>
	{
		public T sjClone(T obj);
	}
}
