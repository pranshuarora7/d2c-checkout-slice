import type {
  AddressInput,
  Cart,
  CheckoutResult,
  ContactInput,
  Order,
  OrderSummary,
  PaymentScenario,
  Product,
  Profile,
} from '../types/index';

export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public details: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch('/api' + path, {
      ...options,
      headers: {
        Accept: 'application/json',
        Authorization: 'Bearer demo-customer-1',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers,
      },
    });
  } catch (error) {
    if (options.signal?.aborted) throw error;
    throw new ApiError('NETWORK_ERROR', "We couldn't reach the store. Please try again.");
  }
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(
      payload?.code ?? 'REQUEST_FAILED',
      payload?.message ??
        (response.status >= 500
          ? 'The store is temporarily unavailable. Please try again.'
          : 'Something went wrong. Please try again.'),
      payload?.details ?? {},
    );
  }
  if (payload === null) {
    throw new ApiError(
      'INVALID_RESPONSE',
      'The store returned an incomplete response. Please retry.',
    );
  }
  return payload as T;
}

export const api = {
  products: (signal?: AbortSignal) => request<Product[]>('/products', { signal }),
  profile: (signal?: AbortSignal) => request<Profile>('/profile', { signal }),
  updateProfile: (contact: ContactInput) =>
    request<Profile>('/profile', { method: 'PUT', body: JSON.stringify(contact) }),
  addAddress: (address: AddressInput) =>
    request<Profile>('/profile/addresses', { method: 'POST', body: JSON.stringify(address) }),
  updateAddress: (id: string, address: AddressInput) =>
    request<Profile>('/profile/addresses/' + encodeURIComponent(id), {
      method: 'PUT',
      body: JSON.stringify(address),
    }),
  removeAddress: (id: string) =>
    request<Profile>('/profile/addresses/' + encodeURIComponent(id), { method: 'DELETE' }),
  cart: (signal?: AbortSignal) => request<Cart>('/cart', { signal }),
  setQuantity: (sku: string, quantity: number) =>
    request<Cart>('/cart/items/' + encodeURIComponent(sku), {
      method: 'PUT',
      body: JSON.stringify({ quantity }),
    }),
  checkout: (addressId: string, key: string) =>
    request<CheckoutResult>('/checkouts', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify({ addressId }),
    }),
  order: (id: string, signal?: AbortSignal) =>
    request<Order>('/orders/' + encodeURIComponent(id), { signal }),
  orders: (signal?: AbortSignal) => request<OrderSummary[]>('/orders', { signal }),
  completePayment: (id: string) =>
    request<Order>('/orders/' + encodeURIComponent(id) + '/mock-payment/complete', {
      method: 'POST',
    }),
  pay: (id: string, outcome: PaymentScenario, key: string) =>
    request<Order>('/orders/' + encodeURIComponent(id) + '/mock-payment', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify({ outcome }),
    }),
};

export const errorMessage = (error: unknown): string =>
  error instanceof Error ? error.message : 'Something went wrong. Please try again.';
