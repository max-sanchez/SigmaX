package net.curecoin.sigmax;

/*
 * SigmaX 1.0.0b1 Source Code
 * Copyright (c) 2016 Curecoin Developers
 * Distributed under MIT License
 * Requires Apache Commons Library
 * Supports Java 1.7+
 */

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;

import javax.xml.bind.DatatypeConverter;

public class Utilities 
{

	private static MessageDigest md;
	static
	{
		try
		{
			md = MessageDigest.getInstance("SHA-256");
		} catch (Exception e)
		{
			e.printStackTrace();
			System.err.println("[CRITICAL] SHA-256 NOT SUPPORTED. EXITING.");
			System.exit(-1);
		}
	}
	
	public static String getSHA256(String input)
	{
		try
		{
			return DatatypeConverter.printHexBinary(md.digest(input.getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e)
		{
			System.err.println("[CRITICAL] UTF-8 ENCODING NOT SUPPORTED. EXITING.");
			System.exit(-1);
			return "ERROR"; // Make compiler happy.
		}
	}
	
	public static boolean isInteger(String toTest)
	{
		try
		{
			Long.parseLong(toTest);
			return true;
		} catch (Exception e)
		{
			return false;
		}
	}
	
	public static boolean isLong(String toTest)
	{
		try
		{
			Long.parseLong(toTest);
			return true;
		} catch (Exception e)
		{
			return false;
		}
	}
	
	public static boolean isBigInteger(String toTest)
	{
		try
		{
			new BigInteger(toTest, 10);
			return true;
		} catch (Exception e)
		{
			return false;
		}
	}
	
	public static boolean isHex(String toTest)
	{
		for (char c : toTest.toLowerCase().toCharArray())
		{
			if (!(('0' <= c && c <= '9') || ('a' <= c && c <= 'f')))
			{
				return false;
			}
		}
		return true;
	}
	
	public static String getMerkleRootOfTransactions(ArrayList<Transaction> transactions)
	{
		if (transactions.size() == 0) 
		{
			return "0000000000000000000000000000000000000000000000000000000000000000";
		}
		String[] currentMerkleLayer = new String[transactions.size()];
		for (int i = 0; i < transactions.size(); i++)
		{
			currentMerkleLayer[i] = getSHA256(transactions.get(i).getFlatTransaction());
		}
		while (currentMerkleLayer.length > 1)
		{
			String[] newMerkleLayer = new String[currentMerkleLayer.length / 2 + currentMerkleLayer.length % 2];
			for (int i = 0; i < newMerkleLayer.length; i++)
			{
				if (i*2 < currentMerkleLayer.length - 1)
				{
					newMerkleLayer[i] = getSHA256(currentMerkleLayer[i*2] + currentMerkleLayer[i*2+1]);
				}
				else
				{
					newMerkleLayer[i] = getSHA256(currentMerkleLayer[i*2] + currentMerkleLayer[i*2]);
				}
			}
			currentMerkleLayer = newMerkleLayer;
		}
		return currentMerkleLayer[0];
	}
	
	/**
	 * Calculates the target difficulty of the next block given a list of previous blocks.
	 * @param pastBlocks List of previous blocks, where the first block (index 0) is the most recent
	 * @return BigInteger The target difficulty for the next block
	 */
	
	
	private static final long TARGET_GAP_MS = 1000 * 60 * 2; // 2 minutes between blocks
	private static final BigInteger MINIMUM_DIFFICULTY = new BigInteger("50000000");
	public static BigInteger calculateNextDifficulty(ArrayList<Block> pastBlocks)
	{
		if (pastBlocks.size() == 1)
		{
			return pastBlocks.get(0).difficulty;
		}
		long[] actualGaps = new long[pastBlocks.size() - 1];
		BigInteger difficultySum = BigInteger.valueOf(0l);
		for (int i = 0; i < pastBlocks.size(); i++)
		{
			difficultySum = difficultySum.add(pastBlocks.get(i).difficulty);
		}
		BigInteger averageDifficulty = difficultySum.divide(BigInteger.valueOf(pastBlocks.size()));
		for (int i = 0; i < actualGaps.length; i++)
		{
			actualGaps[i] = pastBlocks.get(i).timestamp - pastBlocks.get(i + 1).timestamp;
		}
		long totalWeightedGaps = 0;
		long totalWeight = 0;
		for (int i = 0; i < actualGaps.length; i++)
		{
			totalWeightedGaps += (actualGaps.length - i) * actualGaps[i]; // Extremely simple weighting, future versions will use ~Gaussian distributions or similar
			totalWeight += actualGaps.length - i;
		}
		
		long averageGap = totalWeightedGaps / totalWeight;
		double gapDelta = (double)TARGET_GAP_MS / (double)averageGap;
		BigInteger targetDifficulty = new BigDecimal(averageDifficulty).multiply(new BigDecimal(gapDelta)).toBigInteger();
		if (MINIMUM_DIFFICULTY.compareTo(targetDifficulty) > 0)
		{
			return MINIMUM_DIFFICULTY;
		}
		else
		{
			return targetDifficulty;
		}
	}
}
