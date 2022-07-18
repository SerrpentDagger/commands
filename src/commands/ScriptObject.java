package commands;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import commands.Script.CommandParseException;
import mod.serpentdagger.artificialartificing.utils.group.MixedPair;
import utilities.StringUtils;

public class ScriptObject<T>
{
	public final HashMap<String, T> objs = new HashMap<String, T>();
	
	private final String typeName;
	private String description;
	private int unparseID = 0;
	
	private ScriptObject<? extends T> lastParsedType = null;
	private int argRegID = 0;
	private CmdArg<T> cmdArg;
	private CmdArg<T>[] inlineConst;
	private final HashSet<ScriptObject<? extends T>> subs = new HashSet<>();
	private final LinkedHashMap<String, Command> memberCmds = new LinkedHashMap<>();
	
	////////////////////////////
	
	@SuppressWarnings("unchecked")
	public ScriptObject(String typeName, String description, Class<T> cl)
	{
		this.typeName = typeName;
		this.description = description;
		final ScriptObject<T> tmp = this;
		inlineConst = (CmdArg<T>[]) Array.newInstance(CmdArg.class, 0);
		
		cmdArg = new CmdArg<T>(typeName, cl)
		{
			@Override
			public T parse(String trimmed)
			{
				lastParsedType = tmp;
				T obj = objs.get(trimmed), old = null;
				if (obj != null)
					return obj;
				for (ScriptObject<? extends T> sub : subs)
				{
					obj = sub.cmdArg.parse(trimmed);
					if (old != null && obj != null)
						throw new CommandParseException("The key '" + trimmed + "' exists in at least two subtypes of " + typeName);
					if (obj != null)
					{
						old = obj;
						lastParsedType = sub.lastParsedType;
					}
				}
				return old;
			}
			
			@Override
			public String unparse(T obj)
			{
				if (obj == null)
					return Script.NULL;
				if (objs.containsValue(obj))
				{
					for (Entry<String, T> ent : objs.entrySet())
						if (obj.equals(ent.getValue()))
								return ent.getKey();
				}
				String name = newObjKey();
				objs.put(name, obj);
				return name;
			};
		}.reg();
	}
	
	public String newObjKey()
	{
		return typeName + unparseID++;
	}
	
	public String newArgRegID()
	{
		return typeName + argRegID++;
	}
	
	public boolean isObject(String trimmed)
	{
		return cmdArg.parse(trimmed) != null;
	}
	
	public T getObject(String trimmed)
	{
		return cmdArg.parse(trimmed);
	}
	
	@SuppressWarnings("unchecked")
	public ObjectType<T> getObjectType(String trimmed)
	{
		T obj = cmdArg.parse(trimmed);
		return obj == null ? null : new ObjectType<T>(obj, (ScriptObject<T>) lastParsedType, trimmed);
	}
	
	public boolean destroy(String trimmed)
	{
		AtomicBoolean out = new AtomicBoolean(false);
		if (objs.remove(trimmed) != null)
			out.set(true);
		Iterator<ScriptObject<? extends T>> it = subs.iterator();
		while (!out.get() && it.hasNext())
			if (it.next().objs.remove(trimmed) != null)
				out.set(true);
		return out.get();
	}
	
	//////////
	
	public static <SO> ScriptObject<SO> supOf(String type, String description, Class<SO> cl, ScriptObject<? extends SO>[] subs)
	{
		ScriptObject<SO> sup = new ScriptObject<SO>(type, description, cl);
		for (ScriptObject<? extends SO> ext : subs)
			sup.subs.add(ext);
		return sup;
	}
	
	public static <SUP, SUB extends SUP> ScriptObject<SUB> subOf(String type, String description, Class<SUB> cl, ScriptObject<SUP> sup)
	{
		ScriptObject<SUB> sub = new ScriptObject<SUB>(type, description, cl);
		sup.subs.add(sub);
		return sub;
	}
	
	public static <SUP, SUB extends SUP> ScriptObject<SUB> makeSub(ScriptObject<SUB> sub, ScriptObject<SUP> sup)
	{
		sup.subs.add(sub);
		return sub;
	}
	
	public static <SUP> ScriptObject<SUP> makeSup(ScriptObject<SUP> sup, ScriptObject<? extends SUP>[] subs)
	{
		for (ScriptObject<? extends SUP> ext : subs)
			sup.subs.add(ext);
		return sup;
	}
	
