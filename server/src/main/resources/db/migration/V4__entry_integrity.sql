CREATE OR REPLACE FUNCTION enforce_comment_reply_same_entry()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.reply_to_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM comment parent
        WHERE parent.id = NEW.reply_to_id AND parent.entry_id = NEW.entry_id
    ) THEN
        RAISE EXCEPTION 'comment reply must belong to the same entry';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER comment_reply_same_entry
AFTER INSERT OR UPDATE OF entry_id, reply_to_id ON comment
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_comment_reply_same_entry();
