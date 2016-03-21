package net.curecoin.sigmax;

/*
 * SigmaX 1.0.0b1 Source Code
 * Copyright (c) 2016 Curecoin Developers
 * Distributed under MIT License
 * Requires Apache Commons Library
 * Supports Java 1.7+
 */

import java.math.BigInteger;
import java.util.ArrayList;

import net.curecoin.sigmax.exceptions.BlockFormatException;
import net.curecoin.sigmax.exceptions.TransactionContentException;
import net.curecoin.sigmax.exceptions.TransactionFormatException;

/**
 * Class to represent a Block, complete with a certificate, and transactions.
 * A block contains:
 * -Timestamp (Unix Epoch)
 * -Block number
 * -Previous block hash
 * -Its own block hash
 * -Difficulty
 * -Winning nonce
 * -Address of miner
 * -Transaction list
 */
public class Block
{
	public static final BigInteger MAXIMUM_TARGET = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16);
	
	public long timestamp;
	public int blockNum;
	public String previousBlockHash;
	public BigInteger difficulty;
	public long winningNonce;
	public String minerAddress;
	
	public String ledgerHash;
	public ArrayList<Transaction> transactions;
	
	public String rawBlock;
	public String blockHash;
	
	public static final long EARLIEST_POSSIBLE_TIMESTAMP = 1458266140000L;
	
	/**
     * Constructor for Block object. A block object is made for any confirmed or potential network block, and requires all pieces of data in this constructor
     * to be a valid network block. The timestamp is the result of the miner's initial call to System.currentTimeMillis(). When peers are receiving new blocks
     * (synced with the network, not catching up) they will refuse any blocks that are more than 2 hours off their internal adjusted time. This makes difficulty
     * malleability impossible in the long-run, ensures that timestamps are reasonably accurate, etc. As a result, any clients far off from the true network time
     * will be forked off the network as they won't accept valid network blocks. Make sure your computer's time is set correctly!
     * 
     * All blocks stack in one particular order, and each block contains the hash of the previous block, to clear any ambiguities about which chain a block belongs
     * to during a fork. The winning nonce is concatenated with the miner address and transaction merkle root and hashed to get a mining score, which is 
     * then used to determine whether a block is under the target difficulty. 
     * 
     * Blocks are hashed to create a block hash, which ensures blocks are not altered, and is used in block stacking. The data hashed is formatted as a String:
     * {timestamp:blockNum:previousBlockHash:difficulty:winningNonce:minerAddress}#{ledgerHash}#{transactions}
     * The hash is then appended to the block data, to produce the full block:
     * {timestamp:blockNum:previousBlockHash:difficulty:winningNonce:minerAddress}#{ledgerHash}#{transactions}#{blockHash}
     * 
     * A higher difficulty means a block is harder to mine. However, a higher difficulty means the TARGET is smaller. Targets can be calculated from the difficulty. A target is simply Long.MAX_VALUE-difficulty.
     * 
     * Explicit transactions are represented as Transaction objects in an ArrayList<Transaction>.
     * 
     * @param timestamp Timestamp originally set into the block by the miner
     * @param blockNum The block number
     * @param previousBlockHash The hash of the previous block
     * @param difficulty The difficulty at the time this block was mined
     * @param winningNonce The nonce selected by a miner to create the block
     * @param ledgerHash The hash of the ledger as it existed before this block's transactions occurred
     * @param transactions ArrayList<Transaction> of all the transactions included in the block
	 * @throws BlockFormatException When any part of the block data is incorrectly formatted
     */
	
	public Block(long timestamp, int blockNum, String previousBlockHash, BigInteger difficulty, long winningNonce, String minerAddress, String ledgerHash, ArrayList<Transaction> transactions) throws BlockFormatException
	{
		// Save all of the block data
		this.timestamp = timestamp;
		this.blockNum = blockNum;
		this.previousBlockHash = previousBlockHash;
		this.difficulty = difficulty;
		this.winningNonce = winningNonce;
		this.minerAddress = minerAddress;
		this.ledgerHash = ledgerHash;
		this.transactions = transactions;
		
		if (timestamp < EARLIEST_POSSIBLE_TIMESTAMP)
		{
			throw new BlockFormatException("Timestamp cannot be earlier than " + EARLIEST_POSSIBLE_TIMESTAMP + "!");
		}
		
		if (previousBlockHash.length() != 64 || !Utilities.isHex(previousBlockHash))
		{
			throw new BlockFormatException("The previous block hash must be 64 hexadecimal characters!");
		}
		
		rawBlock = assembleBlock();
		
		blockHash = Utilities.getSHA256(previousBlockHash + ":" + Utilities.getMerkleRootOfTransactions(transactions) + ":" + winningNonce);
	}
	
	/**
	 * Create a block with a raw String representing the block, instead of the individual components. Valid formats:
	 * {timestamp:blockNum:previousBlockHash:difficulty:winningNonce}#{ledgerHash}#{transactions}
	 * {timestamp:blockNum:previousBlockHash:difficulty:winningNonce}#{ledgerHash}#{transactions}#{blockHash}
	 * 
	 * If the blockHash is included, it will be checked against the block's internal data, and an exception will be thrown if they don't match.
	 * If the blockHash is not included, it will simply be calculated and stored.
	 * 
	 * @param rawBlock The String holding all of the raw block data
	 * @throws BlockFormatException When any part of the block data is incorrectly formatted
	 * @throws TransactionFormatException When any transaction is incorrectly formatted
	 * @throws TransactionContentException When any transaction contains impossible content
	 */
	public Block(String rawBlock) throws BlockFormatException, TransactionFormatException, TransactionContentException
	{
		transactions = new ArrayList<>();
		
		String[] parts = rawBlock.split("#");
		
		if (parts.length < 3) 
		{
			throw new BlockFormatException("Too few block chunks! Block format: {timestamp:blockNum:previousBlockHash:difficulty:winningNonce:minerAddress}#{ledgerHash}#{transactions}");
		}
		else if (parts.length > 4)
		{
			throw new BlockFormatException("Too many block chunks! Block format: {timestamp:blockNum:previousBlockHash:difficulty:winningNonce:minerAddress}#{ledgerHash}#{transactions}");
		}
		
		String[] firstChunk = parts[0].replace("{", "").replace("}", "").split(":");
		if (firstChunk.length != 6)
		{
			throw new BlockFormatException("Incorrect number of items in the first chunk! First chunk format: {timestamp:blockNum:previousBlockHash:difficulty:winningNonce:minerAddress}");
		}
		
		if (!Utilities.isLong(firstChunk[0]))
		{
			throw new BlockFormatException("The timestamp must be a long!");
		}
		timestamp = Long.parseLong(firstChunk[0]);
		
		if (!Utilities.isInteger(firstChunk[1]))
		{
			throw new BlockFormatException("The block number must be an integer!");
		}
		blockNum = Integer.parseInt(firstChunk[1]);
		
		if (firstChunk[2].length() != 64 || !Utilities.isHex(firstChunk[2]))
		{
			throw new BlockFormatException("The previous block hash must be 64 hexadecimal characters!");
		}
		previousBlockHash = firstChunk[2];
		
		if (!Utilities.isBigInteger(firstChunk[3]))
		{
			throw new BlockFormatException("The difficulty must be a BigInteger!");
		}
		difficulty = new BigInteger(firstChunk[3], 10);
		
		if (!Utilities.isLong(firstChunk[4]))
		{
			throw new BlockFormatException("The winning nonce must be a long!");
		}
		winningNonce = Long.parseLong(firstChunk[4]);
		
		if (!MerkleAddressUtility.isAddressFormattedCorrectly(firstChunk[5]))
		{
			throw new BlockFormatException("The miner address \"" + firstChunk[5] + "\" is incorrectly formatted!");
		}
		minerAddress = firstChunk[5];
		
		ledgerHash = parts[1].replace("{", "").replace("}", "");
		if (ledgerHash.length() != 64 || !Utilities.isHex(ledgerHash))
		{
			throw new BlockFormatException("The ledger hash must be 64 hexadecimal characters!");
		}
		
		String[] transactionsArray = parts[2].replace("{", "").replace("}", "").split("|");
		if (!(transactionsArray.length <= 1 && transactionsArray[0].equals("")))
		{
			for (String transactionString : transactionsArray)
			{
				Transaction transaction = new Transaction(transactionString);
				transactions.add(transaction);
			}
		}
		
		this.rawBlock = assembleBlock();
		
		blockHash = Utilities.getSHA256(previousBlockHash + ":" + Utilities.getMerkleRootOfTransactions(transactions) + ":" + winningNonce);
		
		if (parts.length == 4) // Block hash was provided, let's validate it.
		{
			parts[4] = parts[4].replace("{", "").replace("}", "");
			if (!parts[4].equals(blockHash))
			{
				throw new BlockFormatException("The provided block hash \"" + parts[4] + "\" does not match the calculated hash: \"" + blockHash + "\"!");
			}
		}
	}
	
	/**
	 * Determines whether the nonce the miner mined the block with is valid at this block's difficulty.
	 * 
	 * @return boolean Whether the nonce produces a hash below the 
	 */
	public boolean isMinerHashBelowTarget()
	{
		String blockHash = Utilities.getSHA256(previousBlockHash + ":" + Utilities.getMerkleRootOfTransactions(transactions) + ":" + minerAddress + ":" + winningNonce);
		BigInteger target = MAXIMUM_TARGET.divide(difficulty);
		
		return new BigInteger(blockHash, 16).compareTo(target) < 0;
	}
	
	/**
	 * Assembles the block portions stored as private variables into a flat (raw) block format.
	 * 
	 * @return String The flat/raw block
	 */
	private String assembleBlock()
	{
		// Assemble the block into a flat String
		String rawBlock = "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "}#{" + ledgerHash + "}#";
		String transactionString = "";
		for (Transaction transaction : transactions)
		{
			transactionString += transaction.getFlatTransaction() + "|";
		}

		if (transactions.size() > 0)
		{
			// Remove final | separator
			transactionString = transactionString.substring(0, transactionString.length() - 1);
		}
		
		rawBlock += "{" + transactionString + "}";
		return rawBlock;
	}
}
