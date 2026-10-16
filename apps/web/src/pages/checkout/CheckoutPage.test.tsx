import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ProfileProvider } from '../../context/ProfileContext';
import { CartProvider } from '../../context/CartContext';
import { api, ApiError } from '../../services/api';
import { clearCheckoutKeys } from '../../utils/retryKeys';
import { CheckoutPage } from './CheckoutPage';

beforeEach(() => {
  clearCheckoutKeys();
  vi.spyOn(api, 'cart').mockResolvedValue({
    items: [
      {
        sku: 'TRAINING-MAT',
        name: 'Training mat',
        detail: 'Sage',
        art: 'mat',
        unitPrice: 1299,
        quantity: 1,
        available: 8,
        lineTotal: 1299,
      },
    ],
    subtotal: 1299,
    handling: 29,
    total: 1328,
    currency: 'INR',
  });
  vi.spyOn(api, 'profile').mockResolvedValue({
    name: 'Demo Shopper',
    email: 'demo@example.test',
    phone: '9000000000',
    addresses: [
      {
        id: 'addr_home',
        label: 'Home',
        addressLine: '12 Test Street',
        city: 'Bengaluru',
        state: 'Karnataka',
        pinCode: '560038',
      },
      {
        id: 'addr_work',
        label: 'Work',
        addressLine: '24 Demo Avenue',
        city: 'Bengaluru',
        state: 'Karnataka',
        pinCode: '560034',
      },
    ],
  });
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('saved-profile checkout', () => {
  it('shows one outage message and retries both cart and profile together', async () => {
    const unavailable = new ApiError('NETWORK_ERROR', 'The store is temporarily unavailable.');
    vi.mocked(api.cart).mockRejectedValueOnce(unavailable);
    vi.mocked(api.profile).mockRejectedValueOnce(unavailable);
    render(
      <MemoryRouter>
        <CartProvider>
          <ProfileProvider>
            <CheckoutPage />
          </ProfileProvider>
        </CartProvider>
      </MemoryRouter>,
    );
    await screen.findByRole('alert');
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }));
    await screen.findByText('Demo Shopper');
    expect(api.cart).toHaveBeenCalledTimes(2);
    expect(api.profile).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('keeps checkout disabled when the last saved address has been removed', async () => {
    vi.mocked(api.profile).mockResolvedValue({
      name: 'Demo Shopper',
      email: 'demo@example.test',
      phone: '9000000000',
      addresses: [],
    });
    render(
      <MemoryRouter>
        <CartProvider>
          <ProfileProvider>
            <CheckoutPage />
          </ProfileProvider>
        </CartProvider>
      </MemoryRouter>,
    );
    await screen.findByText('No saved addresses yet. Add a delivery address to continue.');
    expect(
      (screen.getByRole('button', { name: /Continue to payment/ }) as HTMLButtonElement).disabled,
    ).toBe(true);
    expect(screen.getByRole('button', { name: '+ Add address' })).toBeTruthy();
  });

  it('requires saving or cancelling address edits before continuing', async () => {
    render(
      <MemoryRouter>
        <CartProvider>
          <ProfileProvider>
            <CheckoutPage />
          </ProfileProvider>
        </CartProvider>
      </MemoryRouter>,
    );
    await screen.findByText('Demo Shopper');
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '+ Add address' }));
    expect(
      (screen.getByRole('button', { name: /Continue to payment/ }) as HTMLButtonElement).disabled,
    ).toBe(true);
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(
      (screen.getByRole('button', { name: /Continue to payment/ }) as HTMLButtonElement).disabled,
    ).toBe(false);
  });

  it('reuses the same checkout key after a lost response and sends the chosen address only', async () => {
    const checkout = vi
      .spyOn(api, 'checkout')
      .mockRejectedValueOnce(new ApiError('NETWORK_ERROR', 'The response was lost. Please retry.'))
      .mockResolvedValueOnce({
        orderId: 'KIN-test',
        status: 'pending',
        total: 1328,
        currency: 'INR',
      });
    render(
      <MemoryRouter initialEntries={['/checkout']}>
        <CartProvider>
          <ProfileProvider>
            <Routes>
              <Route path="/checkout" element={<CheckoutPage />} />
              <Route path="/orders/:id/payment" element={<p>Payment page reached</p>} />
            </Routes>
          </ProfileProvider>
        </CartProvider>
      </MemoryRouter>,
    );
    const user = userEvent.setup();
    await screen.findByText('Demo Shopper');
    await user.click(screen.getByRole('radio', { name: /Work/ }));
    await user.click(screen.getByRole('button', { name: /Continue to payment/ }));
    await screen.findByRole('alert');
    await user.click(screen.getByRole('button', { name: /Continue to payment/ }));
    await screen.findByText('Payment page reached');
    expect(checkout).toHaveBeenCalledTimes(2);
    expect(checkout.mock.calls[0][0]).toBe('addr_work');
    expect(checkout.mock.calls[0]).toEqual(checkout.mock.calls[1]);
    expect(checkout.mock.calls[0][1]).toMatch(/^[a-f0-9-]{36}$/);
    expect(checkout.mock.calls[0]).toHaveLength(2);
  });
});

