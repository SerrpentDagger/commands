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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import annotations.ScajlClone;
import commands.ParseTracker.BoxTracker;
import commands.ParseTracker.DelimTracker;
import group.MixedPair;
import utilities.ArrayUtils;
import utilities.ArrayUtils.Ind;
import utilities.MapUtils;
import utilities.StringUtils;

public abstract class ScajlVariable implements ScajlClone<ScajlVariable>
{
	public static final SVVal NULL = new SVVal(Scajl.NULL, null);
	
	///////////////////////////
	
	protected String input, modless;
	protected WeakReference<SVMember> selfCtx;
	
	public ScajlVariable(String input, String modless, SVMember selfCtx)
	{
		this.input = input;
		this.modless = modless;
		this.selfCtx = new WeakReference<>(selfCtx);
	}
	
	public abstract String type();
	
	public abstract String val(Scajl ctx);
	public abstract ScajlVariable eval(Scajl ctx);
	public abstract String raw();
	@Override
	public abstract boolean equals(Object other);
	public VarCtx varCtx(String[] memberAccess, int off, boolean put, Scajl ctx)
	{
		if (off < memberAccess.length)
		{
			String val = getVar(memberAccess[off], false, ctx, selfCtx.get()).val(ctx);
			if (val.equals(Scajl.ARR_UP))
				return selfCtx(memberAccess, off, put, ctx);
		}
		if (put || memberAccess != null && off != memberAccess.length)
			ctx.parseExcept("Invalid member access", "The attempted access is not recognized on the value '" + raw() + "' of type: " + type(), "From access: " + StringUtils.toString(memberAccess, "", "" + Scajl.ARR_ACCESS, ""));
		return new VarCtx(() -> this);
	}
	protected VarCtx selfCtx(String[] memberAccess, int off, boolean put, Scajl ctx)
	{
		if (put && off == memberAccess.length - 1)
			ctx.parseExcept("Invalid index: " + Scajl.ARR_UP, "Cannot set the '" + Scajl.ARR_UP + "' value of a variable directly");
		else
		{
			SVMember self = selfCtx.get();
			if (self == null)
			{
				if (off == memberAccess.length - 1)
					return NULL.varCtx(memberAccess, off, put, ctx);
				else
					ctx.parseExcept("Invalid usage of the '%s' keyword.".formatted(Scajl.ARR_UP), "The indexed variable is not contained.");
			}
			return self.varCtx(memberAccess, off + 1, put, ctx);
		}
		return null;
	}
	protected ScajlVariable setSelf(SVMember selfCtx)
	{
		this.selfCtx = new WeakReference<SVMember>(selfCtx);
		return this;
	}
	public abstract boolean test(ScajlVariable other, Scajl ctx);
	public abstract ScajlVariable enforce(ScajlVariable other, Scajl ctx);
	
	@Override
	public String toString()
	{
		return raw();
	}
	
	@Override
	public ScajlVariable sjClone()
	{
		return clone();
	}
	
	@Override
	public abstract ScajlVariable clone();
	
	public ScajlVariable clone(boolean noUnpack)
	{
		return clone();
	}
	
	/////////////////////
	
/*	@Override
	public double valueD(Scajl ctx) { return Double.parseDouble(val(ctx)); }
	@Override
	public boolean valueB(Scajl ctx)
	{
		String b = val(ctx);
		switch (b)
		{
			case "true":
				return true;
			case "false":
				return false;
			default:
				throw new NumberFormatException("Malformed boolean input.");
		}
	}*/
	
	///////////////////////////////////////////////////////////////////////////////////
	
	public static class SVVal extends ScajlVariable
	{
		public SVVal(String input, String modless, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
		}
		
		public SVVal(String inputModless, SVMember selfCtx)
		{
			this(inputModless, inputModless, null);
		}
		
		public SVVal(double val, SVMember selfCtx)
		{
			this("" + val, selfCtx);
		}
		
		@Override
		public String type()
		{
			return Scajl.VALUE;
		}
		
		@Override
		public String val(Scajl ctx)
		{
			return modless;
		}

		@Override
		public ScajlVariable eval(Scajl ctx)
		{
			return this;
		}

		@Override
		public String raw()
		{
			return modless;
		}
		
		@Override
		public boolean test(ScajlVariable other, Scajl ctx)
		{
			if (modless.equals("null"))
				if (other instanceof SVVal)
					return other.equals(NULL) || CmdArg.BOOLEAN.parse(other, ctx) != null || CmdArg.DOUBLE.parse(other, ctx) != null;
				else
					return true;
			boolean t = other instanceof SVVal && other != NULL;
			if (!t)
				return false;
			SVVal oth = (SVVal) other;
			Boolean b = CmdArg.BOOLEAN.parse(this, ctx);
			if (b != null)
				return CmdArg.BOOLEAN.parse(oth, ctx) != null;
			Double d = CmdArg.DOUBLE.parse(this, ctx);
			if (d != null)
				return CmdArg.DOUBLE.parse(oth, ctx) != null;
			return true;
		}
		
		@Override
		public ScajlVariable enforce(ScajlVariable other, Scajl ctx)
		{
			return clone();
		}

		@Override
		public SVVal clone()
		{
			return new SVVal(input, modless, selfCtx.get());
		}
		
		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof SVVal))
				return false;
			return ((SVVal) other).modless.equals(modless);
		}

