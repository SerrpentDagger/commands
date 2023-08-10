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

import java.awt.AWTException;
import java.io.File;
import java.io.FileNotFoundException;

import annotations.Desc;
import annotations.Expose;
import annotations.Reluctant;
import commands.Scajl.ScajlException;
import commands.Label.LabelTree;

@Reluctant
@Desc("A handle to a loaded Scajl script.")
public class Script
{
	public final Scajl ctx, scajl;
	
	public Script(Scajl scajlIn)
	{
		this(null, scajlIn);
	}
	
	public Script(Scajl ctx, String name)
	{
		File scr = Scajl.getScriptFile(name);
		if (scr == null)
			ctx.parseExcept("Specified Script does not exist", name);
		this.ctx = ctx;
		Scajl scj;
		try
		{
			scj = new Scajl(scr);
			ctx.transferCallbacks(scj);
			scj.putVar(Scajl.PARENT, Scajl.valOf(ctx.name));
		}
		catch (FileNotFoundException | AWTException e)
		{
			ctx.getExceptionCallback().accept(e);
			scj = null;
		}
		scajl = scj;
	}
	
	public Script(Scajl ctx, Scajl scajlIn)
	{
		this.ctx = ctx;
		this.scajl = scajlIn;
		if (ctx != null)
		{
			ctx.transferCallbacks(scajlIn);
			scajl.putVar(Scajl.PARENT, Scajl.valOf(ctx.name));
		}
	}
	
	public Script imprt(VarSet... sets)
	{
		LabelTree onImp = scajl.labelTree.getFor(Scajl.IMPORT_LABEL);
		if (onImp != null)
			scajl.runFrom(onImp.root, sets);
		return this;
	}
	
	@Expose
	@Desc("Run this Script from the start of the file, using the given variables, and return the result.")
	public ScajlVariable run(VarSet... sets)
	{
		scajl.run(sets);
		return scajl.prev();
	}
	
	@Expose
	@Desc("Run this Script from the given label, using the given variables, and return the result.")
	public ScajlVariable call(String label, VarSet... sets)
	{
		Label lab = scajl.getLabel(label);
		if (lab != null)
			scajl.runFrom(lab, sets);
		else
		{
			Scajl ctx = (this.ctx == null ? scajl : this.ctx);
			String str = ctx.getParseExceptString("Specified label does not exist", label, "Script: " + scajl.name);
			if (this.ctx == null)
				ctx.getParseExceptionCallback().accept(new ScajlException(str), str);
			else
				throw new ScajlException(str);
		}
		return scajl.prev();
	}
	
	@Expose
	@Desc("Evaluates the given input in this imported Script.")
	public ScajlVariable in(String input)
	{
		return scajl.getVar(input, false, null);
	}
	
	@Override
	public String toString()
	{
		return scajl.path;
	}
}
