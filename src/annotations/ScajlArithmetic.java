package annotations;

import commands.Scajl;

/**
 * All non-default methods are inplace. The rest can default to cloning, and then using the inplace.
 * @author SerpentDagger
 *
 * @param <T>
 */
public interface ScajlArithmetic<T extends ScajlArithmetic<T>> extends ScajlClone<T>
{
	public static final String ADD = "ADD", ADDD = "ADDD", SUB = "SUB", SUBD = "SUBD",
										MULT = "MULT", MULTD = "MULTD", DIVI = "DIVI", DIVID = "DIVID",
										MOD = "MOD", MODD = "MODD", MAX = "MAX", MIN = "MIN",
										VALUED = "VALD", SETD = "SETD";
	
	public static Double dumbParse(String input)
	{
		try
		{
			return Double.parseDouble(input);
		}
		catch (NumberFormatException e)
		{}
		return null;
	}
	
	public static Integer dumbParseI(String input)
	{
		Double d = dumbParse(input);
		return d == null ? null : Math.round(Math.round(d));
	}
	
	//////////////////////////////
	
	T add(T other, Scajl ctx);
	T add(double num, Scajl ctx);
	default T sub(T other, Scajl ctx) { return add(other.mult(-1, false, ctx), ctx); }
	default T sub(double num, Scajl ctx) { return add(-num, ctx); }

	default T add(T other, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).add(other, ctx); }
	default T add(double num, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).add(num, ctx); }
	default T sub(T other, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).sub(other, ctx); }
	default T sub(double num, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).sub(num, ctx); }
	default T inc(boolean inpl, Scajl ctx) { return add(1, inpl, ctx); }
	default T dec(boolean inpl, Scajl ctx) { return add(-1, inpl, ctx); }
	
	
	T mult(T other, Scajl ctx);
	T mult(double num, Scajl ctx);
	default T divi(T other, Scajl ctx) { return mult(other.mult(-1, false, ctx), ctx); }
	default T divi(double num, Scajl ctx) { return mult(1d / num, ctx); }

	default T mult(T other, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).mult(other, ctx); }
	default T mult(double num, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).mult(num, ctx); }
	default T divi(T other, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).divi(other, ctx); }
	default T divi(double num, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).divi(num, ctx); }
	default T negate(boolean inpl, Scajl ctx) { return mult(-1, inpl, ctx); }
	
	default T mod(T other, boolean inpl, Scajl ctx) { throw new UnsupportedOperationException("'mod' by other is unsupported for this Variable."); }
	default T mod(double num, boolean inpl, Scajl ctx) { throw new UnsupportedOperationException("'mod' by number is unsupported for this Variable."); }
	
	default double max(Scajl ctx) { return valueD(ctx); }
	default double min(Scajl ctx) { return valueD(ctx); }
	double valueD(Scajl ctx);
	T setD(double to);
	
	public static interface SArith<T extends SArith<T>> extends ScajlArithmetic<T>
	{
		@Override default T add(T other, Scajl ctx) { return add(other.valueD(ctx), ctx); }
		@Override default T add(double num, Scajl ctx) { return setD(num + valueD(ctx)); }
		@Override default T mult(T other, Scajl ctx) { return mult(other.valueD(ctx), ctx); }
		@Override default T mult(double num, Scajl ctx) { return setD(num + valueD(ctx)); }
	}
}
