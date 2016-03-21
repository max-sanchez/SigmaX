package net.curecoin.sigmax;

/*
 * SigmaX 1.0.0b1 Source Code
 * Copyright (c) 2016 Curecoin Developers
 * Distributed under MIT License
 * Requires Apache Commons Library
 * Supports Java 1.7+
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * This class facilitates blockchain organization. Addition of a block automatically
 * triggers chain fork checking, so only one blockchain object needs to be maintained.
 * 
 * All blocks added to the blockchain will be placed onto their own parent block, but when queried the blockchain
 * object will return information pertaining to the largest chain. If two chains exist of equal length, then the one with
 * the higher PoW difficulty is the "longest."
 * 
 * 
 */
public class Blockchain
{
	private static final long BLOCK_MINING_REWARD = 5_000_000_000L;
	
	// The number of recent blocks to store
	private int chainCutoff = 500;
	
	private ArrayList<ArrayList<Block>> chains = new ArrayList<ArrayList<Block>>();
	private ArrayList<Block> blockQueue = new ArrayList<Block>();
	
	private LedgerManager ledgerManager;
	
	private String dbFolder;
	
	public Blockchain(String dbFolder)
	{
		this.dbFolder = dbFolder;
		this.ledgerManager = new LedgerManager(dbFolder + "/AccountBalances.bal");
	}
	
	public Blockchain(String dbFolder, int chainCutoff)
	{
		this(dbFolder);
		this.chainCutoff = chainCutoff;
	}
	
	/**
	 * Attempt to add a block to the blockchain, and if successful (and the block is not from the blockchain file),
	 * save it to the blockchain file.
	 * 
	 * Blocks from separate forks can be safely saved in the same blockchain file, since forks will be re-resolved
	 * when they are loaded again.
	 * 
	 * @param block The block to add
	 * @param fromBlockchainFile Whether this block already exists in the blockchain file
	 * @return Whether adding the block was successful
	 */
	public boolean addBlockAndSave(Block block, boolean fromBlockchainFile)
	{
		if (addBlock(block, fromBlockchainFile))
		{
			if (!fromBlockchainFile)
			{
				writeBlockToFile(block);
			}
			ledgerManager.writeToFile();
			return true;
		}
		return false;
	}
	
