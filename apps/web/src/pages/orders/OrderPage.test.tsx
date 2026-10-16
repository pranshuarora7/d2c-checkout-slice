import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { CartProvider } from '../../context/CartContext';
import { api } from '../../services/api';
import type { Order } from '../../types/index';
import { OrderPage } from './OrderPage';
import { OrdersPage } from './OrdersPage';

const savedOrder: Order = {
  id: 'KIN-saved',
  status: 'pending',
  paymentScenario: 'pending',
  customer: { name: 'Demo', email: 'demo@example.test', phone: '9000000000' },
  delivery: {
    addressLine: '12 Test Street',
    city: 'Pune',
    state: 'Maharashtra',
    pinCode: '411001',
  },
  items: [
    {
      sku: 'TRAINING-MAT',
      name: 'Training mat',
      art: 'mat',
      unitPrice: 1299,
      quantity: 1,
      lineTotal: 1299,
    },
  ],
  subtotal: 1299,
  handling: 29,
  total: 1328,
  currency: 'INR',
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  expiresAt: new Date(Date.now() + 900000).toISOString(),
};
const cart = { items: [], subtotal: 0, handling: 0, total: 0, currency: 'INR' };
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
function show(order: Order) {
  vi.spyOn(api, 'cart').mockResolvedValue(cart);
  vi.spyOn(api, 'order').mockResolvedValue(order);
  render(
    <MemoryRouter initialEntries={['/orders/KIN-saved']}>
      <CartProvider>
        <Routes>
          <Route path="/orders/:id" element={<OrderPage id={order.id} />} />
          <Route path="/cart" element={<p>Current shopping cart</p>} />
        </Routes>
      </CartProvider>
    </MemoryRouter>,
  );
}
it.each(['pending', 'success', 'failure', 'duplicate'] as const)(
  'shows a saved %s payment as Processing without Continue to payment',
  async (scenario) => {
    show({ ...savedOrder, paymentScenario: scenario });
    await screen.findByRole('heading', { name: 'Your payment is processing.' });
    expect(screen.queryByRole('link', { name: /Continue to payment/ })).toBeNull();
    expect(screen.getByText('Processing')).toBeTruthy();
    expect(screen.getByRole('link', { name: /Keep shopping/ }).getAttribute('href')).toBe('/');
    expect(screen.getByRole('link', { name: /View payment status/ })).toBeTruthy();
  },
);
it('offers payment only for an order whose payment was never started', async () => {
  show({ ...savedOrder, paymentScenario: null });
  await screen.findByRole('link', { name: /Continue to payment/ });
  expect(screen.queryByText('Your payment is processing.')).toBeNull();
});
it.each(['failed', 'expired'] as const)(
  'can copy a %s order without clearing another cart',
  async (status) => {
    vi.spyOn(api, 'copyOrderToCart').mockResolvedValue({
      ...cart,
      items: [{ ...savedOrder.items[0], detail: 'Sage', available: 8 }],
      subtotal: 1299,
      handling: 29,
      total: 1328,
    });
    show({ ...savedOrder, status });
    await userEvent
      .setup()
      .click(await screen.findByRole('button', { name: 'Add these items to cart' }));
    await screen.findByText('Current shopping cart');
    expect(api.copyOrderToCart).toHaveBeenCalledExactlyOnceWith('KIN-saved');
  },
);
it('shows Processing in the order list for a started payment', async () => {
  vi.spyOn(api, 'orders').mockResolvedValue([
    {
      id: 'KIN-saved',
      status: 'pending',
      total: 1328,
      currency: 'INR',
      createdAt: new Date().toISOString(),
      itemCount: 1,
      paymentStarted: true,
    },
  ]);
  render(
    <MemoryRouter>
      <OrdersPage />
    </MemoryRouter>,
  );
  await screen.findByText('Processing');
  expect(screen.queryByText('Pending')).toBeNull();
});
