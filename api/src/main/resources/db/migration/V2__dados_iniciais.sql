-- Zonas da coleta seletiva de Teresina. Telefones do Disk Coleta Seletiva conforme divulgado pela SEMDUH;
-- a zona Centro não tem número próprio divulgado, então usa o telefone geral da secretaria.
-- REVISAR periodicamente: telefones podem mudar.
INSERT INTO zona (codigo, nome, telefone_coleta, fonte) VALUES
('CENTRO',  'Centro',  '(86) 3218-1126', 'SEMDUH (telefone geral), citado em https://www.ecycle.com.br/pontos-de-coleta-seletiva/'),
('NORTE',   'Norte',   '(86) 98130-7739', 'Disk Coleta Seletiva SEMDUH, citado em https://www.ecycle.com.br/pontos-de-coleta-seletiva/'),
('SUL',     'Sul',     '(86) 98104-6786', 'Disk Coleta Seletiva SEMDUH, citado em https://www.ecycle.com.br/pontos-de-coleta-seletiva/'),
('LESTE',   'Leste',   '(86) 98130-7739', 'Disk Coleta Seletiva SEMDUH, citado em https://www.ecycle.com.br/pontos-de-coleta-seletiva/'),
('SUDESTE', 'Sudeste', '(86) 98104-6786', 'Disk Coleta Seletiva SEMDUH, citado em https://www.ecycle.com.br/pontos-de-coleta-seletiva/');

-- Cores conforme Resolução CONAMA nº 275/2001. As orientações são o ponto de partida e
-- podem ser editadas no painel administrativo sem nova versão do app.
INSERT INTO material (codigo, nome, cor_hex, cor_nome, reciclavel, ordem, como_preparar, onde_descartar, observacoes, fonte) VALUES
('PLASTICO', 'Plástico', '#D32F2F', 'vermelho', TRUE, 1,
 'Esvazie e enxágue rapidamente para tirar restos de alimento. Deixe secar e, se possível, amasse garrafas PET para ocupar menos espaço.',
 'Entregue em um Ponto de Entrega Voluntária (PEV) ou separe para a coleta seletiva da sua zona.',
 'Embalagens muito sujas de gordura (ex.: pote de margarina sem lavar) atrapalham a triagem. Isopor (EPS) é plástico, mas nem todo ponto aceita: confira no mapa.',
 'Resolução CONAMA nº 275/2001; Lei nº 12.305/2010 (Política Nacional de Resíduos Sólidos)'),
('PAPEL', 'Papel e papelão', '#1976D2', 'azul', TRUE, 2,
 'Mantenha seco e limpo. Desmonte e dobre caixas de papelão. Caixas longa vida (ex.: leite, suco) devem ser enxaguadas e achatadas.',
 'Entregue em um PEV ou separe para a coleta seletiva da sua zona.',
 'Papel engordurado (caixa de pizza suja), guardanapo usado e papel higiênico NÃO são recicláveis: vão para o rejeito.',
 'Resolução CONAMA nº 275/2001; Lei nº 12.305/2010'),
('METAL', 'Metal', '#F9A825', 'amarelo', TRUE, 3,
 'Enxágue latas de alimento e de bebida. Amasse latas de alumínio. Tampas metálicas podem ir junto.',
 'Entregue em um PEV ou separe para a coleta seletiva da sua zona.',
 'Latas de tinta, aerossóis e embalagens de produtos tóxicos exigem cuidado especial: não misture com o metal comum.',
 'Resolução CONAMA nº 275/2001; Lei nº 12.305/2010'),
('VIDRO', 'Vidro', '#388E3C', 'verde', TRUE, 4,
 'Esvazie e enxágue garrafas e potes (como a garrafa de cajuína). Vidro quebrado deve ser embrulhado em jornal ou colocado em caixa identificada para proteger quem faz a coleta.',
 'Entregue em um PEV que aceite vidro. Em Teresina há cooperativa que trabalha especificamente com vidro: confira os pontos no mapa.',
 'Espelhos, lâmpadas, vidro temperado e cerâmica NÃO são recicláveis junto com o vidro comum.',
 'Resolução CONAMA nº 275/2001; cooperativa de vidro em Teresina: https://mundocoop.com.br/social/cooperativas-ampliam-capacidade-de-destinacao-do-material-reciclavel-em-teresina'),
('ELETRONICO', 'Eletrônicos, pilhas e baterias', '#EF6C00', 'laranja', TRUE, 5,
 'Não desmonte. Se possível, retire pilhas e baterias do aparelho e isole os polos das baterias com fita.',
 'Leve a um ponto de logística reversa: lojas que vendem eletrônicos, pilhas e baterias são obrigadas a recebê-los de volta.',
 'NUNCA descarte no lixo comum: contém metais pesados que contaminam o solo e a água.',
 'Lei nº 12.305/2010, art. 33 (logística reversa); Decreto nº 10.240/2020'),
('ORGANICO', 'Orgânico', '#6D4C41', 'marrom', FALSE, 6,
 'Restos de alimentos, cascas de frutas e verduras, borra de café e folhas.',
 'Se tiver espaço, faça compostagem doméstica. Caso contrário, descarte na coleta convencional (lixo comum), separado dos recicláveis.',
 'Orgânico misturado aos recicláveis contamina o material e prejudica o trabalho das cooperativas.',
 'Resolução CONAMA nº 275/2001; Lei nº 12.305/2010'),
('REJEITO', 'Rejeito (não reciclável)', '#757575', 'cinza', FALSE, 7,
 'Papel higiênico, fraldas, absorventes, guardanapos usados, embalagens metalizadas sujas e materiais contaminados.',
 'Descarte na coleta convencional (lixo comum), em saco bem fechado.',
 'Medicamentos vencidos e seringas não vão no lixo comum: devolva em farmácias que fazem o recolhimento.',
 'Resolução CONAMA nº 275/2001; Lei nº 12.305/2010');
