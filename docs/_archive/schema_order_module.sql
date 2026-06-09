SELECT status, note, created_at
FROM order_status_history
WHERE order_id = 2
ORDER BY created_at ASC;