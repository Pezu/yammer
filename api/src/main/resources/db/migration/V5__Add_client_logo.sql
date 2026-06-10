-- Object key of the client's logo in cloud storage (GCS). NULL = no logo.
ALTER TABLE client ADD COLUMN logo_object VARCHAR(255);
