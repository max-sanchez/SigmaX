package net.curecoin.sigmax.exceptions;

public class TransactionFormatException extends Exception
{
	private static final long serialVersionUID = 0xBEEF;
	
	public TransactionFormatException(String message)
	{
		super(message);
	}
}
