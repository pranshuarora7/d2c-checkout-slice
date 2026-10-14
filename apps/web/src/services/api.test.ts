import { afterEach, expect, it, vi } from 'vitest';
import { api } from './api';
import { clearCheckoutKeys, forgetKey, retryKey } from '../utils/retryKeys';

afterEach(() => {
  vi.unstubAllGlobals();
  clearCheckoutKeys();
  forgetKey('payment:test');
});

it('checkout HTTP contract contains only the saved address choice and retry header', async () => {
  const fetch = vi
    .fn()
    .mockResolvedValue(
      new Response(
        JSON.stringify({ orderId: 'KIN-test', status: 'pending', total: 1328, currency: 'INR' }),
        { status: 201 },
      ),
    );
  vi.stubGlobal('fetch', fetch);
  await api.checkout('addr_home', 'retry-key-12345678');
  const [path, request] = fetch.mock.calls[0];
  expect(path).toBe('/api/checkouts');
  expect(request.method).toBe('POST');
  expect(JSON.parse(request.body)).toEqual({ addressId: 'addr_home' });
  expect(request.headers['Idempotency-Key']).toBe('retry-key-12345678');
  expect(request.headers.Authorization).toBe('Bearer demo-customer-1');
});

it('cart edits reset checkout attempts while keeping a payment retry stable', () => {
  const checkout = retryKey('checkout:addr_home');
  const payment = retryKey('payment:test');
  expect(retryKey('checkout:addr_home')).toBe(checkout);
  clearCheckoutKeys();
  expect(retryKey('checkout:addr_home')).not.toBe(checkout);
  expect(retryKey('payment:test')).toBe(payment);
});

it('turns a proxy outage and an empty success response into recoverable errors', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(new Response('', { status: 500 }))
      .mockResolvedValueOnce(new Response('', { status: 200 })),
  );
  await expect(api.cart()).rejects.toThrow(
    'The store is temporarily unavailable. Please try again.',
  );
  await expect(api.cart()).rejects.toThrow(
    'The store returned an incomplete response. Please retry.',
  );
});
