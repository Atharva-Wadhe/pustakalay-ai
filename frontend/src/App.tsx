import { useState } from 'react';
import { Sidebar } from './components/Sidebar';
import { Dashboard } from './components/Dashboard';
import { Library } from './components/Library';
import { Chat } from './components/Chat';
import { Settings } from './components/Settings';

function App() {
  const [activeTab, setActiveTab] = useState<string>('dashboard');

  return (
    <div className="app-container">
      <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} />

      <main className="main-content">
        {activeTab === 'dashboard' && <Dashboard />}
        {activeTab === 'library' && <Library />}
        {activeTab === 'chat' && <Chat />}
        {activeTab === 'settings' && <Settings />}
      </main>
    </div>
  );
}

export default App;