/*		@Override
		public SVVal setD(double to)
		{
			modless = Double.toString(to);
			input = modless;
			return this;
		}

		@Override
		public SVVal setB(boolean to)
		{
			modless = Boolean.toString(to);
			input = modless;
			return this;
		}*/
	}
	
	public static class SVRef extends ScajlVariable
	{
		public SVRef(String input, String modless)
		{
			super(input, modless, null);
		}
		
		@Override
		public String type()
		{
			return "Reference";
		}
		
		@Override
		public String val(Scajl ctx)
		{
			return val(ctx, new HashSet<>());
		}
		
		@Override
		public ScajlVariable eval(Scajl ctx)
		{
			ScajlVariable var = ctx.scope.get(modless);
			return var == null ? NULL : var;
		}
		
		private String val(Scajl ctx, HashSet<SVRef> selfReference)
		{
			selfReference.add(this);
			ScajlVariable eval = eval(ctx);
			if (eval instanceof SVRef)
				if (selfReference.contains(eval))
					return modless;
				else
					return ((SVRef) eval).val(ctx, selfReference);
			return eval.val(ctx);
		}
		
		@Override
		public String raw()
		{
			return Scajl.REF + modless;
		}
		
		@Override
		public boolean test(ScajlVariable other, Scajl ctx)
		{
			return eval(ctx).test(other, ctx);
		}
		
		@Override
		public ScajlVariable enforce(ScajlVariable other, Scajl ctx)
		{
			return clone();
		}
		
		@Override
		public SVRef clone()
		{
			return new SVRef(input, modless);
		}
		
		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof SVRef))
				return false;
			return ((SVRef) other).modless.equals(modless);
		}

/*		@Override
		public ScajlVariable setD(double to)
		{
			ScajlClone.unsup("setD");
			return this;
		}

		@Override
		public ScajlVariable setB(boolean to)
		{
			ScajlClone.unsup("setB");
			return this;
		}*/
	}
	
	public static class SVString extends ScajlVariable
	{
		protected String unraw;
		
		public SVString(String input, String modless, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			unraw = Scajl.stringTrim(modless);
		}
		
		@Override
		public String type()
		{
			return Scajl.STRING;
		}
		
		@Override
		public String val(Scajl ctx)
		{
			return unraw;
		}
		
		@Override
		public ScajlVariable eval(Scajl ctx)
		{
			return this;
		}
		
		@Override
		public String raw()
		{
			return Scajl.STRING_CHAR + unraw + Scajl.STRING_CHAR;
		}
		
		@Override
		public boolean test(ScajlVariable other, Scajl ctx)
		{
			return other instanceof SVString || other instanceof SVVal;
		}
		
		@Override
		public ScajlVariable enforce(ScajlVariable other, Scajl ctx)
		{
			return clone();
		}
		
		@Override
		public SVString clone()
		{
			return new SVString(input, modless, selfCtx.get());
		}
		
		@Override
		public boolean equals(Object other)
		{
			if (other instanceof SVVal)
				return ((SVVal) other).modless.equals(unraw);
			if (!(other instanceof SVString))
				return false;
			return ((SVString) other).unraw.equals(unraw);
		}

/*		@Override
		public ScajlVariable setD(double to)
		{
			unraw = "" + to;
			input = Scajl.STRING_CHAR + unraw + Scajl.STRING_CHAR;
			modless = input;
			return this;
		}

		@Override
		public ScajlVariable setB(boolean to)
		{
			unraw = "" + to;
			input = Scajl.STRING_CHAR + unraw + Scajl.STRING_CHAR;
			modless = input;
			return this;
		}*/
	}
	
	public static class SVJavObj extends ScajlVariable
	{
		public final Object[] value;
		private SVVal typeCount;

		public SVJavObj(String input, String modless, SVMember selfCtx, Object[] val)
		{
			super(input, modless, selfCtx);
			value = val;
			typeCount = new SVVal(val.length, null);
		}
		
		public SVJavObj(Object val)
		{
			this(null, null, null, new Object[] { val });
		}
		
		@Override
		public String type()
		{
			return Scajl.OBJECT;
		}
		
		@Override
		public String val(Scajl ctx)
		{
			return raw();
		}
		
		@Override
		public ScajlVariable eval(Scajl ctx)
		{
			return this;
		}
		
		@Override
		public String raw()
		{
			return StringUtils.toString(value, (o) -> o == null ? Scajl.NULL : o.toString(), "", " | ", "");
		}

		@Override
		public VarCtx varCtx(String[] memberAccess, int off, boolean put, Scajl ctx)
		{
			boolean last = off == memberAccess.length - 1;
			if (last && getVar(memberAccess[off], false, ctx).val(ctx).equals(Scajl.ARR_LEN))
			{
				if (put)
					ctx.parseExcept("Invalid member access", "Cannot set the '%s' value of an Object directly".formatted(Scajl.ARR_LEN));
				return new VarCtx(() -> typeCount);
			}
			else if (off < memberAccess.length)
			{
				String[] split = Scajl.objCallOf(memberAccess[off]);
				if (split.length == 2)
				{
					if (!split[1].endsWith("" + Scajl.TOK_E))
						ctx.parseExcept("Unfinished delimiter", "The indexed Object is missing a closing parenthesis");
					String name = getVar(split[0], true, ctx).val(ctx);
					String namePref = null;
					for (Object val : value)
					{
						Class<?> cl = val.getClass();
						ScriptObject<?> type = Scajl.getType(cl);
						if (type == null)
							throw new IllegalStateException("A non-Scajl-exposed Object type has been stored in a Scajl variable: " + cl.getCanonicalName());
						if (type.getMemberCmd(name) != null)
						{
							namePref = type.getTypeName();
							ctx.putVar("OBJ", this);
							ctx.runExecutable(namePref + Scajl.ARR_ACCESS + name + " OBJ" + (split[1].length() > 1 ? ", " : "") + split[1].substring(0, split[1].length() - 1), null);
							return ctx.prev().varCtx(memberAccess, off + 1, put, ctx);
						}
					}
					ctx.parseExcept("Unrecognized member command '" + name + "'", "The command could not be found in the Object's aspects");
				}
			}
			return super.varCtx(memberAccess, off, put, ctx);
		}
		
		@Override
		public boolean test(ScajlVariable other, Scajl ctx)
		{
			if (!(other instanceof SVJavObj))
				return false;
			SVJavObj jav = (SVJavObj) other;
			return ArrayUtils.containsAll(jav.value, value, (a, b) -> b.getClass().isAssignableFrom(a.getClass()));
		}
		
		@Override
		public ScajlVariable enforce(ScajlVariable other, Scajl ctx)
		{
			if (!(other instanceof SVJavObj))
				return clone();
			SVJavObj jav = (SVJavObj) other;
			ArrayList<Object> newVal = new ArrayList<>();
			ArrayList<Object> unmatched = new ArrayList<>();
			unmatched.addAll(Arrays.asList(value));
			for (int i = 0; i < jav.value.length; i++)
			{
				newVal.add(jav.value[i]);
				final Class<?> cl = jav.value[i].getClass();
				unmatched.removeIf((un) -> un.getClass().isAssignableFrom(cl));
			}
			newVal.addAll(unmatched);
			return new SVJavObj(null, null, null, newVal.toArray());
		}
		
		@Override
		public SVJavObj clone()
		{
			Object[] newVal = Arrays.copyOf(value, value.length);
			for (int i = 0; i < newVal.length; i++)
				newVal[i] = ScajlClone.tryClone(newVal[i]);
			return new SVJavObj(input, modless, selfCtx.get(), newVal);
		}
		
		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof SVJavObj))
				return false;
			SVJavObj oth = (SVJavObj) other;
			if (oth.value.length != value.length)
				return false;
			for (Object val : value)
			{
				Class<?> cl = val.getClass();
				for (Object oVal : oth.value)
					if (cl.isAssignableFrom(oVal.getClass()) && !val.equals(oVal))
						return false;
			}
			return true;
		}

