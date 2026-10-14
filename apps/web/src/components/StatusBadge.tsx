import type { OrderStatus } from '../types/index';
const labels: Record<OrderStatus, string> = {
  pending: 'Pending',
  confirmed: 'Confirmed',
  failed: 'Payment failed',
  expired: 'Expired',
  payment_issue: 'Payment issue',
};
export function StatusBadge({
  status,
  processing = false,
}: {
  status: OrderStatus;
  processing?: boolean;
}) {
  return (
    <span className={'status-badge status-' + status}>
      {status === 'pending' && processing ? 'Processing' : labels[status]}
    </span>
  );
}
