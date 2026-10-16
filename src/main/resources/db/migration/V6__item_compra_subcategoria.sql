-- =============================================================================
-- V6 — Itens de compra DIRETA/PATRIMONIAL sem vinculo com o estoque
--
--  Compras DIRETA e PATRIMONIAL nao repoem o almoxarifado: o item e descrito
--  livremente (item_compra.descricao) e classificado na propria compra por
--  grupo/subgrupo (subcategoria -> area). material_id passa a ser usado
--  apenas em compras de ESTOQUE (e em registros antigos).
-- =============================================================================

ALTER TABLE item_compra ADD COLUMN subcategoria_id bigint;
ALTER TABLE item_compra ADD CONSTRAINT fk_item_compra_subcategoria
    FOREIGN KEY (subcategoria_id) REFERENCES subcategoria (id);
CREATE INDEX idx_item_compra_subcategoria ON item_compra (subcategoria_id);