/*		@Override
		public ScajlVariable setD(double to)
		{
			for (Object v : value)
				if (v instanceof ScajlArithmetic<?>)
					((ScajlArithmetic<?>) v).setD(to);
			return this;
		}

		@Override
		public ScajlVariable setB(boolean to)
		{
			for (Object v : value)
				if (v instanceof ScajlLogical<?>)
					((ScajlLogical<?>) v).setB(to);
			return this;
		}*/
	}
	
	protected static abstract class SVMember extends ScajlVariable
	{
		public SVMember(String input, String modless, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
		}
		
		@Override
		public String val(Scajl ctx)
		{
			return raw();
		}
		
		@Override
		public ScajlVariable eval(Scajl ctx)
		{
			return this;
		}
		
		@Override
		public VarCtx varCtx(String[] memberAccess, int off, boolean put, Scajl ctx)
		{
			if (off == memberAccess.length)
				return new VarCtx(() -> this);
			SVMember self = selfCtx.get();
			if (memberAccess[off].equals(Scajl.ARR_UP))
			{
				if (off == memberAccess.length - 1)
				{
					if (put)
						ctx.parseExcept("Invalid index: " + Scajl.ARR_UP, "Cannot set the '" + Scajl.ARR_UP + "' value of a variable directly");
					return new VarCtx(() -> self);
				}
				else
					return self.varCtx(memberAccess, off + 1, put, ctx);
			}
			else if (memberAccess[off].equals(Scajl.ARR_DIMS))
			{
				if (off == memberAccess.length - 1)
					return new VarCtx(() -> Scajl.numOf(dimensions()));
			}
			else if (hasAcc(memberAccess[off]))
				return memCtx(memberAccess, off, memberAccess[off], put, ctx);				
			ScajlVariable var = getVar(memberAccess[off], false, ctx, self);
			String val = var.val(ctx);
			return memCtx(memberAccess, off, val, put, ctx);
		}
		
		public int dimensions()
		{
			return dimensions(new HashSet<>());
		}
		private int dimensions(HashSet<SVMember> selfReference)
		{
			selfReference.add(this);
			int minD = Integer.MAX_VALUE - 1;
			Iterator<ScajlVariable> vals = valueIterator();
			Class<? extends SVMember> cls = this.getClass();
			while (vals.hasNext())
			{
				ScajlVariable val = vals.next();
				if (selfReference.contains(val))
					continue;
				if (val.getClass() == cls)
				{
					SVMember mem = (SVMember) val;
					if (minD > 1)
						minD = Math.min(minD, mem.dimensions(selfReference));
				}
				else
					return 1;
			}
			return 1 + minD;
		}
		
		@Override
		public ScajlVariable clone()
		{
			return clone(-1, new HashMap<>());
		}
		@Override
		public ScajlVariable clone(boolean noUnpack)
		{
			return clone(val(noUnpack), new HashMap<>());
		}
		protected abstract SVMember clone(int noUnpack, HashMap<SVMember, SVMember> selfReference);
		
		public abstract Iterator<ScajlVariable> valueIterator();
		public abstract <T extends SVMember> T packTo(int dimensions);
		
		@Override
		public String raw()
		{
			return raw(new HashSet<>());
		}
		protected abstract String raw(HashSet<SVMember> selfReference);
		protected abstract boolean hasAcc(String acc);
		protected abstract VarCtx memCtx(String[] memberAccess, int off, String accVal, boolean put, Scajl ctx);
	}
	
	public static class SVMap extends SVMember
	{
		private LinkedHashMap<String, ScajlVariable> map;
		
		public SVMap(String input, String modless, Scajl ctx, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			String[] elements = Scajl.arrayElementsOf(modless);
			map = new LinkedHashMap<>(elements.length);
			for (int i = 0; i < elements.length; i++)
			{
				String[] keyVal = Scajl.syntaxedSplit(elements[i], Scajl.MAP_KEY_EQ);
				if (keyVal.length == 0)
					continue;
				String key = getVar(keyVal[0], true, ctx, this).val(ctx);
				map.put(key, keyVal.length == 1 ? NULL : getVar(keyVal[1], false, ctx, this));
			}
		}
		public SVMap(String input, String modless, LinkedHashMap<String, ScajlVariable> map, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			this.map = map;
		}

		@Override
		public String type()
		{
			return "Map";
		}
		
		@Override
		protected boolean hasAcc(String acc)
		{
			return acc.equals(Scajl.ARR_LEN) || map.containsKey(acc);
		}
		
		@Override
		public VarCtx memCtx(String[] memberAccess, int off, String accVal, boolean put, Scajl ctx)
		{
			if (off == memberAccess.length - 1)
			{
				if (accVal.equals(Scajl.ARR_LEN))
					return new VarCtx(() -> new SVVal(map.size(), this));
				else
					return new VarCtx(() ->
					{
						if (!map.containsKey(accVal))
							return NULL;
						return map.get(accVal);
					}, (var) ->
					{
						map.put(accVal, var);
						var.selfCtx = new WeakReference<>(this);
					});
			}
			if (!map.containsKey(accVal))
				ctx.parseExcept("Invalid Map key for continued indexing: " + accVal, "The specified key is missing.", "From access: " + StringUtils.toString(memberAccess, "", "" + Scajl.ARR_ACCESS, ""));
			return map.get(accVal).varCtx(memberAccess, off + 1, put, ctx);
		}
		
		@Override
		public Iterator<ScajlVariable> valueIterator()
		{
			return map.values().iterator();
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public SVMap packTo(int dimensions)
		{
			return null;
		}

		@Override
		public String raw(HashSet<SVMember> selfReference)
		{
			String out = "" + Scajl.ARR_S;
			boolean remove = selfReference.add(this);
			Iterator<Entry<String, ScajlVariable>> it = map.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<String, ScajlVariable> ent = it.next();
				ScajlVariable val = ent.getValue();
				boolean sRef = selfReference.contains(val);
				out += ent.getKey() + Scajl.MAP_KEY_EQ + (sRef ? sRef(val) : 
								(val instanceof SVMember ? ((SVMember) val).raw(selfReference) :
										val.raw()))
						+ (it.hasNext() ? Scajl.ARR_SEP + " " : "");
			}
			if (remove)
				selfReference.remove(this);
			return out + Scajl.ARR_E;
		}

		@Override
		public boolean test(ScajlVariable other, Scajl ctx)
		{
			if (!(other instanceof SVMap))
				return false;
			SVMap oth = (SVMap) other;
			Iterator<String> it = map.keySet().iterator();
			while (it.hasNext())
			{
				String key = it.next();
				ScajlVariable v1 = map.get(key), v2 = oth.map.get(key);
				if (v2 == null || !v1.test(v2, ctx))
					return false;
			}
			return true;
		}
		
		@Override
		public ScajlVariable enforce(ScajlVariable other, Scajl ctx)
		{
			if (!(other instanceof SVMap))
				return clone();
			SVMap oth = (SVMap) other;
			Iterator<String> it = map.keySet().iterator();
			while (it.hasNext())
			{
				String key = it.next();
				ScajlVariable v1 = map.get(key), v2 = oth.map.get(key);
				if (v2 == null || !v1.test(v2, ctx))
					oth.map.put(key, v1.enforce(v2, ctx).setSelf(oth));
			}
			return oth;
		}
		
		@Override
		protected SVMap clone(int noUnpack, HashMap<SVMember, SVMember> selfReference)
		{
			LinkedHashMap<String, ScajlVariable> deepCopy = new LinkedHashMap<>();
			SVMap clone = new SVMap(input, modless, deepCopy, selfCtx.get());
			selfReference.put(this, clone);
			Iterator<Entry<String, ScajlVariable>> it = map.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<String, ScajlVariable> ent = it.next();
				ScajlVariable cop = ent.getValue();
				SVMember sRef = selfReference.get(cop);
				if (sRef == null)
				{
					if (cop instanceof SVMember)
						deepCopy.put(ent.getKey(), cop = ((SVMember) cop).clone(noUnpack, selfReference));
					else
						deepCopy.put(ent.getKey(), cop = cop.clone());
					cop.selfCtx = new WeakReference<>(clone);
				}
				else
					deepCopy.put(ent.getKey(), sRef);
			}
			return clone;
		}
		
		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof SVMap))
				return false;
			SVMap oth = (SVMap) other;
			if (oth.map.size() != map.size())
				return false;
			Iterator<Entry<String, ScajlVariable>> it = map.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<String, ScajlVariable> ent = it.next();
				if (!ent.getValue().equals(oth.map.get(ent.getKey())))
					return false;
			}
			return true;
		}
		
		public LinkedHashMap<String, ScajlVariable> getMap()
		{
			return map;
		}
		
