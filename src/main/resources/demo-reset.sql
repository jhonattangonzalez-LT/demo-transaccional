-- Restablece los datos demo a su estado inicial (SQL compatible con H2 y PostgreSQL).
DELETE FROM movement;
DELETE FROM bank_session;
DELETE FROM payment_transaction;
UPDATE account SET balance = 1000000.00  WHERE account_number = '9000000001';
UPDATE account SET balance = 5000000.00  WHERE account_number = '1000000001';
UPDATE account SET balance = 10000.00    WHERE account_number = '1000000002';
UPDATE account SET balance = 2000000.00  WHERE account_number = '2000000001';
UPDATE account SET balance = 10000000.00 WHERE account_number = '3000000001';
