package ch.bfh.blk2.bitcoin.producer;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.utils.BlockFileLoader;

import ch.bfh.blk2.bitcoin.comparator.Sha256HashComparator;
import ch.bfh.blk2.bitcoin.util.BlockFileList;
import ch.bfh.blk2.bitcoin.util.Utility;

/**
 * This class allows to iterate over the blockchain. It uses the BlockSorter to
 * retain the correct order of blocks to work through and offers an iterator
 * over all the transactions in the blockchain.
 *
 * All blocks will be returned to the caller in a logical order. That is to say,
 * if a transaction T_B spends a T_A, then T_A will always be returned before
 * T_B.
 *
 * Please note, that each Iterator can use up to two gigabytes of memory, so be
 * careful when using it.
 *
 * @author niklaus, stefan
 *
 */
public class BlockProducer implements Iterable<Block> {

	private static final int DEFAULT_MIN_BLOCK_DEPTH = 6;
	private static final Logger logger = LogManager.getLogger("BlockProducer");

	private List<Sha256Hash> orderedBlockHashes;
	private BlockFileList fileList;
	// private List<Block> blockBuffer;

	/**
	 * Uses the default minimum block depth 6
	 *
	 * @param bflist a list of unparsed Blockchain Files
	 */
	public BlockProducer(BlockFileList bflist) {
		this(bflist, DEFAULT_MIN_BLOCK_DEPTH);
	}

	/**
	 * Constructs a new Blockproducer. It will use BlockSorter to get a sorted list
	 * of blocks to work on.
	 *
	 * @param bflist a list of unparsed Blockchain Files
	 * @param minBlockDepth the number of blocks at the end of the blockchain to be ignored a number defining the minimal depth a block must have to be accepted
	 */
	public BlockProducer(BlockFileList bflist, int minBlockDepth) {
		this.fileList = bflist;
		BlockSorter bs = new BlockSorter(bflist);
		this.orderedBlockHashes = bs.getLongestBranch();

		if (orderedBlockHashes.size() > minBlockDepth)
			for (int i = 0; i < minBlockDepth; i++)
				orderedBlockHashes.remove(orderedBlockHashes.size() - 1);

		if (this.orderedBlockHashes.size() < 1) {
			logger.fatal("BlockSorter is malfunctioning or an invalid list of files was provided");
			System.exit(1);
		}
		logger.debug("Got a chain of " + this.orderedBlockHashes.size() + "blocks");
	}

	/**
	 * Constructs a blockproducer from a preexisting list of blocks. This saves the BlockProducer
	 * from having to retrieve it from the BlockSorter, which can be a slow process.
	 * 
	 * @param bflist a list of unparsed Blockchain Files
	 * @param validChain an ordered list of hashes, representing the order of blocks inside the blockchain
	 * @param minBlockDepth the number of blocks at the end of the blockchain to be ignored a number defining the minimal depth a block must have to be accepted
	 */
	public BlockProducer(BlockFileList bflist, List<Sha256Hash> validChain, int minBlockDepth) {
		this.fileList = bflist;
		orderedBlockHashes = validChain;

		if (orderedBlockHashes.size() > minBlockDepth)
			for (int i = 0; i < minBlockDepth; i++)
				orderedBlockHashes.remove(orderedBlockHashes.size() - 1);

		if (this.orderedBlockHashes.size() < 1) {
			logger.fatal("The provided list of Hashes was emtpy");
			System.exit(1);
		}
		logger.debug("Got a chain of " + this.orderedBlockHashes.size() + "blocks");
	}

	/**
	 * WARNING! Each Iterator can use up to two Gigabytes of memory. So be
	 * careful when using this.
	 */
	@Override
	public Iterator<Block> iterator() {
		return new BlockIterator();
	}

	public class BlockIterator implements Iterator<Block> {
		private Iterator<Sha256Hash> hashIterator;
		private Iterator<File> fileIterator;

		private Map<Sha256Hash, Block> blockBuffer;
		private List<Block> validBlocks;

		private BlockIterator() {
			hashIterator = BlockProducer.this.orderedBlockHashes.iterator();
			fileIterator = BlockProducer.this.fileList.iterator();

			blockBuffer = new TreeMap<>(new Sha256HashComparator());
			validBlocks = new LinkedList<>();

			// Place holder to make recursion work. Will be removed after
			// reading in the actual first blocks
			validBlocks.add(null);

			logger.debug("Going to get the very first block now");
		}

		@Override
		public boolean hasNext() {
			return hashIterator.hasNext();
		}

		@Override
		public Block next() throws NoSuchElementException {
			if (!hashIterator.hasNext())
				throw new NoSuchElementException();
			return getBlock(hashIterator.next());
		}

		private Block getBlock(Sha256Hash blockHash) {
			logger.trace("Got asked for block " + blockHash);
			if (blockBuffer.containsKey(blockHash)) {
				logger.trace("Found the block in the blockBuffer");
				return blockBuffer.remove(blockHash);
			} else {
				try {
					readNextFile();
				} catch (NoSuchFileException e) {
					System.err.println("Reached end of list of files while looking for Block " + blockHash);
					System.exit(1);
				}
				return getBlock(blockHash);
			}

		}

		private void readNextFile() throws NoSuchFileException {
			if (!fileIterator.hasNext())
				throw new NoSuchFileException("End of list of files");

			List<File> tmpList = new ArrayList<>(1);
			tmpList.add(fileIterator.next());
			logger.trace("Going to read in file" + tmpList.get(0).getName());
			BlockFileLoader bfl = new BlockFileLoader(Utility.PARAMS, tmpList);

			for (Block b : bfl)
				blockBuffer.put(b.getHash(), b);
		}

	}

}