it('keeps the shopper out of payment when another buyer reserves the last item', async () => {
  vi.spyOn(api, 'checkout').mockRejectedValue(
    new ApiError(
      'OUT_OF_STOCK',
      'Training mat no longer has enough stock. Please update your cart.',
      { sku: 'TRAINING-MAT' },
    ),
  );
  render(
    <MemoryRouter initialEntries={['/checkout']}>
      <CartProvider>
        <ProfileProvider>
          <Routes>
            <Route path="/checkout" element={<CheckoutPage />} />
            <Route path="/orders/:id/payment" element={<p>Payment page reached</p>} />
            <Route path="/cart" element={<p>Review saved cart</p>} />
          </Routes>
        </ProfileProvider>
      </CartProvider>
    </MemoryRouter>,
  );
  await screen.findByText('Demo Shopper');
  const user = userEvent.setup();
  await user.click(screen.getByRole('button', { name: /Continue to payment/ }));
  expect((await screen.findByRole('alert')).textContent).toContain('No payment was taken.');
  expect(screen.queryByText('Payment page reached')).toBeNull();
  await user.click(screen.getByRole('link', { name: /Review cart/ }));
  await screen.findByText('Review saved cart');
});

it('recovers the saved order after a lost response and address change even if refresh fails', async () => {
  const checkout = vi
    .spyOn(api, 'checkout')
    .mockRejectedValueOnce(new ApiError('NETWORK_ERROR', 'The response was lost. Please retry.'))
    .mockRejectedValueOnce(
      new ApiError('RETRY_KEY_REUSED', 'You already have a checkout awaiting payment.', {
        orderId: 'KIN-recovered',
      }),
    );
  render(
    <MemoryRouter>
      <CartProvider>
        <ProfileProvider>
          <CheckoutPage />
        </ProfileProvider>
      </CartProvider>
    </MemoryRouter>,
  );
  const user = userEvent.setup();
  await screen.findByText('Demo Shopper');
  await user.click(screen.getByRole('button', { name: /Continue to payment/ }));
  await screen.findByRole('alert');
  await user.click(screen.getByRole('radio', { name: /Work/ }));
  vi.mocked(api.cart).mockRejectedValue(new ApiError('NETWORK_ERROR', 'Cart refresh unavailable'));
  await user.click(screen.getByRole('button', { name: /Continue to payment/ }));
  expect(checkout.mock.calls[0][1]).toBe(checkout.mock.calls[1][1]);
  expect((await screen.findByRole('link', { name: /View saved order/ })).getAttribute('href')).toBe(
    '/orders/KIN-recovered',
  );
  expect(screen.queryByRole('button', { name: /Continue to payment/ })).toBeNull();
  expect(api.cart).toHaveBeenCalledTimes(2);
});

it('refreshes cart state when checkout reports an existing order', async () => {
  vi.spyOn(api, 'checkout').mockRejectedValue(
    new ApiError('RETRY_KEY_REUSED', 'Checkout already exists.', { orderId: 'KIN-existing' }),
  );
  render(
    <MemoryRouter>
      <CartProvider>
        <ProfileProvider>
          <CheckoutPage />
        </ProfileProvider>
      </CartProvider>
    </MemoryRouter>,
  );
  await screen.findByText('Demo Shopper');
  const saved = await api.cart();
  vi.mocked(api.cart).mockResolvedValue({
    ...saved,
    items: [],
    subtotal: 0,
    handling: 0,
    total: 0,
  });
  await userEvent.setup().click(screen.getByRole('button', { name: /Continue to payment/ }));
  expect((await screen.findByRole('link', { name: /View saved order/ })).getAttribute('href')).toBe(
    '/orders/KIN-existing',
  );
  expect(screen.queryByRole('button', { name: /Continue to payment/ })).toBeNull();
  expect(screen.queryByRole('alert')).toBeNull();
});
