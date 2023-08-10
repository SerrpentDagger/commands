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

package commands.libs;

import annotations.Desc;
import annotations.ScajlClone;
@Desc("A utility class to provide functionality for complex numbers.")
public class Complex implements ScajlClone<Complex>
{
	private double a, b;
	
	public Complex()
	{
		a = 0;
		b = 0;
	}
	
	private Complex(double a, double b)
	{
		this.a = a;
		this.b = b;
	}
	
	public Complex add(Complex other)
	{
		a += other.a;
		b += other.b;
		return this;
	}
	
	public Complex sub(Complex other)
	{
		a -= other.a;
		b -= other.b;
		return this;
	}
	
	public Complex mult(Complex other)
	{
		a = a*other.a - b*other.b;
		b = a*other.b + b*other.a;
		return this;
	}
	
	public Complex pow(double pow)
	{
		double nMag = Math.pow(mag(), pow);
		double nArg = arg() * pow;
		a = nMag * Math.cos(nArg);
		b = nMag * Math.sin(nArg);
		return this;
	}
	
	public Complex scale(double by)
	{
		a *= by;
		b *= by;
		return this;
	}
	
	public Complex conj()
	{
		b = -b;
		return this;
	}

	public Complex divi(Complex other)
	{
		return mult(other.sjClone().conj()).scale(1d / other.magSq());
	}
	
	public double real()
	{
		return a;
	}
	
	public double imaj()
	{
		return b;
	}
	
	public double arg()
	{
		return Math.atan2(b, a);
	}
	
	public double mag()
	{
		return Math.sqrt(magSq());
	}
	
	public double magSq()
	{
		return a*a + b*b;
	}
	
	/////////////////////////////
	
	@Override
	public Complex sjClone()
	{
		return new Complex(a, b);
	}
	
	@Override
	protected Object clone() throws CloneNotSupportedException
	{
		return sjClone();
	}
	
	@Override
	public String toString()
	{
		return "" + a + "+" + b + "i";
	}
	
	/////////////////////////
	
	@Desc("Create a new Complex with the given real and imaginary components.")
	public static Complex ofABi(double a, double b)
	{
		return new Complex(a, b);
	}
	
	@Desc("Create a new Complex with the given magnitude and argument.")
	public static Complex ofRTheta(double r, double theta)
	{
		return new Complex(r * Math.cos(theta), r * Math.sin(theta));
	}
}
