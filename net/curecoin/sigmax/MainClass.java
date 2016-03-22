package net.curecoin.sigmax;

import java.awt.GraphicsEnvironment;
import java.io.Console;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

/**
 * Main intersection of all SigmaX functionality. Heavy lifting is delegated to helper classes.
 */
public class MainClass
{
	public static void main(String[] args)
	{
		launch();
		if (!new File("database").exists() || !new File("database").isDirectory())
		{
			System.out.println("CREATING DATABASE FOLDER");
			new File("database").mkdir();
			try
			{
				PrintWriter writeGenesisBlock = new PrintWriter(new File("database/blockchain.dta"));
				
				// Write the genesis block
				writeGenesisBlock.println("{1458510590893:0:0000000000000000000000000000000000000000000000000000000000000000:125000000:587935525:S1GENEGENEGENEGENEGENEGENEGENEGENEJF2R}#{0000000000000000000000000000000000000000000000000000000000000000}#{}");
				writeGenesisBlock.close();
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		
		Blockchain blockchain = new Blockchain("database");
		
		PendingTransactionContainer pendingTransactions = new PendingTransactionContainer(blockchain);
		
		try
		{
			Scanner scan = new Scanner(new File("database/blockchain.dta"));
			while (scan.hasNextLine())
			{
				try
				{
					System.out.println("Pulling a block from file...");
					Block fromDatabase = new Block(scan.nextLine());
					System.out.println("Add block: " + fromDatabase.blockNum + "?: " + blockchain.addBlockAndSave(fromDatabase, true));
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
		
		RPC rpcAgent = new RPC();
        rpcAgent.start();
        
        File peerFile = new File("peers.lst");
        ArrayList<String> peers = new ArrayList<String>();
        AddressManager addressManager = new AddressManager();
        if (!peerFile.exists())
        {
            try
            {
                PrintWriter out = new PrintWriter(peerFile);
                out.println("155.94.254.14:8025");
                
                out.close();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        try
        {
            Scanner scan = new Scanner(peerFile);
            while (scan.hasNextLine())
            {
                String combo = scan.nextLine();
                peers.add(combo);
                String host = combo.substring(0, combo.indexOf(":"));
                int port = Integer.parseInt(combo.substring(combo.indexOf(":") + 1));
                peerNetwork.connectToPeer(host, port);
            }
            scan.close();
            Thread.sleep(2000);
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        
        peerNetwork.broadcast("REQUEST_NET_STATE");
        int topBlock = blockchain.getBlockNumOfLastBlockOnLongestChain();
        ArrayList<String> allBroadcastTransactions = new ArrayList<String>();
        ArrayList<String> allBroadcastBlocks = new ArrayList<String>();
        
        while (true) 
        {
            //Look for new peers
            if (peerNetwork.newPeers.size() > 0)
            {
                for (int i = 0; i < peerNetwork.newPeers.size(); i++)
                {
                    if (peers.indexOf(peerNetwork.newPeers.get(i)) < 0)
                    peers.add(peerNetwork.newPeers.get(i));
                }
                peerNetwork.newPeers = new ArrayList<String>();
                try
                {
                    PrintWriter writePeerFile = new PrintWriter(new File("peers.lst"));
                    for (int i = 0; i < peers.size(); i++)
                    {
                        writePeerFile.println(peers.get(i));
                    }
                    writePeerFile.close();
                } catch (Exception e)
                {
                    System.err.println("[CRITICAL ERROR] UNABLE TO WRITE TO PEER FILE!");
                    e.printStackTrace();
                }
            }
            //Look for new data from peers
            for (int i = 0; i < peerNetwork.peerThreads.size(); i++)
            {
                ArrayList<String> input = peerNetwork.peerThreads.get(i).inputThread.readData();
                if (input == null)
                {
                    System.out.println("NULL INPUT, EXITING");
                    System.exit(-4);
                    break;
                }
                
                /*
                 * While taking up new transactions and blocks, the client will broadcast them to the network if they are new to the client.
                 * As a result, if you are connected to 7 peers, you will get reverb 7 times for a broadcast of a block or transaction.
                 */
                for (int j = 0; j < input.size(); j++)
                {
                    String data = input.get(j);
                    if (data.length() > 60)
                    {
                        System.out.println("got data: " + data.substring(0, 30) + "..." + data.substring(data.length() - 30, data.length()));
                    } else
                    {
                        System.out.println("got data: " + data);
                    }
                    String[] parts = data.split(" ");
                    if (parts.length > 0)
                    {
                        if (parts[0].equalsIgnoreCase("NETWORK_STATE"))
                        {
                            topBlock = Integer.parseInt(parts[1]);
                        }
                        else if (parts[0].equalsIgnoreCase("REQUEST_NET_STATE"))
                        {
                            peerNetwork.peerThreads.get(i).outputThread.write("NETWORK_STATE " + blockchain.getBlockNumOfLastBlockOnLongestChain() + " " + blockchain.getHashOfLastBlockOnLongestChain());
                            for (int k = 0; k < pendingTransactions.pendingTransactions.size(); k++)
                            {
                                peerNetwork.peerThreads.get(i).outputThread.write("TRANSACTION " + pendingTransactions.pendingTransactions.get(k));
                            }
                        }
                        //BLOCK BLOCKDATA
                        else if (parts[0].equalsIgnoreCase("BLOCK"))
                        {
                            /*
                             * If a block is new to the client, the client will attempt to add it to the blockchain.
                             * When added to the blockchain, it may get added to a chain, put on a new fork, put on an existing, shorter-length chain that's forked less than 10 blocks back, or
                             * it may end up being queued or deleted. Queued blocks are blocks that self-validate (signatures match, etc.) but don't fit onto any chain.
                             * They are often used when getting blocks from a peer, in case one arrives out of order.
                             */
                            System.out.println("Attempting to add block...");
                            boolean hasSeenBefore = false;
                            for (int k = 0; k < allBroadcastBlocks.size(); k++)
                            {
                                //Likely due to P2P reverb / echo
                                //System.out.println("Have seen block before... not adding.");
                                if (parts[1].equals(allBroadcastBlocks.get(k)))
                                {
                                    hasSeenBefore = true;
                                }
                            }
                            if (!hasSeenBefore)
                            {
                                //Block has not been previously received, so it will be added to the blockchain (hopefully)
                                System.out.println("Adding new block from network!");
                                System.out.println("Block: ");
                                System.out.println(parts[1].substring(0, 30) + "...");
                                allBroadcastBlocks.add(parts[1]);
                                try
                                {
	                                Block blockToAdd = new Block(parts[1]);
	                                if (blockchain.addBlockAndSave(blockToAdd, false))
	                                {
	                                    //If block is new to client and appears valid, rebroadcast
	                                    System.out.println("Added block " + blockToAdd.blockNum + " with hash: [" + blockToAdd.blockHash.substring(0, 30) + "..." + blockToAdd.blockHash.substring(blockToAdd.blockHash.length() - 30, blockToAdd.blockHash.length() - 1) + "]");
	                                    peerNetwork.broadcast("BLOCK " + parts[1]);
	                                }
	                                //Remove all transactions from the pendingTransactionPool that appear in the block
	                                pendingTransactions.removeTransactionsInBlock(blockToAdd);
                                } catch (Exception e)
                                {
                                	System.err.println("A peer has sent an invalid block: " + parts[1]);
                                }
                            }
                        }
                        //TRANSACTION TRANSACTIONDATA
                        else if (parts[0].equalsIgnoreCase("TRANSACTION"))
                        {
                            /*
                             * Any transactions that are received will be checked against the table of existing received transactions. If they are new (and validate correctly), they will be added
                             * to the pending transaction pool. Currently, this pool is only useful when mining blocks. In the future, this pool will be accessible using RPC commands to show
                             * unconfirmed transactions, etc.
                             */
                            boolean alreadyExisted = false;
                            for (int b = 0; b < allBroadcastTransactions.size(); b++)
                            {
                                if (parts[1].equals(allBroadcastTransactions.get(b)))
                                {
                                    alreadyExisted = true;
                                }
                            }
                            if (!alreadyExisted) //Transaction was not already received
                            {
                                /*
                                 * Put the transaction in the received transactions pile, check it for validity, and put it in the pool if valid.
                                 * Important to note--validity checks are done by the Transaction constructor. Transactions that fail to validate will not
                                 * be rebroadcast.
                                 */
                            	
                            	try
                            	{
                            	
	                                allBroadcastTransactions.add(parts[1]);
	                                pendingTransactions.addTransaction(new Transaction(parts[1]));
	                                
	                                System.out.println("New transaction on network:");
	                                String[] transactionParts = parts[1].split(";");
	                                for (int k = 2; k < transactionParts.length - 2; k+=2)
	                                {
	                                    System.out.println("     " + transactionParts[k + 1] + " SigmaX from " + transactionParts[0] + " to " + transactionParts[k]);
	                                }
	                                System.out.println("Total SigmaX sent: " + transactionParts[1]);
	                                peerNetwork.broadcast("TRANSACTION " + parts[1]);
                            	} catch (Exception e)
                            	{
                            		System.err.println("Invalid transaction received: " + parts[1]);
                            	}
                            }
                        }
                        else if (parts[0].equalsIgnoreCase("PEER"))
                        {
                            /*
                             * Peer discovery mechanisms are currently limited. 
                             */
                            boolean exists = false;
                            for (int k = 0; k < peers.size(); k++)
                            {
                                if (peers.get(k).equals(parts[1] + ":" + parts[2]))
                                {
                                    exists = true;
                                }
                            }
                            if (!exists)
                            {
                                try
                                {
                                    peerNetwork.connectToPeer(parts[1].substring(0, parts[1].indexOf(":")), Integer.parseInt(parts[1].substring(parts[1].indexOf(":") + 1)));
                                    peers.add(parts[1]);
                                    PrintWriter out = new PrintWriter(peerFile);
                                    for (int k = 0; k < peers.size(); k++)
                                    {
                                        out.println(peers.get(k));
                                    }
                                    out.close();
                                } catch (Exception e)
                                {
                                    System.out.println("PEER COMMUNICATED INVALID PEER!");
                                }
                            }
                        }
                        else if (parts[0].equalsIgnoreCase("GET_PEER"))
                        {
                            /*
                             * Returns a random peer host/port combo to the querying peer.
                             * Future versions will detect dynamic ports and not send peers likely to not support direct connections.
                             * While not part of GET_PEER, very-far-in-the-future-versions may support TCP punchthrough assists.
                             */
                            Random random = new Random();
                            peerNetwork.peerThreads.get(i).outputThread.write("PEER " + peers.get(random.nextInt(peers.size())));
                        }
                        else if (parts[0].equalsIgnoreCase("GET_BLOCK"))
                        {
                            try
                            {
                                Block block = blockchain.getBlock(Integer.parseInt(parts[1]));
                                if (block != null)
                                {
                                    System.out.println("Sending block " + parts[1] + " to peer...");
                                    peerNetwork.peerThreads.get(i).outputThread.write("BLOCK " + block.rawBlock);
                                }
                            } catch (Exception e)
                            {
                            }
                        }
                    }
                }
            }
            int currentChainHeight = blockchain.getBlockNumOfLastBlockOnLongestChain();
            /*
             * Current chain is shorter than peer chains. Chain starts counting at 0, so a chain height of 15, for example, means there are 15 blocks, and the top block's index is 14.
             */
            if (topBlock > currentChainHeight)
            {
                System.out.println("currentChainHeight: " + currentChainHeight);
                System.out.println("topBlock: " + topBlock);
                try
                {
                    Thread.sleep(100); //Sleep for a bit, wait for responses before requesting more data.
                } catch (Exception e)
                {
                    //If this throws an error, something's terribly off.
                    System.err.println("MainClass has insomnia.");
                }
                for (int i = currentChainHeight; i <= topBlock; i++) //Broadcast request for new block(s)
                {
                    System.out.println("Requesting block " + i + "...");
                    peerNetwork.broadcast("GET_BLOCK " + i);
                }
            }
            /*
             * Loop through all of the rpcAgent rpcThreads looking for new queries. Note that setting the response to a string twice in response to one command will cause queue issues.
             * This may be changed in a later version, but I want to keep the RPCServer elements light on memory with less moving parts--they shouldn't be a point of failure.
             * Keeping with only one String allowed in the output queue (instead of the ArrayList<String> model employed by the P2P networking functions) is simplistic for now.
             */
            for (int i = 0; i < rpcAgent.rpcThreads.size(); i++)
            {
                String request = rpcAgent.rpcThreads.get(i).request;
                if (request != null)
                {
                    String[] parts = request.split(" ");
                    parts[0] = parts[0].toLowerCase();
                    if (parts[0].equals("getbalance"))
                    {
                        if (parts.length > 1)
                        {
                            rpcAgent.rpcThreads.get(i).response = blockchain.getAddressBalance(parts[1]) + ""; //Turn it into a String
                        }
                        else
                        {
                            rpcAgent.rpcThreads.get(i).response = blockchain.getAddressBalance(addressManager.getDefaultAddress()) + "";
                        }
                    }
                    else if (parts[0].equals("getinfo"))
                    {
                        /*
                         * getinfo will be expanded in the future to give a lot more information.
                         */
                        String response = "Blocks: " + blockchain.getBlockNumOfLastBlockOnLongestChain();
                        response += "\nLast block hash: " + blockchain.getBlock(blockchain.getBlockNumOfLastBlockOnLongestChain() - 1).blockHash;
                        response += "\nDifficulty: " + blockchain.getNextDifficultyForLongestChain();
                        response += "\nMain address: " + addressManager.getDefaultAddress();
                        response += "\nMain address balance: " + blockchain.getAddressBalance(addressManager.getDefaultAddress());
                        rpcAgent.rpcThreads.get(i).response = response;
                    }
                    else if (parts[0].equals("send"))
                    {
                        try
                        {
                            long amount = Long.parseLong(parts[1]);
                            String destinationAddress = parts[2];
                            String address = addressManager.getDefaultAddress();
                            ArrayList<String> outputAddress = new ArrayList<String>();
                            outputAddress.add(destinationAddress);
                            ArrayList<Long> outputAmount = new ArrayList<Long>();
                            outputAmount.add(amount);
                            Transaction fullTransaction = TransactionUtility.signTransaction(addressManager.getPrivateKey(), addressManager.getDefaultAddress(), amount, outputAddress, outputAmount, blockchain.getAddressNextIndex(addressManager.getDefaultAddress()));
                            pendingTransactions.addTransaction(fullTransaction);
                            peerNetwork.broadcast("TRANSACTION " + fullTransaction.getFlatTransaction());
                            System.out.println("Sending " + amount + " from " + address + " to " + destinationAddress);
                            rpcAgent.rpcThreads.get(i).response = "Sent " + amount + " from " + address + " to " + destinationAddress;
                        } catch (Exception e)
                        {
                            rpcAgent.rpcThreads.get(i).response = "Syntax (don't use < and >): send <amount> <destination>";
                        }
                    }
                    else if (parts[0].equals("submittx"))
                    {
                    	try
                    	{
							Transaction transaction = new Transaction(parts[1]);
                    		pendingTransactions.addTransaction(transaction);
                    		peerNetwork.broadcast("TRANSACTION " + parts[1]);
                            rpcAgent.rpcThreads.get(i).response = "Sent raw transaction!";
                    	} catch (Exception e)
                    	{
                            rpcAgent.rpcThreads.get(i).response = "Non-valid transaction.";
                    	}
                    }
                    else if (parts[0].equals("submitblock"))
                    {
                        rpcAgent.rpcThreads.get(i).request = null;
                        
                        try
                        {
                        	Block block = new Block(parts[1]);
                        	blockchain.addBlockAndSave(block, false);
                        	peerNetwork.broadcast("BLOCK " + parts[1]);
                            rpcAgent.rpcThreads.get(i).response = "Successfully submitted block!";
                        } catch (Exception e)
                        {
                        	System.err.println("A block was submitted but invalid: " + parts[1]);
                        }
                    }
                    else if (parts[0].equals("gethistory"))
                    {
                        if (parts.length > 1)
                        {
                            ArrayList<Transaction> allTransactions = blockchain.getAllTransactionsInvolvingAddress(parts[1]);
                            String allTransactionsFlat = "";
                            for (int j = 0; j < allTransactions.size(); j++)
                            {
                                allTransactionsFlat += allTransactions.get(j).getTransactionSummary().trim() + "\n";
                            }
                            rpcAgent.rpcThreads.get(i).response = allTransactionsFlat;
                        }
                        else
                        {
                            rpcAgent.rpcThreads.get(i).response = "gethistory <address>";
                        }
                    }
                    else if (parts[0].equals("getpending"))
                    {
                        if (parts.length > 1)
                        {
                            rpcAgent.rpcThreads.get(i).response = "" + pendingTransactions.getPendingBalance(parts[1]);
                        }
                        else
                        {
                            rpcAgent.rpcThreads.get(i).response = "getpending <address>";
                        }
                    }
                    else if (parts[0].equals("blockinfo"))
                    {
                    	ArrayList<Transaction> pendingTransactionsList = TransactionUtility.sortTransactionsBySignatureIndex(pendingTransactions.pendingTransactions);
                    	
                    	
                    	
                    	String response = "";
                    	response += "PBH: " + blockchain.getHashOfLastBlockOnLongestChain() + "\n";
                    	response += "TXMKL: " + Utilities.getMerkleRootOfTransactions(pendingTransactionsList) + "\n";
                    	response += "ADD: " + addressManager.getDefaultAddress() + "\n";
                    	response += "DIFF: " + blockchain.getNextDifficultyForLongestChain() + "\n";
                    	response += "BN: " + blockchain.getBlockNumOfLastBlockOnLongestChain() + "\n";
                    	for (int j = 0; j < pendingTransactionsList.size(); j++)
                    	{
                    		response += "TX" + j + ": " + pendingTransactionsList.get(j).getFlatTransaction() + "\n";
                    	}
                    	rpcAgent.rpcThreads.get(i).response = response;
                    }
                    else
                    {
                        rpcAgent.rpcThreads.get(i).response = "Unknown command \"" + parts[0] + "\"";
                    }
                }
            }
            try
            {
                Thread.sleep(50);
            } catch (Exception e) {}
        }
	}
	
    public static void launch()
    {
        Console console = System.console(); //Get a system console object
        if (console != null) //If the application has a console
        {
            File f = new File("launch.bat");
            if (f.exists())
            {
                f.delete(); //delete bat file if it exists
            }
        } 
        else if (!GraphicsEnvironment.isHeadless()) //Application doesn't have a console, let's give it one!
        {
            String os = System.getProperty("os.name").toLowerCase(); //Get OS
            if (os.contains("indows")) //If OS is a windows OS
            { 
                try
                {
                    File JarFile = new File(MainClass.class.getProtectionDomain().getCodeSource().getLocation().toURI());//Get the absolute location of the .jar file
                    PrintWriter out = new PrintWriter(new File("launch.bat")); //Get a PrintWriter object to make a batch file
                    out.println("@echo off"); //turn echo off for batch file
                    out.println("title SigmaX 1.0.0b1 Testnet Miner"); 
                    out.println("java -jar \"" + JarFile.getPath() + "\"");
                    out.println("start /b \"\" cmd /c del \"%~f0\"&exit /b");
                    out.close(); //saves file
                    Runtime rt = Runtime.getRuntime(); //gets runtime
                    rt.exec("cmd /c start launch.bat"); //executes batch file
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
                System.exit(0); //Exit program, so only instance of program with command line runs!
            }
        }
    }
}
