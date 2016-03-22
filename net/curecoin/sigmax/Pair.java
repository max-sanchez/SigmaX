package net.curecoin.sigmax;
/*
 * SigmaX 1.0.0b1 Source Code
 * Copyright (c) 2016 Curecoin Developers
 * Distributed under MIT License
 * Requires Apache Commons Library
 * Supports Java 1.7+
 */

/**
 * Pairs two items together, basically a 2-tuple
 *
 * @param <A>
 * @param <B>
 */
public class Pair <A, B> 
{
	private A a;
	private B b;
	
	public Pair(A a, B b)
	{
		this.a = a;
		this.b = b;
	}
	
	public A getFirst()
	{
		return a;
	}
	
	public B getSecond()
	{
		return b;
	}
	
	public void setFirst(A a)
	{
		this.a = a;
	}
	
	public void setSecond(B b)
	{
		this.b = b;
	}
}
