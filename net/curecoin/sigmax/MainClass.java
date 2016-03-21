package net.curecoin.sigmax;

import java.io.File;
import java.io.PrintWriter;
import java.util.Scanner;

/**
 * After significant code revamps, this main class handles very little "heavy-lifting,"
 * opting instead to delegate work as appropriate to beefier classes.
 * 
 */
public class MainClass
{
	public static void main(String[] args)
	{
		if (!new File("database").exists() || new File("database").isDirectory())
		{
			new File("database").mkdir();
			try
			{
				PrintWriter writeGenesisBlock = new PrintWriter(new File("database/blockchain.dta"));
				writeGenesisBlock.println("{1458510590893:0:0000000000000000000000000000000000000000000000000000000000000000:125000000:587935525:S1GENEGENEGENEGENEGENEGENEGENEGENEJF2R}#{0000000000000000000000000000000000000000000000000000000000000000}#{}");
				writeGenesisBlock.close();
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		
		Blockchain blockchain = new Blockchain("database");
		
		try
		{
			Scanner scan = new Scanner(new File("database/blockchain.dta"));
			while (scan.hasNextLine())
			{
				try
				{
					Block fromDatabase = new Block(scan.nextLine());
					blockchain.addBlockAndSave(fromDatabase, true);
				} catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			scan.close();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		
		PeerNetwork peerNetwork = new PeerNetwork(8025);
		
		peerNetwork.start();
		
		
		System.out.println(blockchain.getAddressBalance("S1GENEGENEGENEGENEGENEGENEGENEGENEJF2R"));
		System.out.println(blockchain.getLedgerHash());
		/*try
		{
			blockchain.addBlockAndSave(new Block("{1458510590893:0:00000000448A0FE417E8B8E3B37D0A574CCB617620D3CB879BAFBE90C0D82D00:160420597:379934465:S1RAL73LJSE6HNWSYJRWJDBQ2BEECXX323ZNDA}#{AD20C4770265819E2A7A3A613FDCF418653A7EEE62DFCA7F40F08D425108A8B3}#{}"), false);
		} catch (Exception e) { } */
		System.out.println(blockchain.getLedgerHash());
		System.out.println(blockchain.getAddressBalance("S1GENEGENEGENEGENEGENEGENEGENEGENEJF2R"));
		System.out.println(blockchain.getLedgerHash());
		
	}
}
