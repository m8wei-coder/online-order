import { render, screen } from '@testing-library/react';
import App from './App';

test('shows the online ordering app and login form when signed out', () => {
  render(<App />);
  expect(screen.getByText('Online Order System')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /^login$/i })).toBeInTheDocument();
});
