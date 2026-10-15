import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCart } from '../../context/CartContext';
import { useProfile } from '../../context/ProfileContext';
import { Arrow } from '../../components/Arrow';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Loading } from '../../components/Loading';
import { Notice } from '../../components/Notice';
import { PageHeading } from '../../components/PageHeading';
import { OrderSummary } from '../../components/OrderSummary';
import { AddressBook } from '../../components/profile/AddressBook';
import { ProfileEditor } from '../../components/profile/ProfileEditor';
import { api, ApiError } from '../../services/api';
import { forgetKey, retryKey } from '../../utils/retryKeys';

export function CheckoutPage() {
  const profile = useProfile();
  const { cart, loading, error: cartError, refresh } = useCart();
  const [selected, setSelected] = useState('');
  const [profileEditing, setProfileEditing] = useState(false);
  const [addressEditing, setAddressEditing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const recoveredOrderId =
    error instanceof ApiError && error.code === 'ACTIVE_ORDER' ? error.details.orderId : null;
  const activeOrderId = cart?.activeOrderId || recoveredOrderId;
  const inFlight = useRef(false);
  const navigate = useNavigate();
  const addressId = profile.data?.addresses.some((address) => address.id === selected)
    ? selected
    : profile.data?.addresses[0]?.id || '';

  async function submit() {
    if (inFlight.current || !addressId || activeOrderId) return;
    inFlight.current = true;
    setSubmitting(true);
    setError(null);
    const scope = 'checkout:' + addressId;
    try {
      const result = await api.checkout(addressId, retryKey(scope));
      forgetKey(scope);
      void refresh();
      navigate('/orders/' + result.orderId + (result.status === 'pending' ? '/payment' : ''));
    } catch (reason) {
      setError(reason);
      if (reason instanceof ApiError && reason.code === 'ACTIVE_ORDER') void refresh();
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }

  return (
    <>
      <Link className="back-link" to="/cart">
        ← Back to cart
      </Link>
      <PageHeading eyebrow="01 / DELIVERY → 02 / PAYMENT" title="Confirm your details.">
        Choose a saved address or add a new one.
      </PageHeading>
      <ErrorNotice
        error={profile.error || cartError}
        retry={() => {
          profile.refresh();
          void refresh();
        }}
      />
      {error instanceof ApiError && error.code === 'OUT_OF_STOCK' ? (
        <Notice tone="error">
          <span>{error.message} No payment was taken.</span>
          <Link to="/cart">
            Review cart <Arrow />
          </Link>
        </Notice>
      ) : (
        <ErrorNotice error={recoveredOrderId ? null : error} />
      )}
      {((profile.loading && !profile.data) || (loading && !cart)) && <Loading />}
      {cart?.items.length === 0 && !activeOrderId && (
        <EmptyState title="Your cart is empty.">
          Add an essential before continuing to payment.
        </EmptyState>
      )}
      {activeOrderId && (
        <Notice>
          You already have a checkout in progress.{' '}
          <Link to={'/orders/' + activeOrderId + '/payment'}>
            Resume payment <Arrow />
          </Link>
        </Notice>
      )}
      {profile.data && cart && cart.items.length > 0 && !activeOrderId && (
        <div className="checkout-grid">
          <div className="delivery-details">
            <ProfileEditor disabled={submitting} onEditing={setProfileEditing} />
            <AddressBook
              selectedId={addressId}
              onSelect={setSelected}
              disabled={submitting}
              onEditing={setAddressEditing}
            />
          </div>
          <OrderSummary totals={cart}>
            <button
              className="button button-dark"
              disabled={submitting || !addressId || loading || profileEditing || addressEditing}
              aria-busy={submitting}
              onClick={() => void submit()}
            >
              {submitting ? 'Preparing your checkout…' : 'Continue to payment'}
              {submitting ? <span className="spinner" aria-hidden="true" /> : <Arrow />}
            </button>
            <p className="fine-print">
              {profileEditing || addressEditing
                ? 'Save or cancel your changes before continuing.'
                : addressId
                  ? 'Your items will be held for 15 minutes during payment.'
                  : 'Add a delivery address to continue.'}
            </p>
          </OrderSummary>
        </div>
      )}
    </>
  );
}