	@SuppressWarnings("unchecked")
	public static <SUP, SUB extends SUP> MixedPair<ScriptObject<SUP>, ScriptObject<SUB>> castHirearchy(ScriptObject<?> sup, ScriptObject<?> sub)
	{
		return new MixedPair<>((ScriptObject<SUP>) sup, (ScriptObject<SUB>) sub);
	}
	
	///////////////////////////
	
	public Command add(String name, String ret, String desc, CmdArg<?>... args)
	{
		Command cmd = new Command(name, ret, desc, args);
		if (memberCmds.put(name, cmd) != null)
			throw new IllegalArgumentException("Cannot register two commands to the same name in the same type: " + typeName + Script.MEMBER_ACCESS + name);
		return cmd;
	}
	
	public CmdArg<T> add(SOConstruct<T> construct, CmdArg<?>... args)
	{
		int tokenCount = 0;
		String format = "";
		for (CmdArg<?> arg : args)
		{
			tokenCount += arg.tokenCount();
			format += arg.type + " ";
		}
		format.trim();
		final int tc = tokenCount;
		
		CmdArg<T> inline = new CmdArg<T>(format, cmdArg.cls)
		{
			@Override
			public int tokenCount()
			{
				return tc;
			}
		
			@Override
			public boolean rawToken(int ind)
			{
				for (int i = 0; i < args.length; i++)
				{
					if (ind < args[i].tokenCount())
						return args[i].rawToken(ind);
					ind -= args[i].tokenCount();
				}
				return false;
			}
			
			@Override
			public T parse(String trimmed)
			{
				Object[] objs = new Object[args.length];
				String[] tokens = Script.tokensOf(trimmed);
				int t = 0;
				for (int a = 0; a < args.length; a++)
				{
					String trmd = "";
					for (int i = 0; i < args[a].tokenCount(); i++)
						trmd += tokens[t + i] + (i == args[a].tokenCount() - 1 ? "" : " ");
					t += args[a].tokenCount();
					objs[a] = args[a].parse(trmd);
					if (objs[a] == null)
						return null;
				}
				
				return construct.construct(objs);
			}
			
			@Override
			public String unparse(T obj)
			{
				return cmdArg.unparse(obj);
			}
		};
		
		inlineConst = Arrays.copyOf(inlineConst, inlineConst.length + 1);
		inlineConst[inlineConst.length - 1] = inline;
		inline.reg(newArgRegID());
		return inline;
	}
	
	public String getDescription()
	{
		return description;
	}
	
	public Command[] getMemberCommands()
	{
		return memberCmds.values().toArray(new Command[memberCmds.size()]);
	}
	
	public LinkedHashMap<String, Command> getMemberCommandMap()
	{
		return memberCmds;
	}
	
	public String getTypeName()
	{
		return typeName;
	}
	
	public String getCommandName()
	{
		return typeName.toLowerCase();
	}
	
	public String getInfoString()
	{
		String inf = this.getTypeName() + " | Inline formats: '";
		inf += cmdArg.getInfoString();
		inf += inlineConst.length > 0 ? StringUtils.toString(inlineConst, (arg) -> arg.getInfoString(), "', '", "', '", "'") : "'";
		inf += ", Desc: " + this.getDescription();
		return inf;
	}
	
	public String hirearchyString()
	{
		return hirearchyString(0);
	}
	private String hirearchyString(int level)
	{
		String str = StringUtils.mult("   ", level) + "~ " +  getInfoString();
		if (subs.isEmpty())
			return str;
		Iterator<ScriptObject<? extends T>> it = subs.iterator();
		while (it.hasNext())
			str += "\n" + it.next().hirearchyString(level + 1);
		return str;
	}
	
	public CmdArg<T> argOf()
	{
		return cmdArg;
	}
	
	public <SUB extends T> ScriptObject<SUB> getSub(String subtype)
	{
		Iterator<ScriptObject<? extends T>> it = subs.iterator();
		while (it.hasNext())
		{
			@SuppressWarnings("unchecked")
			ScriptObject<SUB> n = (ScriptObject<SUB>) it.next();
			if (n.typeName.equals(subtype))
				return n;
		}
		return null;
	}
	
	public Command getMemberCmd(String name)
	{
		return memberCmds.get(name);
	}
	
	///////////////////////
	
	@FunctionalInterface
	public interface SOConstruct<T>
	{
		public T construct(Object[] objs);
	}
}
