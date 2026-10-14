import { createContext, useContext, type ReactNode } from 'react';
import { api } from '../services/api';
import { useResource } from '../hooks/useResource';
import type { Profile } from '../types/index';

const ProfileContext = createContext<ReturnType<typeof useResource<Profile>> | null>(null);

export function ProfileProvider({ children }: { children: ReactNode }) {
  const profile = useResource(api.profile);
  return <ProfileContext.Provider value={profile}>{children}</ProfileContext.Provider>;
}

export function useProfile() {
  const profile = useContext(ProfileContext);
  if (!profile) throw new Error('useProfile requires ProfileProvider');
  return profile;
}
