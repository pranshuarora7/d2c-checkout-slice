import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useCart } from '../../context/CartContext';
import { Arrow } from '../../components/Arrow';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Loading } from '../../components/Loading';
import { ProductArt } from '../../components/ProductArt';
import { StatusBadge } from '../../components/StatusBadge';
import { OrderSummary } from '../../components/OrderSummary';
import { api } from '../../services/api';
import { date, money } from '../../utils/format';
import type { OrderStatus } from '../../types/index';
import { useResource } from '../../hooks/useResource';

const copy: Record<OrderStatus, { symbol: string; title: string; description: string }> = {
  pending: {
    symbol: '…',
    title: 'Your order is saved.',
    description:
      'Continue to payment to confirm your order. Your items are held for 15 minutes from checkout.',
  },
  confirmed: {
    symbol: '✓',
    title: "You're all set.",
    description:
      'Payment received. Your order is confirmed. You can find it here whenever you need.',
  },
  failed: {
    symbol: '!',
    title: "That payment didn't go through.",
    description:
      'No real money was charged. Your items are back in your cart, ready for a fresh checkout.',
  },
  expired: {
    symbol: '↺',
    title: 'This checkout has expired.',
    description:
      'The 15-minute hold ended before payment was completed. Your cart is still available; availability will be checked again.',
  },
  payment_issue: {
    symbol: '!',
    title: 'This payment needs a check.',
    description:
      "The payment details didn't match, or arrived after this checkout closed. This demo will not confirm the order automatically. No real money was charged.",
  },
};

export function OrderPage({ id }: { id: string }) {
  const load = useCallback((signal: AbortSignal) => api.order(id, signal), [id]);
  const { data: order, loading, error, refresh } = useResource(load);
  const { refresh: refreshCart } = useCart();
  const [pollingStopped, setPollingStopped] = useState(false);
  const status = order?.status;
  useEffect(() => {
    if (status !== 'pending') return;
    const interval = setInterval(refresh, 3000);
    const limit = setTimeout(() => {
      clearInterval(interval);
      setPollingStopped(true);
    }, 60_000);
    return () => {
      clearInterval(interval);
      clearTimeout(limit);
    };
  }, [status, refresh]);
  useEffect(() => {
    if (status && status !== 'pending') void refreshCart();
  }, [status, refreshCart]);

  const processing = order?.status === 'pending' && order.paymentScenario === 'pending';
  const state = order
    ? processing
      ? {
          symbol: '◷',
          title: 'Your payment is processing.',
          description:
            'We’re waiting for confirmation. You don’t need to pay again. Open your payment to check its status.',
        }
      : copy[order.status]
    : null;
  return (
    <>
      <Link className="back-link" to="/orders">
        ← My orders
      </Link>
      <ErrorNotice error={error} retry={refresh} />
      {loading && !order && <Loading label="Checking your order…" />}
      {order && state && (
        <>
          <section className={'order-status-hero is-' + order.status} aria-live="polite">
            <div className="status-symbol" aria-hidden="true">
              {processing ? <span className="spinner" /> : state.symbol}
            </div>
            <div>
              <p className="eyebrow">
                {order.id} · {date(order.createdAt)}
              </p>
              <h1>{state.title}</h1>
              <p>{state.description}</p>
              <StatusBadge status={order.status} processing={processing} />
            </div>
          </section>
          <div className="order-actions">
            {order.status === 'pending' ? (
              <Link className="button button-dark" to={'/orders/' + id + '/payment'}>
                {processing ? 'View payment status' : 'Continue to payment'} <Arrow />
              </Link>
            ) : (
              <Link
                className="button button-dark"
                to={order.status === 'confirmed' ? '/' : '/cart'}
              >
                {order.status === 'confirmed' ? 'Back to the collection' : 'Return to cart'}
                <Arrow />
              </Link>
            )}
            <button className="text-button" onClick={refresh} disabled={loading}>
              {loading ? 'Checking…' : 'Refresh status ↻'}
            </button>
            {order.status === 'pending' && (
              <span className="poll-note">
                {pollingStopped
                  ? 'Refresh to check for another update.'
                  : 'Checking for updates every few seconds.'}
              </span>
            )}
          </div>
          <div className="checkout-grid order-details-grid">
            <div>
              <section className="ordered-items">
                <p className="eyebrow">YOUR ESSENTIALS</p>
                {order.items.map((item) => (
                  <div className="ordered-item" key={item.sku}>
                    <ProductArt art={item.art} />
                    <div>
                      <h2>{item.name}</h2>
                      <p>
                        Quantity {item.quantity} · {money(item.unitPrice)} each
                      </p>
                    </div>
                    <strong>{money(item.lineTotal)}</strong>
                  </div>
                ))}
              </section>
              <section className="order-address">
                <p className="eyebrow">DELIVERY DETAILS</p>
                <h2>{order.customer.name}</h2>
                <p>
                  {order.delivery.addressLine}
                  <br />
                  {order.delivery.city}, {order.delivery.state} {order.delivery.pinCode}
                </p>
                <p>
                  {order.customer.email}
                  <br />
                  {order.customer.phone}
                </p>
              </section>
            </div>
            <OrderSummary totals={order} />
          </div>
        </>
      )}
    </>
  );
}
