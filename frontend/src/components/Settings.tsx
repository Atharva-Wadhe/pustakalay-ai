import { useEffect, useState } from 'react';
import { Settings as SettingsIcon, Save, RefreshCw, AlertCircle } from 'lucide-react';

interface SystemConfiguration {
    key: string;
    value: string;
}

export const Settings = () => {
    const [configs, setConfigs] = useState<SystemConfiguration[]>([]);
    const [loading, setLoading] = useState(true);
    const [savingKey, setSavingKey] = useState<string | null>(null);
    const [editValues, setEditValues] = useState<Record<string, string>>({});

    const fetchConfigs = async () => {
        try {
            const res = await fetch('http://localhost:8080/api/config');
            if (res.ok) {
                const data = await res.json();
                setConfigs(data);
                const values: Record<string, string> = {};
                data.forEach((c: SystemConfiguration) => {
                    values[c.key] = c.value;
                });
                setEditValues(values);
            }
        } catch (error) {
            console.error("Error fetching configurations:", error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchConfigs();
    }, []);

    const handleSave = async (key: string) => {
        setSavingKey(key);
        try {
            const res = await fetch('http://localhost:8080/api/config', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ key, value: editValues[key] })
            });

            if (res.ok) {
                // Update local state
                setConfigs(configs.map(c => c.key === key ? { ...c, value: editValues[key] } : c));
                alert(`Configuration '${key}' updated successfully.`);
            } else {
                alert('Failed to save configuration.');
            }
        } catch (error) {
            console.error("Error saving configuration:", error);
        } finally {
            setSavingKey(null);
        }
    };

    const handleValueChange = (key: string, val: string) => {
        setEditValues(prev => ({
            ...prev,
            [key]: val
        }));
    };

    if (loading) {
        return <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>Loading Settings...</div>;
    }

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'between', alignItems: 'center', marginBottom: '32px' }}>
                <div>
                    <h1 style={{ fontSize: '2rem', marginBottom: '8px' }}>System Settings</h1>
                    <p style={{ color: 'var(--text-secondary)' }}>Tweak RAG parameters, swappable models, and ingestion thresholds dynamically.</p>
                </div>
                <button className="btn btn-secondary" onClick={fetchConfigs} style={{ marginLeft: 'auto' }}>
                    <RefreshCw className="nav-item-icon" /> Refresh
                </button>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: '32px', alignItems: 'start' }}>
                {/* Configurations List */}
                <div className="panel">
                    <div className="panel-header">
                        <h2 className="panel-title">
                            <SettingsIcon size={20} className="nav-item-icon" /> RAG Parameters & Models
                        </h2>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                        {configs.map((config) => (
                            <div key={config.key} style={{
                                borderBottom: '1px solid var(--border-color)',
                                paddingBottom: '20px',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '10px'
                            }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <span style={{ fontWeight: 700, fontSize: '1rem', fontFamily: 'monospace', color: 'var(--color-secondary)' }}>
                                        {config.key}
                                    </span>
                                    <button
                                        className="btn btn-primary"
                                        onClick={() => handleSave(config.key)}
                                        disabled={savingKey === config.key || editValues[config.key] === config.value}
                                        style={{ padding: '6px 14px', fontSize: '0.8rem' }}
                                    >
                                        <Save size={14} /> {savingKey === config.key ? 'Saving...' : 'Save'}
                                    </button>
                                </div>

                                {config.key === 'embedding_model' && (
                                    <select
                                        className="form-input"
                                        value={editValues[config.key] || ''}
                                        onChange={(e) => handleValueChange(config.key, e.target.value)}
                                    >
                                        <option value="nomic-embed-text">nomic-embed-text (Ollama)</option>
                                        <option value="text-embedding-3-small">text-embedding-3-small (OpenAI)</option>
                                        <option value="all-minilm-l6-v2">all-minilm-l6-v2 (Local)</option>
                                    </select>
                                )}

                                {config.key === 'chat_model' && (
                                    <select
                                        className="form-input"
                                        value={editValues[config.key] || ''}
                                        onChange={(e) => handleValueChange(config.key, e.target.value)}
                                    >
                                        <option value="qwen3">qwen3:8b (Ollama)</option>
                                        <option value="llama3">llama3:8b (Ollama)</option>
                                        <option value="gpt-4o">gpt-4o (OpenAI)</option>
                                        <option value="gpt-3.5-turbo">gpt-3.5-turbo (OpenAI)</option>
                                    </select>
                                )}

                                {config.key === 'top_k' && (
                                    <input
                                        type="number"
                                        className="form-input"
                                        min={1}
                                        max={50}
                                        value={editValues[config.key] || ''}
                                        onChange={(e) => handleValueChange(config.key, e.target.value)}
                                    />
                                )}

                                {config.key === 'temperature' && (
                                    <input
                                        type="number"
                                        className="form-input"
                                        min={0}
                                        max={1}
                                        step={0.1}
                                        value={editValues[config.key] || ''}
                                        onChange={(e) => handleValueChange(config.key, e.target.value)}
                                    />
                                )}

                                <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                                    {config.key === 'embedding_model' && 'The vector embedding model used to represent document chunks in vector space.'}
                                    {config.key === 'chat_model' && 'The large language model used to generate answers based on retrieved context.'}
                                    {config.key === 'top_k' && 'The number of most similar chunks retrieved from Qdrant to build the LLM prompt context.'}
                                    {config.key === 'temperature' && 'Controls LLM creativity. Lower values produce more factual, deterministic responses.'}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Info Sidebar */}
                <div className="panel" style={{ backgroundColor: 'rgba(99, 102, 241, 0.03)' }}>
                    <h3 style={{ fontSize: '1rem', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <AlertCircle size={18} style={{ color: 'var(--color-primary)' }} /> Swappable Architecture
                    </h3>
                    <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: '1.5', marginBottom: '12px' }}>
                        Pustakalay.ai is built with a completely decoupled architecture.
                    </p>
                    <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: '1.5', marginBottom: '12px' }}>
                        By changing these configurations, the system dynamically swaps the underlying models or parameters without requiring any code changes or server restarts.
                    </p>
                    <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: '1.5' }}>
                        To migrate from Ollama to OpenAI or Local Storage to S3, simply update the corresponding configuration keys.
                    </p>
                </div>
            </div>
        </div>
    );
};
