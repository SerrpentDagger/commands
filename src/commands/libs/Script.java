package commands.libs;

import java.awt.AWTException;
import java.io.File;
import java.io.FileNotFoundException;

import annotations.Desc;
import annotations.Expose;
import annotations.Reluctant;
import commands.Label;
import commands.Scajl;
import commands.ScajlVariable;
import commands.VarSet;

@Reluctant
@Desc("A handle to a loaded Scajl script.")
public class Script
{
	public final Scajl ctx, scajl;
	
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
		ctx.transferCallbacks(scajlIn);
		scajl.putVar(Scajl.PARENT, Scajl.valOf(ctx.name));
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
			ctx.parseExcept("Specified label does not exist", label, "Script: " + scajl.name);
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
