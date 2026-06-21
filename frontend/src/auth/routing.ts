import type { UserRole } from '../types';

export function postLoginDestination(role: UserRole) {
  return role === 'STUDENT' ? '/' : '/admin/';
}
