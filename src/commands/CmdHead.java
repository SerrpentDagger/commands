package commands;

import java.util.ArrayList;
import java.util.regex.Pattern;

import commands.ParseTracker.BoxTracker;
import commands.ParseTracker.DelimTracker;
import utilities.StringUtils;

public class CmdHead
{
	private static final DelimTracker QTRACK = new DelimTracker(Script.STRING_CHAR, Script.ESCAPE_CHAR);
	private static final BoxTracker CURLTRACK = new BoxTracker(Script.SCOPE_S, Script.SCOPE_E, Script.ESCAPE_CHAR);
	
	public final String inlineIf, inlineFor, inlineWhile, name, input;
	public final String[] storing, parentPath;
	public final boolean isInlineIf, isInlineElse, isInlineFor, isInlineWhile, printHelp, isMemberCmd;
	
	public CmdHead(String firstToken)
	{
		input = firstToken;
		firstToken = StringUtils.endWithout(firstToken, Script.END_SCRIPT);
		String[] inlLoop = Script.syntaxedSplit(firstToken, Script.INLINE_SEP, 2);
		
		isInlineFor = inlLoop.length == 2 && !firstToken.startsWith(Script.INLINE_SEP);
		if (isInlineFor)
		{
			inlineFor = inlLoop[0];
			firstToken = inlLoop[1];
		}
		else
			inlineFor = null;
		
		isInlineWhile = inlLoop.length == 2 && firstToken.startsWith(Script.INLINE_SEP);
		if (isInlineWhile)
		{
			inlineWhile = inlLoop[0];
			firstToken = inlLoop[1];
		}
		else
			inlineWhile = null;
		
		isInlineElse = !isInlineFor && firstToken.startsWith(Script.INLINE_SEP);
		if (isInlineElse)
			firstToken = firstToken.substring(1);
		String[] inlIf = Script.syntaxedSplit(firstToken, Script.INLINE_IF);
		isInlineIf = inlIf.length == 2;
		String[] storeSpl;
		if (isInlineIf)
		{
			inlineIf = inlIf[0];
			storeSpl = Script.syntaxedSplit(inlIf[1], Script.STORE);
		}
		else
		{
			inlineIf = null;
			storeSpl = Script.syntaxedSplit(firstToken, Script.STORE);
		}
		storing = new String[storeSpl.length - 1];
		for (int i = 1; i < storeSpl.length; i++)
			storing[i - 1] = storeSpl[i];
		String preName = StringUtils.endWithout(storeSpl[0], Script.HELP_CHAR_STR);
		printHelp = storeSpl[0].endsWith(Script.HELP_CHAR_STR);
		parentPath = getParentPath(preName);
		name = parentPath[parentPath.length - 1];
		isMemberCmd = parentPath.length > 1;
	}
	
	private static final Pattern PATH_BREAKER = Pattern.compile("[" + Pattern.quote(Script.MEMBER_ACCESS) + "\\s]");
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
