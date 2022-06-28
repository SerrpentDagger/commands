package commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public class Scope
{
	private final ArrayList<SNode> stack = new ArrayList<>();
	private SNode last = null;
	private final SNode global;
	
	///////////////////////
	
	public Scope()
	{
		stack.add(new SNode(null, Script.GLOBAL));
		last = stack.get(0);
		global = last;
	}
	
	///////////////////////
	
	public void forEach(ScopeConsumer sc)
	{
		AtomicInteger level = new AtomicInteger(0);
		stack.forEach((sNode) ->
		{
			sNode.vars.forEach((k, v) ->
			{
				sc.accept(level.get(), sNode.label, k, v);
			});
			level.incrementAndGet();
		});
	}
	
	public void integrateFrom(Scope other)
	{
		while (stack.size() < other.stack.size())
			push(other.stack.get(stack.size()).label);
		Iterator<SNode> it1 = stack.iterator();
		Iterator<SNode> it2 = other.stack.iterator();
		while (it1.hasNext())
		{
			SNode n1 = it1.next();
			SNode n2 = it2.next();

			n2.vars.forEach((var, val) ->
			{
				if (!var.equals(Script.PARENT))
					n1.put(var, val);
			});
		}
	}
	
	public void put(String name, String val)
	{
		last.put(name, val);
	}
	
	public void makeGlobal(String name)
	{
		String was = last.vars.remove(name);
		global.put(name, was == null ? Script.NULL : was);
	}
	
	public String get(String name)
	{
		return last.get(name);
	}
	
	public void push(Label to)
	{
		stack.add(last = new SNode(last, to));
	}
	
	public SNode pop()
	{
		int s = stack.size();
		SNode out = stack.remove(s - 1);
		last = stack.get(s - 2);
		return out;
	}
	
	public SNode getLast()
	{
		return last;
	}
	
	public SNode getGlobal()
	{
		return global;
	}
	
	/////////////////////////////////////////////
	
	protected class SNode
	{
		private final HashMap<String, String> vars = new HashMap<>();
		private final SNode parent;
		private final Label label;
		
		private SNode(SNode parent, Label label)
		{
			this.parent = parent;
			this.label = label;
		}
		
		protected void put(String name, String val)
		{
			SNode sn = this, old = this;
			boolean contains = false, couldAccessOld = true;
			while (!contains && sn != null)
			{
				contains = (sn == this || sn.label.isAccessible || (couldAccessOld && old.label.getsAccess)) && sn.vars.containsKey(name);
				if (!contains)
				{
					old = sn;
					couldAccessOld = couldAccessOld || old.label.isAccessible;
					sn = sn.parent;
				}
				else
				{
					sn.vars.put(name, val);
					return;
				}
			}
			vars.put(name, val);
		}
		
		protected String get(String name)
		{
			String out = vars.get(name);
			if (out == null && parent != null)
				return parent.get(name);
			return out;
		}
	}
	
	////////////////////////////////////////////
	
	public static interface ScopeConsumer
	{
		public void accept(int level, Label label, String key, String val);
	}
}