	/**
	 * Adds a block to any of the existing chains if it fits. Updates the ledger as appropriate.
	 * 
	 * @param block The block to attempt to add
	 * @return Whether the block was successfully added
	 */
	private boolean addBlock(Block block, boolean fromBlockchainFile)
	{	
		if (!block.isMinerHashBelowTarget())
		{
			return false;
		}
		
		if (blockQueue.size() > 0)
		{
			boolean addedABlock = true;
			while (addedABlock)
			{
				addedABlock = false;
				for (int i = 0; i < blockQueue.size(); i++)
				{
					if (addBlock(blockQueue.get(i), false))
					{
						addedABlock = true;
						System.out.println("Added a block (#" + blockQueue.get(i).blockNum + ") from the queue!");
						blockQueue.remove(i);
						i--;
					}
				}
			}
		}
		
		System.out.println("Attempting to add block #" + block.blockNum + " with the hash of " + block.blockHash + "...");
		try
		{
			if (chains.size() == 0) // We should be adding the genesis block.
			{
				chains.add(new ArrayList<Block>());
				chains.get(0).add(block);
				System.out.println("Ledger hash before: " + ledgerManager.getLedgerHash());
				if (ledgerManager.getLedgerHash().equals(block.ledgerHash))
				{
					ledgerManager.adjustAddressBalance(block.minerAddress, BLOCK_MINING_REWARD);
					ledgerManager.lastBlockNum = 0;
				}
				System.out.println("ledger hash after: " + ledgerManager.getLedgerHash());
				return true;
			}
			
			if (block.blockNum == 0)
			{
				return false; // Should have been handled above, duplicate genesis blocks shouldn't work
			}
			
			// Check for duplicates
			for (ArrayList<Block> chain : chains)
			{
				if (chain.get(block.blockNum).blockHash.equals(block.blockHash))
				{
					return false; // Duplicate   
				}
			}

			ArrayList<Block> largestChain = getLongestChain();
			
			if (block.blockNum > largestChain.size())
			{
				blockQueue.add(block);
				System.out.println("Added block " + block.blockNum + " to the blockQueue for later processing.");
				return false;
			}
			
			
			// Remove all of the chains that are too short to be useful.
			for (int i = 0; i < chains.size(); i++)
			{
				if (chains.get(i).size() + chainCutoff < largestChain.size())
				{
					chains.remove(i);
					i--; // Account for chain removal
				}
			}
			
			if (!block.previousBlockHash.equals(largestChain.get(largestChain.size() - 1).blockHash)) // Block doesn't fit on end of longest chain
			{
				for (int i = 0; i < chains.size(); i++)
				{
					ArrayList<Block> potentialChain = chains.get(i);
					if (block.blockNum > potentialChain.size())
					{
						continue;
					}
					if (block.previousBlockHash.equals(potentialChain.get(block.blockNum - 1).blockHash))
					{
						if (block.blockNum == potentialChain.size()) // Add on to an existing fork
						{
							potentialChain.add(block);
							System.out.println("[INFO] Added a block with hash " + block.blockHash + " to a shorter chain than master.");
							
							// If this new chain is the best, reverse transactions on the older fork, and apply new fork transactions
							boolean switchForks = false;
							if (potentialChain.size() > largestChain.size())
							{
								switchForks = true;
							}
							else if (potentialChain.size() == largestChain.size())
							{
								if (potentialChain.get(potentialChain.size() - 1).difficulty.compareTo(largestChain.get(largestChain.size() - 1).difficulty) > 0)
								{
									switchForks = true;
								}
							}
							if (switchForks) // Reverse transactions of previously-largest-chain back to the forking point, apply new fork's ledger adjustments
							{
								boolean foundForkingPoint = false;
								int pointer = largestChain.size() - 1;
								while (!foundForkingPoint)
								{
									if (largestChain.get(pointer).blockHash.equals(potentialChain.get(pointer).blockHash))
									{
										foundForkingPoint = true;
										break;
									}
									
									// Haven't found forking point yet, continue reversing transactions
									ArrayList<Transaction> transactionsToReverse = new ArrayList<Transaction>(largestChain.get(pointer).transactions);
									boolean reversedTransaction = true;
									while (transactionsToReverse.size() > 0 && reversedTransaction)
									{
										reversedTransaction = false;

										for (int j = transactionsToReverse.size() - 1; j >= 0; j--)
										{
											if (ledgerManager.reverseTransaction(transactionsToReverse.get(j)))
											{
												transactionsToReverse.remove(j);
												reversedTransaction = true;
												j++;
											}
										}
									}
									
									ledgerManager.adjustAddressBalance(largestChain.get(pointer).minerAddress, -1 * BLOCK_MINING_REWARD);

									ledgerManager.setLastBlockNum(largestChain.get(pointer).blockNum - 1);
									if (transactionsToReverse.size() > 0)
									{
										System.err.println("[CRITICAL ERROR] UNABLE TO REVERSE TRANSACTIONS ON BLOCK " + pointer + " with hash " + largestChain.get(pointer).blockHash + " TO HANDLE FORK CAUSED BY BLOCK #" + block.blockNum + " with hash " + block.blockHash + "!");
									}
									pointer--;
								}
								while (pointer < potentialChain.size())
								{
									if (executeTransactionsForBlock(potentialChain.get(pointer)))
									{
										pointer++;
									}
									else
									{
										System.err.println("[CRITICAL ERROR] UNEXECUTABLE TRANSACTIONS FOUND WHILE SWITCHING FORKS!");
									}
								}
							}
							return true;
						}
						
						else // Need to make a new fork
						{
							ArrayList<Block> newFork = new ArrayList<Block>(potentialChain);
							newFork.add(block.blockNum, block);
							while (newFork.size() > block.blockNum + 1)
							{
								newFork.remove(newFork.size() - 1);
							}
							System.out.println("[INFO] Created a new fork with a block with hash " + block.blockHash + ".");
							chains.add(newFork);
							// No need to test for switching forks, because this can't possibly be the best if it's at most as long as an inferior chain
						}
						return true;
					}
				}
				return false;
			}
			
			// If execution reached this point, then the block is added to the end of the longest chain, so execute transactions.
			if (!fromBlockchainFile)
				return executeTransactionsForBlock(block);
			else
				return true;
			
			
		} catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}
    
    /**
     * Saves the entire blockchain to the file. The blockchain will be saved to "blockchain.dta" inside of the db folder.
     * 
     * @return boolean Whether saving the blockchain was successful
     */
    public boolean saveToFile()
    {
    	try
    	{
            PrintWriter out = new PrintWriter(new File(dbFolder + "/blockchain.dta"));
            for (int i = 0; i < chains.size(); i++)
            {
                for (int j = 0; j < chains.get(i).size(); j++)
                {
                    out.println(chains.get(i).get(j).rawBlock);
                }
            }
            out.close();
    	} catch (Exception e)
    	{
            System.out.println("[CRITICAL ERROR] UNABLE TO WRITE BLOCKCHAIN FILE \"" + dbFolder + "/blockchain.dta!");
            e.printStackTrace();
            return false;
    	}
    	return true;
    }
	
