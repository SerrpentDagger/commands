package annotations;

public interface ScajlLogical<T extends ScajlLogical<T>> extends ScajlClone<T>
{
	T not(boolean inpl);
	T or(boolean inpl);
	T and(boolean inpl);
	default boolean all() { return valueB(); }
	default boolean any() { return valueB(); }
	boolean valueB();
}
