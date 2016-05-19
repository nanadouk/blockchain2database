package ch.bfh.blk2.bitcoin.blockchain2database.Dataclasses;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;

public class InputScriptCreator {

	private static final Logger logger = LogManager.getLogger("InputScriptCreator");
	
	/*
	// Lengths of keys and signatures according to
	// https://en.bitcoin.it/wiki/Elliptic_Curve_Digital_Signature_Algorithm
	private final static int MAX_KEY_LENGTH = 65;
	private final static int MAX_SIG_LENGTH = 73;
	*/

	public static InputScript parseScript(TransactionInput in, long txId, int txIndex, ScriptType prevOutType,
			long prevTxId, int prevTxIndex) {

		byte[] inputBytes = in.getScriptBytes();
		int scriptSize = inputBytes.length;

		// COINBASE
		if (in.isCoinBase())
			return new CoinbaseInputScript(inputBytes, txId, txIndex, scriptSize);

		Script script = new Script(inputBytes);

		// P2PKH
		if (prevOutType == ScriptType.OUT_P2PKHASH){
			InputScript inputScript;
			try{
				inputScript =  new P2PKHInputScript(script, txId, txIndex, scriptSize);
			} catch (IllegalArgumentException e){
				logger.debug("Non standard Pay to public key hash input script: " + e.toString() );
				logger.debug("The script looks like so: " + script.toString());
				inputScript =  new OtherInputScript(txId, txIndex, script, scriptSize, ScriptType.IN_P2PKH_SPEC);
			}
			
			return inputScript;
		}

		// MULTISIG
		if (prevOutType == ScriptType.OUT_MULTISIG || prevOutType == ScriptType.OUT_MULTISIG_SPEC){
			InputScript inputScript;
			try{
				inputScript =  new MultisigInputScript(txId, txIndex, script, scriptSize);
			} catch (IllegalArgumentException e){
				logger.debug("Non standard Multisig input script: " + e.toString() );
				logger.debug("The script looks like so: " + script.toString());
				inputScript =  new OtherInputScript(txId, txIndex, script, scriptSize, ScriptType.IN_MLUTISIG_SPEC);
			}
			
			return inputScript;
		}
		
		// RAW PUB KEY
		if (prevOutType == ScriptType.OUT_P2RAWPUBKEY || prevOutType == ScriptType.OUT_RAWPUBKEY_SPEC){
			InputScript inputScript;
			try{
				inputScript= new P2RawPubKeyInputscript(txId, txIndex, script, scriptSize);
			} catch( IllegalArgumentException e){
				logger.debug("Non standard RAW PUB KEY input script: " + e.toString() );
				logger.debug("The script looks like so: " + script.toString());
				inputScript =  new OtherInputScript(txId, txIndex, script, scriptSize, ScriptType.IN_P2RAWPUBKEY_SPEC);
			}
			
			return inputScript;
		}
				
		// P2SH
		if (prevOutType == ScriptType.OUT_P2SH){
			InputScript inputScript;
			if (isP2SHMultisig(script))
				try{
					inputScript = new P2SHMultisigInputScript(txId, txIndex, script, scriptSize);
				} catch (IllegalArgumentException e){
					logger.debug("P2SH Multisig Input with weird Input script: " + e.toString() );
					logger.debug("The script looks like so: " + script.toString());
					inputScript =  new OtherInputScript(txId, txIndex, script, scriptSize, ScriptType.IN_P2SH_MULTISIG_SPEC);
				}
			else
				inputScript =  new P2SHOtherInputScript(script, txId, txIndex, scriptSize);
			
			return inputScript;
		}

		// OTHER
		if (prevOutType == ScriptType.OUT_OTHER)
			return new OtherInputScript(txId, txIndex, script, scriptSize);

		// input script must be one of these types input script can't be invalid
		logger.fatal("Invalid Inputscript of type " + prevOutType + " : " + script.toString());
		System.exit(1);
		return null;
	}
	
	private static boolean isP2SHMultisig(Script script) {
		List<ScriptChunk> scriptChunks = script.getChunks();
		ScriptChunk lastChunk = scriptChunks.get(script.getChunks().size() - 1);
		
		if( lastChunk.data == null)
			return false;

		try {
			Script redeemScript = new Script(lastChunk.data);
			if(redeemScript.isSentToMultiSig())
				return true;
			
			return false;
		} catch (ScriptException e) {
			logger.debug("invalid redeem Script or data. Can't parse the script");
			return false;
		}
	}
}
