import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { api, errorMessage } from '../services/api';
import { clearCheckoutKeys } from '../utils/retryKeys';
import type { Cart } from '../types/index';

type CartState = {
  cart: Cart | null;
  loading: boolean;
  updating: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  setQuantity: (sku: string, quantity: number) => Promise<void>;
  copyOrder: (id: string) => Promise<void>;
};
const CartContext = createContext<CartState | null>(null);

export function CartProvider({ children }: { children: ReactNode }) {
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // A slow refresh must not overwrite the result of a newer cart mutation.
  const revision = useRef(0);
  const mutationInFlight = useRef(false);

  const refresh = useCallback(async () => {
    const current = ++revision.current;
    setLoading(true);
    try {
      const next = await api.cart();
      if (current === revision.current) {
        setCart(next);
        setError(null);
      }
    } catch (reason) {
      if (current === revision.current) setError(errorMessage(reason));
    } finally {
      if (current === revision.current) setLoading(false);
    }
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh]);

  const mutate = useCallback(async (save: () => Promise<Cart>) => {
    if (mutationInFlight.current) return;
    mutationInFlight.current = true;
    ++revision.current;
    setUpdating(true);
    try {
      const next = await save();
      clearCheckoutKeys();
      setCart(next);
      setError(null);
    } finally {
      // Refreshes started during this update may still contain the previous cart.
      ++revision.current;
      mutationInFlight.current = false;
      setUpdating(false);
      setLoading(false);
    }
  }, []);
  const setQuantity = useCallback(
    (sku: string, quantity: number) => mutate(() => api.setQuantity(sku, quantity)),
    [mutate],
  );
  const copyOrder = useCallback((id: string) => mutate(() => api.copyOrderToCart(id)), [mutate]);

  return (
    <CartContext.Provider
      value={{ cart, loading, updating, error, refresh, setQuantity, copyOrder }}
    >
      {children}
    </CartContext.Provider>
  );
}

export function useCart(): CartState {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart requires CartProvider');
  return context;
}
