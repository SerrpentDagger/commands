package commands;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import arrays.AUtils;
import arrays.AUtils.Ind;
import mod.serpentdagger.artificialartificing.utils.group.MixedPair;
import utilities.ArrayUtils;
import utilities.StringUtils;

public abstract class ScajlVariable
{
	public static final SVVal NULL = new SVVal(Script.NULL, null);
	
	///////////////////////////
	
	public final String input, modless;
	protected WeakReference<SVMember> selfCtx;
	
	public ScajlVariable(String input, String modless, SVMember selfCtx)
	{
		this.input = input;
		this.modless = modless;
		this.selfCtx = new WeakReference<>(selfCtx);
	}
	
	public abstract String val(Script ctx);
	public abstract ScajlVariable eval(Script ctx);
	public abstract String raw();
	public VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx)
	{
		String val = getVar(memberAccess[off], false, ctx, selfCtx.get()).val(ctx);
		if (val.equals(Script.ARR_SELF))
			return selfCtx(memberAccess, off, put, ctx);
		if (put || memberAccess != null && off != memberAccess.length)
			ctx.parseExcept("Invalid member access", "The indexed variable is not a type which can be indexed.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
		return new VarCtx(() -> this);
	}
	protected VarCtx selfCtx(String[] memberAccess, int off, boolean put, Script ctx)
	{
		if (put && off == memberAccess.length - 1)
			ctx.parseExcept("Invalid index: " + Script.ARR_SELF, "Cannot set the '" + Script.ARR_SELF + "' value of a variable directly");
		else
		{
			SVMember self = selfCtx.get();
			if (self == null)
			{
				if (off == memberAccess.length - 1)
					return NULL.varCtx(memberAccess, off, put, ctx);
				else
					ctx.parseExcept("Invalid usage of the '%s' keyword.".formatted(Script.ARR_SELF), "The indexed variable is not contained.");
			}
			return self.varCtx(memberAccess, off + 1, put, ctx);
		}
		return null;
	}
	
	@Override
	public String toString()
	{
		return raw();
	}
	
/*	public ScajlVariable[] splitTokens(IntPredicate rawDefault, Script ctx)
	{
		String[] split = Script.tokensOf(input);
		ScajlVariable[] out = new ScajlVariable[split.length];
		if (out.length != 1)
			for (int i = 0; i < out.length; i++)
				out[i] = getVar(split[i], rawDefault.test(i), ctx);
		else
			out[0] = this;
		return out;
	}*/
	
	@Override
	public abstract ScajlVariable clone();
	
	public ScajlVariable clone(boolean noUnpack)
	{
		return clone();
	}
	//////////////////////
	
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
		public String val(Script ctx)
		{
			return modless;
		}

		@Override
		public ScajlVariable eval(Script ctx)
		{
			return this;
		}

		@Override
		public String raw()
		{
			return modless;
		}

		@Override
		public SVVal clone()
		{
			return new SVVal(input, modless, selfCtx.get());
		}
	}
	
	public static class SVRef extends ScajlVariable
	{
		public SVRef(String input, String modless)
		{
			super(input, modless, null);
		}
		
		@Override
		public String val(Script ctx)
		{
			return eval(ctx).val(ctx);
		}
		
		@Override
		public ScajlVariable eval(Script ctx)
		{
			ScajlVariable var = ctx.scope.get(modless);
			return var == null ? NULL : var;
		}
		
		@Override
		public String raw()
		{
			return Script.REF + modless;
		}
		
		@Override
		public SVRef clone()
		{
			return new SVRef(input, modless);
		}
	}
	
	public static class SVString extends ScajlVariable
	{
		public final String unraw;
		
		public SVString(String input, String modless, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			unraw = Script.stringTrim(modless);
		}
		
		@Override
		public String val(Script ctx)
		{
			return unraw;
		}
		
		@Override
		public ScajlVariable eval(Script ctx)
		{
			return this;
		}
		
		@Override
		public String raw()
		{
			return Script.STRING_CHAR + unraw + Script.STRING_CHAR;
		}
		
		@Override
		public SVString clone()
		{
			return new SVString(input, modless, selfCtx.get());
		}
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
		public String val(Script ctx)
		{
			return raw();
		}
		
		@Override
		public ScajlVariable eval(Script ctx)
		{
			return this;
		}
		
		@Override
		public String raw()
		{
			return StringUtils.toString(value, (o) -> o == null ? Script.NULL : o.toString(), "", " | ", "");
		}

		@Override
		public VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx)
		{
			if (off == memberAccess.length - 1 && getVar(memberAccess[off], false, ctx).val(ctx).equals(Script.ARR_LEN))
			{
				if (put)
					ctx.parseExcept("Invalid member access", "Cannot set the '%s' value of an Object directly.".formatted(Script.ARR_LEN));
				return new VarCtx(() -> typeCount);
			}
			return super.varCtx(memberAccess, off, put, ctx);
		}
		
		@Override
		public SVJavObj clone()
		{
			return new SVJavObj(input, modless, selfCtx.get(), value);
		}
	}
	
	protected static abstract class SVMember extends ScajlVariable
	{
		public SVMember(String input, String modless, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
		}
		
		@Override
		public String val(Script ctx)
		{
			return raw();
		}
		
		@Override
		public ScajlVariable eval(Script ctx)
		{
			return this;
		}
		
		@Override
		public VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx)
		{
			SVMember self = selfCtx.get();
			ScajlVariable var = getVar(memberAccess[off], false, ctx, self);
			if (var == self)
			{
				if (put)
					ctx.parseExcept("Invalid index: " + Script.ARR_SELF, "Cannot set the '" + Script.ARR_SELF + "' value of a variable directly");
				if (off == memberAccess.length - 1)
					return new VarCtx(() -> self);
				else
					return self.varCtx(memberAccess, off + 1, put, ctx);
			}
			String val = var.val(ctx);
			return memCtx(memberAccess, off, val, put, ctx);
		}
		
		protected abstract VarCtx memCtx(String[] memberAccess, int off, String accVal, boolean put, Script ctx);
	}
	
	public static class SVMap extends SVMember
	{
		private LinkedHashMap<String, ScajlVariable> map;
		
		public SVMap(String input, String modless, Script ctx, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			String[] elements = Script.arrayElementsOf(modless);
			map = new LinkedHashMap<>(elements.length);
			for (int i = 0; i < elements.length; i++)
			{
				String[] keyVal = Script.syntaxedSplit(elements[i], Script.MAP_KEY_EQ);
				String key = getVar(keyVal[0], true, ctx, this).val(ctx);
				map.put(key, getVar(keyVal[1], false, ctx, this));
			}
		}
		public SVMap(String input, String modless, LinkedHashMap<String, ScajlVariable> map, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			this.map = map;
		}

		@Override
		public VarCtx memCtx(String[] memberAccess, int off, String accVal, boolean put, Script ctx)
		{
			if (off == memberAccess.length - 1)
			{
				if (accVal.equals(Script.ARR_LEN))
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
				ctx.parseExcept("Invalid Map key for continued indexing: " + accVal, "The specified key is missing.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
			return map.get(accVal).varCtx(memberAccess, off + 1, put, ctx);
		}

		@Override
		public String raw()
		{
			String out = "" + Script.ARR_S;
			Iterator<Entry<String, ScajlVariable>> it = map.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<String, ScajlVariable> ent = it.next();
				out += ent.getKey() + Script.MAP_KEY_EQ + ent.getValue().raw() + (it.hasNext() ? Script.ARR_SEP + " " : "");
			}
			return out + Script.ARR_E;
		}

		@Override
		public ScajlVariable clone()
		{
			LinkedHashMap<String, ScajlVariable> deepCopy = new LinkedHashMap<>();
			SVMap clone = new SVMap(input, modless, deepCopy, selfCtx.get());
			Iterator<Entry<String, ScajlVariable>> it = map.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<String, ScajlVariable> ent = it.next();
				ScajlVariable cop;
				deepCopy.put(ent.getKey(), cop = ent.getValue().clone());
				cop.selfCtx = new WeakReference<>(clone);
			}
			return clone;
		}
	}
	
	public static class SVTokGroup extends SVMember
	{
		private ScajlVariable[] array;

		public SVTokGroup(String input, String modless, Script ctx, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			String[] elements = Script.tokensOf(Script.unpack(modless));
			array = new ScajlVariable[elements.length];
			for (int i = 0; i < elements.length; i++)
				array[i] = getVar(elements[i], false, ctx);
		}
		public SVTokGroup(String input, String modless, ScajlVariable[] array, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			this.array = array;
		}
		
		@Override
		public String raw()
		{
			String out = "" + Script.TOK_S;
			for (int i = 0; i < array.length; i++)
				out += array[i].raw() + (i == array.length - 1 ? "" : " ");
			return out + Script.TOK_E;
		}
		
		@Override
		public SVTokGroup clone()
		{
			ScajlVariable[] deepCopy = Arrays.copyOf(array, array.length);
			SVTokGroup clone = new SVTokGroup(input, modless, deepCopy, selfCtx.get());
			for (int i = 0; i < deepCopy.length; i++)
			{
				deepCopy[i] = deepCopy[i].clone();
				deepCopy[i].selfCtx = new WeakReference<>(clone);
			}
			return clone;
		}
		@Override
		public VarCtx memCtx(String[] memberAccess, int off, String accVal, boolean put, Script ctx)
		{
			String val = getVar(memberAccess[off], false, ctx, selfCtx.get()).val(ctx);
			if (val.equals(Script.ARR_SELF))
				return selfCtx(memberAccess, off, put, ctx);
			if (put || memberAccess != null && off != memberAccess.length)
				ctx.parseExcept("Invalid member access on Token Group", "The indexed variable is not a type which can be indexed.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
			return new VarCtx(() -> this);
		}
		
		public ScajlVariable[] getArray()
		{
			return array;
		}
	}
	
	public static class SVArray extends SVMember
	{
		private ScajlVariable[] array;
		private SVVal length;
		public final boolean noUnpack;
		
		public SVArray(String input, String modless, boolean noUnpack, Script ctx, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			String[] elements = Script.arrayElementsOf(modless);
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
		}

		@Override
		public String raw()
		{
			String out = "" + Script.ARR_S;
			for (int i = 0; i < array.length; i++)
				out += array[i].raw() + (i == array.length - 1 ? "" : Script.ARR_SEP + " ");
			return out + Script.ARR_E;
		}
		
		@Override
		public VarCtx memCtx(String[] memberAccess, int off, String accVal, boolean put, Script ctx)
		{
			if (accVal.equals(Script.ARR_LEN) && off == memberAccess.length - 1)
				return new VarCtx(() -> length, (var) ->
				{
					Integer len = CmdArg.INT.parse(var.val(ctx));
					if (len == null)
						ctx.parseExcept("Invalid token resolution for Array length", "Array lengths must be specified as numbers.");
					int oldLen = array.length;
					array = Arrays.copyOf(array, len);
					if (len > oldLen)
						Arrays.fill(array, oldLen, len, NULL);
					length = new SVVal(len, this);
				});
			else
			{
				Integer ind = CmdArg.INT.parse(accVal);
				if (ind == null)
					ctx.parseExcept("Invalid Array index: " + accVal, "Array indices must be numbers.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
				if (ind >= array.length)
					ctx.parseExcept("Invalid Array index: " + ind, "Index out of bounds.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
				if (off == memberAccess.length - 1)
					return new VarCtx(() -> array[ind], (var) ->
					{
						array[ind] = var;
						var.selfCtx = new WeakReference<>(this);
					});
				return array[ind].varCtx(memberAccess, off + 1, put, ctx);
			}
		}
		
		@Override
		public ScajlVariable clone()
		{
			return clone(noUnpack);
		}
		
		@Override
		public SVArray clone(boolean noUnpack)
		{
			ScajlVariable[] deepCopy = Arrays.copyOf(array, array.length);
			SVArray clone = new SVArray(input, modless, deepCopy, noUnpack, selfCtx.get());
			for (int i = 0; i < deepCopy.length; i++)
			{
				deepCopy[i] = deepCopy[i].clone();
				deepCopy[i].selfCtx = new WeakReference<>(clone);
			}
			return clone;
		}
		
		public ScajlVariable[] getArray()
		{
			return array;
		}
	}
	
	public static class SVExec extends ScajlVariable
	{
		public SVExec(String input, String modless, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
		}

		@Override
		public String val(Script ctx)
		{
			return eval(ctx).val(ctx);
		}
		
		@Override
		public ScajlVariable eval(Script ctx)
		{
			return ctx.runExecutable(modless, selfCtx.get()).output;
		}

		@Override
		public String raw()
		{
			return Script.REF + modless;
		}

		@Override
		public SVExec clone()
		{
			return new SVExec(input, modless, selfCtx.get());
		}
	}
	
	////////////////////////
	
	public static void putVar(String name, ScajlVariable var, Script ctx)
	{
		putVar(name, var, ctx, null);
	}
	public static void putVar(String name, ScajlVariable var, Script ctx, SVMember selfCtx)
	{
		String[] arrAcc = Script.syntaxedSplit(name, "" + Script.ARR_ACCESS);
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
			
			toVar.varCtx(access, 1, true, ctx).put.accept(var);
		}
		else
		{
			name = toVar.input;
			if (Script.ILLEGAL_VAR_MATCHER.matcher(name).matches())
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
	
	public static boolean isVar(String input, Script ctx)
	{
		if (input.equals(Script.NULL))
			return false;
		
		MixedPair<boolean[], String> modPair = Script.prefixModsFrom(input, Script.VALID_VAR_MODS);
		String modless = modPair.b();
		boolean[] mods = modPair.a();
		if (mods[1])
			return false;
		ScajlVariable var = ctx.scope.get(modless);
		return var != null;
	}
	
	public static Object[] preParse(String token, Script ctx)
	{
		return preParse(new String[] { token }, ctx);
	}
	
	public static Object[] preParse(String[] tokens, Script ctx)
	{
		Ind ind = new Ind(0);
		Object[] out = new Object[tokens.length];
		ArrayUtils.fillFrom(out, tokens);
		for (int i = 0; i < out.length; i = ind.get())
		{
			String str;
			if (out[i] instanceof String && (str = (String) out[i]).charAt(0) == Script.UNPACK)
			{
				ScajlVariable unp = getVar(str.substring(1), false, ctx).eval(ctx);
				if (unp instanceof SVTokGroup)
					out = AUtils.replace(out, ((SVTokGroup) unp).array, i, ind);
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
	
	public static ScajlVariable getVar(String input, boolean rawDef, Script ctx)
	{
		return getVar(input, rawDef, ctx, null);
	}
	public static ScajlVariable getVar(String input, boolean rawDef, Script ctx, SVMember selfCtx)
	{
		if (input.equals(Script.NULL))
			return NULL;
		
		boolean isNumber = CmdArg.DOUBLE.parse(input) != null;
		if (isNumber)
			return new SVVal(input, selfCtx);
		MixedPair<boolean[], String> modPair = Script.prefixModsFrom(input, Script.VALID_VAR_MODS);
		String modless = modPair.b();
		boolean[] mods = modPair.a();
		boolean isUnraw = mods[0];
		boolean isRaw = mods[1] || (rawDef && !isUnraw);
		boolean isRef = mods[2];
		boolean isRawCont = mods[3];
		boolean isUnpack = mods[4];
		boolean noUnpack = mods[5];
		if (isUnpack)
			ctx.parseExcept("Illegal reference modifier", "The 'unpack' reference modifier is disallowed for this location", "From input: " + input);
		int modCount = countTrues(mods);
		if (modCount > 1 && !(noUnpack && isRawCont))
			ctx.parseExcept("Invalid variable usage", "A maximum of 1 reference modifier is allowed per token, except for the '" + Script.NO_UNPACK + Script.RAW_CONTENTS + "' combination applied to an Array variable.", "From input: " + input);
		if (!isRaw)
		{
			String[] arrAcc = Script.syntaxedSplit(input, "" + Script.ARR_ACCESS);
			if (arrAcc.length > 1)
			{
				ScajlVariable var = getVar(arrAcc[0], false, ctx, selfCtx);
				return var.varCtx(arrAcc, 1, false, ctx).get.get();
			}
		}
		else
			return new SVVal(input, modless, selfCtx);
		
		boolean isString = modless.startsWith("" + Script.STRING_CHAR);
		if (isString && !modless.endsWith("" + Script.STRING_CHAR))
			ctx.parseExcept("Malformed String", "A quoted String must start and end with the '\"' character.", "From input: " + input);
		boolean isContainer = modless.startsWith("" + Script.ARR_S);
		boolean hasEq = isContainer && Script.syntaxedContains(modless.substring(1), Pattern.quote("" + Script.MAP_KEY_EQ), 1);
		if (isContainer && !modless.endsWith("" + Script.ARR_E))
			ctx.parseExcept("Malformed Container", "An Array or Map must start and end with the '" + Script.ARR_S + "' and '" + Script.ARR_E + "' characters, respectively.", "From input: " + input);
		boolean isArray = isContainer && !hasEq;
		if (noUnpack && !(isArray || isRawCont))
			ctx.parseExcept("Illegal reference modifier", "The \"don't unpack\" modifier is only allowed on Array declarations and clonings.", "From input: " + input);
		boolean isMap = isContainer && hasEq;
		if ((isArray || isMap || isString) && (isUnraw || isRef || isRawCont || isUnpack))
			ctx.parseExcept("Illegal reference modifiers", "The only reference modifier allowed on an Array, Map or String declaration is the \"don't unpack\" modifier placed in front of an Array declaration or cloning", "From input: " + input);
			//ctx.parseExcept("Invalid value usage", "A quoted String or an Array or Map must start and end with their respective boxing characters.", "From input: " + input);
			
		boolean isGroup = modless.startsWith("" + Script.TOK_S);
		if (isGroup && !modless.endsWith("" + Script.TOK_E))
			ctx.parseExcept("Malformed Token Group", "A Token Group must start and end with the '" + Script.TOK_S + "' and '" + Script.TOK_E + "' characters, respectively.", "From input: " + input);
		
		boolean isExec = modless.startsWith("" + Script.SCOPE_S);
		if (isExec && !modless.endsWith("" + Script.SCOPE_E))
			ctx.parseExcept("Malformed Executable", "An Executable must start and end with the '" + Script.SCOPE_S + "' and '" + Script.SCOPE_E + "' characters, respectively.", "From input: " + input);
		
		if (isExec && (isRaw || isRawCont || isUnraw))
			ctx.parseExcept("Invalid executable modifier", "An Executable can only be modified with the 'lookup' reference modifier '" + Script.REF + "'", "From input: " + input);
		
		if (isExec && !isRef)
			return ctx.runExecutable(modless, selfCtx).output;
		
		if (isString)
			return new SVString(input, modless, selfCtx);
		if (isArray)
			return new SVArray(input, modless, noUnpack, ctx, selfCtx);
		if (isGroup)
			return new SVTokGroup(input, modless, ctx, selfCtx);
		if (isMap)
			return new SVMap(input, modless, ctx, selfCtx);
		if (isRef)
		{
			if (isExec)
				return new SVExec(input, modless, selfCtx);
			if (Script.ILLEGAL_VAR_MATCHER.matcher(modless).matches())
				ctx.parseExcept("Illegal characters in reference name", "The reference name must be a legal variable name", "From input: " + input);
			return new SVRef(input, modless);
		}
		if (modless.equals(Script.ARR_SELF))
			return selfCtx == null ? NULL : selfCtx;
				//ctx.parseExcept("Illegal usage of the '%s' keyword.".formatted(Script.ARR_SELF), "'%s' can only be used within Container types", "From input: " + input);
		
		ScajlVariable var = ctx.scope.get(modless);
		if (var == null)
			return new SVVal(input, modless, selfCtx);
		if (isRawCont)
			return noUnpack ? var.clone(noUnpack) : var.clone();
		return var;
	}
	
	private static int countTrues(boolean[] in)
	{
		int b = 0;
		for (int i = 0; i < in.length; i++)
			b += in[i] ? 1 : 0;
		return b;
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
