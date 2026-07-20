import { useEffect, useState } from 'react';
import { BookOpen, Plus, Trash2, RefreshCw, Eye, X, AlertCircle } from 'lucide-react';

interface Document {
    id: string;
    title: string;
    author: string;
    category: string;
    fileName: string;
    filePath: string;
    fileSize: number;
    status: string;
    pageCount: number | null;
    createdAt: string;
}

interface DocumentEvent {
    id: string;
    eventType: string;
    eventPayload: string;
    createdAt: string;
}

export const Library = () => {
    const [books, setBooks] = useState<Document[]>([]);
    const [loading, setLoading] = useState(true);

    // Form State
    const [filePath, setFilePath] = useState('');
    const [title, setTitle] = useState('');
    const [author, setAuthor] = useState('');
    const [category, setCategory] = useState('Technical');
    const [registering, setRegistering] = useState(false);
    const [formError, setFormError] = useState('');

    // Event Modal State
    const [selectedBook, setSelectedBook] = useState<Document | null>(null);
    const [events, setEvents] = useState<DocumentEvent[]>([]);
    const [loadingEvents, setLoadingEvents] = useState(false);

    const fetchBooks = async () => {
        try {
            const res = await fetch('http://localhost:8080/api/books');
            if (res.ok) {
                setBooks(await res.json());
            }
        } catch (error) {
            console.error("Error fetching books:", error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchBooks();
        const interval = setInterval(fetchBooks, 5000); // Poll for status changes
        return () => clearInterval(interval);
    }, []);

    const handleRegister = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!filePath) {
            setFormError('File path is required');
            return;
        }

        setRegistering(true);
        setFormError('');
        try {
            const res = await fetch('http://localhost:8080/api/books/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ filePath, title, author, category })
            });

            if (res.ok) {
                setFilePath('');
                setTitle('');
                setAuthor('');
                fetchBooks();
            } else {
                setFormError('Failed to register book. Ensure the file path exists on the backend host.');
            }
        } catch (error) {
            setFormError('Network error registering book.');
        } finally {
            setRegistering(false);
        }
    };

    const handleDelete = async (id: string) => {
        if (!confirm('Are you sure you want to delete this book? This will delete the physical file, Qdrant vectors, and database chunks.')) {
            return;
        }

        try {
            const res = await fetch(`http://localhost:8080/api/books/${id}`, {
                method: 'DELETE'
            });
            if (res.ok) {
                fetchBooks();
            }
        } catch (error) {
            console.error("Error deleting book:", error);
        }
    };

    const handleReindex = async (id: string) => {
        try {
            const res = await fetch(`http://localhost:8080/api/books/${id}/reindex`, {
                method: 'POST'
            });
            if (res.ok) {
                alert('Re-indexing job queued successfully.');
                fetchBooks();
            }
        } catch (error) {
            console.error("Error re-indexing book:", error);
        }
    };

    const viewEvents = async (book: Document) => {
        setSelectedBook(book);
        setLoadingEvents(true);
        try {
            const res = await fetch(`http://localhost:8080/api/books/${book.id}/events`);
            if (res.ok) {
                setEvents(await res.json());
            }
        } catch (error) {
            console.error("Error fetching book events:", error);
        } finally {
            setLoadingEvents(false);
        }
    };

    const formatBytes = (bytes: number) => {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'between', alignItems: 'center', marginBottom: '32px' }}>
                <div>
                    <h1 style={{ fontSize: '2rem', marginBottom: '8px' }}>Library Manager</h1>
                    <p style={{ color: 'var(--text-secondary)' }}>Register new books, track ingestion status, and manage document lifecycles.</p>
                </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: '32px', alignItems: 'start' }}>
                {/* Books List Panel */}
                <div className="panel">
                    <div className="panel-header">
                        <h2 className="panel-title">
                            <BookOpen size={20} className="nav-item-icon" /> Registered Documents
                        </h2>
                    </div>

                    {loading ? (
                        <div style={{ textAlign: 'center', padding: '40px' }}>Loading library...</div>
                    ) : (
                        <div className="table-container">
                            <table className="custom-table">
                                <thead>
                                    <tr>
                                        <th>Title</th>
                                        <th>Author</th>
                                        <th>Category</th>
                                        <th>Size</th>
                                        <th>Status</th>
                                        <th>Actions</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {books.length === 0 ? (
                                        <tr>
                                            <td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>No books registered yet.</td>
                                        </tr>
                                    ) : (
                                        books.map((book) => (
                                            <tr key={book.id}>
                                                <td style={{ fontWeight: 600 }}>
                                                    <div>{book.title}</div>
                                                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontFamily: 'monospace', marginTop: '4px' }}>
                                                        {book.fileName}
                                                    </div>
                                                </td>
                                                <td>{book.author}</td>
                                                <td>
                                                    <span className="badge info">{book.category}</span>
                                                </td>
                                                <td>{formatBytes(book.fileSize)}</td>
                                                <td>
                                                    <span className={`badge ${book.status === 'INDEXED' ? 'success' :
                                                        book.status === 'INDEXING' ? 'warning' :
                                                            book.status === 'DISCOVERED' ? 'info' : 'danger'
                                                        }`}>
                                                        {book.status}
                                                    </span>
                                                </td>
                                                <td>
                                                    <div style={{ display: 'flex', gap: '8px' }}>
                                                        <button className="btn btn-secondary btn-icon" onClick={() => viewEvents(book)} title="View Lifecycle Events">
                                                            <Eye size={16} />
                                                        </button>
                                                        <button className="btn btn-secondary btn-icon" onClick={() => handleReindex(book.id)} title="Re-index Book">
                                                            <RefreshCw size={16} />
                                                        </button>
                                                        <button className="btn btn-danger btn-icon" onClick={() => handleDelete(book.id)} title="Delete Book">
                                                            <Trash2 size={16} />
                                                        </button>
                                                    </div>
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>

                {/* Register Book Panel */}
                <div className="panel">
                    <h2 className="panel-title" style={{ marginBottom: '20px' }}>
                        <Plus size={20} className="nav-item-icon" /> Ingest New Document
                    </h2>

                    {formError && (
                        <div style={{
                            backgroundColor: 'var(--color-danger-bg)',
                            color: 'var(--color-danger)',
                            padding: '12px',
                            borderRadius: '8px',
                            fontSize: '0.85rem',
                            marginBottom: '16px',
                            display: 'flex',
                            gap: '8px',
                            alignItems: 'start'
                        }}>
                            <AlertCircle size={16} style={{ flexShrink: 0, marginTop: '2px' }} />
                            <span>{formError}</span>
                        </div>
                    )}

                    <form onSubmit={handleRegister}>
                        <div className="form-group">
                            <label className="form-label">Absolute File Path (.pdf)</label>
                            <input
                                type="text"
                                className="form-input"
                                placeholder="e.g. D:\Library\Incoming\book.pdf"
                                value={filePath}
                                onChange={(e) => setFilePath(e.target.value)}
                                required
                            />
                        </div>

                        <div className="form-group">
                            <label className="form-label">Title (Optional)</label>
                            <input
                                type="text"
                                className="form-input"
                                placeholder="Auto-detected if blank"
                                value={title}
                                onChange={(e) => setTitle(e.target.value)}
                            />
                        </div>

                        <div className="form-group">
                            <label className="form-label">Author (Optional)</label>
                            <input
                                type="text"
                                className="form-input"
                                placeholder="Auto-detected if blank"
                                value={author}
                                onChange={(e) => setAuthor(e.target.value)}
                            />
                        </div>

                        <div className="form-group">
                            <label className="form-label">Category</label>
                            <select
                                className="form-input"
                                value={category}
                                onChange={(e) => setCategory(e.target.value)}
                            >
                                <option value="Technical">Technical</option>
                                <option value="Fiction">Fiction</option>
                                <option value="Business">Business</option>
                                <option value="Science">Science</option>
                                <option value="Philosophy">Philosophy</option>
                                <option value="General">General</option>
                            </select>
                        </div>

                        <button type="submit" className="btn btn-primary" style={{ width: '100%', marginTop: '10px' }} disabled={registering}>
                            {registering ? 'Registering...' : 'Register & Index'}
                        </button>
                    </form>
                </div>
            </div>

            {/* Lifecycle Events Modal */}
            {selectedBook && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                            <h3 style={{ fontSize: '1.25rem' }}>Lifecycle Events: {selectedBook.title}</h3>
                            <button className="btn btn-secondary btn-icon" onClick={() => setSelectedBook(null)}>
                                <X size={18} />
                            </button>
                        </div>

                        {loadingEvents ? (
                            <div style={{ textAlign: 'center', padding: '20px' }}>Loading events...</div>
                        ) : (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', maxHeight: '400px', overflowY: 'auto', paddingRight: '8px' }}>
                                {events.length === 0 ? (
                                    <div style={{ textAlign: 'center', color: 'var(--text-muted)' }}>No events logged for this document.</div>
                                ) : (
                                    events.map((event) => (
                                        <div key={event.id} style={{
                                            borderLeft: '3px solid var(--color-primary)',
                                            paddingLeft: '16px',
                                            paddingTop: '8px',
                                            paddingBottom: '8px',
                                            backgroundColor: 'rgba(255, 255, 255, 0.02)',
                                            borderRadius: '0 8px 8px 0',
                                            padding: '12px'
                                        }}>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                                                <span className="badge info" style={{ fontSize: '0.65rem' }}>{event.eventType}</span>
                                                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                                    {new Date(event.createdAt).toLocaleString()}
                                                </span>
                                            </div>
                                            <pre style={{
                                                fontSize: '0.8rem',
                                                color: 'var(--text-secondary)',
                                                whiteSpace: 'pre-wrap',
                                                wordBreak: 'break-all',
                                                fontFamily: 'monospace',
                                                backgroundColor: 'rgba(0,0,0,0.2)',
                                                padding: '8px',
                                                borderRadius: '4px',
                                                marginTop: '8px'
                                            }}>
                                                {JSON.stringify(JSON.parse(event.eventPayload), null, 2)}
                                            </pre>
                                        </div>
                                    ))
                                )}
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};
