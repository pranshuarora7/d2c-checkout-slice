import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useProfile } from '../../context/ProfileContext';
import { ErrorNotice } from '../ErrorNotice';
import { api } from '../../services/api';
import type { ContactInput } from '../../types/index';

export function ProfileEditor({
  disabled = false,
  onEditing,
}: {
  disabled?: boolean;
  onEditing?: (editing: boolean) => void;
}) {
  const { data: profile, setData } = useProfile();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<ContactInput>({ name: '', email: '', phone: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);
  const inFlight = useRef(false);
  useEffect(() => {
    onEditing?.(editing || busy);
  }, [editing, busy, onEditing]);
  if (!profile) return null;

  async function save(event: FormEvent) {
    event.preventDefault();
    if (inFlight.current || disabled) return;
    inFlight.current = true;
    setBusy(true);
    setError(null);
    try {
      setData(await api.updateProfile(draft));
      setEditing(false);
      setSaved(true);
    } catch (reason) {
      setError(reason);
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }
  return (
    <section className="contact-card" aria-label="Contact details">
      <div className="section-title">
        <h2>Contact details</h2>
        {!editing && (
          <button
            className="text-button"
            disabled={disabled}
            onClick={() => {
              setDraft({ name: profile.name, email: profile.email, phone: profile.phone });
              setEditing(true);
              setSaved(false);
              setError(null);
            }}
          >
            Edit profile
          </button>
        )}
      </div>
      <ErrorNotice error={error} />
      {editing ? (
        <form onSubmit={(event) => void save(event)} className="field-grid">
          <label className="field full">
            Full name
            <input
              autoFocus
              autoComplete="name"
              required
              maxLength={80}
              value={draft.name}
              onChange={(e) => setDraft({ ...draft, name: e.target.value })}
              disabled={busy || disabled}
            />
          </label>
          <label className="field">
            Email
            <input
              type="email"
              autoComplete="email"
              required
              maxLength={120}
              value={draft.email}
              onChange={(e) => setDraft({ ...draft, email: e.target.value })}
              disabled={busy || disabled}
            />
          </label>
          <label className="field">
            Mobile number
            <input
              type="tel"
              autoComplete="tel-national"
              inputMode="numeric"
              required
              pattern="[6-9][0-9]{9}"
              title="A 10-digit Indian mobile number"
              maxLength={10}
              value={draft.phone}
              onChange={(e) => setDraft({ ...draft, phone: e.target.value })}
              disabled={busy || disabled}
            />
          </label>
          <div className="form-actions full">
            <button className="button button-dark" disabled={busy || disabled}>
              {busy ? 'Saving…' : 'Save profile'}
            </button>
            <button
              type="button"
              className="button button-outline"
              disabled={busy || disabled}
              onClick={() => {
                setEditing(false);
                setError(null);
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <>
          <strong className="contact-name">{profile.name}</strong>
          <p>
            {profile.email} <span className="contact-divider">·</span>{' '}
            <span className="contact-phone">{profile.phone}</span>
          </p>
          {saved && (
            <span role="status" className="saved-label">
              Profile saved
            </span>
          )}
        </>
      )}
    </section>
  );
}