	/**
	 * Returns an ArrayList holding all of the transactions from the longest chain involving the provided address.
	 * 
	 * @param addressToCheck The address to search for
	 * @return ArrayList<Transaction> Containing all transactions in the longest chain involving the provided address.
	 */
	public ArrayList<Transaction> getAllTransactionsInvolvingAddress(String addressToCheck)
	{
		ArrayList<Transaction> transactions = new ArrayList<Transaction>();
		ArrayList<Block> longestChain = getLongestChain();
		for (int i = 0; i < longestChain.size(); i++)
		{
			Block b = longestChain.get(i);
			for (Transaction t : b.transactions)
			{
				if (t.involvesAddress(addressToCheck))
				{
					transactions.add(t);
				}
			}
		}
		return transactions;
	}
	
	/**
	 * Passthrough method to LedgerManager's getAddressBalance(String address) method, returns the balance of
	 * a given address, according to the ledger updated to the state of the largest chain.
	 * 
	 * @param address Address to get the balance of
	 * @return long The balance of the provided address; 0 if never seen on the blockchain
	 */
	public long getAddressBalance(String address)
	{
		return ledgerManager.getAddressBalance(address);
	}
	
	/**
	 * Passthrough method to LedgerManager's getLedgerhash() method, returns the hash of the ledger as it
	 * currently exists.
	 * 
	 * @return String The hash of the ledger
	 */
	public String getLedgerHash()
	{
		return ledgerManager.getLedgerHash();
	}
	
	/**
     * Writes a block to the blockchain file
     * 
     * @return boolean Whether write was successful
     */
    private boolean writeBlockToFile(Block block)
    {
        System.out.println("Writing a block to file...");
        try (FileWriter fileWriter = new FileWriter(dbFolder + "/blockchain.dta", true);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        PrintWriter out = new PrintWriter(bufferedWriter))
        {
            out.println(block.rawBlock);
            out.close();
        } catch (Exception e)
        {
            System.out.println("ERROR: UNABLE TO SAVE BLOCK TO DATABASE!");
            e.printStackTrace();
            return false;
        }
        return true;
    }
	
    /**
     * Execute all of the transactions in a given block on the internal ledger.
     * 
     * @param block Block from which to execute transactions
     * @return boolean Whether executing the transactions was successful
     */
	private boolean executeTransactionsForBlock(Block block)
	{
		if (ledgerManager.lastBlockNum >= block.blockNum)
		{
			return true; // Transaction has already been applied
		}
		
		ArrayList<Transaction> transactionsToApply = new ArrayList<Transaction>(block.transactions);
		ArrayList<Transaction> appliedTransactions = new ArrayList<Transaction>();
		
		boolean appliedTransaction = true;
		while (transactionsToApply.size() > 0 && appliedTransaction)
		{
			appliedTransaction = false;
			for (int i = 0; i < transactionsToApply.size(); i++)
			{
				if (ledgerManager.executeTransaction(transactionsToApply.get(i)))
				{
					appliedTransactions.add(transactionsToApply.get(i));
					transactionsToApply.remove(i);
					appliedTransaction = true;
					i--;
				}
			}
		}
		
		ledgerManager.adjustAddressBalance(block.minerAddress, BLOCK_MINING_REWARD);
		
		if (transactionsToApply.size() != 0) // At least one transaction was unable to be processed!
		{
			for (int i = appliedTransactions.size() - 1; i >= 0; i--)
			{
				ledgerManager.reverseTransaction(appliedTransactions.get(i));
			}
			System.err.println("[ERROR] A block with unexecutable transactions exists! Block #" + block.blockNum + " with hash " + block.blockHash);
			return false;
		}

		ledgerManager.setLastBlockNum(block.blockNum);
		return true;
	}
	
	/**
	 * Get a pointer to the longest chain in this blockchain.
	 * @return ArrayList<Block> The longest chain
	 */
	private ArrayList<Block> getLongestChain()
	{

		boolean tiedBlockchains = false;
		int largestChainIndex = -1;
		int largestChainSize = -1;
		for (int i = 0; i < chains.size(); i++)
		{
			if (chains.get(i).size() > largestChainSize)
			{
				tiedBlockchains = false;
				largestChainIndex = i;
				largestChainSize = chains.get(i).size();
			}
			else if (chains.get(i).size() == largestChainSize)
			{
				tiedBlockchains = true;
			}
		}
		
		ArrayList<Block> largestChain = chains.get(0);
		
		// Set largestChain to the correct chain
		if (!tiedBlockchains)
		{
			largestChain = chains.get(largestChainIndex);
		}
		else // There were at least two chains of the same length, pick a winner by difficulty (otherwise precedence)
		{
			ArrayList<ArrayList<Block>> tiedChains = new ArrayList<ArrayList<Block>>();
			for (int i = 0; i < chains.size(); i++)
			{
				if (chains.get(i).size() == largestChainSize)
				{
					tiedChains.add(chains.get(i));
				}
			}
			
			for (ArrayList<Block> potentialChain : tiedChains)
			{
				if (potentialChain.get(potentialChain.size() - 1).difficulty.compareTo(largestChain.get(largestChain.size() - 1).difficulty) > 0)
				{
					largestChain = potentialChain;
				}
			}
		}
		
		return largestChain;
	}
}
