import { Link } from 'react-router-dom';
import { Arrow } from '../../components/Arrow';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Loading } from '../../components/Loading';
import { PageHeading } from '../../components/PageHeading';
import { StatusBadge } from '../../components/StatusBadge';
import { api } from '../../services/api';
import { date, money } from '../../utils/format';
import { useResource } from '../../hooks/useResource';

export function OrdersPage() {
  const orders = useResource(api.orders);
  return (
    <>
      <div className="heading-with-action">
        <PageHeading eyebrow="YOUR ACCOUNT / ORDERS" title="Keep track.">
          Your essentials, and where they stand.
        </PageHeading>
        <button
          className="button button-outline button-small"
          disabled={orders.loading}
          onClick={orders.refresh}
        >
          Refresh orders ↻
        </button>
      </div>
      <ErrorNotice error={orders.error} retry={orders.refresh} />
      {orders.loading && !orders.data && <Loading label="Finding your orders…" />}
      {orders.data?.length === 0 && (
        <EmptyState title="Your story starts with a first order.">
          When you place an order, its latest status will appear here.
        </EmptyState>
      )}
      <section className="orders-list" aria-label="Your orders">
        {orders.data?.map((order) => (
          <article className="order-row" key={order.id}>
            <div className="order-row-main">
              <p className="eyebrow">{order.id}</p>
              <h2>
                {order.itemCount} {order.itemCount === 1 ? 'essential' : 'essentials'} for your next
                move
              </h2>
              <p>Placed {date(order.createdAt)}</p>
            </div>
            <StatusBadge status={order.status} processing={order.paymentStarted} />
            <strong className="order-row-price">{money(order.total)}</strong>
            <Link
              className="button button-outline button-small"
              to={'/orders/' + order.id}
              aria-label={'View status for ' + order.id}
            >
              View status <Arrow />
            </Link>
          </article>
        ))}
      </section>
    </>
  );
}
