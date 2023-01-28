package annotations;

import commands.Scajl;

public interface ScajlLogical<T extends ScajlLogical<T>> extends ScajlClone<T>
{
	public static final String NOT = "NOT", OR = "OR", AND = "AND", ALL = "ALL", ANY = "ANY",
										VALUEB = "VALB", SETB = "SETB";
	
	/////////////////////////////////////////
	
	T not(Scajl ctx);
	T or(T other, Scajl ctx);
	T or(boolean bool, Scajl ctx);
	T and(T other, Scajl ctx);
	T and(boolean bool, Scajl ctx);
	default T not(boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).not(ctx); }
	default T or(T other, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).or(other, ctx); }
	default T or(boolean bool, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).or(bool, ctx); }
	default T and(T other, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).and(other, ctx); }
	default T and(boolean bool, boolean inpl, Scajl ctx) { return (inpl ? this : sjClone()).and(bool, ctx); }
	default boolean all(Scajl ctx) { return valueB(ctx); }
	default boolean any(Scajl ctx) { return valueB(ctx); }
	boolean valueB(Scajl ctx);
	T setB(boolean to);
	
	public static interface SLogic<T extends SLogic<T>> extends ScajlLogical<T>
	{
		@Override default T or(T other, Scajl ctx) { return or(other.valueB(ctx), ctx); }
		@Override default T or(boolean bool, Scajl ctx) { return setB(valueB(ctx) || bool); }
		@Override default T and(T other, Scajl ctx) { return and(other.valueB(ctx), ctx); }
		@Override default T and(boolean bool, Scajl ctx) { return setB(valueB(ctx) && bool); }
		@Override default T not(Scajl ctx) { return setB(!valueB(ctx)); }
	}
}
