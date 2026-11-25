-- =====================================================================
-- Datos semilla PayLink para PostgreSQL (perfil "vercel").
-- Idempotente: se ejecuta en cada arranque de cada instancia sin duplicar
-- ni sobrescribir datos. Para volver a los saldos iniciales: POST /api/v1/demo/reset
-- Todos los datos son ficticios.
-- =====================================================================

INSERT INTO bank (id, code, name) VALUES
  (1, 'ANDINO',   'Banco Andino'),
  (2, 'CONDOR',   'Banco Cóndor'),
  (3, 'PACIFICO', 'Banco Pacífico Digital')
ON CONFLICT DO NOTHING;

INSERT INTO account (id, account_number, holder_name, document_type, document_number, bank_id, balance, account_type, simulated_pin) VALUES
  (1, '9000000001', 'Tienda Demo S.A.S.',     'NIT', '900000001',  1, 1000000.00,  'MERCHANT', NULL),
  (2, '1000000001', 'Ana Prueba Ficticia',    'CC',  '1000000001', 1, 5000000.00,  'PAYER',    '1234'),
  (3, '1000000002', 'Luis Saldo Bajo',        'CC',  '1000000002', 1, 10000.00,    'PAYER',    '1234'),
  (4, '2000000001', 'Marta Ejemplo Cóndor',   'CE',  '2000000001', 2, 2000000.00,  'PAYER',    '1234'),
  (5, '3000000001', 'Empresa Ficticia Ltda.', 'NIT', '800000001',  3, 10000000.00, 'PAYER',    '1234')
ON CONFLICT DO NOTHING;

INSERT INTO merchant (id, name, nit, account_id, api_key) VALUES
  (1, 'Tienda Demo S.A.S.', '900000001', 1, 'demo-api-key-123')
ON CONFLICT DO NOTHING;
