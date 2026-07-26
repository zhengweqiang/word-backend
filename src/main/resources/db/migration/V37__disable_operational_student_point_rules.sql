UPDATE student_point_rules
SET enabled = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE code IN ('MANUAL_ADJUSTMENT', 'ADMIN_CORRECTION');
