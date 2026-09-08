-- V4: review & recommendation
CREATE TABLE review (
    review_id      BIGSERIAL PRIMARY KEY,
    member_id      BIGINT NOT NULL REFERENCES member (member_id),
    product_id     BIGINT NOT NULL REFERENCES product (product_id),
    order_item_id  BIGINT NOT NULL REFERENCES order_item (order_item_id),
    rating         INT NOT NULL,
    content        TEXT NOT NULL,
    height_cm      INT,
    weight_kg      INT,
    purchased_size VARCHAR(30),
    fit_rating     VARCHAR(32),
    status         VARCHAR(32) NOT NULL DEFAULT 'VISIBLE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_review_order_item UNIQUE (order_item_id),
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_review_fit CHECK (fit_rating IS NULL OR fit_rating IN ('SMALL', 'TRUE_TO_SIZE', 'LARGE')),
    CONSTRAINT ck_review_status CHECK (status IN ('VISIBLE', 'HIDDEN'))
);

CREATE INDEX idx_review_product ON review (product_id);

CREATE TABLE ai_recommendation (
    recommendation_id   BIGSERIAL PRIMARY KEY,
    member_id           BIGINT REFERENCES member (member_id),
    recommendation_type VARCHAR(50) NOT NULL,
    input_data          JSONB NOT NULL,
    result_data         JSONB NOT NULL,
    provider            VARCHAR(50) NOT NULL,
    model_name          VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recommendation_event (
    event_id          BIGSERIAL PRIMARY KEY,
    recommendation_id BIGINT NOT NULL REFERENCES ai_recommendation (recommendation_id),
    product_id        BIGINT REFERENCES product (product_id),
    event_type        VARCHAR(32) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_reco_event_type CHECK (event_type IN ('VIEW', 'CLICK', 'CART', 'PURCHASE', 'SKIP'))
);
