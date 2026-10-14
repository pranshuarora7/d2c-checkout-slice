import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Arrow } from './Arrow';
export function EmptyState({
  title,
  children,
  action = 'Explore the collection',
}: {
  title: string;
  children: ReactNode;
  action?: string;
}) {
  return (
    <div className="empty-state">
      <span className="empty-symbol" aria-hidden="true">
        ↗
      </span>
      <h2>{title}</h2>
      <p>{children}</p>
      <Link className="button button-dark" to="/">
        {action}
        <Arrow />
      </Link>
    </div>
  );
}