/*		@Override
		public ScajlVariable setD(double to)
		{
			tryCall(ScajlArithmetic.SETD, (sv) -> sv.setD(to));
			return this;
		}
		
		@Override
		public ScajlVariable setB(boolean to)
		{
			tryCall(ScajlLogical.SETB, (sv) -> sv.setB(to));
			return null;
		}*/
		
		protected boolean tryCall(String name, Consumer<ScajlVariable> call)
		{
			ScajlVariable val = map.get(name);
			if (val == null)
				return false;
			call.accept(val);
			return true;
		}
	}
	
	public static class SVTokGroup extends SVArray
	{	
		public SVTokGroup(String input, String modless, boolean noUnpack, Scajl ctx, SVMember selfCtx)
		{
			super(input, modless, ArrayUtils.transform(Scajl.tokensOf(Scajl.unpack(modless)), (s) -> getVar(s, false, ctx)), noUnpack, selfCtx);
		}
		public SVTokGroup(String input, String modless, ScajlVariable[] array, boolean noUnpack, SVMember selfCtx)
		{
			super(input, modless, array, noUnpack, selfCtx);
		}
		
		@Override
		public String type()
		{
			return "TokenGroup";
		}
		
		@Override
		public String raw(HashSet<SVMember> selfReference)
		{
			boolean remove = selfReference.add(this);
			String out = "" + Scajl.TOK_S;
			ScajlVariable[] array = getArray();
			for (int i = 0; i < array.length; i++)
				out += (selfReference.contains(array[i]) ? sRef(array[i]) :
								(array[i] instanceof SVMember ? ((SVMember) array[i]).raw(selfReference)
										: array[i].raw()))
						+ (i == array.length - 1 ? "" : " ");
			if (remove)
				selfReference.remove(this);
			return out + Scajl.TOK_E;
		}
		
		@Override
		public SVArray clone(int noUnpack, HashMap<SVMember, SVMember> selfReference)
		{
			ScajlVariable[] deepCopy = Arrays.copyOf(array, array.length);
			SVTokGroup clone = new SVTokGroup(input, modless, deepCopy, val(noUnpack), selfCtx.get());
			selfReference.put(this, clone);
			for (int i = 0; i < deepCopy.length; i++)
			{
				ScajlVariable cop = deepCopy[i], sRef = selfReference.get(cop);
				if (sRef == null)
				{
					if (cop instanceof SVMember)
						deepCopy[i] = ((SVMember) cop).clone(noUnpack, selfReference);
					else
						deepCopy[i] = cop.clone();
					deepCopy[i].selfCtx = new WeakReference<>(clone);
				}
				else
					deepCopy[i] = sRef;
			}
			return clone;
		}
		
		@Override
		protected boolean hasAcc(String acc)
		{
			return false;
		}
		
		@Override
		public VarCtx memCtx(String[] memberAccess, int off, String accVal, boolean put, Scajl ctx)
		{
			String val = getVar(memberAccess[off], false, ctx, selfCtx.get()).val(ctx);
			if (val.equals(Scajl.ARR_UP))
				return selfCtx(memberAccess, off, put, ctx);
			if (put || memberAccess != null && off != memberAccess.length)
				ctx.parseExcept("Invalid member access on Token Group", "The indexed variable is not a type which can be indexed.", "From access: " + StringUtils.toString(memberAccess, "", "" + Scajl.ARR_ACCESS, ""));
			return new VarCtx(() -> this);
		}
		
		@Override
		public Iterator<ScajlVariable> valueIterator()
		{
			return MapUtils.of(getArray());
		}
		
		@Override
		public SVTokGroup packTo(int dimensions)
		{
			return null;
		}
		
		@Override
		public boolean test(ScajlVariable other, Scajl ctx)
		{
			return false;
		}
		
		@Override
		public ScajlVariable enforce(ScajlVariable other, Scajl ctx)
		{
			if (!(other instanceof SVTokGroup))
				return clone();
			return null;
		}
		
		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof SVTokGroup))
				return false;
			SVTokGroup oth = (SVTokGroup) other;
			if (oth.array.length != array.length)
				return false;
			for (int i = 0; i < array.length; i++)
				if (!array[i].equals(oth.array[i]))
					return false;
			return true;
		}
	}
	
	public static class SVArray extends SVMember
	{
		protected ScajlVariable[] array;
		private SVVal length;
		public final boolean noUnpack;
		
		public SVArray(String input, String modless, boolean noUnpack, Scajl ctx, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			String[] elements = Scajl.arrayElementsOf(modless);
			array = new ScajlVariable[elements.length];
			Arrays.fill(array, NULL);
			for (int i = 0; i < elements.length; i++)
				array[i] = getVar(elements[i], false, ctx, this);
			length = new SVVal(array.length, this);
			this.noUnpack = noUnpack;
		}
		public SVArray(String input, String modless, ScajlVariable[] array, boolean noUnpack, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			this.array = array;
			length = new SVVal(array.length, this);
			this.noUnpack = noUnpack;
		}
		public SVArray(ScajlVariable[] array, SVMember selfCtx)
		{
			this(null, null, array, false, selfCtx);
			for (ScajlVariable var : array)
				var.setSelf(this);
		}
		
		@Override
		public String type()
		{
			return "Array";
		}

		@Override
		public String raw(HashSet<SVMember> selfReference)
		{
			boolean remove = selfReference.add(this);
			String out = "" + Scajl.ARR_S;
			for (int i = 0; i < array.length; i++)
				out += (selfReference.contains(array[i]) ? sRef(array[i]) : 
								(array[i] instanceof SVMember ? ((SVMember) array[i]).raw(selfReference)
										: array[i].raw()))
						+ (i == array.length - 1 ? "" : Scajl.ARR_SEP + " ");
			if (remove)
				selfReference.remove(this);
			return out + Scajl.ARR_E;
		}
		
		@Override
		protected boolean hasAcc(String acc)
		{
			return acc.equals(Scajl.ARR_LEN);
		}
		
		@Override
		public VarCtx memCtx(String[] memberAccess, int off, String accVal, boolean put, Scajl ctx)
		{
			if (accVal.equals(Scajl.ARR_LEN) && off == memberAccess.length - 1)
				return new VarCtx(() -> length, (var) ->
				{
					Integer len = CmdArg.INT.parse(var, ctx);
					if (len == null)
						ctx.parseExcept("Invalid token resolution for Array length", "Array lengths must be specified as numbers.");
					resize(len);
				});
			else
			{
//				Integer ind = ScajlArithmetic.dumbParseI(accVal); // TODO: This doesn't seem very clever.
				Integer ind = CmdArg.dumbParseI(accVal);
				if (ind == null)
					ctx.parseExcept("Invalid Array index: " + accVal, "Array indices must be numbers.", "From access: " + StringUtils.toString(memberAccess, "", "" + Scajl.ARR_ACCESS, ""));
				if (ind < 0)
					ind = array.length + ind;
				if (ind >= array.length || ind < 0)
					ctx.parseExcept("Invalid Array index: " + ind, "Index out of bounds.", "From access: " + StringUtils.toString(memberAccess, "", "" + Scajl.ARR_ACCESS, ""));
				final int iind = ind;
				if (off == memberAccess.length - 1)
					return new VarCtx(() -> array[iind], (var) ->
					{
						array[iind] = var;
						var.selfCtx = new WeakReference<>(this);
					});
				return array[ind].varCtx(memberAccess, off + 1, put, ctx);
			}
		}
		public void resize(int len)
		{
			int oldLen = array.length;
			array = Arrays.copyOf(array, len);
			if (len > oldLen)
				Arrays.fill(array, oldLen, len, NULL);
			length = new SVVal(len, this);
		}
		
		@Override
		public Iterator<ScajlVariable> valueIterator()
		{
			return MapUtils.of(array);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public SVArray packTo(int dimensions)
		{
			int dims = dimensions();
			SVArray out = this;
			for (int i = 0; i < dimensions - dims; i++)
			{
				SVArray old = out;
				out = new SVArray(new ScajlVariable[] { old }, null);
			}
			return out;
		}
		
		@Override
		public boolean test(ScajlVariable other, Scajl ctx)
		{
			if (!(other instanceof SVArray))
				return false;
			SVArray oth = (SVArray) other;
			if (oth.array.length < array.length)
				return false;
			for (int i = 0; i < array.length; i++)
				if (oth.array[i] == null || !array[i].test(oth.array[i], ctx))
					return false;
			return true;
		}
		
		@Override
		public ScajlVariable enforce(ScajlVariable other, Scajl ctx)
		{
			if (!(other instanceof SVArray))
				return clone();
			SVArray oth = (SVArray) other;
			if (oth.array.length < array.length)
				oth.resize(array.length);
			for (int i = 0; i < array.length; i++)
				if (oth.array[i] == null || !array[i].test(other, ctx))
					oth.array[i] = array[i].enforce(oth.array[i], ctx).setSelf(oth);
			return oth;
		}
		
		@Override
		public SVArray clone()
		{
			return clone(val(noUnpack), new HashMap<>());
		}
		
		@Override
		public SVArray clone(int noUnpack, HashMap<SVMember, SVMember> selfReference)
		{
			ScajlVariable[] deepCopy = Arrays.copyOf(array, array.length);
			SVArray clone = new SVArray(input, modless, deepCopy, val(noUnpack), selfCtx.get());
			selfReference.put(this, clone);
			for (int i = 0; i < deepCopy.length; i++)
			{
				ScajlVariable cop = deepCopy[i], sRef = selfReference.get(cop);
				if (sRef == null)
				{
					if (cop instanceof SVMember)
						deepCopy[i] = ((SVMember) cop).clone(noUnpack, selfReference);
					else
						deepCopy[i] = cop.clone();
					deepCopy[i].selfCtx = new WeakReference<>(clone);
				}
				else
					deepCopy[i] = sRef;
			}
			return clone;
		}
		
		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof SVArray))
				return false;
			SVArray oth = (SVArray) other;
			if (oth.array.length != array.length || oth.noUnpack != noUnpack)
				return false;
			for (int i = 0; i < array.length; i++)
				if (!array[i].equals(oth.array[i]))
					return false;
			return true;
		}
		
		public ScajlVariable[] getArray()
		{
			return array;
		}
		
		//////////////////////////////////// Interface
		
