import { Link } from 'react-router-dom';
import { ApiError, errorMessage } from '../services/api';
import { Notice } from './Notice';
import { Arrow } from './Arrow';
export function ErrorNotice({ error, retry }: { error: unknown; retry?: () => void }) {
  if (!error) return null;
  return (
    <Notice tone="error">
      <span>{typeof error === 'string' ? error : errorMessage(error)}</span>
      {error instanceof ApiError && error.details.orderId && (
        <Link to={'/orders/' + error.details.orderId + '/payment'}>
          Resume checkout <Arrow />
        </Link>
      )}
      {retry && (
        <button className="text-button" onClick={retry}>
          Try again
        </button>
      )}
    </Notice>
  );
}
