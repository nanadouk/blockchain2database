package ch.bfh.blk2.bitcoin.producer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.BlockFileLoader;

import ch.bfh.blk2.bitcoin.comparator.Sha256HashComparator;
import ch.bfh.blk2.bitcoin.util.Utility;

/**
 * The blockchain can contain orphan blocks. Whenever it does, one could imagine
 * the blockchain like a tree, from which the orphan blocks branch out (in tiny
 * branches, most often). We need to find the way to the top of the tree without
 * getting lost in the branches. This class does exactly this.
 *
 * fetch an ordered List of blockhashes skip orphan blocks
 *
 */
public class BlockSorter {
	private static final Logger logger = LogManager.getLogger("BlockSorter");

	private BlockIdentifier deepestBlock;
	private Map<Sha256Hash, BlockIdentifier> blockMap;
	private BlockFileLoader bfl;
	private List<BlockIdentifier> unsortedBlocks;

	/* Default values for when we start at the bottom of the chain */
	private Sha256Hash rootHash = Sha256Hash.ZERO_HASH;
	private int virtBlockHeight = -1;

	/**
	 * @param blockChainFiles
	 *            the files containing the unparsed blockchain
	 */
	public BlockSorter(List<File> blockChainFiles) {
		Collections.sort(blockChainFiles);

		blockMap = new TreeMap<>(new Sha256HashComparator());
		unsortedBlocks = new LinkedList<>();

		bfl = new BlockFileLoader(Utility.PARAMS, blockChainFiles);

		// Check if a chain file is present already...
		File file = new File("./chain");
		if (file.exists() && !file.isDirectory() && file.canRead()) {
			logger.info("Found a chain File. Will attempt to continue from there");

			try {
				BufferedReader reader = new BufferedReader(new FileReader(file));
				List<String> hashes = new LinkedList<>();
				String line = null;
				int lineNr = 0;
				while ((line = reader.readLine()) != null) {
					lineNr++;
					hashes.add(line);
					if (hashes.size() > 100)
						hashes.remove(0);
				}
				logger.info("Highest block in chian file:\n\theight: "
						+ (lineNr - 1)
						+ "\tblock: "
						+ hashes.get(hashes.size() - 1));
				logger.info("Block #" + (lineNr - 101) + " : " + hashes.get(0));

				return;
			} catch (IOException e) {
				logger.info("Unable to process the chain file. Continuing without.");
				logger.debug("reason: ", e);
			}
		}

		sort();

	}

	private void insertBlock(BlockIdentifier bi) {
		logger.trace("Inserting yet another block: " + bi.getBlockHash().toString());
		BlockIdentifier parent = blockMap.get(bi.getParentHash());
		bi.setParent(parent);
		bi.setDepth(parent.getDepth() + 1);
		blockMap.put(bi.getBlockHash(), bi);
		if (bi.getDepth() > deepestBlock.getDepth())
			deepestBlock = bi;

		for (BlockIdentifier bli : unsortedBlocks)
			if (blockMap.containsKey(bli.getParentHash())) {
				unsortedBlocks.remove(bli);
				insertBlock(bli);
				break;
			}
	}

	private void sort() {
		/*
		 * Insert a virtual block that is parent of gensis, allowing us to
		 * handle Genesis Block in the same way as normal blocks.
		 */
		BlockIdentifier virtualBlock = new BlockIdentifier(rootHash);
		virtualBlock.setDepth(virtBlockHeight);
		deepestBlock = virtualBlock;
		blockMap.put(virtualBlock.getBlockHash(), virtualBlock);

		logger.debug("start sorting");

		logger.debug(bfl);

		for (Block blk : bfl) {
			logger.trace("block: " + blk.getHashAsString());
			BlockIdentifier bi = new BlockIdentifier(blk);
			if (blockMap.containsKey(bi.getParentHash()))
				insertBlock(bi);
			else
				unsortedBlocks.add(bi);
		}

		logger.debug("Gonna write now");
		saveChain();
	}

	private void saveChain() {
		File file = new File("./chain");
		if (file.exists()) {
			logger.info("Found an already existing version of the chain file. Will replace it");
			file.delete();
		}

		try {
			file.createNewFile();
			PrintWriter pw = new PrintWriter(file);

			List<Sha256Hash> chain = getLongestBranch();
			for (Sha256Hash hash : chain)
				pw.println(hash.toString());

			pw.flush();
			pw.close();

		} catch (IOException e) {
			logger.error("Can't create the chain file. Will continue nonetheless.");
		}

	}

	/**
	 *
	 * @return a list of hashes representing blocks in the order that is
	 *         considered the current state of the blockchain
	 */
	public List<Sha256Hash> getLongestBranch() {
		List<Sha256Hash> branch = new LinkedList<>();

		BlockIdentifier current = deepestBlock;

		while (current.getDepth() >= 0) {
			branch.add(0, current.getBlockHash());
			current = current.getParent();
		}

		return branch;
	}

	// main method for testing
	public static void main(String[] args) {
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream("target/classes/blockchain.properties"));
		} catch (Exception e) {
			logger.fatal("Unable to read the properties file");
			logger.fatal("failed at", e);
			System.exit(1);
		}

		NetworkParameters params = null;

		if (Boolean.parseBoolean(properties.getProperty("testnet"))) {
			params = new TestNet3Params();
			logger.info("Operating on Testnet3");
		} else {
			params = new MainNetParams();
			logger.info("Operating on MainNet");
		}
		Utility.setParams(params);
		Context context = Context.getOrCreate(params);

		List<File> blockChainFiles = Utility.getDefaultFileList();

		BlockSorter sorter = new BlockSorter(blockChainFiles);

		System.out.println("================================");
		System.out.println("Highest block: " + (sorter.getLongestBranch().size() - 1));
		System.out.println("================================");
	}
}
