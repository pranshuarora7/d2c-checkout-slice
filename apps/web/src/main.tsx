import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { CartProvider } from './context/CartContext';
import { ProfileProvider } from './context/ProfileContext';
import { ErrorBoundary } from './components/ErrorBoundary';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <BrowserRouter>
        <CartProvider>
          <ProfileProvider>
            <App />
          </ProfileProvider>
        </CartProvider>
      </BrowserRouter>
    </ErrorBoundary>
  </StrictMode>,
);
