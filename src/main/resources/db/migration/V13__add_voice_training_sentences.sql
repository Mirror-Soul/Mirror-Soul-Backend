CREATE TABLE voice_training_sentences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content VARCHAR(500) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_voice_training_sentences PRIMARY KEY (id)
);

ALTER TABLE voice_training_jobs
    ADD COLUMN voice_training_sentence_id BIGINT NULL,
    ADD CONSTRAINT fk_voice_training_jobs_sentence
        FOREIGN KEY (voice_training_sentence_id) REFERENCES voice_training_sentences(id);

INSERT INTO voice_training_sentences (content) VALUES
    ('오늘은 맑은 하늘 아래에서 새로운 하루를 시작합니다.'),
    ('따뜻한 햇살이 창문을 통해 방 안으로 조용히 들어옵니다.'),
    ('작은 변화가 모이면 언젠가 큰 성장을 이룰 수 있습니다.'),
    ('천천히 숨을 고르고 또렷한 목소리로 문장을 읽어 봅니다.'),
    ('새로운 경험은 우리에게 생각하지 못한 가능성을 열어 줍니다.'),
    ('좋아하는 음악을 들으며 여유로운 오후 시간을 보냈습니다.'),
    ('서로의 이야기에 귀를 기울이면 마음을 더 잘 이해할 수 있습니다.'),
    ('꾸준한 연습은 자신감 있는 목소리를 만드는 데 도움이 됩니다.'),
    ('오늘 세운 작은 목표를 하나씩 차분하게 실천해 나갑니다.'),
    ('밝은 목소리로 인사를 건네면 기분 좋은 대화가 시작됩니다.');
