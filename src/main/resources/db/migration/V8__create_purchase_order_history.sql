-- 이슈 #36: PO 변경이력 테이블
-- 모든 PO 변경(생성/헤더수정/라인수정/완료/취소)을 변경 전/후 스냅샷과 함께 기록

CREATE TABLE purchase_order_history (
                                        id              BIGSERIAL PRIMARY KEY,
                                        po_number       VARCHAR(20)  NOT NULL,
                                        change_type     VARCHAR(30)  NOT NULL,
                                        before_payload  TEXT,
                                        after_payload   TEXT         NOT NULL,
                                        changed_by      VARCHAR(20)  NOT NULL,
                                        changed_at      TIMESTAMP    NOT NULL
);

CREATE INDEX idx_po_history_po_number ON purchase_order_history (po_number);

COMMENT ON COLUMN purchase_order_history.change_type IS 'CREATED / HEADER_UPDATE / LINES_REPLACED / COMPLETED / CANCELED';
COMMENT ON COLUMN purchase_order_history.before_payload IS '변경 전 PO 전체 스냅샷 JSON (CREATED는 NULL)';
COMMENT ON COLUMN purchase_order_history.after_payload IS '변경 후 PO 전체 스냅샷 JSON';