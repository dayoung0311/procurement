-- purchase_order의 varchar(20) 식별자/텍스트 컬럼 길이 확장 (value too long 방지)
ALTER TABLE purchase_order ALTER COLUMN warehouse_code TYPE varchar(255);
ALTER TABLE purchase_order ALTER COLUMN created_by     TYPE varchar(255);
ALTER TABLE purchase_order ALTER COLUMN received_by    TYPE varchar(255);