/*		@Override
		public double valueD(Scajl ctx)
		{
			return array.length;
		}*/
		
/*		@Override
		public ScajlVariable setD(double to)
		{
			for (ScajlVariable var : array)
				var.setD(to);
			return this;
		}*/
		
/*		@Override
		public ScajlVariable setB(boolean to)
		{
			for (ScajlVariable var : array)
				var.setB(to);
			return this;
		}*/
		
/*		@Override
		public ScajlVariable add(double num, Scajl ctx)
		{
			for (ScajlVariable var : array)
				var.add(num, ctx);
			return this;
		} */
		
/*		@Override
		public ScajlVariable add(ScajlVariable other, Scajl ctx)
		{
			if (other instanceof SVArray)
			{
				SVArray oth = (SVArray) other;
				for (int i = 0; i < Math.min(array.length, oth.array.length); i++)
					array[i].add(oth.array[i], ctx);
				return this;
			}
			return add(other.valueD(ctx), ctx);
		}*/
	}
	
/*	public static class SVUnresolved extends ScajlVariable
	{
		private final ScajlVariable target;
		
		public SVUnresolved(String input, String modless, ScajlVariable target)
		{
			super(input, modless, target.selfCtx.get());
			this.target = target;
		}

		@Override
		public String type()
		{
			return "Unresolved";
		}

		@Override
		public String val(Scajl ctx)
		{
			return target.val(ctx);
		}

		@Override
		public ScajlVariable eval(Scajl ctx)
		{
			return target;
		}

		@Override
		public String raw()
		{
			return Scajl.RAW + target.raw();
		}

		@Override
		public boolean equals(Object other)
		{
			return target.equals(other);
		}

		@Override
		public boolean test(ScajlVariable other, Scajl ctx)
		{
			return target.test(other, ctx);
		}

		@Override
		public ScajlVariable enforce(ScajlVariable other, Scajl ctx)
		{
			return target.enforce(other, ctx);
		}

		@Override
		public ScajlVariable clone()
		{
			return new SVUnresolved(input, modless, target);
		}
		
		@Override
		public ScajlVariable setD(double to)
		{
			return target.setD(to);
		}
		
		@Override
		public ScajlVariable setB(boolean to)
		{
			return target.setB(to);
		}
	}*/
	
	public static class SVExec extends ScajlVariable
	{
		protected final Scajl runCtx;
		protected final Variable[] sets;
		
		public SVExec(String input, String modless, SVMember selfCtx, Scajl runCtx)
		{
			this(input, modless, selfCtx, runCtx, null);
		}
		
		public SVExec(String input, String modless, SVMember selfCtx, Scajl runCtx, Variable[] sets)
		{
			super(input, modless, selfCtx);
			this.runCtx = runCtx;
			this.sets = sets;
		}
		
		@Override
		public String type()
		{
			return "Executable";
		}

		@Override
		public String val(Scajl ctx)
		{
			ctx = runCtx == null ? ctx : runCtx;
			return eval(ctx).val(ctx);
		}
		
		@Override
		public ScajlVariable eval(Scajl ctx)
		{
			ctx = runCtx == null ? ctx : runCtx;
			if (sets != null)
			{
				ctx.scope.push();
				for (Variable set : sets)
					ctx.scope.put(set.name, set.var);
			}
			ScajlVariable out = ctx.runExecutable(modless, selfCtx.get()).output;
			if (sets != null)
				ctx.scope.pop();
			return out;
		}

		@Override
		public String raw()
		{
			return Scajl.REF + modless;
		}

		@Override
		public boolean test(ScajlVariable other, Scajl ctx)
		{
			return other instanceof SVExec;
		}
		
		@Override
		public ScajlVariable enforce(ScajlVariable other, Scajl ctx)
		{
			return clone();
		}
		
		@Override
		public SVExec clone()
		{
			return new SVExec(input, modless, selfCtx.get(), runCtx);
		}
		
		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof SVExec))
				return false;
			return ((SVExec) other).modless.equals(modless);
		}

