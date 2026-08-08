import './globals.css';
import AuthProvider from './components/AuthProvider';
import AppShell from './components/AppShell';

export const metadata = {
  title: 'LLM Gateway — Console',
  description: 'Operator console for the LLM Gateway',
};

/**
 * The session lives in a client component (AuthProvider) and the frame is chosen
 * by another (AppShell), so this stays a plain server component.
 */
export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>
          <AppShell>{children}</AppShell>
        </AuthProvider>
      </body>
    </html>
  );
}
