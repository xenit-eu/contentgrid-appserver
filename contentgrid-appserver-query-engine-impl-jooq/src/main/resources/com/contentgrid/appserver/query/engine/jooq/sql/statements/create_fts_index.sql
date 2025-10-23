CREATE INDEX IF NOT EXISTS ?
ON ?
USING GIN (to_tsvector(?, coalesce(?, '')));