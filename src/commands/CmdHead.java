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
	
	public final String inlineIf, inlineFor, name, input;
	public final String[] storing, parentPath;
	public final boolean isInlineIf, isInlineElse, isInlineFor, printHelp, isMemberCmd;
	
	public CmdHead(String firstToken)
	{
		input = firstToken;
		firstToken = StringUtils.endWithout(firstToken, Script.END_SCRIPT);
		String[] inlFor = firstToken.split(Pattern.quote(Script.INLINE_SEP), 2);
		isInlineFor = inlFor.length == 2 && !inlFor[0].isEmpty();
		if (isInlineFor)
		{
			inlineFor = inlFor[0];
			firstToken = inlFor[1];
		}
		else
			inlineFor = null;
		
		isInlineElse = !isInlineFor && firstToken.startsWith(Script.INLINE_SEP);
		if (isInlineElse)
			firstToken = firstToken.substring(1);
		String[] inlIf = firstToken.split(Pattern.quote(Script.INLINE_IF));
		isInlineIf = inlIf.length == 2;
		String[] storeSpl;
		if (isInlineIf)
		{
			inlineIf = inlIf[0];
			storeSpl = inlIf[1].split(Pattern.quote(Script.STORE));
		}
		else
		{
			inlineIf = null;
			storeSpl = firstToken.split(Pattern.quote(Script.STORE));
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
