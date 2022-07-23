package commands;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import mod.serpentdagger.artificialartificing.utils.group.MixedPair;
import utilities.StringUtils;

public abstract class ScajlVariable
{
	public static final SVVal NULL = new SVVal(Script.NULL);
	
	///////////////////////////
	
	public final String input, modless;
	
	public ScajlVariable(String input, String modless)
	{
		this.input = input;
		this.modless = modless;
	}
	
	public abstract String val(Script ctx);
	public abstract String raw();
	public VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx)
	{
		if (put || memberAccess != null && off != memberAccess.length)
			ctx.parseExcept("Invalid member access", "The indexed variable is not a type which can be indexed.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
		return new VarCtx(() -> this);
	}
	
	public ScajlVariable[] splitTokens(IntPredicate rawDefault, Script ctx)
	{
		String[] split = Script.tokensOf(input);
		ScajlVariable[] out = new ScajlVariable[split.length];
		if (out.length != 1)
			for (int i = 0; i < out.length; i++)
				out[i] = getVar(split[i], rawDefault.test(i), ctx);
		else
			out[0] = this;
		return out;
	}
	
	@Override
	public abstract ScajlVariable clone();
	//////////////////////
	
	public static class SVVal extends ScajlVariable
	{
		public SVVal(String input, String modless)
		{
			super(input, modless);
		}
		
		public SVVal(String inputModless)
		{
			this(inputModless, inputModless);
		}
		
		public SVVal(double val)
		{
			this("" + val);
		}
		
		@Override
		public String val(Script ctx)
		{
			return modless;
		}

		@Override
		public String raw()
		{
			return modless;
		}

		@Override
		public SVVal clone()
		{
			return new SVVal(input, modless);
		}
	}
	
	public static class SVRef extends ScajlVariable
	{
		public SVRef(String input, String modless)
		{
			super(input, modless);
		}
		
		@Override
		public String val(Script ctx)
		{
			ScajlVariable var = ctx.scope.get(modless);
			return var == null ? NULL.input : var.val(ctx);
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
		
		public SVString(String input, String modless)
		{
			super(input, modless);
			unraw = Script.stringTrim(modless);
		}
		
		@Override
		public String val(Script ctx)
		{
			return unraw;
		}
		
		@Override
		public String raw()
		{
			return Script.STRING_CHAR + unraw + Script.STRING_CHAR;
		}
		
		@Override
		public SVString clone()
		{
			return new SVString(input, modless);
		}
	}
	
	protected static abstract class SVMember extends ScajlVariable
	{
		public final String name;
		
		public SVMember(String input, String modless, String name)
		{
			super(input, modless);
			this.name = name;
		}
		
		@Override
		public String val(Script ctx)
		{
			return name == null ? raw() : name;
		}
		
		@Override
		public abstract VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx);
	}
	
	public static class SVMap extends SVMember
	{
		private LinkedHashMap<String, ScajlVariable> map;
		
		public SVMap(String input, String modless, String name, Script ctx)
		{
			super(input, modless, name);
			String[] elements = Script.arrayElementsOf(modless);
			map = new LinkedHashMap<>(elements.length);
			for (int i = 0; i < elements.length; i++)
			{
				String[] keyVal = Script.syntaxedSplit(elements[i], Script.MAP_KEY_EQ);
				String key = getVar(keyVal[0], true, ctx).val(ctx);
				map.put(key, getVar(keyVal[1], false, ctx));
			}
		}
		public SVMap(String input, String modless, String name, LinkedHashMap<String, ScajlVariable> map)
		{
			super(input, modless, name);
			this.map = map;
		}

		@Override
		public VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx)
		{
			if (memberAccess[off].equals(Script.ARR_LEN) && off == memberAccess.length - 1)
				return new VarCtx(() -> new SVVal(map.size()));
			else if (off == memberAccess.length - 1)
				return new VarCtx(() ->
				{
					if (!map.containsKey(memberAccess[off]))
						return NULL;
					return map.get(memberAccess[off]);
				}, (var) -> map.put(memberAccess[off], var));
			if (!map.containsKey(memberAccess[off]))
				ctx.parseExcept("Invalid Map key for continued indexing: " + memberAccess[off], "The specified key is missing.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
			return map.get(memberAccess[off]).varCtx(memberAccess, off + 1, put, ctx);
		}

		@Override
		public String raw()
		{
			String out = "" + Script.ARR_S;
			Iterator<Entry<String, ScajlVariable>> it = map.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<String, ScajlVariable> ent = it.next();
				out += ent.getKey() + Script.MAP_KEY_EQ + ent.getValue().raw() + (it.hasNext() ? Script.ARR_SEP + " " : Script.ARR_E);
			}
			return out;
		}

		@SuppressWarnings("unchecked")
		@Override
		public ScajlVariable clone()
		{
			return new SVMap(input, modless, null, (LinkedHashMap<String, ScajlVariable>) map.clone());
		}
		
	}
	
	public static class SVArray extends SVMember
	{
		private ScajlVariable[] array;
		private SVVal length;
		
		public SVArray(String input, String modless, String name, Script ctx)
		{
			super(input, modless, name);
			String[] elements = Script.arrayElementsOf(modless);
			array = new ScajlVariable[elements.length];
			for (int i = 0; i < elements.length; i++)
				array[i] = getVar(elements[i], false, ctx);
			length = new SVVal(array.length);
		}
		public SVArray(String input, String modless, String name, ScajlVariable[] array)
		{
			super(input, modless, name);
			this.array = array;
			length = new SVVal(array.length);
		}
		public SVArray(String name, ScajlVariable[] array)
		{
			this(name, name, name, array);
		}

		@Override
		public String raw()
		{
			String out = "" + Script.ARR_S;
			for (int i = 0; i < array.length; i++)
				out += array[i].raw() + (i == array.length - 1 ? Script.ARR_E : Script.ARR_SEP + " ");
			return out;
		}
		
		@Override
		public VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx)
		{
			if (memberAccess[off].equals(Script.ARR_LEN) && off == memberAccess.length - 1)
				return new VarCtx(() -> length, (var) ->
				{
					Integer len = CmdArg.INT.parse(var.val(ctx));
					if (len == null)
						ctx.parseExcept("Invalid token resolution for Array length", "Array lengths must be specified as numbers.");
					int oldLen = array.length;
					array = Arrays.copyOf(array, len);
					if (len > oldLen)
						Arrays.fill(array, oldLen, len, NULL);
					length = new SVVal(len);
				});
			else
			{
				Integer ind = CmdArg.INT.parse(memberAccess[off]);
				if (ind == null)
					ctx.parseExcept("Invalid Array index: " + memberAccess[off], "Array indices must be numbers.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
				if (ind >= array.length)
					ctx.parseExcept("Invalid Array index: " + ind, "Index out of bounds.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
				if (off == memberAccess.length - 1)
					return new VarCtx(() -> array[ind], (var) -> array[ind] = var);
				return array[ind].varCtx(memberAccess, off + 1, put, ctx);
			}
		}
		
		@Override
		public SVArray clone()
		{
			return new SVArray(input, modless, null, Arrays.copyOf(array, array.length));
		}
	}
	
	public static class SVExec extends ScajlVariable
	{
		public SVExec(String input, String modless)
		{
			super(input, modless);
		}

		@Override
		public String val(Script ctx)
		{
			return ctx.runExecutable(modless).output;
		}

		@Override
		public String raw()
		{
			return Script.REF + modless;
		}

		@Override
		public SVExec clone()
		{
			return new SVExec(input, modless);
		}
	}
	
	////////////////////////
	
	public static void putVar(String name, ScajlVariable var, Script ctx)
	{
		String[] arrAcc = Script.syntaxedSplit(name, "" + Script.ARR_ACCESS);
		ScajlVariable toVar = getVar(arrAcc[0], true, ctx);
		if (arrAcc.length > 1)
			toVar.varCtx(arrAcc, 1, true, ctx).put.accept(var);
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
	
	public static ScajlVariable getVar(String input, boolean rawDef, Script ctx)
	{
		if (input.equals(Script.NULL))
			return NULL;
		
		boolean isNumber = CmdArg.DOUBLE.parse(input) != null;
		if (isNumber)
			return new SVVal(input);
		MixedPair<boolean[], String> modPair = Script.prefixModsFrom(input, Script.VALID_VAR_MODS);
		String modless = modPair.b();
		boolean[] mods = modPair.a();
		boolean isUnraw = mods[0];
		boolean isRaw = mods[1] || (rawDef && !isUnraw);
		boolean isRef = mods[2];
		boolean isRawCont = mods[3];
		int modCount = countTrues(mods);
		if (modCount > 1)
			ctx.parseExcept("Invalid variable usage", "A maximum of 1 reference modifier is allowed per token");
		String[] arrAcc = Script.syntaxedSplit(modless, "" + Script.ARR_ACCESS);
		if (arrAcc.length > 1)
		{
			ScajlVariable var = ctx.scope.get(arrAcc[0]);
			if (var == null)
				return NULL;
			return var.varCtx(arrAcc, 1, false, ctx).get.get();
		}
		
		boolean isString = modless.startsWith("" + Script.STRING_CHAR);
		if (isString && !modless.endsWith("" + Script.STRING_CHAR))
			ctx.parseExcept("Malformed String", "A quoted String must start and end with the '\"' character.");
		boolean hasEq = Script.syntaxedContains(modless, Pattern.quote("" + Script.MAP_KEY_EQ), 1);
		boolean isContainer = modless.startsWith("" + Script.ARR_S);
		if (isContainer && !modless.endsWith("" + Script.ARR_E))
			ctx.parseExcept("Malformed Container", "An Array or Map must start and end with the '" + Script.ARR_S + "' and '" + Script.ARR_E + "' characters, respectively.");
		boolean isArray = isContainer && !hasEq;
		boolean isMap = isContainer && hasEq;
		
		if ((isArray || isMap || isString) && modCount > 0)
			ctx.parseExcept("Invalid value usage", "A quoted String or an Array or Map must start and end with their respective boxing characters.");
		
		boolean isExec = modless.startsWith("" + Script.SCOPE_S);
		if (isExec && !modless.endsWith("" + Script.SCOPE_E))
			ctx.parseExcept("Malformed Executable", "An Executable must start and end with the '" + Script.SCOPE_S + "' and '" + Script.SCOPE_E + "' characters, respectively.");
		
		if (isExec && (isRaw || isRawCont || isUnraw))
			ctx.parseExcept("Invalid executable modifier", "An Executable can only be modified with the 'reference' reference modifier '" + Script.REF + "'.");
		
		if (isExec && !isRef)
			return getVar(ctx.runExecutable(modless).output, false, ctx);
		
		if (isString)
			return new SVString(input, modless);
		if (isArray)
			return new SVArray(input, modless, null, ctx);
		if (isMap)
			return new SVMap(input, modless, null, ctx);
		if (isRaw)
			return new SVVal(input, modless);
		if (isRef)
		{
			if (isExec)
				return new SVExec(input, modless);
			if (Script.ILLEGAL_VAR_MATCHER.matcher(modless).matches())
				ctx.parseExcept("Illegal characters in reference name", "The reference name must be a legal variable name.");
			return new SVRef(input, modless);
		}
		
		ScajlVariable var = ctx.scope.get(modless);
		if (var == null)
			return new SVVal(input, modless);
		if (isRawCont)
			return var.clone();
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
