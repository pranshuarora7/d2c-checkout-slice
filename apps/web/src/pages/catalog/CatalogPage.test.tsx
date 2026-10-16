import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { CartProvider } from '../../context/CartContext';
import { api } from '../../services/api';
import { CatalogPage } from './CatalogPage';
import type { Cart } from '../../types/index';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it('replaces Add with the saved quantity and removes the item when it reaches zero', async () => {
  const product = {
    sku: 'MAT',
    name: 'Training mat',
    category: 'Foundation',
    description: '',
    detail: 'Sage',
    art: 'mat',
    price: 100,
    stock: 3,
  };
  const cart = (quantity: number): Cart => ({
    items: quantity
      ? [
          {
            sku: 'MAT',
            name: 'Training mat',
            detail: 'Sage',
            art: 'mat',
            unitPrice: 100,
            quantity,
            available: 3,
            lineTotal: 100 * quantity,
          },
        ]
      : [],
    subtotal: 100 * quantity,
    handling: quantity ? 29 : 0,
    total: quantity ? 100 * quantity + 29 : 0,
    currency: 'INR',
  });
  vi.spyOn(api, 'products').mockResolvedValue([product]);
  vi.spyOn(api, 'cart').mockResolvedValue(cart(0));
  const update = vi
    .spyOn(api, 'setQuantity')
    .mockImplementation(async (_, quantity) => cart(quantity));
  render(
    <MemoryRouter>
      <CartProvider>
        <CatalogPage />
      </CartProvider>
    </MemoryRouter>,
  );
  const user = userEvent.setup();
  await user.click(await screen.findByRole('button', { name: 'Add Training mat to cart' }));
  await user.click(await screen.findByRole('button', { name: 'Increase Training mat quantity' }));
  expect(screen.getByLabelText('Training mat quantity in cart').textContent).toBe('2');
  await user.click(screen.getByRole('button', { name: 'Decrease Training mat quantity' }));
  await user.click(screen.getByRole('button', { name: 'Decrease Training mat quantity' }));
  expect(await screen.findByRole('button', { name: 'Add Training mat to cart' })).toBeTruthy();
  expect(update.mock.calls).toEqual([
    ['MAT', 1],
    ['MAT', 2],
    ['MAT', 1],
    ['MAT', 0],
  ]);
});
