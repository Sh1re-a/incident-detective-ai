UPDATE runbook_embeddings
SET input_characters = char_length(
        'title: ' || title || ' | text: ' || body
    )
WHERE input_characters = 0;
