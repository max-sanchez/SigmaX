package net.curecoin.sigmax;

/*
 * SigmaX 1.0.0b1 Source Code
 * Copyright (c) 2016 Curecoin Developers
 * Distributed under MIT License
 * Requires Apache Commons Library
 * Supports Java 1.7+
 */

import java.util.ArrayList;

import net.curecoin.sigmax.exceptions.TransactionContentException;
import net.curecoin.sigmax.exceptions.TransactionFormatException;

/**
 * Class to represent a transaction. Transactions are in the format: 
 * 
 * SourceAddress,SourceAmount;DestAddress1,DestAmount1;DestAddress2,DestAmount2;...;DestAddressN,DestAmountN;signature,index
 * 
 * DestinationAmount1 through DestinationAmountN should sum to SourceAmount or lower. If the sum of the outputs is less
 * than the input, then the remainder is a transaction fee.
 * 
 * At a bare minimum, ALL transactions must have an InputAddress, InputAmount, and one OutputAddress and one OutputAmount
 * Anything left over after all OutputAmounts have been subtracted from the InputAmount is the transaction fee which goes to a block miner.
 * The payment of transaction fees and block rewards are IMPLICIT transactions. They never actually appear on the network. Clients, when processing blocks, automatically adjust the ledger as required. 
 * 
 *
 */
public class Transaction
{
	private String sourceAddress;
	private long sourceAmount;
	
	private ArrayList<Pair<String, Long>> outputs = new ArrayList<>();
	
	private String signature;
	private long signatureIndex;
	
	private long transactionFee;
	
	public Transaction(String transactionData) throws TransactionFormatException, TransactionContentException
	{
		String[] parts = transactionData.split(";");
		if (parts.length < 3) // Need input address + amount, at least one output address + amount, and a signature
		{
			throwFormatException();
		}
		
		long outputAmount = 0;

		boolean processedSource = false;

		String message = "";
		for (int i = 0; i < parts.length - 1; i++)
		{
			if (i == 0)
			{
				message += parts[i];
			}
			else
			{
				message += ";" + parts[i];
			}
			
			String[] subparts = parts[i].split(",");
			if (subparts.length != 2)
			{
				throwFormatException();
			}
			
			if (!MerkleAddressUtility.isAddressFormattedCorrectly(subparts[0]))
			{
				throw new TransactionContentException("The address \"" + subparts[0] + "\" is not valid!");
			}
			if (!processedSource)
			{
				if (!Utilities.isLong(subparts[1]))
				{
					throwFormatException();
				}
				if (!MerkleAddressUtility.isAddressFormattedCorrectly(subparts[0]))
				{
					throw new TransactionContentException("The address \"" + subparts[0] + "\" is not valid!");
				}
				sourceAmount = Long.parseLong(subparts[1]);
				sourceAddress = subparts[0];
				processedSource = true;
			}
			else
			{
				if (!Utilities.isLong(subparts[1]))
				{
					throwFormatException();
				}
				
				long localOutput = Long.parseLong(subparts[1]);
				if (localOutput <= 0)
				{
					throw new TransactionContentException("A transaction cannot have any negative or zero outputs!");
				}
				
				outputAmount += localOutput;
				outputs.add(new Pair<>(subparts[0], localOutput));
			}
		}
		
		String[] signature = parts[parts.length - 1].split(",");

		if (sourceAmount < outputAmount)
		{
			throw new TransactionContentException("Transaction with " + sourceAmount + " in attempts to spend " + outputAmount + "!");
		}
		
		transactionFee = sourceAmount - outputAmount;
		
		if (!Utilities.isLong(signature[2]))
		{
			throwFormatException();
		}
		
		this.signature = signature[0] + "," + signature[1];
		this.signatureIndex = Long.parseLong(signature[2]);
		if (!MerkleAddressUtility.verifyMerkleSignature(message, this.signature, sourceAddress, signatureIndex))
		{
			throw new TransactionContentException("Transaction from " + sourceAddress + " is not accompanied by a valid signature!");
		}
	}

	public String getSourceAddress()
	{
		return sourceAddress;
	}
	
	public long getSourceAmount()
	{
		return sourceAmount;
	}
	
	public ArrayList<Pair<String, Long>> getOutputs()
	{
		return outputs;
	}
	
	public String getSignature()
	{
		return signature;
	}
	
	public long getSignatureIndex()
	{
		return signatureIndex;
	}
	
	public long getTransactionFee()
	{
		return transactionFee;
	}
	
	public String getFlatTransaction()
	{
		String flatTransaction = sourceAddress + "," + sourceAmount;
		for (int i = 0; i < outputs.size(); i++)
		{
			flatTransaction+= ";" + outputs.get(i).getFirst() + "," + outputs.get(i).getSecond();
		}
		flatTransaction += ";" + signature + "," + signatureIndex;
		return flatTransaction;
	}
	
	/**
	 * Determines whether this transaction involves the provided address.
	 * 
	 * @param address The address to check for
	 * @return Whether this transaction involves the provided address
	 */
	public boolean involvesAddress(String address)
	{
		if (sourceAddress.equals(address))
		{
			return true;
		}
		for (Pair<String, Long> output : outputs)
		{
			if (output.getFirst().equals(address))
			{
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Returns a human-readable transaction summary: source amount and address, and all output amounts and addresses.
	 * @return A human-readable transaction summary
	 */
	public String getTransactionSummary()
	{
		String transactionSummary = "";
		transactionSummary += getSourceAmount() + " from " + getSourceAddress() + " sent to: ";
		for (Pair<String, Long> output : outputs)
		{
			transactionSummary += output.getFirst() + "(" + output.getSecond() + " SigmaX) ";
		}
		return transactionSummary;
	}
	
	private void throwFormatException() throws TransactionFormatException
	{
		throw new TransactionFormatException("Transactions must follow the format: "
				+ "SourceAddress:SourceAmount;DestAddress1:DestAmount1;DestAddress2:DestAmount2;...;DestAddressN:DestAmountN;signature:index");
	}
}
