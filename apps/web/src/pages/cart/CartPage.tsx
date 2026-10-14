import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useCart } from '../../context/CartContext';
import { Arrow } from '../../components/Arrow';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Loading } from '../../components/Loading';
import { Notice } from '../../components/Notice';
import { PageHeading } from '../../components/PageHeading';
import { ProductArt } from '../../components/ProductArt';
import { OrderSummary } from '../../components/OrderSummary';
import { QuantityControl } from '../../components/QuantityControl';
import { money } from '../../utils/format';

export function CartPage() {
  const { cart, loading, updating, error, refresh, setQuantity } = useCart();
  const [actionError, setActionError] = useState<unknown>(null);
  async function update(sku: string, quantity: number) {
    setActionError(null);
    try {
      await setQuantity(sku, quantity);
    } catch (reason) {
      setActionError(reason);
      await refresh();
    }
  }
  return (
    <>
      <Link className="back-link" to="/">
        ← Back to the collection
      </Link>
      <PageHeading eyebrow="YOUR CART" title="Good choices.">
        A little gear. A little momentum.
      </PageHeading>
      <ErrorNotice error={error} retry={() => void refresh()} />
      <ErrorNotice error={actionError} />
      {loading && !cart && <Loading />}
      {cart && cart.items.length === 0 && (
        <EmptyState title="Room for your next move.">
          Your cart is empty. Find an essential to get started.
        </EmptyState>
      )}
      {cart && cart.items.length > 0 && (
        <>
          {cart.activeOrderId && (
            <Notice>
              Your items are held for your current checkout.{' '}
              <Link to={'/orders/' + cart.activeOrderId + '/payment'}>
                Continue to payment <Arrow />
              </Link>
            </Notice>
          )}
          <div className="checkout-grid">
            <section className="cart-list" aria-label="Cart items">
              <div className="list-heading">
                <span>YOUR ESSENTIALS</span>
                <span>{cart.items.reduce((sum, item) => sum + item.quantity, 0)} ITEMS</span>
              </div>
              {cart.items.map((item) => (
                <article className="cart-item" key={item.sku}>
                  <ProductArt art={item.art} className="cart-art" />
                  <div className="cart-item-details">
                    <h2>{item.name}</h2>
                    <p>{item.detail}</p>
                    <span className="unit-price">{money(item.unitPrice)} each</span>
                    <div className="cart-item-actions">
                      <QuantityControl
                        name={item.name}
                        quantity={item.quantity}
                        max={Math.min(10, item.available)}
                        disabled={updating || !!cart.activeOrderId}
                        onChange={(quantity) => void update(item.sku, quantity)}
                      />
                      <button
                        className="text-button muted"
                        disabled={updating || !!cart.activeOrderId}
                        onClick={() => void update(item.sku, 0)}
                      >
                        Remove
                      </button>
                    </div>
                    {item.quantity > item.available && !cart.activeOrderId && (
                      <span className="inline-error">
                        Only {item.available} available. Please lower the quantity or remove this
                        item.
                      </span>
                    )}
                  </div>
                  <strong className="line-price">{money(item.lineTotal)}</strong>
                </article>
              ))}
            </section>
            <OrderSummary totals={cart}>
              <Link
                className="button button-lime"
                to={cart.activeOrderId ? '/orders/' + cart.activeOrderId + '/payment' : '/checkout'}
              >
                {cart.activeOrderId ? 'Continue to payment' : 'Continue to checkout'}
                <Arrow />
              </Link>
              <p className="fine-print">
                A flat ₹29 for packing and handling. No surprise fees at the next step.
              </p>
            </OrderSummary>
          </div>
        </>
      )}
    </>
  );
}
