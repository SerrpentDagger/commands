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

package commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import commands.Label.LabelTree;

public class Scope
{
	private final ArrayList<SNode> stack = new ArrayList<>();
	private SNode last = null;
	private final SNode global;
	
	///////////////////////
	
	public Scope(LabelTree root)
	{
		stack.add(new SNode(null, root));
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
				sc.accept(level.get(), sNode.label.root, k, v);
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
				if (!var.equals(Scajl.PARENT))
					n1.put(var, val);
			});
		}
	}
	
	public void put(String name, ScajlVariable val)
	{
		last.put(name, val);
	}
	
	public void put(VarSet... sets)
	{
		for (VarSet set : sets)
			put(set.var, set.set);
	}
	
	public void makeGlobal(String name)
	{
		ScajlVariable was = last.vars.remove(name);
		global.put(name, was == null ? ScajlVariable.NULL : was);
	}
	
	public ScajlVariable get(String name)
	{
		return last.get(name);
	}
	
	public void push(LabelTree to)
	{
		stack.add(last = new SNode(last, to));
	}
	
	public void push(SNode to)
	{
		stack.add(last = to);
	}
	
	public void push(VarSet... sets)
	{
		push(last.label);
		put(sets);
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
	
	@Override
	public String toString()
	{
		String str = "";
		for (int i = 0; i < stack.size(); i++)
			str += stack.get(i).label + (i != stack.size() - 1 ? "." : "");
		return str;
	}
	
	/////////////////////////////////////////////
	
	protected class SNode
	{
		private final HashMap<String, ScajlVariable> vars = new HashMap<>();
		private final SNode parent;
		private final LabelTree label;
		
		private SNode(SNode parent, LabelTree label)
		{
			this.parent = parent;
			this.label = label;
		}
		
		protected void put(String name, ScajlVariable val)
		{
			SNode sn = this, old = this;
			boolean contains = false, couldAccessOld = true;
			while (!contains && sn != null)
			{
				contains = (sn == this || sn.label.root.isAccessible || (couldAccessOld && old.label.root.getsAccess)) && sn.vars.containsKey(name);
				if (!contains)
				{
					old = sn;
					couldAccessOld = couldAccessOld || old.label.root.isAccessible;
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
		
		protected ScajlVariable get(String name)
		{
			ScajlVariable out = vars.get(name);
			if (out == null && parent != null)
				return parent.get(name);
			return out;
		}
		
		public Label getLabel()
		{
			return label.root;
		}
		
		public LabelTree getLabelTree()
		{
			return label;
		}
	}
	
	////////////////////////////////////////////
	
	public static interface ScopeConsumer
	{
		public void accept(int level, Label label, String key, ScajlVariable val);
	}
}
