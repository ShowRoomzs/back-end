CREATE TABLE creator_application_history (
    creator_application_history_id BIGINT       NOT NULL AUTO_INCREMENT,
    creator_application_id           BIGINT       NOT NULL,
    previous_status                  VARCHAR(20)  NOT NULL,
    new_status                       VARCHAR(20)  NOT NULL,
    reason                           VARCHAR(500) NULL,
    created_at                       DATETIME(6)  NOT NULL,
    modified_at                      DATETIME(6)  NOT NULL,
    PRIMARY KEY (creator_application_history_id),
    CONSTRAINT FK_CREATOR_APPLICATION_HISTORY_APPLICATION
        FOREIGN KEY (creator_application_id) REFERENCES CREATOR_APPLICATION (CREATOR_APPLICATION_ID)
);
