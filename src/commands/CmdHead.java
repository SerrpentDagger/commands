package commands;

import java.util.regex.Pattern;

import utilities.StringUtils;

public class CmdHead
{
	public final String inlineIf, name, input;
	public final String[] storing, parentPath;
	public final boolean isInlineIf, isInlineElse, printHelp, isMemberCmd;
	
	public CmdHead(String firstToken)
	{
		input = firstToken;
		firstToken = StringUtils.endWithout(firstToken, Script.END_SCRIPT);
		isInlineElse = firstToken.startsWith(Script.INLINE_ELSE);
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
		parentPath = preName.split(Pattern.quote(Script.MEMBER_ACCESS));
		name = parentPath[parentPath.length - 1];
		isMemberCmd = parentPath.length > 1;
	}
}
