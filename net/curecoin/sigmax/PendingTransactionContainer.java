package net.curecoin.sigmax;

/*
 * SigmaX 1.0.0b1 Source Code
 * Copyright (c) 2016 Curecoin Developers
 * Distributed under MIT License
 * Requires Apache Commons Library
 * Supports Java 1.7+
 */

import java.util.*;

/**
 * This class offers basic functionality for storing transactions until they make it into a block.
 * It could be just an ArrayList<String> inside of MainClass, however it seemed easier and more OOP-ish to give it its own object.
 * Adding future functionality to pending transaction pool management is much easier when it has its own object. 
 */
public class PendingTransactionContainer
{
    public ArrayList<Transaction> pendingTransactions;
    public Blockchain blockchain;

    //ArrayList holding objects that pair addresses with their pending transaction amounts, so transactions above an account's spendable balance are rejected.
    public ArrayList<Pair<String, Long>> accountBalanceDeltaTables;
    /**
     * Constructor for PendingTransactionContainer sets up required ArrayList for holding transactions. The database manager object is passed in, for checking balances
     * when a transaction is being added.
     */
    public PendingTransactionContainer(Blockchain blockchain)
    {
    	this.blockchain = blockchain;
        this.pendingTransactions = new ArrayList<>();
        this.accountBalanceDeltaTables = new ArrayList<>();
    }

    /**
     * Adds a transaction to the pending transaction list if it is formatted correctly and accompanied by a correct signature. Does not check for account balances!
     * Rejects duplicate transactions.
     * Transaction format: 
     * Additional work in the future on this method will include keeping track of signature indexes and prioritizing lower-index transactions.
     * 
     * @param transaction Transaction to add
     * 
     * @return boolean Whether adding the transaction was valid
     */
    public boolean addTransaction(Transaction transaction)
    {
        try
        {
            for (int i = 0; i < pendingTransactions.size(); i++)
            {
                if (pendingTransactions.get(i).equals(transaction))
                {
                    return false;
                }
            }
            
            //We need to check to make sure the input address isn't sending coins they don't own.
            String inputAddress = transaction.getSourceAddress();
            long inputAmount = transaction.getSourceAmount();
            //Check for the outstanding outgoing amount for this address
            long outstandingOutgoingAmount = 0L;
            int indexOfDelta = -1;
            for (int i = 0; i < accountBalanceDeltaTables.size(); i++)
            {
                if (accountBalanceDeltaTables.get(i).getFirst().equals(inputAddress))
                {
                    outstandingOutgoingAmount = accountBalanceDeltaTables.get(i).getSecond();
                    indexOfDelta = i;
                    break;
                }
            }
            long previousBalance = blockchain.getAddressBalance(inputAddress);
            if (previousBalance < inputAmount + outstandingOutgoingAmount)
            {
                System.out.println("Account " + inputAddress + " tried to spend " + inputAmount + " but only had " + (previousBalance - outstandingOutgoingAmount) + " coins.");
                return false; //Account does not have the coins to spend!
            }
            if (indexOfDelta >= 0)
            {
                accountBalanceDeltaTables.get(indexOfDelta).setSecond(accountBalanceDeltaTables.get(indexOfDelta).getSecond() + inputAmount);
            }
            else
            {
                accountBalanceDeltaTables.add(new Pair<String, Long>(inputAddress, inputAmount)); //No existing entry in the pending delta tables, so we create an ew one
            }
            pendingTransactions.add(transaction); //Can only get to here if the transaction is valid, accounted for, and the balance checks out. 
            String flat = transaction.getFlatTransaction();
            System.out.println("Added transaction " + flat.substring(0, 20) + "..." + flat.substring(flat.length() - 20, flat.length()));
        } catch (Exception e)
        {
            System.out.println("An exception has occurred...");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Self-explanatory method called whenever the daemon desires to reset the pending transaction pool to be blank.
     */
    public void reset()
    {
        pendingTransactions = new ArrayList<>();
        accountBalanceDeltaTables = new ArrayList<>();
    }

    /**
     * Removes an identical transaction from the pending transactions pool
     * 
     * @param transaction The transaction to remove
     * 
     * @return boolean Whether removal was successful
     */
    public boolean removeTransaction(Transaction transaction)
    {
        for (int i = 0; i < pendingTransactions.size(); i++)
        {
            if (pendingTransactions.get(i).equals(transaction))
            {
                pendingTransactions.remove(i);
                return true;
            }
        }
        return false; //Transaction was not found in pending transaction pool
    }

    /**
     * This method is the most useful method in this class--it allows the mass removal of all transactions from the pending transaction pool that were included
     * in a network block, all in one call.
     * 
     * @param block The block holding transactions to remove
     * 
     * @return boolean Whether all transactions in the block were successfully removed
     */
    public boolean removeTransactionsInBlock(Block block)
    {
        try
        {
            /* 
             * We are removing only transactions that match the exact String from the block. If the block validation fails, NO transactions are removed from the transaction pool.
             */
            ArrayList<Transaction> transactions = block.transactions;
            boolean allSuccessful = true;
            for (int i = 0; i < transactions.size(); i++)
            {
                if (!removeTransaction(transactions.get(i)))
                {
                    allSuccessful = false; //This might happen if a transaction was in a block before it made it across the network to a peer, so not always a big deal!
                }
            }
            return allSuccessful;
        } catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * This method scans through all of the pending transactions to calculate the total (net) balance change pending on an address. A negative value represents
     * coins that were sent from the address in question, and a positive value represents coins awaiting confirmations to arrive. 
     * 
     * @param address The address to search the pending transaction pool for
     * 
     * @return long The pending total (net) change for the address in question
     */
    public long getPendingBalance(String address)
    {
        long totalChange = 0L;
        for (int i = 0; i < pendingTransactions.size(); i++)
        {
            Transaction transaction = pendingTransactions.get(i);
            try
            {
                if (transaction.involvesAddress(address))
                {
                    String senderAddress = transaction.getSourceAddress();
                    if (senderAddress.equals(address))
                    {
                        totalChange -= transaction.getSourceAmount();
                    }
                    for (Pair<String, Long> output : transaction.getOutputs())
                    {
                        if (output.getFirst().equals(address))
                        {
                            totalChange += output.getSecond();
                        }
                    }
                }
            } catch (Exception e)
            {
                e.printStackTrace();
                System.err.println("Major problem: Transaction in the pending transaction pool is incorrectly formatted!");
                System.err.println("Transaction in question: " + transaction);
            }
        }
        return totalChange;
    }
}
