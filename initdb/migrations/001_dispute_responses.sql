-- Adds admin replies on disputes to an existing OLTP database.
-- Fresh databases get this from oltp_schema.sql; this file is safe to re-run.
-- Apply with: docker exec -i postgres_container psql -U postgres -d sampleoltpdata < initdb/migrations/001_dispute_responses.sql
CREATE TABLE IF NOT EXISTS Dispute_Responses (
    Response_ID BIGSERIAL PRIMARY KEY,
    Dispute_ID BIGINT NOT NULL REFERENCES Disputes(Dispute_ID),
    Admin_ID BIGINT NOT NULL REFERENCES Users(User_ID),
    Message TEXT NOT NULL CHECK (length(btrim(Message)) BETWEEN 1 AND 2000),
    Created_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS ix_dispute_responses_dispute
    ON Dispute_Responses(Dispute_ID, Created_Date, Response_ID);
