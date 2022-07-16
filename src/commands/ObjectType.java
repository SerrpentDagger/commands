package commands;

public class ObjectType<T>
{
	public final T obj;
	public final ScriptObject<T> type;
	private String key;
	
	public ObjectType(T obj, ScriptObject<T> type, String key)
	{
		this.obj = obj;
		this.type = type;
		this.key = key;
	}
	
	public boolean destroy()
	{
		return type.objs.remove(key) != null;
	}
	
	public void moveTo(String newKey)
	{
		destroy();
		type.objs.put(newKey, obj);
		key = newKey;
	}
	
	public String key()
	{
		return key;
	}
}
