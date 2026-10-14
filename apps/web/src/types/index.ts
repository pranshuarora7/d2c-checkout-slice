export type Product = {
  sku: string;
  name: string;
  category: string;
  description: string;
  detail: string;
  price: number;
  stock: number;
  art: string;
};
export type CartItem = {
  sku: string;
  name: string;
  detail: string;
  art: string;
  unitPrice: number;
  quantity: number;
  available: number;
  lineTotal: number;
};
export type Totals = { subtotal: number; handling: number; total: number; currency: string };
export type Cart = Totals & { items: CartItem[]; activeOrderId: string | null };
export type Address = {
  id: string;
  label: string;
  addressLine: string;
  city: string;
  state: string;
  pinCode: string;
};
export type Profile = { name: string; email: string; phone: string; addresses: Address[] };
export type ContactInput = Omit<Profile, 'addresses'>;
export type AddressInput = Omit<Address, 'id'>;
export type PaymentScenario = 'success' | 'failure' | 'pending' | 'duplicate';
export type OrderStatus = 'pending' | 'confirmed' | 'failed' | 'expired' | 'payment_issue';
export type Order = Totals & {
  id: string;
  status: OrderStatus;
  customer: Pick<Profile, 'name' | 'email' | 'phone'>;
  delivery: Omit<Address, 'id' | 'label'>;
  items: Omit<CartItem, 'available' | 'detail'>[];
  createdAt: string;
  expiresAt: string;
  updatedAt: string;
  paymentScenario?: PaymentScenario | null;
};
export type OrderSummary = Pick<Order, 'id' | 'status' | 'total' | 'currency' | 'createdAt'> & {
  itemCount: number;
};
export type CheckoutResult = {
  orderId: string;
  status: OrderStatus;
  total: number;
  currency: string;
};
