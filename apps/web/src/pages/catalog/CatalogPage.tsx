import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useCart } from '../../context/CartContext';
import { Arrow } from '../../components/Arrow';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Loading } from '../../components/Loading';
import { Notice } from '../../components/Notice';
import { ProductArt } from '../../components/ProductArt';
import { QuantityControl } from '../../components/QuantityControl';
import { api } from '../../services/api';
import { money } from '../../utils/format';
import { useResource } from '../../hooks/useResource';
import type { Product } from '../../types/index';

export function CatalogPage() {
  const products = useResource(api.products);
  const { cart, loading, updating, error, refresh, setQuantity } = useCart();
  const [actionError, setActionError] = useState<unknown>(null);
  const [message, setMessage] = useState('');
  const itemCount = cart?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0;

  async function update(product: Product, quantity: number) {
    setActionError(null);
    try {
      await setQuantity(product.sku, quantity);
      setMessage(
        quantity
          ? product.name + ': ' + quantity + ' in your cart.'
          : product.name + ' removed from your cart.',
      );
    } catch (reason) {
      setActionError(reason);
    }
  }

  return (
    <>
      <section className="collection-hero">
        <div>
          <p className="eyebrow">THE EVERYDAY COLLECTION</p>
          <h1>
            Small kit. <em>More movement.</em>
          </h1>
          <p>Five essentials. Pick what moves you.</p>
        </div>
        <span className="collection-chip">₹29 packing &amp; handling · delivery included</span>
      </section>
      <ErrorNotice error={products.error} retry={products.refresh} />
      <ErrorNotice error={error} retry={() => void refresh()} />
      <ErrorNotice error={actionError} />
      {cart?.activeOrderId && (
        <Notice>
          Your items are held for an open checkout.{' '}
          <Link to={'/orders/' + cart.activeOrderId + '/payment'}>
            Continue payment <Arrow />
          </Link>
        </Notice>
      )}
      <div className="catalog-layout">
        <section aria-label="Product collection">
          <div className="section-title">
            <h2>
              The essentials <span className="muted">/ 05</span>
            </h2>
            <span className="subtle-text">Made for your everyday</span>
          </div>
          {products.loading && !products.data && <Loading label="Getting the collection ready…" />}
          <div className="product-grid">
            {products.data?.map((product) => {
              const quantity = cart?.items.find((item) => item.sku === product.sku)?.quantity ?? 0;
              const disabled = loading || updating || !cart || !!cart.activeOrderId;
              return (
                <article className="product-card" key={product.sku}>
                  <div className="product-image-wrap">
                    <ProductArt art={product.art} />
                    <span className="art-label">{product.category}</span>
                  </div>
                  <div className="product-info">
                    <h3>{product.name}</h3>
                    <p>{product.detail}</p>
                    <div className="product-stock">
                      <span className={'stock-dot' + (product.stock === 0 ? ' empty' : '')} />
                      {product.stock === 0
                        ? 'Out of stock'
                        : product.stock <= 3
                          ? 'Only ' + product.stock + ' available'
                          : 'In stock'}
                    </div>
                    <div className="product-bottom">
                      <strong>{money(product.price)}</strong>
                      {quantity > 0 ? (
                        <QuantityControl
                          name={product.name}
                          quantity={quantity}
                          max={Math.min(10, product.stock)}
                          disabled={disabled}
                          onChange={(value) => void update(product, value)}
                        />
                      ) : (
                        <button
                          className="button button-add"
                          aria-label={'Add ' + product.name + ' to cart'}
                          disabled={disabled || product.stock === 0}
                          onClick={() => void update(product, 1)}
                        >
                          {product.stock === 0 ? 'Sold out' : '+ Add'}
                        </button>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
          <p className="cart-feedback" role="status">
            {message || 'Add what you need. Adjust quantities right here.'}
          </p>
        </section>
        <aside className="mini-cart" aria-label="Your cart">
          <div className="section-title">
            <h2>Your cart</h2>
            <span className="count-badge">{itemCount}</span>
          </div>
          {cart?.items.length ? (
            <div className="mini-cart-items">
              {cart.items.map((item) => (
                <div className="mini-cart-item" key={item.sku}>
                  <ProductArt art={item.art} />
                  <div>
                    <strong>{item.name}</strong>
                    <span>
                      {item.quantity} × {money(item.unitPrice)}
                    </span>
                  </div>
                  <strong>{money(item.lineTotal)}</strong>
                </div>
              ))}
            </div>
          ) : (
            <div className="mini-empty">
              <span aria-hidden="true">＋</span>
              <strong>A little room for movement.</strong>
              <p>Your essentials will appear here.</p>
            </div>
          )}
          <dl>
            <div>
              <dt>Subtotal</dt>
              <dd>{money(cart?.subtotal ?? 0)}</dd>
            </div>
            <div>
              <dt>Packing &amp; handling</dt>
              <dd>{money(cart?.handling ?? 0)}</dd>
            </div>
            <div className="mini-total">
              <dt>Total</dt>
              <dd>{money(cart?.total ?? 0)}</dd>
            </div>
          </dl>
          {itemCount > 0 ? (
            <Link
              className="button button-dark"
              to={cart?.activeOrderId ? '/orders/' + cart.activeOrderId + '/payment' : '/cart'}
            >
              {cart?.activeOrderId ? 'Resume payment' : 'Review cart'}
              <Arrow />
            </Link>
          ) : (
            <button className="button button-dark" disabled>
              Review cart
              <Arrow />
            </button>
          )}
          <p className="fine-print">Local demo. No real payments.</p>
        </aside>
      </div>
    </>
  );
}