/*		@Override
		public ScajlVariable setD(double to)
		{
			ScajlClone.unsup("setD");
			return this;
		}

		@Override
		public ScajlVariable setB(boolean to)
		{
			ScajlClone.unsup("setB");
			return this;
		}*/
	}
	
	////////////////////////
	
	public static void putVar(String name, ScajlVariable var, Scajl ctx)
	{
		putVar(name, var, ctx, null);
	}
	public static void putVar(String name, ScajlVariable var, Scajl ctx, SVMember selfCtx)
	{
		String[] arrAcc = Scajl.syntaxedSplit(name, "" + Scajl.ARR_ACCESS);
		ScajlVariable toVar = getVar(arrAcc[0], arrAcc.length == 1, ctx);
		if (arrAcc.length > 1)
		{
			String[] access = new String[arrAcc.length];
			for (int i = 0; i < access.length; i++)
			{
				if (i > 0)
					access[i] = getVar(arrAcc[i], false, ctx).val(ctx);
				else
					access[i] = arrAcc[0];
			}
			
			VarCtx vCtx = toVar.varCtx(access, 1, true, ctx);
			if (vCtx.put != null)
				vCtx.put.accept(var);
			else
				ctx.parseExcept("Unsupported put operation", "Unable to put value '" + var.raw() + "' to context '" + name + "'");
		}
		else
		{
			name = toVar.input;
			if (Scajl.ILLEGAL_VAR_MATCHER.matcher(name).matches())
				ctx.parseExcept("Illegal characters in variable name", name);
			try
			{
				Double.parseDouble(name);
				ctx.parseExcept("Numerical variable name", name);
			}
			catch (NumberFormatException e)
			{}
			ctx.scope.put(name, var);
		}
	}
	
	public static boolean isVar(String input, Scajl ctx)
	{
		if (input.equals(Scajl.NULL))
			return false;
		
		MixedPair<boolean[], String> modPair = Scajl.prefixModsFrom(input, Scajl.VALID_VAR_MODS);
		String modless = modPair.b();
		boolean[] mods = modPair.a();
		if (mods[1])
			return false;
		ScajlVariable var = ctx.scope.get(modless);
		return var != null;
	}
	
	public static Object[] preParse(String token, Scajl ctx)
	{
		return preParse(new String[] { token }, ctx);
	}
	
	public static Object[] preParse(String[] tokens, Scajl ctx)
	{
		Ind ind = new Ind(0);
		Object[] out = new Object[tokens.length];
		ArrayUtils.fillFrom(out, tokens);
		for (int i = 0; i < out.length; i = ind.get())
		{
			String str;
			if (out[i] instanceof String && (str = (String) out[i]).charAt(0) == Scajl.UNPACK)
			{
				ScajlVariable unp = getVar(str.substring(1), false, ctx).eval(ctx);
				if (unp instanceof SVTokGroup)
					out = ArrayUtils.replace(out, ((SVTokGroup) unp).array, i, ind);
				else
				{
					out[i] = unp;
					ind.inc();
				}
			}
			else
				ind.inc();
		}
		return out;
	}
	
	public static ScajlVariable getVar(String input, boolean rawDef, Scajl ctx)
	{
		return getVar(input, rawDef, ctx, null);
	}
	public static ScajlVariable getVar(String input, boolean rawDef, Scajl ctx, SVMember selfCtx)
	{
		if (input.equals(Scajl.NULL))
			return NULL;
		
		boolean isNumber = CmdArg.dumbParse(input) != null;
		if (isNumber)
			return new SVVal(input, selfCtx);
		MixedPair<boolean[], String> modPair = Scajl.prefixModsFrom(input, Scajl.VALID_VAR_MODS);
		String modless = modPair.b();
		boolean[] mods = modPair.a();
		boolean isUnraw = mods[0];
		boolean isRaw = mods[1] || (rawDef && !isUnraw);
		boolean isRef = mods[2];
		boolean isRawCont = mods[3];
		boolean isUnpack = mods[4];
		boolean noUnpack = mods[5];
		//boolean unresolved = mods[6];
		if (isUnpack)
			ctx.parseExcept("Illegal reference modifier", "The 'unpack' reference modifier is disallowed for this location", "From input: " + input);
		int modCount = countTrues(mods);
		if (modCount > 1 && !(noUnpack && isRawCont))
			ctx.parseExcept("Invalid variable usage", "A maximum of 1 reference modifier is allowed per token, except for the '" + Scajl.NO_UNPACK + Scajl.RAW_CONTENTS + "' combination applied to an Array or TokenGroup variable.", "From input: " + input);
		if (isRaw)
			return new SVVal(input, modless, selfCtx);

	//	if (unresolved)
	//	{
	//		mods[6] = false;
	//		return new SVUnresolved(input, modless, getVar(Scajl.prefixWithMods(modless, mods, Scajl.VALID_VAR_MODS), false, ctx, selfCtx));			
	//	}
		String[] arrAcc = Scajl.syntaxedSplit(input, "" + Scajl.ARR_ACCESS);
		if (arrAcc.length > 1)
		{
			ScajlVariable var = getVar(arrAcc[0], false, ctx, selfCtx);
			return var.varCtx(arrAcc, 1, false, ctx).get.get();
		}
		
		boolean isString = modless.startsWith("" + Scajl.STRING_CHAR);
		if (isString && !modless.endsWith("" + Scajl.STRING_CHAR))
			ctx.parseExcept("Malformed String", "A quoted String must start and end with the '\"' character.", "From input: " + input);
		boolean isContainer = containerCheck(modless, input, ctx);
		boolean hasEq = isContainer && Scajl.syntaxedContains(modless.substring(1), Pattern.quote("" + Scajl.MAP_KEY_EQ), 1);
		boolean isArray = isContainer && !hasEq;
		if (noUnpack && !(isArray || isRawCont))
			ctx.parseExcept("Illegal reference modifier", "The \"don't unpack\" modifier is only allowed on Array declarations and clonings.", "From input: " + input);
		boolean isMap = isContainer && hasEq;
		if ((isArray || isMap || isString) && (isUnraw || isRef || isRawCont || isUnpack))
			ctx.parseExcept("Illegal reference modifiers", "The only reference modifier allowed on an Array, Map or String declaration is the \"don't unpack\" modifier placed in front of an Array declaration or cloning", "From input: " + input);
			//ctx.parseExcept("Invalid value usage", "A quoted String or an Array or Map must start and end with their respective boxing characters.", "From input: " + input);
			
		boolean isGroup = modless.startsWith("" + Scajl.TOK_S);
		if (isGroup && !modless.endsWith("" + Scajl.TOK_E))
			ctx.parseExcept("Malformed Token Group", "A Token Group must start and end with the '" + Scajl.TOK_S + "' and '" + Scajl.TOK_E + "' characters, respectively.", "From input: " + input);
		
		boolean isExec = modless.startsWith("" + Scajl.SCOPE_S);
		if (isExec && !modless.endsWith("" + Scajl.SCOPE_E))
			ctx.parseExcept("Malformed Executable", "An Executable must start and end with the '" + Scajl.SCOPE_S + "' and '" + Scajl.SCOPE_E + "' characters, respectively.", "From input: " + input);
		
		if (isExec && (isRaw || isRawCont || isUnraw))
			ctx.parseExcept("Invalid executable modifier", "An Executable can only be modified with the 'lookup' reference modifier '" + Scajl.REF + "'", "From input: " + input);
		
		if (isExec && !isRef)
			return ctx.runExecutable(modless, selfCtx).output;
		
		if (isString)
			return new SVString(input, modless, selfCtx);
		if (isArray)
			return new SVArray(input, modless, noUnpack, ctx, selfCtx);
		if (isGroup)
			return new SVTokGroup(input, modless, noUnpack, ctx, selfCtx);
		if (isMap)
			return new SVMap(input, modless, ctx, selfCtx);
		if (isRef)
		{
			if (isExec)
				return new SVExec(input, modless, selfCtx, ctx);
			if (Scajl.ILLEGAL_VAR_MATCHER.matcher(modless).matches())
				ctx.parseExcept("Illegal characters in reference name", "The reference name must be a legal variable name", "From input: " + input);
			return new SVRef(input, modless);
		}
		if (modless.equals(Scajl.ARR_UP))
			return selfCtx == null ? NULL : selfCtx;
		
		ScajlVariable var = ctx.scope.get(modless);
		if (var == null)
			return new SVVal(input, modless, selfCtx);
		if (isRawCont)
			return noUnpack ? var.clone(noUnpack) : var.clone();
		return var;
	}
	
	private static boolean containerCheck(String modless, String input, Scajl ctx)
	{
		BoxTracker ARRTRACK = Scajl.ARRTRACK;
		DelimTracker QTRACK = Scajl.QTRACK;
		boolean isContainer = modless.startsWith("" + Scajl.ARR_S);
		if (!isContainer)
			return false;
		if (!modless.endsWith("" + Scajl.ARR_E))
			ctx.parseExcept("Malformed Container", "An Array or Map must start and end with the '" + Scajl.ARR_S + "' and '" + Scajl.ARR_E + "' characters, respectively", "From input: " + input);
		ARRTRACK.reset();
		QTRACK.reset();
		for (int i = 0; i < modless.length() - 1; i++)
		{
			char parse = input.charAt(i);
			QTRACK.track(parse);
			ARRTRACK.track(parse, QTRACK.inside());
			if (!ARRTRACK.inside())
				ctx.parseExcept("Malformed Container", "Invalid syntax in container element", "From input: " + input);
		}
		return true;
	}
	
	private static int countTrues(boolean[] in)
	{
		int b = 0;
		for (int i = 0; i < in.length; i++)
			b += in[i] ? 1 : 0;
		return b;
	}
	
	private static int val(boolean in)
	{
		return in ? 1 : 0;
	}
	private static boolean val(int in)
	{
		return in > 0;
	}
	
	private static String sRef(ScajlVariable selfReffed)
	{
		return Scajl.SELF_REF + "-%x".formatted(selfReffed.hashCode()).toUpperCase();
	}
	
	///////////////////////////
	
	public static class VarCtx
	{
		public final Supplier<ScajlVariable> get;
		public final Consumer<ScajlVariable> put;
		
		public VarCtx(Supplier<ScajlVariable> get)
		{
			this(get, null);
		}
		public VarCtx(Supplier<ScajlVariable> get, Consumer<ScajlVariable> put)
		{
			this.put = put;
			this.get = get;
		}
	}
}
