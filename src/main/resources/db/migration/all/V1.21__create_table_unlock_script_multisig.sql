CREATE TABLE IF NOT EXISTS unlock_script_multisig(
  tx_id BIGINT,
  tx_index INT,
  script_size INT,
    PRIMARY KEY(tx_id,tx_index),
    FOREIGN KEY(tx_id) REFERENCES input(tx_id),
    FOREIGN KEY(tx_index) REFERENCES input(tx_index)
)ENGINE = MEMORY;
