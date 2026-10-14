import { useEffect } from 'react';
import { Link, NavLink, Route, Routes, useLocation, useParams } from 'react-router-dom';
import { CatalogPage } from './pages/catalog/CatalogPage';
import { CartPage } from './pages/cart/CartPage';
import { CheckoutPage } from './pages/checkout/CheckoutPage';
import { PaymentPage } from './pages/payment/PaymentPage';
import { OrderPage } from './pages/orders/OrderPage';
import { OrdersPage } from './pages/orders/OrdersPage';
import { ProfilePage } from './pages/profile/ProfilePage';
import { useProfile } from './context/ProfileContext';
import { useCart } from './context/CartContext';
import { EmptyState } from './components/EmptyState';

function OrderRoute({ payment = false }: { payment?: boolean }) {
  const { id = '' } = useParams();
  return payment ? <PaymentPage key={id} id={id} /> : <OrderPage key={id} id={id} />;
}

export function App() {
  const { pathname } = useLocation();
  const profile = useProfile();
  const { cart } = useCart();
  const itemCount = cart?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0;
  useEffect(() => {
    window.scrollTo(0, 0);
    document.title =
      'KINETIC — ' +
      (pathname === '/'
        ? 'Move well.'
        : pathname === '/profile'
          ? 'Your account'
          : pathname.startsWith('/orders')
            ? 'Your orders'
            : 'Your checkout');
  }, [pathname]);
  return (
    <>
      <a href="#main" className="skip-link">
        Skip to content
      </a>
      <div className="top-strip">
        <span>A LITTLE MOVEMENT, EVERY DAY.</span>
        <span>LOCAL DEMO · NO REAL PAYMENTS</span>
      </div>
      <header className="site-header">
        <Link className="brand" to="/" aria-label="KINETIC home">
          <span className="brand-mark" aria-hidden="true">
            K<span>↗</span>
          </span>
          <span>
            KINETIC<small>MOVE WELL.</small>
          </span>
        </Link>
        <nav aria-label="Main navigation">
          <NavLink to="/" end>
            Collection
          </NavLink>
          <NavLink
            to="/cart"
            aria-label={'Cart, ' + itemCount + (itemCount === 1 ? ' item' : ' items')}
          >
            Cart {itemCount > 0 && <span className="nav-count">{itemCount}</span>}
          </NavLink>
          <NavLink to="/orders">My orders</NavLink>
          <NavLink to="/profile" className="account-nav">
            Account
          </NavLink>
        </nav>
        <Link to="/profile" className="profile-label" aria-label="My account">
          <span className="avatar" aria-hidden="true">
            {profile.data?.name.charAt(0) || 'K'}
          </span>
          <span>
            {profile.data?.name || 'My account'}
            <small>Edit your details</small>
          </span>
        </Link>
      </header>
      <main id="main" className="page-shell" tabIndex={-1}>
        <Routes>
          <Route path="/" element={<CatalogPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/cart" element={<CartPage />} />
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/orders/:id/payment" element={<OrderRoute payment />} />
          <Route path="/orders/:id" element={<OrderRoute />} />
          <Route
            path="*"
            element={
              <EmptyState title="A little off track.">
                That page could not be found. Let's get you back to the collection.
              </EmptyState>
            }
          />
        </Routes>
      </main>
      <footer className="site-footer">
        <div>
          <strong>
            KINETIC<span>↗</span>
          </strong>
          <p>Good gear. Everyday movement.</p>
        </div>
        <span>FIVE ESSENTIALS. ONE SMALL DEMO.</span>
        <Link to="/orders">Track your order ↗</Link>
      </footer>
    </>
  );
}
