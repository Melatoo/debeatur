-- A SELIC passa a vir da série diária 11 do SGS, acumulada nos dias úteis de cada mês, no lugar
-- da série mensal 4390, que tem duas casas (divergência 6 do docs/gabarito.md). O cache guarda os
-- valores sem dizer de qual série vieram, então os da 4390 saem: a próxima consulta busca tudo
-- de novo. Os cálculos salvos não mudam, porque guardam o próprio snapshot das séries.
DELETE FROM serie_mensal WHERE serie = 'SELIC';
DELETE FROM serie_consulta WHERE serie = 'SELIC';
