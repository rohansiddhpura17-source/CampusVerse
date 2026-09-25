// Tests frontend administrative guard logic to ensure consistency across all 6 administrative roles

const ADMINISTRATIVE_ROLES = [
  'SUPER_ADMIN',
  'ADMIN',
  'MODERATOR',
  'CONTENT_MANAGER',
  'SUPPORT_ADMIN',
  'ANALYTICS_ADMIN',
];

function hasAdministrativeAccess(user?: any): boolean {
  if (!user) return false;
  if (user.role === 'ADMIN' && user.isAdminAuthorized) return true;
  if (user.roles && user.roles.some((r: string) => ADMINISTRATIVE_ROLES.includes(r.toUpperCase()))) {
    return true;
  }
  return false;
}

describe('Frontend RBAC Guard Logic Unit Tests', () => {
  it('Grants access for all 6 administrative roles in user.roles', () => {
    for (const role of ADMINISTRATIVE_ROLES) {
      const user = {
        id: 'u1',
        email: `${role.toLowerCase()}@campusverse.edu`,
        role: 'STUDENT', // Base role may be student, but elevated by RBAC
        isAdminAuthorized: false,
        roles: [role],
      };
      expect(hasAdministrativeAccess(user)).toBe(true);
    }
  });

  it('Grants access for legacy ADMIN role when isAdminAuthorized is true', () => {
    const user = {
      id: 'u2',
      email: 'admin@campusverse.edu',
      role: 'ADMIN',
      isAdminAuthorized: true,
      roles: [],
    };
    expect(hasAdministrativeAccess(user)).toBe(true);
  });

  it('Denies access for legacy ADMIN role when isAdminAuthorized is false and no elevated roles', () => {
    const user = {
      id: 'u3',
      email: 'unauth_admin@campusverse.edu',
      role: 'ADMIN',
      isAdminAuthorized: false,
      roles: [],
    };
    expect(hasAdministrativeAccess(user)).toBe(false);
  });

  it('Denies access for standard STUDENT, ASPIRANT, ALUMNI without administrative roles', () => {
    const student = { id: 's1', role: 'STUDENT', isAdminAuthorized: false, roles: [] };
    const aspirant = { id: 'a1', role: 'ASPIRANT', isAdminAuthorized: false, roles: [] };
    const alumni = { id: 'al1', role: 'ALUMNI', isAdminAuthorized: false, roles: [] };

    expect(hasAdministrativeAccess(student)).toBe(false);
    expect(hasAdministrativeAccess(aspirant)).toBe(false);
    expect(hasAdministrativeAccess(alumni)).toBe(false);
  });

  it('Handles undefined, null, or empty user objects safely without crashing', () => {
    expect(hasAdministrativeAccess(undefined)).toBe(false);
    expect(hasAdministrativeAccess(null)).toBe(false);
    expect(hasAdministrativeAccess({})).toBe(false);
  });
});
