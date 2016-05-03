package ch.bfh.blk2.bitcoin.blockchain2database.Dataclasses;

import org.bitcoinj.script.Script;

import ch.bfh.blk2.bitcoin.blockchain2database.DatabaseConnection;

public class OPReturnScript implements OutputScript {

	private Script script;

	public OPReturnScript(Script script) {
		if (!script.isOpReturn())
			throw new IllegalArgumentException("Script must be of type OP_RETURN");

		this.script = script;
	}

	@Override
	public OutputType getType() {
		return OutputType.OP_RETURN;
	}

	@Override
	public void writeOutputScript(DatabaseConnection connection) {
		// TODO Auto-generated method stub

	}

}