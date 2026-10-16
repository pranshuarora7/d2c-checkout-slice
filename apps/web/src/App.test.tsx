import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';
import { CartProvider } from './context/CartContext';
import { ProfileProvider } from './context/ProfileContext';
import { api } from './services/api';
import { clearCheckoutKeys } from './utils/retryKeys';
import type { Cart, Order, Product } from './types/index';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  clearCheckoutKeys();
});
it('shops and creates two orders while the first payment processes, then keeps the third cart on confirmation', async () => {
  clearCheckoutKeys();
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  const products: Product[] = [
    {
      sku: 'MAT',
      name: 'Training mat',
      category: 'Foundation',
      description: '',
      detail: 'Sage',
      art: 'mat',
      price: 1299,
      stock: 8,
    },
    {
      sku: 'LOOP',
      name: 'Resistance loop',
      category: 'Strength',
      description: '',
      detail: 'Woven',
      art: 'loops',
      price: 499,
      stock: 12,
    },
    {
      sku: 'BOTTLE',
      name: 'Steel bottle',
      category: 'Recovery',
      description: '',
      detail: 'Steel',
      art: 'bottle',
      price: 799,
      stock: 6,
    },
  ];
  const empty: Cart = { items: [], subtotal: 0, handling: 0, total: 0, currency: 'INR' };
  let cart: Cart = structuredClone(empty);
  const orders: Order[] = [];
  vi.spyOn(api, 'products').mockResolvedValue(products);
  vi.spyOn(api, 'profile').mockResolvedValue({
    name: 'Demo',
    email: 'demo@example.test',
    phone: '9000000000',
    addresses: [
      {
        id: 'home',
        label: 'Home',
        addressLine: '12 Test Street',
        city: 'Pune',
        state: 'Maharashtra',
        pinCode: '411001',
      },
    ],
  });
  vi.spyOn(api, 'cart').mockImplementation(async () => structuredClone(cart));
  vi.spyOn(api, 'setQuantity').mockImplementation(async (sku, quantity) => {
    const product = products.find((p) => p.sku === sku)!;
    const items = cart.items.filter((item) => item.sku !== sku);
    if (quantity)
      items.push({
        sku,
        name: product.name,
        detail: product.detail,
        art: product.art,
        unitPrice: product.price,
        quantity,
        available: product.stock,
        lineTotal: product.price * quantity,
      });
    const subtotal = items.reduce((sum, item) => sum + item.lineTotal, 0);
    cart = {
      items,
      subtotal,
      handling: items.length ? 29 : 0,
      total: subtotal + (items.length ? 29 : 0),
      currency: 'INR',
    };
    return structuredClone(cart);
  });
  const checkout = vi.spyOn(api, 'checkout').mockImplementation(async () => {
    const order: Order = {
      ...structuredClone(cart),
      id: 'KIN-' + (orders.length + 1),
      status: 'pending',
      paymentScenario: null,
      customer: { name: 'Demo', email: 'demo@example.test', phone: '9000000000' },
      delivery: {
        addressLine: '12 Test Street',
        city: 'Pune',
        state: 'Maharashtra',
        pinCode: '411001',
      },
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 900000).toISOString(),
    };
    orders.push(order);
    cart = structuredClone(empty);
    return {
      orderId: order.id,
      status: order.status,
      total: order.total,
      currency: order.currency,
    };
  });
  vi.spyOn(api, 'pay').mockImplementation(async (id, outcome) => {
    const order = orders.find((o) => o.id === id)!;
    order.paymentScenario = outcome;
    return structuredClone(order);
  });
  vi.spyOn(api, 'order').mockImplementation(async (id) =>
    structuredClone(orders.find((o) => o.id === id)!),
  );
  vi.spyOn(api, 'orders').mockImplementation(async () =>
    orders.map((o) => ({
      id: o.id,
      status: o.status,
      total: o.total,
      currency: o.currency,
      createdAt: o.createdAt,
      itemCount: o.items.reduce((sum, i) => sum + i.quantity, 0),
      paymentStarted: !!o.paymentScenario,
    })),
  );
  const complete = vi.spyOn(api, 'completePayment').mockImplementation(async (id) => {
    const order = orders.find((o) => o.id === id)!;
    order.status = 'confirmed';
    return structuredClone(order);
  });
  render(
    <MemoryRouter>
      <CartProvider>
        <ProfileProvider>
          <App />
        </ProfileProvider>
      </CartProvider>
    </MemoryRouter>,
  );
  const user = userEvent.setup();
  for (const [name, amount] of [
    ['Training mat', '1,328.00'],
    ['Resistance loop', '528.00'],
  ]) {
    await user.click(await screen.findByRole('button', { name: 'Add ' + name + ' to cart' }));
    await user.click(screen.getByRole('link', { name: 'Review cart' }));
    await user.click(screen.getByRole('link', { name: 'Continue to checkout' }));
    await user.click(await screen.findByRole('button', { name: /Continue to payment/ }));
    await user.click(await screen.findByRole('radio', { name: 'Processing' }));
    await user.click(screen.getByRole('button', { name: 'Pay ₹' + amount }));
    await screen.findByRole('heading', { name: 'Your payment is processing.' });
    expect(screen.queryByRole('link', { name: /Continue to payment/ })).toBeNull();
    await user.click(screen.getByRole('link', { name: /Keep shopping/ }));
  }
  expect(orders).toHaveLength(2);
  expect(checkout.mock.calls[0][1]).not.toBe(checkout.mock.calls[1][1]);
  await user.click(await screen.findByRole('button', { name: 'Add Steel bottle to cart' }));
  await user.click(screen.getByRole('link', { name: 'My orders' }));
  await user.click(await screen.findByRole('link', { name: 'View status for KIN-1' }));
  await user.click(await screen.findByRole('link', { name: /View payment status/ }));
  await screen.findByRole('heading', { name: 'Your payment is processing.' });
  expect(screen.queryByRole('button', { name: /^Pay / })).toBeNull();
  await user.click(screen.getByRole('button', { name: 'Check payment status' }));
  await screen.findByRole('heading', { name: "You're all set." });
  expect(complete).toHaveBeenCalledExactlyOnceWith('KIN-1');
  await user.click(screen.getByRole('link', { name: /Keep shopping/ }));
  await screen.findByRole('button', { name: 'Increase Steel bottle quantity' });
  expect(cart.items.map((item) => item.sku)).toEqual(['BOTTLE']);
  expect(orders[1].status).toBe('pending');
});
