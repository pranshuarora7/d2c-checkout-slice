import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { CartProvider } from '../../context/CartContext';
import { api } from '../../services/api';
import type { Order } from '../../types/index';
import { OrderPage } from './OrderPage';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it('shows confirmation only when an updated server order is confirmed', async () => {
  const order: Order = {
    id: 'KIN-test',
    status: 'pending',
    customer: { name: 'Demo Shopper', email: 'demo@example.test', phone: '9000000000' },
    delivery: {
      addressLine: '12 Test Street',
      city: 'Bengaluru',
      state: 'Karnataka',
      pinCode: '560038',
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
    expiresAt: new Date(Date.now() + 900000).toISOString(),
    updatedAt: new Date().toISOString(),
  };
  vi.spyOn(api, 'cart').mockResolvedValue({
    items: [],
    subtotal: 0,
    handling: 0,
    total: 0,
    currency: 'INR',
    activeOrderId: null,
  });
  vi.spyOn(api, 'order')
    .mockResolvedValueOnce(order)
    .mockResolvedValue({ ...order, status: 'confirmed' });
  render(
    <MemoryRouter>
      <CartProvider>
        <OrderPage id="KIN-test" />
      </CartProvider>
    </MemoryRouter>,
  );
  await screen.findByRole('heading', { name: 'Your order is saved.' });
  expect(screen.queryByRole('heading', { name: "You're all set." })).toBeNull();
  await userEvent.setup().click(screen.getByRole('button', { name: /Refresh status/ }));
  await screen.findByRole('heading', { name: "You're all set." });
  expect(screen.queryByRole('link', { name: /Continue to payment/ })).toBeNull();
});
