CREATE INDEX IF NOT EXISTS "%s"
    ON "%s" USING bm25("%s", "%s")
    WITH (
    key_field = "%s",
    text_fields = '{"%s":{"tokenizer":{"type":"default","stemmer":"English"}}}'
    )