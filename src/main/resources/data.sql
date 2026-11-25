-- =====================================================================
-- Datos semilla PayLink (100% ficticios: nombres, documentos y cuentas)
-- =====================================================================

-- Bancos simulados
INSERT INTO bank (id, code, name) VALUES (1, 'ANDINO',   'Banco Andino');
INSERT INTO bank (id, code, name) VALUES (2, 'CONDOR',   'Banco Cóndor');
INSERT INTO bank (id, code, name) VALUES (3, 'PACIFICO', 'Banco Pacífico Digital');

-- Cuenta recaudadora del comercio
INSERT INTO account (id, account_number, holder_name, document_type, document_number, bank_id, balance, account_type, simulated_pin)
VALUES (1, '9000000001', 'Tienda Demo S.A.S.', 'NIT', '900000001', 1, 1000000.00, 'MERCHANT', NULL);

-- Cuentas de pagadores (clave simulada: 1234)
INSERT INTO account (id, account_number, holder_name, document_type, document_number, bank_id, balance, account_type, simulated_pin)
VALUES (2, '1000000001', 'Ana Prueba Ficticia', 'CC', '1000000001', 1, 5000000.00, 'PAYER', '1234');

INSERT INTO account (id, account_number, holder_name, document_type, document_number, bank_id, balance, account_type, simulated_pin)
VALUES (3, '1000000002', 'Luis Saldo Bajo', 'CC', '1000000002', 1, 10000.00, 'PAYER', '1234');

INSERT INTO account (id, account_number, holder_name, document_type, document_number, bank_id, balance, account_type, simulated_pin)
VALUES (4, '2000000001', 'Marta Ejemplo Cóndor', 'CE', '2000000001', 2, 2000000.00, 'PAYER', '1234');

INSERT INTO account (id, account_number, holder_name, document_type, document_number, bank_id, balance, account_type, simulated_pin)
VALUES (5, '3000000001', 'Empresa Ficticia Ltda.', 'NIT', '800000001', 3, 10000000.00, 'PAYER', '1234');

-- Comercio de ejemplo
INSERT INTO merchant (id, name, nit, account_id, api_key)
VALUES (1, 'Tienda Demo S.A.S.', '900000001', 1, 'demo-api-key-123');

-- Reinicia los contadores de identidad para que nuevos registros no choquen con los ids semilla
ALTER TABLE bank ALTER COLUMN id RESTART WITH 100;
ALTER TABLE account ALTER COLUMN id RESTART WITH 100;
ALTER TABLE merchant ALTER COLUMN id RESTART WITH 100;
