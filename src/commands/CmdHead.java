package commands;

import java.util.ArrayList;
import java.util.regex.Pattern;

import commands.ParseTracker.BoxTracker;
import commands.ParseTracker.DelimTracker;
import utilities.StringUtils;

public class CmdHead
{
	private static final DelimTracker QTRACK = new DelimTracker(Scajl.STRING_CHAR, Scajl.ESCAPE_CHAR);
	private static final BoxTracker CURLTRACK = new BoxTracker(Scajl.SCOPE_S, Scajl.SCOPE_E, Scajl.ESCAPE_CHAR);
	
	public final String inlineIf, inlineFor, inlineWhile, name, input;
	public final String[] storing, parentPath;
	public final boolean isInlineIf, isInlineElse, isInlineFor, isInlineWhile, printHelp, isMemberCmd;
	
	public CmdHead(String firstToken)
	{
		input = firstToken;
		firstToken = StringUtils.endWithout(firstToken, Scajl.END_SCRIPT);
		String[] inlLoop = Scajl.syntaxedSplit(firstToken, Scajl.INLINE_SEP, 2);
		
		isInlineFor = inlLoop.length == 2 && !firstToken.startsWith(Scajl.INLINE_SEP);
		if (isInlineFor)
		{
			inlineFor = inlLoop[0];
			firstToken = inlLoop[1];
		}
		else
			inlineFor = null;
		
		isInlineWhile = inlLoop.length == 2 && firstToken.startsWith(Scajl.INLINE_SEP);
		if (isInlineWhile)
		{
			inlineWhile = inlLoop[0];
			firstToken = inlLoop[1];
		}
		else
			inlineWhile = null;
		
		isInlineElse = !isInlineFor && firstToken.startsWith(Scajl.INLINE_SEP);
		if (isInlineElse)
			firstToken = firstToken.substring(1);
		String[] inlIf = Scajl.syntaxedSplit(firstToken, Scajl.INLINE_IF);
		isInlineIf = inlIf.length == 2;
		String[] storeSpl;
		if (isInlineIf)
		{
			inlineIf = inlIf[0];
			storeSpl = Scajl.syntaxedSplit(inlIf[1], Scajl.STORE);
		}
		else
		{
			inlineIf = null;
			storeSpl = Scajl.syntaxedSplit(firstToken, Scajl.STORE);
		}
		storing = new String[storeSpl.length - 1];
		for (int i = 1; i < storeSpl.length; i++)
			storing[i - 1] = storeSpl[i];
		String preName = StringUtils.endWithout(storeSpl[0], Scajl.HELP_CHAR_STR);
		printHelp = storeSpl[0].endsWith(Scajl.HELP_CHAR_STR);
		parentPath = getParentPath(preName);
		name = parentPath[parentPath.length - 1];
		isMemberCmd = parentPath.length > 1;
	}
	
	private static final Pattern PATH_BREAKER = Pattern.compile("[" + Pattern.quote(Scajl.MEMBER_ACCESS) + "\\s]");
	private static String[] getParentPath(String preName)
	{
		QTRACK.reset();
		CURLTRACK.reset();
		
		ArrayList<String> out = new ArrayList<>();
		
		String building = "";
		for (int i = 0; i < preName.length(); i++)
		{
			char parse = preName.charAt(i);
			
			QTRACK.track(parse);
			CURLTRACK.track(parse, QTRACK.inside());
			
			if (QTRACK.inside() || CURLTRACK.inside() || (!PATH_BREAKER.matcher("" + parse).matches()))
			{
				building += parse;
				if (i == preName.length() - 1)
					out.add(building);
			}
			else if (!building.isEmpty())
			{
				out.add(building);
				building = "";
			}
		}
		return out.toArray(new String[out.size()]);
	}
}
