package net.curecoin.sigmax.exceptions;

public class TransactionContentException extends Exception
{
	private static final long serialVersionUID = 0xBEEF;
	
	public TransactionContentException(String message)
	{
		super(message);
	}
}
