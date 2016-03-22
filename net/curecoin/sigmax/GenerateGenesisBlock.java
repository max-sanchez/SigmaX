package net.curecoin.sigmax;

import java.math.BigInteger;

public class GenerateGenesisBlock 
{
	public static void main(String[] args)
	{
		long nonce = 0;
		String block = "E7F2E6D81B925C0A6609886B37F7D8C3F7DEED7577045EC8D9EEF319C5BCDB04:70CF123DC155EA6577BEC1DBE274D10B50FEC08E8812AFA6715A978FB264000C:S1IYVVJ6E5IFTIHMHKOKQMUHASIQ5SVAQAQUHC";
		String difficulty = "110454295";
		BigInteger maxTarget = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16);
		
		System.out.println(System.currentTimeMillis());

		BigInteger target = maxTarget.divide(new BigInteger(difficulty, 10));
		System.out.println("Divided " + maxTarget + " by " + new BigInteger(difficulty, 10));
		System.out.println("Original: " + maxTarget);
		System.out.println("Target  : " + target);
		// System.exit(-3);
		
		
		
		while (true)
		{
			if (nonce % 10000000 == 0) System.out.println(nonce);
			String blockHash = Utilities.getSHA256(block + ":" + nonce);
			if (new BigInteger(blockHash, 16).compareTo(target) < 0)
			{
				System.out.println("Lowest nonce: " + nonce + " which is below the target of " + target + " coming in at " + new BigInteger(blockHash, 16));
				System.out.println("    Making the block hash: " + blockHash);
				System.out.println("    Which falls under difficulty: " + "" + (maxTarget.divide(new BigInteger(blockHash, 16))));
				
			}
			nonce++;
		}
	}
}
