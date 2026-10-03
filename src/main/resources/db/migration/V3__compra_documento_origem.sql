-- ===== Compras: dados do documento de origem (Parte / Oficio) =====
-- Preenchidos automaticamente pela leitura do PDF anexado em "Nova pre-compra"
-- (ParteCompraParser) ou digitados pelo colaborador. Todos opcionais.

ALTER TABLE compra ADD COLUMN numero_documento        varchar(30);   -- ex.: 001/2026
ALTER TABLE compra ADD COLUMN numero_sgd              varchar(40);   -- ex.: 2026/09039/000362
ALTER TABLE compra ADD COLUMN data_documento          date;
ALTER TABLE compra ADD COLUMN assunto                 varchar(255);
ALTER TABLE compra ADD COLUMN solicitante_documento   varchar(255);  -- quem assina a Parte/Oficio

CREATE INDEX idx_compra_numero_documento ON compra (numero_documento);
