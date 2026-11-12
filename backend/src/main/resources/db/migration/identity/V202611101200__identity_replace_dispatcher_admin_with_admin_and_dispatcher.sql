-- Owner: identity module (ADR 0003). Replaces DISPATCHER_ADMIN with ADMIN and DISPATCHER roles.

-- 1. Insert new roles (roles table has only name column)
INSERT INTO roles (name) VALUES
    ('ADMIN'),
    ('DISPATCHER')
ON CONFLICT (name) DO NOTHING;

-- 2. Deterministic Fail-Closed Verification & Migration
DO $$
DECLARE
    v_admin_id UUID;
    v_admin_count INT;
    v_user_count INT;
    v_admin_enabled BOOLEAN;
    v_admin_has_role BOOLEAN;
BEGIN
    SELECT COUNT(*) INTO v_user_count FROM users;

    IF v_user_count > 0 THEN
        -- Verify exactly one account matches admin@swiftdrop.local
        SELECT COUNT(*) INTO v_admin_count 
        FROM users 
        WHERE email = 'admin@swiftdrop.local';

        IF v_admin_count != 1 THEN
            RAISE EXCEPTION 'Migration halted: Expected exactly 1 account with email admin@swiftdrop.local, found %', v_admin_count;
        END IF;

        -- Fetch Admin details and verify prerequisites
        SELECT id, enabled INTO v_admin_id, v_admin_enabled 
        FROM users 
        WHERE email = 'admin@swiftdrop.local';

        IF NOT v_admin_enabled THEN
            RAISE EXCEPTION 'Migration halted: Existing Admin account admin@swiftdrop.local is disabled.';
        END IF;

        -- Verify it currently holds DISPATCHER_ADMIN
        SELECT EXISTS(
            SELECT 1 FROM user_roles 
            WHERE user_id = v_admin_id AND role_name = 'DISPATCHER_ADMIN'
        ) INTO v_admin_has_role;

        IF NOT v_admin_has_role THEN
            RAISE EXCEPTION 'Migration halted: Existing Admin account admin@swiftdrop.local does not hold DISPATCHER_ADMIN role.';
        END IF;

        -- 3. Update the single verified Admin account's role assignment to ADMIN
        -- Preserves user ID, password hash, email, and all profile data without modification
        UPDATE user_roles 
        SET role_name = 'ADMIN' 
        WHERE user_id = v_admin_id AND role_name = 'DISPATCHER_ADMIN';

        -- 4. Dynamic cleanup of ALL other non-Admin accounts currently holding DISPATCHER_ADMIN
        -- Deactivate all non-Admin legacy DISPATCHER_ADMIN accounts (preserves user records & references)
        UPDATE users 
        SET enabled = FALSE 
        WHERE id IN (
            SELECT ur.user_id 
            FROM user_roles ur 
            WHERE ur.role_name = 'DISPATCHER_ADMIN' 
              AND ur.user_id != v_admin_id
        );

        -- Remove obsolete DISPATCHER_ADMIN role assignments for all non-Admin accounts
        DELETE FROM user_roles 
        WHERE role_name = 'DISPATCHER_ADMIN' 
          AND user_id != v_admin_id;

    END IF;
END $$;

-- 5. Enforce single-admin invariant at database engine level
CREATE UNIQUE INDEX uq_user_roles_single_admin 
ON user_roles (role_name) 
WHERE role_name = 'ADMIN';

-- 6. Remove obsolete DISPATCHER_ADMIN role from roles catalog once all references are cleared
DELETE FROM roles WHERE name = 'DISPATCHER_ADMIN';

