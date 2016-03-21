package net.curecoin.sigmax.exceptions;

public class BlockFormatException extends Exception
{
	private static final long serialVersionUID = 0xBEEF;
	
	public BlockFormatException(String message)
	{
		super(message);
	}
}
