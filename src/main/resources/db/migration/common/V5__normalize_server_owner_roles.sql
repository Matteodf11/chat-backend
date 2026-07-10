UPDATE server_members sm
SET role = 'owner'
FROM servers s
WHERE sm.server_id = s.id
  AND sm.user_id = s.owner_id
  AND sm.role <> 'owner';
