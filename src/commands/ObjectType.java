package commands;

public class ObjectType<T>
{
	public final T obj;
	public final ScriptObject<T> type;
	
	public ObjectType(T obj, ScriptObject<T> type)
	{
		this.obj = obj;
		this.type = type;
	}
}
