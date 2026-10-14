import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useProfile } from '../../context/ProfileContext';
import { ErrorNotice } from '../ErrorNotice';
import { api } from '../../services/api';
import type { Address, AddressInput } from '../../types/index';

const empty: AddressInput = { label: 'Home', addressLine: '', city: '', state: '', pinCode: '' };

export function AddressBook({
  selectedId,
  onSelect,
  disabled = false,
  onEditing,
}: {
  selectedId?: string;
  onSelect?: (id: string) => void;
  disabled?: boolean;
  onEditing?: (editing: boolean) => void;
}) {
  const { data: profile, setData } = useProfile();
  const [editing, setEditing] = useState<string | null>(null);
  const [draft, setDraft] = useState<AddressInput>(empty);
  const [removing, setRemoving] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [message, setMessage] = useState('');
  const inFlight = useRef(false);
  useEffect(() => {
    onEditing?.(editing !== null || removing !== null || busy);
  }, [editing, removing, busy, onEditing]);
  if (!profile) return null;

  function edit(address?: Address) {
    setEditing(address?.id ?? 'new');
    setRemoving(null);
    setError(null);
    setMessage('');
    setDraft(
      address
        ? {
            label: address.label,
            addressLine: address.addressLine,
            city: address.city,
            state: address.state,
            pinCode: address.pinCode,
          }
        : empty,
    );
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (inFlight.current || disabled) return;
    inFlight.current = true;
    setBusy(true);
    setError(null);
    try {
      const next =
        editing === 'new' ? await api.addAddress(draft) : await api.updateAddress(editing!, draft);
      setData(next);
      if (editing === 'new') onSelect?.(next.addresses[next.addresses.length - 1].id);
      setEditing(null);
      setMessage('Address saved.');
    } catch (reason) {
      setError(reason);
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }
  async function remove(id: string) {
    if (inFlight.current || disabled) return;
    inFlight.current = true;
    setBusy(true);
    setError(null);
    try {
      const next = await api.removeAddress(id);
      setData(next);
      if (selectedId === id) onSelect?.(next.addresses[0]?.id ?? '');
      setRemoving(null);
      setMessage('Address removed. Existing orders keep their delivery details.');
    } catch (reason) {
      setError(reason);
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }
  return (
    <section className="address-book" aria-label="Saved addresses">
      <div className="section-title">
        <h2>{onSelect ? 'Delivery address' : 'Saved addresses'}</h2>
        <button
          type="button"
          className="text-button"
          disabled={busy || disabled || profile.addresses.length >= 10}
          onClick={() => edit()}
        >
          + Add address
        </button>
      </div>
      <ErrorNotice error={error} />
      {message && (
        <p role="status" className="success-text">
          {message}
        </p>
      )}
      {profile.addresses.length === 0 && (
        <p className="address-empty">No saved addresses yet. Add a delivery address to continue.</p>
      )}
      <div className="address-list">
        {profile.addresses.map((address) => (
          <article
            key={address.id}
            className={'saved-address' + (selectedId === address.id ? ' selected' : '')}
          >
            {onSelect ? (
              <label className="address-choice">
                <input
                  type="radio"
                  name="address"
                  aria-label={address.label + ', ' + address.addressLine}
                  checked={selectedId === address.id}
                  disabled={busy || disabled}
                  onChange={() => onSelect(address.id)}
                />
                <AddressText address={address} />
              </label>
            ) : (
              <AddressText address={address} />
            )}
            <div className="address-actions">
              <button
                type="button"
                className="text-button"
                disabled={busy || disabled}
                aria-label={'Edit ' + address.label + ' address'}
                onClick={() => edit(address)}
              >
                Edit
              </button>
              <button
                type="button"
                className="text-button muted"
                disabled={busy || disabled}
                aria-label={'Remove ' + address.label + ' address'}
                onClick={() => {
                  setRemoving(address.id);
                  setEditing(null);
                  setError(null);
                }}
              >
                Remove
              </button>
            </div>
            {removing === address.id && (
              <div className="remove-confirm" role="group" aria-label="Confirm address removal">
                <p>Remove this saved address? Existing orders will keep their delivery details.</p>
                <button
                  type="button"
                  className="button button-danger"
                  disabled={busy}
                  onClick={() => void remove(address.id)}
                >
                  {busy ? 'Removing…' : 'Remove address'}
                </button>
                <button
                  type="button"
                  className="text-button"
                  disabled={busy}
                  onClick={() => setRemoving(null)}
                >
                  Keep address
                </button>
              </div>
            )}
          </article>
        ))}
      </div>
      {editing && (
        <form
          className="address-form"
          onSubmit={(event) => void save(event)}
          aria-label={editing === 'new' ? 'New address' : 'Edit address'}
        >
          <h3>{editing === 'new' ? 'Add a new address' : 'Edit address'}</h3>
          <div className="field-grid">
            <label className="field full">
              Address label
              <input
                autoFocus
                required
                maxLength={30}
                value={draft.label}
                placeholder="Home, Work…"
                disabled={busy}
                onChange={(e) => setDraft({ ...draft, label: e.target.value })}
              />
            </label>
            <label className="field full">
              Street address
              <input
                autoComplete="street-address"
                required
                maxLength={180}
                value={draft.addressLine}
                placeholder="Flat, building, street and area"
                disabled={busy}
                onChange={(e) => setDraft({ ...draft, addressLine: e.target.value })}
              />
            </label>
            <label className="field">
              City
              <input
                autoComplete="address-level2"
                required
                maxLength={80}
                value={draft.city}
                disabled={busy}
                onChange={(e) => setDraft({ ...draft, city: e.target.value })}
              />
            </label>
            <label className="field">
              State
              <input
                autoComplete="address-level1"
                required
                maxLength={80}
                value={draft.state}
                disabled={busy}
                onChange={(e) => setDraft({ ...draft, state: e.target.value })}
              />
            </label>
            <label className="field">
              PIN code
              <input
                autoComplete="postal-code"
                inputMode="numeric"
                required
                pattern="[1-9][0-9]{5}"
                title="A 6-digit Indian PIN code"
                maxLength={6}
                value={draft.pinCode}
                disabled={busy}
                onChange={(e) => setDraft({ ...draft, pinCode: e.target.value })}
              />
            </label>
            <div className="form-actions full">
              <button className="button button-dark" disabled={busy || disabled}>
                {busy ? 'Saving…' : 'Save address'}
              </button>
              <button
                type="button"
                className="button button-outline"
                disabled={busy}
                onClick={() => {
                  setEditing(null);
                  setError(null);
                }}
              >
                Cancel
              </button>
            </div>
          </div>
        </form>
      )}
    </section>
  );
}

function AddressText({ address }: { address: Address }) {
  return (
    <div className="address-text">
      <strong>{address.label}</strong>
      <span>{address.addressLine}</span>
      <span>
        {address.city}, {address.state} {address.pinCode}
      </span>
    </div>
  );
}
