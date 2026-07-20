import { useEffect, useState } from 'react';
import { BookOpen, Database, MessageSquare, Cpu, RefreshCw, Clock } from 'lucide-react';

interface Summary {
    totalBooks: number;
    totalChunks: number;
    totalQueries: number;
    activeJobs: number;
}

interface Job {
    id: string;
    documentId: string;
    triggerType: string;
    status: string;
    queuedAt: string;
    startedAt: string | null;
    completedAt: string | null;
    progress: number;
    errorMessage: string | null;
    chunkCount: number | null;
}

interface RetrievalLog {
    id: string;
    query: string;
    topK: number;
    retrievalMs: number;
    generationMs: number | null;
    tokens: number | null;
    createdAt: string;
}

export const Dashboard = () => {
    const [summary, setSummary] = useState<Summary>({ totalBooks: 0, totalChunks: 0, totalQueries: 0, activeJobs: 0 });
    const [jobs, setJobs] = useState<Job[]>([]);
    const [logs, setLogs] = useState<RetrievalLog[]>([]);
    const [loading, setLoading] = useState(true);

    const fetchData = async () => {
        try {
            const [summaryRes, jobsRes, logsRes] = await Promise.all([
                fetch('http://localhost:8080/api/analytics/summary'),
                fetch('http://localhost:8080/api/jobs'),
                fetch('http://localhost:8080/api/analytics/retrieval-logs')
            ]);

            if (summaryRes.ok) setSummary(await summaryRes.json());
            if (jobsRes.ok) setJobs(await jobsRes.json());
            if (logsRes.ok) setLogs(await logsRes.json());
        } catch (error) {
            console.error("Error fetching dashboard data:", error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData();
        const interval = setInterval(fetchData, 5000); // Poll every 5 seconds for live updates
        return () => clearInterval(interval);
    }, []);

    if (loading) {
        return <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>Loading Dashboard...</div>;
    }

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'between', alignItems: 'center', marginBottom: '32px' }}>
                <div>
                    <h1 style={{ fontSize: '2rem', marginBottom: '8px' }}>System Dashboard</h1>
                    <p style={{ color: 'var(--text-secondary)' }}>Real-time platform metrics, indexing pipelines, and retrieval analytics.</p>
                </div>
                <button className="btn btn-secondary" onClick={fetchData} style={{ marginLeft: 'auto' }}>
                    <RefreshCw className="nav-item-icon" /> Refresh
                </button>
            </div>

            {/* Stats Grid */}
            <div className="dashboard-grid">
                <div className="stat-card">
                    <div className="stat-icon-container primary">
                        <BookOpen size={24} />
                    </div>
                    <div className="stat-info">
                        <div className="stat-value">{summary.totalBooks}</div>
                        <div className="stat-label">Total Documents</div>
                    </div>
                </div>

                <div className="stat-card">
                    <div className="stat-icon-container secondary">
                        <Database size={24} />
                    </div>
                    <div className="stat-info">
                        <div className="stat-value">{summary.totalChunks}</div>
                        <div className="stat-label">Indexed Chunks</div>
                    </div>
                </div>

                <div className="stat-card">
                    <div className="stat-icon-container accent">
                        <MessageSquare size={24} />
                    </div>
                    <div className="stat-info">
                        <div className="stat-value">{summary.totalQueries}</div>
                        <div className="stat-label">Total Queries</div>
                    </div>
                </div>

                <div className="stat-card">
                    <div className="stat-icon-container success">
                        <Cpu size={24} />
                    </div>
                    <div className="stat-info">
                        <div className="stat-value">{summary.activeJobs}</div>
                        <div className="stat-label">Active Indexing Jobs</div>
                    </div>
                </div>
            </div>

            {/* Active & Recent Indexing Jobs */}
            <div className="panel">
                <div className="panel-header">
                    <h2 className="panel-title">
                        <Cpu size={20} className="nav-item-icon" /> Indexing Pipeline Jobs
                    </h2>
                </div>
                <div className="table-container">
                    <table className="custom-table">
                        <thead>
                            <tr>
                                <th>Job ID</th>
                                <th>Trigger</th>
                                <th>Status</th>
                                <th>Progress</th>
                                <th>Chunks</th>
                                <th>Queued At</th>
                            </tr>
                        </thead>
                        <tbody>
                            {jobs.length === 0 ? (
                                <tr>
                                    <td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>No indexing jobs found.</td>
                                </tr>
                            ) : (
                                jobs.slice(0, 5).map((job) => (
                                    <tr key={job.id}>
                                        <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{job.id.substring(0, 8)}...</td>
                                        <td>
                                            <span className={`badge ${job.triggerType === 'AUTO' ? 'info' : 'success'}`}>
                                                {job.triggerType}
                                            </span>
                                        </td>
                                        <td>
                                            <span className={`badge ${job.status === 'COMPLETED' ? 'success' :
                                                job.status === 'RUNNING' ? 'warning' :
                                                    job.status === 'QUEUED' ? 'info' : 'danger'
                                                }`}>
                                                {job.status}
                                            </span>
                                        </td>
                                        <td style={{ width: '250px' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                                <div className="progress-container" style={{ flexGrow: 1 }}>
                                                    <div className="progress-bar" style={{ width: `${job.progress}%` }}></div>
                                                </div>
                                                <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{job.progress}%</span>
                                            </div>
                                        </td>
                                        <td>{job.chunkCount ?? '-'}</td>
                                        <td>{new Date(job.queuedAt).toLocaleString()}</td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Retrieval Logs & Analytics */}
            <div className="panel">
                <div className="panel-header">
                    <h2 className="panel-title">
                        <Clock size={20} className="nav-item-icon" /> Retrieval & Generation Logs
                    </h2>
                </div>
                <div className="table-container">
                    <table className="custom-table">
                        <thead>
                            <tr>
                                <th>Query</th>
                                <th>Top K</th>
                                <th>Vector Search</th>
                                <th>LLM Generation</th>
                                <th>Est. Tokens</th>
                                <th>Timestamp</th>
                            </tr>
                        </thead>
                        <tbody>
                            {logs.length === 0 ? (
                                <tr>
                                    <td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>No retrieval logs found.</td>
                                </tr>
                            ) : (
                                logs.slice(0, 5).map((log) => (
                                    <tr key={log.id}>
                                        <td style={{ maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                            {log.query}
                                        </td>
                                        <td>{log.topK}</td>
                                        <td>
                                            <span style={{ color: 'var(--color-secondary)', fontWeight: 600 }}>
                                                {log.retrievalMs} ms
                                            </span>
                                        </td>
                                        <td>
                                            {log.generationMs ? (
                                                <span style={{ color: 'var(--color-accent)', fontWeight: 600 }}>
                                                    {log.generationMs} ms
                                                </span>
                                            ) : (
                                                <span style={{ color: 'var(--text-muted)' }}>-</span>
                                            )}
                                        </td>
                                        <td>{log.tokens ?? '-'}</td>
                                        <td>{new Date(log.createdAt).toLocaleString()}</td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
};
