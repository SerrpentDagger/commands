package annotations;

public interface ScajlArithmetic<T extends ScajlArithmetic<T>> extends ScajlClone<T>
{
	T add(T other, boolean inpl);
	T add(double num, boolean inpl);
	default T sub(T other, boolean inpl) { return add(other.mult(-1, false), inpl); }
	default T sub(double num, boolean inpl) { return add(-num, inpl); }
	default T inc(boolean inpl) { return add(1, inpl); }
	default T dec(boolean inpl) { return add(-1, inpl); }
	
	T mult(T other, boolean inpl);
	T mult(double num, boolean inpl);
	default T divi(T other, boolean inpl) { return mult(other.mult(-1, false), inpl); }
	default T divi(double num, boolean inpl) { return mult(1d / num, inpl); }
	default T negate(boolean inpl) { return mult(-1, inpl); }
	
	T mod(T other, boolean inpl);
	T mod(double num, boolean inpl);
	
	default double max() { return valueD(); }
	default double min() { return valueD(); }
	double valueD();
}
