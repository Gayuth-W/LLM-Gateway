import './globals.css';
import Sidebar from './components/Sidebar';

export const metadata = {
  title: 'LLM Gateway — Console',
  description: 'Operator console for the LLM Gateway',
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        <div className="app">
          <Sidebar />
          <main className="content">{children}</main>
        </div>
      </body>
    </html>
  );
}
