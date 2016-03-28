package net.curecoin.sigmax;

/*
 * SigmaX 1.0.0b1 Source Code
 * Copyright (c) 2016 Curecoin Developers
 * Distributed under MIT License
 * Requires Apache Commons Library
 * Supports Java 1.7+
 */

import java.util.*;

import net.curecoin.sigmax.exceptions.TransactionContentException;
import net.curecoin.sigmax.exceptions.TransactionFormatException;

/**
 * TransactionUtility simplifies a few basic tasks dealing with transaction parsing and verification.
 */
public class TransactionUtility
{
    /**
     * Transactions on the SigmaX network from the same address must occur in a certain order, dictated by the signature index.
     * As such, We want to order all transactions from the same address in order.
     * The order of transactions from different addresses does not matter--coins will not be received and spent in the same transaction.
     * 
     * Transactions are sorted in a manner similar to insertion sort.
     * 
     * @param transactionsToSort ArrayList<Transaction> containing all of the transactions to order
     * 
     * @return ArrayList<Transaction> All of the transactions sorted in order for block inclusion, with any self-invalidating transactions removed.
     */
    public static ArrayList<Transaction> sortTransactionsBySignatureIndex(ArrayList<Transaction> transactionsToSort)
    {
    	ArrayList<Transaction> sortedTransactions = new ArrayList<>();
    	
        for (Transaction transactionToSort : transactionsToSort)
        {
            if (sortedTransactions.size() == 0)
            {
                sortedTransactions.add(transactionsToSort.get(0));
            }
            else
            {
                String address = transactionToSort.getSourceAddress();
                long index = transactionToSort.getSignatureIndex();
                boolean added = false;
                for (int j = 0; j < sortedTransactions.size(); j++)
                {
                    if (sortedTransactions.get(j).getSourceAddress().equals(address))
                    {
                        long existingSigIndex = sortedTransactions.get(j).getSignatureIndex();
                        if (index < existingSigIndex)
                        {
                            //Insertion should occur before the currently-studied element
                            sortedTransactions.add(j, transactionToSort);
                            added = true;
                            break;
                        }
                        else if (index == existingSigIndex)
                        {
                            //This should never happen--double-signed transaction. Discard the new one!
                        	System.err.println("TWO TRANSACTIONS FROM ADDRESS " + transactionToSort.getSourceAddress() + " ARE BOTH SIGNED WITH THE SAME INDEX!");
                        	break;
                        }
                    }
                }
                if (!added)
                {
                    sortedTransactions.add(transactionToSort);
                }
            }
        }
        return sortedTransactions;
    }

    /**
     * Signs a Transaction built with the provided sending address and amount, and destination address(es) and amount(s).
     * 
     * @param privateKey The private key for inputAddress
     * @param inputAddress Address to send coins from
     * @param inputAmount Total amount to send
     * @param outputAddresses Addresses to send coins to
     * @param outputAmounts Amounts lined up with addresses to send
     * @param signatureIndex The signature index to use
     * 
     * @return String The full transaction, formatted for use in the Curecoin 2.0 network, including the signature and signature index. Returns null if transaction is incorrect for any reason.
     * @throws TransactionContentException 
     */
    public static Transaction signTransaction(String privateKey, String inputAddress, long inputAmount, ArrayList<String> outputAddresses, ArrayList<Long> outputAmounts, long index) throws TransactionContentException
    {
        if (inputAddress == null || outputAddresses == null || inputAmount <= 0) //Immediate red flags
        {
            return null;
        }
        if (outputAddresses.size() != outputAmounts.size()) //Output addresses and amounts go together, and so each ArrayList must be the same size
        {
            return null;
        }
        String fullTransaction = inputAddress + "," + inputAmount; //The start of the Transaction
        for (int i = 0; i < outputAddresses.size(); i++) //Didn't bother doing address checks here, as they will be conducted in isTransactionValid()
        {
            fullTransaction += ";" + outputAddresses.get(i) + "," + outputAmounts.get(i);
        }
        
        fullTransaction += ";" + MerkleAddressUtility.getMerkleSignature(fullTransaction, privateKey, index, inputAddress) + "," + index; //Now it's actually the 'full transaction'
        System.out.println(fullTransaction);
        try
        {
        	return new Transaction(fullTransaction);
        }
        catch (TransactionFormatException e)
        {
        	e.printStackTrace(); // Should not occur; no formatting issues should be present
        	return null;
        }
    }
}
