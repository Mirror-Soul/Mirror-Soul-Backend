CREATE TABLE users (
                       id BIGINT NOT NULL AUTO_INCREMENT,
                       email VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       name VARCHAR(50) NOT NULL,
                       gender VARCHAR(20) NULL,
                       job VARCHAR(30) NULL,
                       birth_date DATE NULL,
                       region VARCHAR(100) NULL,
                       profile_image_url VARCHAR(500) NULL,
                       status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                       CONSTRAINT pk_users PRIMARY KEY (id),
                       CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE interviews (
                            id BIGINT NOT NULL AUTO_INCREMENT,
                            question VARCHAR(255) NOT NULL,

                            CONSTRAINT pk_interviews PRIMARY KEY (id)
);

CREATE TABLE interview_record (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  user_id BIGINT NOT NULL,
                                  interview_id BIGINT NOT NULL,
                                  answer_audio_url VARCHAR(500) NULL,
                                  answer_text TEXT NULL,
                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT pk_interview_record PRIMARY KEY (id),

                                  CONSTRAINT uk_user_interview UNIQUE (user_id, interview_id),

                                  CONSTRAINT fk_interview_record_user
                                      FOREIGN KEY (user_id) REFERENCES users(id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT fk_interview_record_interview
                                      FOREIGN KEY (interview_id) REFERENCES interviews(id)
                                          ON DELETE CASCADE
);

CREATE TABLE face_files (
                            id BIGINT NOT NULL AUTO_INCREMENT,
                            user_id BIGINT NOT NULL,
                            file_url VARCHAR(500) NOT NULL,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                            CONSTRAINT pk_face_files PRIMARY KEY (id),
                            CONSTRAINT fk_face_files_user
                                FOREIGN KEY (user_id) REFERENCES users(id)
                                    ON DELETE CASCADE
);

CREATE TABLE mbti_profile (
                              id BIGINT NOT NULL AUTO_INCREMENT,
                              user_id BIGINT NOT NULL,
                              mbti VARCHAR(10) NOT NULL,
                              ie_score INT NOT NULL,
                              ns_score INT NOT NULL,
                              ft_score INT NOT NULL,
                              pj_score INT NOT NULL,
                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                              CONSTRAINT pk_mbti_profile PRIMARY KEY (id),
                              CONSTRAINT fk_mbti_profile_user
                                  FOREIGN KEY (user_id) REFERENCES users(id)
                                      ON DELETE CASCADE,
                              CONSTRAINT uk_mbti_profile_user UNIQUE (user_id)
);

CREATE TABLE region (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        lawd_cd VARCHAR(10) NOT NULL,
                        sido_name VARCHAR(50) NOT NULL,
                        sigungu_name VARCHAR(50) NOT NULL,
                        eupmyeondong_name VARCHAR(50) NOT NULL,

                        CONSTRAINT pk_region PRIMARY KEY (id),
                        CONSTRAINT uk_region_lawd_cd UNIQUE (lawd_cd)
);

CREATE TABLE user_preferred_region (
                                       id BIGINT NOT NULL AUTO_INCREMENT,
                                       user_id BIGINT NOT NULL,
                                       region_id BIGINT NOT NULL,
                                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                       CONSTRAINT pk_user_preferred_region PRIMARY KEY (id),

                                       CONSTRAINT fk_upr_user
                                           FOREIGN KEY (user_id) REFERENCES users(id)
                                               ON DELETE CASCADE,

                                       CONSTRAINT fk_upr_region
                                           FOREIGN KEY (region_id) REFERENCES region(id)
                                               ON DELETE CASCADE,

                                       CONSTRAINT uk_user_region UNIQUE (user_id, region_id)
);

CREATE TABLE missions (
                          id BIGINT NOT NULL AUTO_INCREMENT,
                          title VARCHAR(100) NOT NULL,
                          description TEXT NULL,
                          mission_type VARCHAR(30) NOT NULL,
                          reward_point INT NOT NULL DEFAULT 0,

                          CONSTRAINT pk_missions PRIMARY KEY (id)
);

CREATE TABLE user_mission (
                              id BIGINT NOT NULL AUTO_INCREMENT,
                              user_id BIGINT NOT NULL,
                              mission_id BIGINT NOT NULL,
                              status VARCHAR(20) NOT NULL,
                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              started_at DATETIME NULL,
                              completed_at DATETIME NULL,

                              CONSTRAINT pk_user_mission PRIMARY KEY (id),

                              CONSTRAINT fk_user_mission_user
                                  FOREIGN KEY (user_id) REFERENCES users(id)
                                      ON DELETE CASCADE,

                              CONSTRAINT fk_user_mission_mission
                                  FOREIGN KEY (mission_id) REFERENCES missions(id)
                                      ON DELETE CASCADE
);

CREATE TABLE clones (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        user_id BIGINT NOT NULL,
                        sync_rate INT NOT NULL DEFAULT 0,
                        avatar_image_url VARCHAR(500) NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        summary TEXT NULL,

                        CONSTRAINT pk_clones PRIMARY KEY (id),
                        CONSTRAINT uk_clones_user UNIQUE (user_id),

                        CONSTRAINT fk_clones_user
                            FOREIGN KEY (user_id) REFERENCES users(id)
                                ON DELETE CASCADE
);

CREATE TABLE video_calls (
                             id BIGINT NOT NULL AUTO_INCREMENT,
                             clone_id BIGINT NOT NULL,
                             user_id BIGINT NOT NULL,
                             started_at DATETIME NOT NULL,
                             ended_at DATETIME NULL,
                             duration_sec INT NULL,
                             status VARCHAR(20) NOT NULL,
                             video_url VARCHAR(500) NULL,

                             CONSTRAINT pk_video_calls PRIMARY KEY (id),

                             CONSTRAINT fk_video_calls_clone
                                 FOREIGN KEY (clone_id) REFERENCES clones(id)
                                     ON DELETE CASCADE,

                             CONSTRAINT fk_video_calls_user
                                 FOREIGN KEY (user_id) REFERENCES users(id)
                                     ON DELETE CASCADE
);

CREATE TABLE talk_logs (
                           id BIGINT NOT NULL AUTO_INCREMENT,
                           video_call_id BIGINT NOT NULL,
                           speaker VARCHAR(20) NOT NULL,
                           message TEXT NOT NULL,
                           started_at DATETIME NOT NULL,
                           ended_at DATETIME NULL,

                           CONSTRAINT pk_talk_logs PRIMARY KEY (id),

                           CONSTRAINT fk_talk_logs_video_call
                               FOREIGN KEY (video_call_id) REFERENCES video_calls(id)
                                   ON DELETE CASCADE
);

CREATE TABLE clone_jobs (
                            id BIGINT NOT NULL AUTO_INCREMENT,
                            clone_id BIGINT NOT NULL,
                            status VARCHAR(20) NOT NULL,
                            input_face_url TEXT NOT NULL,
                            input_voice_url TEXT NOT NULL,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            finished_at DATETIME NULL,
                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                            CONSTRAINT pk_clone_jobs PRIMARY KEY (id),

                            CONSTRAINT fk_clone_jobs_clone
                                FOREIGN KEY (clone_id) REFERENCES clones(id)
                                    ON DELETE CASCADE
);