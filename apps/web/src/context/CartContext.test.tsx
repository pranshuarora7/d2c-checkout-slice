import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { CartProvider, useCart } from './CartContext';
import { api } from '../services/api';
import type { Cart } from '../types/index';
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
const cart = (quantity: number): Cart => ({
  items: [
    {
      sku: 'TRAINING-MAT',
      name: 'Training mat',
      detail: 'Sage',
      art: 'mat',
      unitPrice: 1299,
      quantity,
      available: 8,
      lineTotal: 1299 * quantity,
    },
  ],
  subtotal: 1299 * quantity,
  handling: 29,
  total: 1299 * quantity + 29,
  currency: 'INR',
  activeOrderId: null,
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
it('ignores an old cart response from a refresh started during a quantity update', async () => {
  const read = deferred<Cart>();
  const write = deferred<Cart>();
  vi.spyOn(api, 'cart').mockResolvedValueOnce(cart(1)).mockReturnValueOnce(read.promise);
  vi.spyOn(api, 'setQuantity').mockReturnValueOnce(write.promise);
  const { result } = renderHook(() => useCart(), { wrapper: CartProvider });
  await act(async () => {});
  let updating!: Promise<void>;
  let refreshing!: Promise<void>;
  act(() => {
    updating = result.current.setQuantity('TRAINING-MAT', 2);
  });
  act(() => {
    refreshing = result.current.refresh();
  });
  await act(async () => {
    write.resolve(cart(2));
    await updating;
  });
  expect(result.current.cart?.items[0].quantity).toBe(2);
  await act(async () => {
    read.resolve(cart(1));
    await refreshing;
  });
  expect(result.current.cart?.items[0].quantity).toBe(2);
});
it('ignores a stale refresh error after a completed quantity update', async () => {
  const read = deferred<Cart>();
  const write = deferred<Cart>();
  vi.spyOn(api, 'cart').mockResolvedValueOnce(cart(1)).mockReturnValueOnce(read.promise);
  vi.spyOn(api, 'setQuantity').mockReturnValueOnce(write.promise);
  const { result } = renderHook(() => useCart(), { wrapper: CartProvider });
  await act(async () => {});
  let updating!: Promise<void>;
  let refreshing!: Promise<void>;
  act(() => {
    updating = result.current.setQuantity('TRAINING-MAT', 2);
  });
  act(() => {
    refreshing = result.current.refresh();
  });
  await act(async () => {
    write.resolve(cart(2));
    await updating;
  });
  await act(async () => {
    read.reject(new Error('Old refresh failed'));
    await refreshing;
  });
  expect(result.current.error).toBeNull();
});

it('keeps a quantity update when a refresh started before it finishes later', async () => {
  const read = deferred<Cart>();
  const write = deferred<Cart>();
  vi.spyOn(api, 'cart').mockResolvedValueOnce(cart(1)).mockReturnValueOnce(read.promise);
  vi.spyOn(api, 'setQuantity').mockReturnValueOnce(write.promise);
  const { result } = renderHook(() => useCart(), { wrapper: CartProvider });
  await act(async () => {});
  let updating!: Promise<void>;
  let refreshing!: Promise<void>;
  act(() => {
    refreshing = result.current.refresh();
  });
  act(() => {
    updating = result.current.setQuantity('TRAINING-MAT', 2);
  });
  await act(async () => {
    write.resolve(cart(2));
    await updating;
  });
  await act(async () => {
    read.resolve(cart(1));
    await refreshing;
  });
  expect(result.current.cart?.items[0].quantity).toBe(2);
  expect(result.current.loading).toBe(false);
});
it('allows a new refresh after a completed update', async () => {
  vi.spyOn(api, 'cart').mockResolvedValueOnce(cart(1)).mockResolvedValueOnce(cart(3));
  vi.spyOn(api, 'setQuantity').mockResolvedValue(cart(2));
  const { result } = renderHook(() => useCart(), { wrapper: CartProvider });
  await act(async () => {});
  await act(async () => {
    await result.current.setQuantity('TRAINING-MAT', 2);
  });
  await act(async () => {
    await result.current.refresh();
  });
  expect(result.current.cart?.items[0].quantity).toBe(3);
  expect(result.current.loading).toBe(false);
});
it('clears busy state after an update fails and allows recovery', async () => {
  const read = deferred<Cart>();
  const write = deferred<Cart>();
  vi.spyOn(api, 'cart')
    .mockResolvedValueOnce(cart(1))
    .mockReturnValueOnce(read.promise)
    .mockResolvedValueOnce(cart(2));
  vi.spyOn(api, 'setQuantity').mockReturnValueOnce(write.promise);
  const { result } = renderHook(() => useCart(), { wrapper: CartProvider });
  await act(async () => {});
  let updating!: Promise<void>;
  let refreshing!: Promise<void>;
  act(() => {
    updating = result.current.setQuantity('TRAINING-MAT', 2);
  });
  act(() => {
    refreshing = result.current.refresh();
  });
  await act(async () => {
    write.reject(new Error('Response lost'));
    await expect(updating).rejects.toThrow('Response lost');
  });
  await act(async () => {
    read.resolve(cart(1));
    await refreshing;
  });
  expect(result.current.updating).toBe(false);
  expect(result.current.loading).toBe(false);
  await act(async () => {
    await result.current.refresh();
  });
  expect(result.current.cart?.items[0].quantity).toBe(2);
});
