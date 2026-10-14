import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useCart } from '../../context/CartContext';
import { Arrow } from '../../components/Arrow';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Loading } from '../../components/Loading';
import { PageHeading } from '../../components/PageHeading';
import { OrderSummary } from '../../components/OrderSummary';
import { api } from '../../services/api';
import { money } from '../../utils/format';
import { retryKey } from '../../utils/retryKeys';
import { useResource } from '../../hooks/useResource';
import type { PaymentScenario } from '../../types/index';

const scenarios: { value: PaymentScenario; title: string; description: string; icon: string }[] = [
  {
    value: 'success',
    title: 'Success',
    description: 'Payment goes through. Your order is confirmed.',
    icon: '✓',
  },
  {
    value: 'failure',
    title: 'Failure',
    description: 'Payment fails. Your cart is ready to try again.',
    icon: '↺',
  },
  {
    value: 'pending',
    title: 'Processing',
    description: 'Payment takes a little longer. Check its status later.',
    icon: '◷',
  },
];

export function PaymentPage({ id }: { id: string }) {
  const load = useCallback((signal: AbortSignal) => api.order(id, signal), [id]);
  const order = useResource(load);
  const { refresh: refreshCart } = useCart();
  const [now, setNow] = useState(Date.now());
  const [busy, setBusy] = useState(false);
  const [selected, setSelected] = useState<PaymentScenario>('success');
  const [chosen, setChosen] = useState<PaymentScenario | null>(null);
  const [error, setError] = useState<unknown>(null);
  const inFlight = useRef(false);
  const navigate = useNavigate();
  const scenario = order.data?.paymentScenario || chosen || selected;
  const awaitingConfirmation = order.data?.paymentScenario === 'pending';
  const locked = !!order.data?.paymentScenario || chosen !== null;
  const seconds = order.data
    ? Math.max(0, Math.ceil((Date.parse(order.data.expiresAt) - now) / 1000))
    : 0;
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const refreshOrder = order.refresh;
  useEffect(() => {
    if (order.data?.status === 'pending' && seconds === 0) refreshOrder();
  }, [seconds, order.data?.status, refreshOrder]);

  async function pay() {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setChosen(scenario);
    setError(null);
    try {
      if (awaitingConfirmation) await api.completePayment(id);
      else await api.pay(id, scenario, retryKey('payment:' + id));
      void refreshCart();
      navigate('/orders/' + id);
    } catch (reason) {
      setError(reason);
      order.refresh();
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  if (order.data && order.data.status !== 'pending')
    return <Navigate to={'/orders/' + id} replace />;
  return (
    <>
      <Link className="back-link" to={'/orders/' + id}>
        ← Order details
      </Link>
      <PageHeading
        eyebrow="01 / DELIVERY → 02 / PAYMENT"
        title={awaitingConfirmation ? 'Payment in progress.' : 'Complete your payment.'}
      >
        {awaitingConfirmation
          ? 'Your order is saved. Check here for an update.'
          : 'Your total is ready. This is a demo; no real money is charged.'}
      </PageHeading>
      <ErrorNotice error={order.error} retry={order.refresh} />
      <ErrorNotice error={error} />
      {order.loading && !order.data && <Loading />}
      {order.data && (
        <div className="checkout-grid">
          <section className="payment-card">
            <div className="section-title">
              <h2>{awaitingConfirmation ? 'Payment status' : 'Payment experience'}</h2>
              <span className="demo-tag">DEMO</span>
            </div>
            {awaitingConfirmation ? (
              <div className="pending-payment">
                <span className="spinner payment-spinner" aria-hidden="true" />
                <h3>Your payment is processing.</h3>
                <p>
                  We’re waiting for confirmation. You don’t need to pay again. Check the status
                  below, or come back through My orders.
                </p>
                <p className="fine-print">Demo: checking the status completes this payment.</p>
              </div>
            ) : (
              <fieldset className="payment-scenarios">
                <legend>Choose what happens when you pay in this demo.</legend>
                {scenarios.map((option) => (
                  <label
                    className={'scenario-option' + (scenario === option.value ? ' selected' : '')}
                    key={option.value}
                  >
                    <input
                      type="radio"
                      name="scenario"
                      value={option.value}
                      aria-label={option.title}
                      checked={scenario === option.value}
                      disabled={busy || locked}
                      onChange={() => setSelected(option.value)}
                    />
                    <span className="scenario-icon" aria-hidden="true">
                      {option.icon}
                    </span>
                    <span>
                      <strong>{option.title}</strong>
                      <small>{option.description}</small>
                    </span>
                  </label>
                ))}
              </fieldset>
            )}
            <div className="hold-timer">
              <span className="tiny-dot" /> Your items are reserved for{' '}
              <strong>
                {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}
              </strong>
            </div>
            <button
              className="button button-dark payment-submit"
              disabled={busy || seconds === 0}
              aria-busy={busy}
              onClick={() => void pay()}
            >
              {busy
                ? 'Checking your payment…'
                : awaitingConfirmation
                  ? 'Check payment status'
                  : 'Pay ' + money(order.data.total)}
              {busy ? <span className="spinner" aria-hidden="true" /> : <Arrow />}
            </button>
            <Link className="text-link payment-status-link" to={'/orders/' + id}>
              Check order status <Arrow />
            </Link>
          </section>
          <OrderSummary totals={order.data}>
            <div className="summary-items">
              {order.data.items.map((item) => (
                <div key={item.sku}>
                  <span>
                    {item.name} × {item.quantity}
                  </span>
                  <strong>{money(item.lineTotal)}</strong>
                </div>
              ))}
            </div>
            <p className="fine-print">
              Delivery to {order.data.delivery.city} {order.data.delivery.pinCode}
            </p>
          </OrderSummary>
        </div>
      )}
    </>
  );
}
