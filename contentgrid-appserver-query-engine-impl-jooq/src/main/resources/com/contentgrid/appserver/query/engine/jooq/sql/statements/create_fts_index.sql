CREATE INDEX IF NOT EXISTS "%s"
ON "%s"
USING GIN (to_tsvector('english', "%s"));

