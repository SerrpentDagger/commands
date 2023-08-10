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

import java.util.function.Predicate;

public abstract class ParseTracker
{
	int count = 0, parseInd = 0;
	final String tracked;
	final char escape;
	String recent = "";
	char lastChar;
	boolean wasInside = false;
	
	public ParseTracker(String track, char escape)
	{
		this.tracked = track;
		this.escape = escape;
	}
	
	public ParseTracker(char track, char escape)
	{
		this("" + track, escape);
	}
	
	public int getCount()
	{
		return count;
	}
	
	public void reset()
	{
		count = 0;
		parseInd = 0;
		wasInside = false;
	}
	
	public void pass(char ch)
	{
		wasInside = inside();
		recent += ch;
		lastChar = recent.charAt(0);
		if (recent.length() > tracked.length()) recent = recent.substring(1);
		parseInd++;
	}
	public void pass(String ch) { pass(ch.charAt(0)); }
	public void track(char ch)
	{
		wasInside = inside();
		recent += ch;
		lastChar = ch;
		if (recent.length() > tracked.length()) recent = recent.substring(1);
		trackSub(ch, parseInd > 0 && lastChar == escape);
		parseInd++;
	}
	public void track(String ch) { track(ch.charAt(0)); }
	public void track(char ch, boolean pass)
	{
		if (pass)
			pass(ch);
		else
			track(ch);
	}
	public void track(String ch, boolean pass) { track(ch.charAt(0), pass); }
	protected abstract void trackSub(char ch, boolean escaped);
	public abstract boolean inside();
	public boolean wasInside()
	{
		return wasInside;
	}
	public boolean justEntered()
	{
		return inside() && !wasInside();
	}
	public boolean justLeft()
	{
		return !inside() && wasInside();
	}
	
	//////////////////
	
	public static abstract class BiTracker extends ParseTracker
	{
		protected final String tracked2;
		
		public BiTracker(String start, String end, char escape)
		{
			super(start, escape);
			tracked2 = end;
		}
		public BiTracker(char start, char end, char escape)
		{
			this("" + start, "" + end, escape);
		}
	}
	
	/////////////////////////////////////////
	
	public static class DelimTracker extends ParseTracker
	{
		public DelimTracker(String track, char escape)
		{
			super(track, escape);
		}
		public DelimTracker(char track, char escape)
		{
			super(track, escape);
		}

		@Override
		protected void trackSub(char ch, boolean escaped)
		{
			if (!escaped && recent.equals(tracked))
				count++;
		}
		
		@Override
		public boolean inside()
		{
			return count % 2 == 1;
		}
	}
	
	public static class RepeatTracker extends ParseTracker
	{
		public final int repeatCount;
		public RepeatTracker(char track, char escape, int repeatCount)
		{
			super(track, escape);
			this.repeatCount = repeatCount;
		}

		@Override
		protected void trackSub(char ch, boolean escaped)
		{
			if (!escaped && recent.equals(tracked))
				count++;
			else
				count = 0;
		}

		@Override
		public boolean inside()
		{
			return count >= repeatCount;
		}
		
	}
	
	public static class BoxTracker extends BiTracker
	{
		public BoxTracker(String start, String end, char escape)
		{
			super(start, end, escape);
		}
		public BoxTracker(char start, char end, char escape)
		{
			super(start, end, escape);
		}

		@Override
		protected void trackSub(char ch, boolean escaped)
		{
			if (!escaped)
			{
				if (recent.equals(tracked))
					count++;
				else if (recent.equals(tracked2))
					count--;
			}
		}

		@Override
		public boolean inside()
		{
			return count > 0;
		}
	}
	
	public static class WrapTracker extends BiTracker
	{
		public WrapTracker(String start, String end, char escape)
		{
			super(start, end, escape);
		}
		public WrapTracker(char start, char end, char escape)
		{
			super(start, end, escape);
		}
		
		@Override
		protected void trackSub(char ch, boolean escaped)
		{
			if (!escaped)
			{
				if (recent.equals(tracked))
					count = 1;
				else if (recent.equals(tracked2))
					count = 0;
			}
		}
		
		@Override
		public boolean inside()
		{
			return count == 1;
		}
	}
	
	public static class MultiTracker
	{
		public final ParseTracker[] multi;
		
		public MultiTracker(ParseTracker... multi)
		{
			this.multi = multi;
		}
		
		public void track(char ch, boolean pass)
		{
			for (ParseTracker p : multi)
				p.track(ch, pass);
		}
		public void track(char ch) { track(ch, false); }
		public void track(String ch, boolean pass) { track(ch.charAt(0), pass); }
		public void track(String ch) { track(ch.charAt(0)); }
		
		public void reset()
		{
			for (ParseTracker p : multi)
				p.reset();
		}
		
		public void resetIf(Predicate<ParseTracker> iff)
		{
			for (ParseTracker p : multi)
				if (iff.test(p))
					p.reset();
		}
		
		public boolean insideOne()
		{
			for (ParseTracker p : multi)
				if (p.inside())
					return true;
			return false;
		}
		
		public boolean insideAll()
		{
			for (ParseTracker p : multi)
				if (!p.inside())
					return false;
			return true;
		}
	}
}
