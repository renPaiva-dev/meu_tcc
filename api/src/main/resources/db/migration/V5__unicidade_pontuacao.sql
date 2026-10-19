-- Dia do evento no fuso de Teresina (o mesmo de reciclathe.fuso-horario). Base das regras
-- "uma vez por dia" e da restrição de unicidade do check-in, que não pode usar now() num índice.
ALTER TABLE evento_pontuacao ADD COLUMN dia DATE;
UPDATE evento_pontuacao SET dia = (criado_em AT TIME ZONE 'America/Fortaleza')::date;
ALTER TABLE evento_pontuacao ALTER COLUMN dia SET NOT NULL;

-- Remove duplicidades que uma corrida entre requisições possa ter deixado, antes de criar as restrições
DELETE FROM evento_pontuacao e
USING evento_pontuacao o
WHERE e.tipo IN ('FEEDBACK', 'SUGESTAO_APROVADA')
  AND e.tipo = o.tipo
  AND e.referencia_id = o.referencia_id
  AND e.id > o.id;

DELETE FROM evento_pontuacao e
USING evento_pontuacao o
WHERE e.tipo = 'CHECKIN'
  AND o.tipo = 'CHECKIN'
  AND e.usuario_id = o.usuario_id
  AND e.referencia_id = o.referencia_id
  AND e.dia = o.dia
  AND e.id > o.id;

-- Um check-in pontuado por usuário, ponto e dia
CREATE UNIQUE INDEX uq_evento_checkin_dia ON evento_pontuacao (usuario_id, referencia_id, dia)
    WHERE tipo = 'CHECKIN';
-- Um feedback pontuado por identificação
CREATE UNIQUE INDEX uq_evento_feedback ON evento_pontuacao (referencia_id)
    WHERE tipo = 'FEEDBACK';
-- Uma pontuação por sugestão aprovada, mesmo que o ponto seja recusado e aprovado de novo
CREATE UNIQUE INDEX uq_evento_sugestao ON evento_pontuacao (referencia_id)
    WHERE tipo = 'SUGESTAO_APROVADA';
