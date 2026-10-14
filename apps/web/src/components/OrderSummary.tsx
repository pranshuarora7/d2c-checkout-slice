import type { ReactNode } from 'react';
import { money } from '../utils/format';
import type { Totals } from '../types/index';
export function OrderSummary({ totals, children }: { totals: Totals; children?: ReactNode }) {
  return (
    <aside className="order-summary" aria-label="Order summary">
      <p className="eyebrow">THE FULL PICTURE</p>
      <h2>Order summary</h2>
      <dl>
        <div>
          <dt>Subtotal</dt>
          <dd>{money(totals.subtotal)}</dd>
        </div>
        <div>
          <dt>Packing &amp; handling</dt>
          <dd>{money(totals.handling)}</dd>
        </div>
        <div>
          <dt>Delivery</dt>
          <dd>Included</dd>
        </div>
        <div className="total-row">
          <dt>Total</dt>
          <dd>{money(totals.total)}</dd>
        </div>
      </dl>
      <p className="summary-note">All prices in INR. The full demo amount is shown above.</p>
      {children}
    </aside>
  );
}
