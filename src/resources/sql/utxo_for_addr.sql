-- Get all unspent transaction outputs for a given address
DELIMITER // 

CREATE OR REPLACE PROCEDURE get_utxo( addr varchar(255) )
  BEGIN
    SELECT tx_id,tx_index FROM output WHERE spent_at is NULL AND address = addr;
  END;

//
DELIMITER ;
