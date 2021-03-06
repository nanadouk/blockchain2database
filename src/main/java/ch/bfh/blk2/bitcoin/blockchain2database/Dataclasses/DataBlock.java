package ch.bfh.blk2.bitcoin.blockchain2database.Dataclasses;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

import ch.bfh.blk2.bitcoin.blockchain2database.DatabaseConnection;

/**
 * Used to write Blocks into the database.
 * 
 * This is what will be written when writeBlock() is called, in order:
 * 
 * <ul>
 *   <li> The block </li>
 *   <li> For each transaction within the block:
 *   <ul>
 *     <li> The transaction itself </li>
 *     <li> For each output in the transaction
 *     <ul>
 *       <li> The output itself </li>
 *       <li> The output script (including further needed information, such as public keys and script information, depending on the type of script)</li>
 *     </ul>
 *     </li>
 *     <li> For each input in the transaction
 *     <ul>
 *       <li> The input itself </li>
 *       <li> The output script (including further needed information, such as signatures, public keys and script information, depending on the type of script)</li>
 *       <li> Updates to the previous output </li>
 *     </ul>
 *     </li>
 *   </ul>
 *   </li>
 * </ul>
 * 
 * Of course, not all of this is handled by the DataBlock itself though.
 * 
 */
public class DataBlock {

	private static final Logger logger = LogManager.getLogger("DataBlock");

	private Block block;
	private int height;
	private long prevBlockId = -1;
	private long blockId;
	private NetworkParameters params;
	private DatabaseConnection connection;

	private String insertBlockQuery = "INSERT INTO block"
			+ " (difficulty, hash, prev_blk_id, mrkl_root, time, height, version, nonce)"
			+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

	//private String getPrevBlockIdQuery = "SELECT block_id FROM block WHERE block_hash = ?;";

	/**
	 * This only creates the object and initializes the values. Nothing will be parsed or inserted into the database until 
	 * the writeBlock() method is called.
	 * 
	 * @param block The bitcoinj block object
	 * @param params The network parameters (mainnetparams or testnet3params in most cases)
	 * @param connection A Databaseconnection. Used to talk to the database
	 * @param height The block's height Will be stored to the databaes
	 * @param prevBlockId The databaseId of the previous block. Used to link them together (no prevBlkHash is stored)
	 */
	public DataBlock(Block block, NetworkParameters params, DatabaseConnection connection, int height,
			long prevBlockId) {

		this.block = block;
		this.connection = connection;
		this.height = height;
		this.prevBlockId = prevBlockId;
	}

	/**
	 * Writes the information on this block into the database.
	 */
	public void writeBlock() {

		try {
			PreparedStatement statement = (PreparedStatement) connection.getPreparedStatement(insertBlockQuery);

			statement.setLong(1, block.getDifficultyTarget()); // difficulty
			statement.setString(2, block.getHashAsString()); // hash
			// special treatment for genesis Block
			if (prevBlockId >= 0)
				statement.setLong(3, prevBlockId); // prevBlockId
			else
				statement.setNull(3, java.sql.Types.NULL);
			statement.setString(4, block.getMerkleRoot().toString()); // mrkl_root
			statement.setTimestamp(5, new java.sql.Timestamp(block.getTime().getTime())); // time
			statement.setLong(6, height); // height
			statement.setLong(7, block.getVersion()); // version
			statement.setLong(8, block.getNonce()); // Nonce

			statement.executeUpdate();

			ResultSet rs = statement.getGeneratedKeys();

			if (rs.next())
				blockId = rs.getLong(1);
			else {
				logger.fatal("Bad generatedKeySet from Block " + block.getHashAsString());
				connection.commit();
				connection.closeConnection();
				System.exit(1);
			}

			rs.close();
			statement.close();
			
			// Now that the block has been written, write all the transactions
			int blk_index = 0;
			for( Transaction transaction: block.getTransactions()){
				DataTransaction dataTransaction = new DataTransaction(transaction, blockId, connection, block.getTime(), blk_index++);
				dataTransaction.writeTransaction();
			}

		} catch (SQLException e) {
			logger.fatal("Failed to write Block " + block.getHashAsString());
			logger.fatal(e);
			connection.commit();
			connection.closeConnection();
			System.exit(1);
		}
		logger.debug("Writing Block " + block.getHashAsString());
	}

	public long getId() {
		return blockId;
	}
}
