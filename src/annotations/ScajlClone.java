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
