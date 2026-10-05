-- =============================================================================
-- V5 — Setores por organização (FPTO x FA-Saúde)
--
--  * setor.organizacao: FPTO (Fundação Pró-Tocantins) | FA_SAUDE (FA-Saúde).
--    As duas têm setores homônimos (ex.: Financeiro); a coluna os diferencia.
--    Setores já cadastrados ficam como FPTO — revisar na tela /setores.
--  * setor.responsavel deixa de ser obrigatório: os setores são cadastrados
--    sem pessoas (o acesso de cada pessoa é controlado pelos grupos do AD).
--  * Carga dos setores oficiais das duas organizações. Idempotente: não
--    duplica um setor que já exista com o mesmo nome na mesma organização.
--    Não foram criados: "Administradores / Domain Admins" (não é setor) e os
--    subgrupos de permissão do RH (contas, digitalizadas, ponto), que fazem
--    parte do setor Recursos Humanos.
-- =============================================================================

ALTER TABLE setor ADD COLUMN organizacao varchar(10) NOT NULL DEFAULT 'FPTO';
ALTER TABLE setor ALTER COLUMN responsavel DROP NOT NULL;
CREATE INDEX idx_setor_organizacao ON setor (organizacao);

-- ----- Fundação Pró-Tocantins (FPTO) -----------------------------------------
INSERT INTO setor (nome, organizacao) SELECT 'Compras e Almoxarifado', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Compras e Almoxarifado') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Financeiro', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Financeiro') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Documentação da Fundação', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Documentação da Fundação') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Transporte e Patrimônio', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Transporte e Patrimônio') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Recursos Humanos', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Recursos Humanos') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Jurídico', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Jurídico') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Conselho', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Conselho') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Assistência Social', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Assistência Social') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Tecnologia da Informação', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Tecnologia da Informação') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Controle Interno', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Controle Interno') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Contratos de Prestação', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Contratos de Prestação') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Pecúlio e Cadastro', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Pecúlio e Cadastro') AND organizacao = 'FPTO');
INSERT INTO setor (nome, organizacao) SELECT 'Recepção', 'FPTO'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Recepção') AND organizacao = 'FPTO');

-- ----- FA-Saúde ---------------------------------------------------------------
INSERT INTO setor (nome, organizacao) SELECT 'Administração / T.I.', 'FA_SAUDE'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Administração / T.I.') AND organizacao = 'FA_SAUDE');
INSERT INTO setor (nome, organizacao) SELECT 'Financeiro', 'FA_SAUDE'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Financeiro') AND organizacao = 'FA_SAUDE');
INSERT INTO setor (nome, organizacao) SELECT 'Atendimento', 'FA_SAUDE'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Atendimento') AND organizacao = 'FA_SAUDE');
INSERT INTO setor (nome, organizacao) SELECT 'Pecúlio', 'FA_SAUDE'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Pecúlio') AND organizacao = 'FA_SAUDE');
INSERT INTO setor (nome, organizacao) SELECT 'FAMCARD', 'FA_SAUDE'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('FAMCARD') AND organizacao = 'FA_SAUDE');
INSERT INTO setor (nome, organizacao) SELECT 'Coordenação', 'FA_SAUDE'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Coordenação') AND organizacao = 'FA_SAUDE');
INSERT INTO setor (nome, organizacao) SELECT 'Faturamento', 'FA_SAUDE'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Faturamento') AND organizacao = 'FA_SAUDE');
INSERT INTO setor (nome, organizacao) SELECT 'Cadastro', 'FA_SAUDE'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Cadastro') AND organizacao = 'FA_SAUDE');
INSERT INTO setor (nome, organizacao) SELECT 'Enfermagem', 'FA_SAUDE'
    WHERE NOT EXISTS (SELECT 1 FROM setor WHERE lower(nome) = lower('Enfermagem') AND organizacao = 'FA_SAUDE');
