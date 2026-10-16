import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { CartProvider } from '../../context/CartContext';
import { api } from '../../services/api';
import { PaymentPage } from './PaymentPage';
import type { Order } from '../../types/index';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function paymentOrder(scenario: Order['paymentScenario']): Order {
  return {
    id: 'KIN-pending',
    status: 'pending',
    paymentScenario: scenario,
    customer: { name: 'Demo', email: 'demo@example.test', phone: '9000000000' },
    delivery: {
      addressLine: '1 Test Street',
      city: 'Pune',
      state: 'Maharashtra',
      pinCode: '411001',
    },
    items: [],
    subtotal: 100,
    handling: 29,
    total: 129,
    currency: 'INR',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 900000).toISOString(),
  };
}

function renderPayment(order: Order) {
  vi.spyOn(api, 'order').mockResolvedValue(order);
  vi.spyOn(api, 'cart').mockResolvedValue({
    items: [],
    subtotal: 0,
    handling: 0,
    total: 0,
    currency: 'INR',
  });
  render(
    <MemoryRouter initialEntries={['/payment']}>
      <CartProvider>
        <Routes>
          <Route path="/payment" element={<PaymentPage id={order.id} />} />
          <Route path="/orders/:id" element={<p>Order status page</p>} />
        </Routes>
      </CartProvider>
    </MemoryRouter>,
  );
}

it.each(['pending', 'success', 'failure', 'duplicate'] as const)(
  'restores a %s attempt as Processing and checks it without paying again',
  async (scenario) => {
    const order = paymentOrder(scenario);
    const pay = vi.spyOn(api, 'pay');
    const complete = vi
      .spyOn(api, 'completePayment')
      .mockResolvedValue({ ...order, status: 'confirmed' });
    renderPayment(order);
    await screen.findByText('Your payment is processing.');
    expect(screen.queryByRole('radio')).toBeNull();
    expect(screen.queryByRole('button', { name: /^Pay / })).toBeNull();
    expect(screen.getByRole('link', { name: /Keep shopping/ })).toBeTruthy();
    await userEvent.setup().click(screen.getByRole('button', { name: 'Check payment status' }));
    await screen.findByText('Order status page');
    expect(complete).toHaveBeenCalledExactlyOnceWith(order.id);
    expect(pay).not.toHaveBeenCalled();
  },
);
