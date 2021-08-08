package commands;

import java.util.HashMap;

public class ScriptObject<T>
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
	
	public void construct(Script ctx, String name, Object[] params)
	{
		if (constructor == null)
			throw new IllegalStateException("Null constructor for ScriptObject of type: " + typeName);
		objs.put(name, constructor.construct(ctx, name, params));
	}
	
	//////////
	
	@SafeVarargs
	public static <SO> CmdArg<SO> supOf(String type, Class<SO> cl, ScriptObject<? extends SO>... exts)
	{
		return new CmdArg<SO>(type, cl)
		{
			@Override
			public SO parse(String trimmed)
			{
				SO obj;
				for (ScriptObject<? extends SO> ext : exts)
				{
					obj = ext.cmdArg.parse(trimmed);
					if (obj != null)
						return obj;
				}
				return null;
			}
		};
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
		public T construct(Script ctx, String name, Object[] objs);
	}
}
