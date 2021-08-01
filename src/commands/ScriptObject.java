package commands;

import java.util.HashMap;

public class ScriptObject<T extends Object>
{
	public final HashMap<String, T> objs = new HashMap<String, T>();
	
	private final String typeName;
	private final CmdArg<?>[] constArgs;
	private String description;
	private SOConstruct<T> constructor;
	
	private final CmdArg<T> cmdArg;
	
	////////////////////////////
	
	public ScriptObject(String typeName, String description, Class<T> cl, CmdArg<?>... constArgs)
	{
		this.typeName = typeName;
		this.description = description;
		this.constArgs = constArgs;
		
		cmdArg = new CmdArg<T>(typeName, cl)
		{
			@Override
			public T parse(String trimmed)
			{
				return objs.get(trimmed);
			}
		};
	}
	
	public void construct(String name, Object[] params)
	{
		objs.put(name, constructor.construct(params));
	}
	
	///////////////////////////
	
	public ScriptObject<T> setConstructor(SOConstruct<T> construct)
	{
		this.constructor = construct;
		return this;
	}
	
	public String getDescription()
	{
		return description;
	}
	
	public String getTypeName()
	{
		return typeName;
	}
	
	public String getCommandName()
	{
		return typeName.toLowerCase();
	}
	
	public CmdArg<?>[] getConstArgs()
	{
		return constArgs;
	}
	
	public CmdArg<T> argOf()
	{
		return cmdArg;
	}
	
	///////////////////////
	
	public interface SOConstruct<T>
	{
		public T construct(Object[] objs);
	}
}
