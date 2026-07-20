import { useEffect, useState, useRef } from 'react';
import { MessageSquare, Plus, Trash2, Send, BookOpen, Layers, Globe } from 'lucide-react';

interface ChatSession {
    id: string;
    title: string;
    createdAt: string;
    updatedAt: string;
}

interface ChatMessage {
    id: string;
    sessionId: string;
    role: string;
    message: string;
    createdAt: string;
}

interface Citation {
    retrievalLogId: string;
    chunkId: string;
    text: string;
    similarityScore: number;
    rank: number;
    documentTitle: string;
    chapter: string;
    section: string;
    pageStart: number;
    pageEnd: number;
}

interface Book {
    id: string;
    title: string;
    category: string;
    status: string;
}

export const Chat = () => {
    const [sessions, setSessions] = useState<ChatSession[]>([]);
    const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [books, setBooks] = useState<Book[]>([]);

    // Scope State
    const [scopeType, setScopeType] = useState<'all' | 'book' | 'category'>('all');
    const [selectedBookId, setSelectedBookId] = useState<string>('');
    const [selectedCategory, setSelectedCategory] = useState<string>('Technical');

    // Input State
    const [inputMessage, setInputMessage] = useState('');
    const [sending, setSending] = useState(false);

    // Citations State
    const [activeCitations, setActiveCitations] = useState<Citation[]>([]);
    const [messageCitationsMap, setMessageCitationsMap] = useState<Record<string, Citation[]>>({});

    const messagesEndRef = useRef<HTMLDivElement>(null);

    const fetchSessions = async () => {
        try {
            const res = await fetch('http://localhost:8080/api/chat/sessions');
            if (res.ok) {
                const data = await res.json();
                setSessions(data);
                if (data.length > 0 && !activeSessionId) {
                    setActiveSessionId(data[0].id);
                }
            }
        } catch (error) {
            console.error("Error fetching chat sessions:", error);
        }
    };

    const fetchBooks = async () => {
        try {
            const res = await fetch('http://localhost:8080/api/books');
            if (res.ok) {
                const data = await res.json();
                setBooks(data.filter((b: Book) => b.status === 'INDEXED'));
            }
        } catch (error) {
            console.error("Error fetching books:", error);
        }
    };

    const fetchMessages = async (sessionId: string) => {
        try {
            const res = await fetch(`http://localhost:8080/api/chat/sessions/${sessionId}/messages`);
            if (res.ok) {
                setMessages(await res.json());
                setActiveCitations([]); // Clear citations when switching sessions
            }
        } catch (error) {
            console.error("Error fetching messages:", error);
        }
    };

    useEffect(() => {
        fetchSessions();
        fetchBooks();
    }, []);

    useEffect(() => {
        if (activeSessionId) {
            fetchMessages(activeSessionId);
        } else {
            setMessages([]);
        }
    }, [activeSessionId]);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    const handleCreateSession = async () => {
        try {
            const title = prompt('Enter conversation title:') || 'New Conversation';
            const res = await fetch('http://localhost:8080/api/chat/sessions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ title })
            });

            if (res.ok) {
                const newSession = await res.json();
                setSessions([newSession, ...sessions]);
                setActiveSessionId(newSession.id);
            }
        } catch (error) {
            console.error("Error creating session:", error);
        }
    };

    const handleDeleteSession = async (id: string, e: React.MouseEvent) => {
        e.stopPropagation();
        if (!confirm('Are you sure you want to delete this conversation?')) {
            return;
        }

        try {
            const res = await fetch(`http://localhost:8080/api/chat/sessions/${id}`, {
                method: 'DELETE'
            });

            if (res.ok) {
                const updated = sessions.filter(s => s.id !== id);
                setSessions(updated);
                if (activeSessionId === id) {
                    setActiveSessionId(updated.length > 0 ? updated[0].id : null);
                }
            }
        } catch (error) {
            console.error("Error deleting session:", error);
        }
    };

    const handleSendMessage = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!inputMessage.trim() || !activeSessionId || sending) return;

        const userMsgText = inputMessage;
        setInputMessage('');
        setSending(true);

        // Optimistically add user message to UI
        const tempUserMsg: ChatMessage = {
            id: Math.random().toString(),
            sessionId: activeSessionId,
            role: 'user',
            message: userMsgText,
            createdAt: new Date().toISOString()
        };
        setMessages(prev => [...prev, tempUserMsg]);

        try {
            const payload: any = { message: userMsgText };
            if (scopeType === 'book' && selectedBookId) {
                payload.documentId = selectedBookId;
            } else if (scopeType === 'category' && selectedCategory) {
                payload.category = selectedCategory;
            }

            const res = await fetch(`http://localhost:8080/api/chat/sessions/${activeSessionId}/messages`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                const data = await res.json();

                // Add assistant message to UI
                setMessages(prev => {
                    // Remove optimistic message if needed, or just append
                    const filtered = prev.filter(m => m.id !== tempUserMsg.id);
                    return [...filtered, tempUserMsg, data.message];
                });

                // Store citations for this message
                if (data.citations && data.citations.length > 0) {
                    setMessageCitationsMap(prev => ({
                        ...prev,
                        [data.message.id]: data.citations
                    }));
                    setActiveCitations(data.citations);
                } else {
                    setActiveCitations([]);
                }
            }
        } catch (error) {
            console.error("Error sending message:", error);
        } finally {
            setSending(false);
        }
    };

    const handleMessageClick = (msg: ChatMessage) => {
        if (msg.role === 'assistant') {
            const citations = messageCitationsMap[msg.id] || [];
            setActiveCitations(citations);
        }
    };

    const categories = ['Technical', 'Fiction', 'Business', 'Science', 'Philosophy', 'General'];

    return (
        <div className="chat-container">
            {/* Sessions Sidebar */}
            <div className="chat-sidebar">
                <button className="btn btn-primary" onClick={handleCreateSession} style={{ width: '100%' }}>
                    <Plus size={18} /> New Conversation
                </button>

                <div className="panel" style={{ flexGrow: 1, padding: '16px', overflowY: 'auto', marginBottom: 0 }}>
                    <h3 style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '12px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Recent Chats
                    </h3>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                        {sessions.length === 0 ? (
                            <div style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.85rem', padding: '20px' }}>
                                No active chats.
                            </div>
                        ) : (
                            sessions.map((session) => (
                                <div
                                    key={session.id}
                                    className={`nav-item ${activeSessionId === session.id ? 'active' : ''}`}
                                    onClick={() => setActiveSessionId(session.id)}
                                    style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 12px' }}
                                >
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', overflow: 'hidden' }}>
                                        <MessageSquare size={16} style={{ flexShrink: 0 }} />
                                        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                            {session.title}
                                        </span>
                                    </div>
                                    <button
                                        className="btn btn-secondary btn-icon"
                                        onClick={(e) => handleDeleteSession(session.id, e)}
                                        style={{ padding: '4px', backgroundColor: 'transparent', border: 'none' }}
                                    >
                                        <Trash2 size={14} className="text-muted" />
                                    </button>
                                </div>
                            ))
                        )}
                    </div>
                </div>
            </div>

            {/* Main Chat Window */}
            <div className="chat-main">
                {activeSessionId ? (
                    <>
                        {/* Scope / Filter Bar */}
                        <div className="chat-header">
                            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                                <div style={{ display: 'flex', gap: '4px', backgroundColor: 'rgba(255,255,255,0.03)', padding: '4px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                                    <button
                                        className={`btn ${scopeType === 'all' ? 'btn-primary' : 'btn-secondary'}`}
                                        onClick={() => setScopeType('all')}
                                        style={{ padding: '6px 12px', fontSize: '0.8rem', borderRadius: '6px' }}
                                    >
                                        <Globe size={14} /> Entire Library
                                    </button>
                                    <button
                                        className={`btn ${scopeType === 'book' ? 'btn-primary' : 'btn-secondary'}`}
                                        onClick={() => setScopeType('book')}
                                        style={{ padding: '6px 12px', fontSize: '0.8rem', borderRadius: '6px' }}
                                    >
                                        <BookOpen size={14} /> Specific Book
                                    </button>
                                    <button
                                        className={`btn ${scopeType === 'category' ? 'btn-primary' : 'btn-secondary'}`}
                                        onClick={() => setScopeType('category')}
                                        style={{ padding: '6px 12px', fontSize: '0.8rem', borderRadius: '6px' }}
                                    >
                                        <Layers size={14} /> Category
                                    </button>
                                </div>

                                {scopeType === 'book' && (
                                    <select
                                        className="form-input"
                                        value={selectedBookId}
                                        onChange={(e) => setSelectedBookId(e.target.value)}
                                        style={{ padding: '6px 12px', fontSize: '0.8rem', width: '200px' }}
                                    >
                                        <option value="">Select a book...</option>
                                        {books.map(b => (
                                            <option key={b.id} value={b.id}>{b.title}</option>
                                        ))}
                                    </select>
                                )}

                                {scopeType === 'category' && (
                                    <select
                                        className="form-input"
                                        value={selectedCategory}
                                        onChange={(e) => setSelectedCategory(e.target.value)}
                                        style={{ padding: '6px 12px', fontSize: '0.8rem', width: '150px' }}
                                    >
                                        {categories.map(c => (
                                            <option key={c} value={c}>{c}</option>
                                        ))}
                                    </select>
                                )}
                            </div>
                        </div>

                        {/* Messages Area */}
                        <div className="chat-messages">
                            {messages.length === 0 ? (
                                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-secondary)', gap: '12px' }}>
                                    <MessageSquare size={48} style={{ color: 'var(--color-primary)', opacity: 0.5 }} />
                                    <p style={{ fontWeight: 600 }}>Start the conversation</p>
                                    <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', textAlign: 'center', maxWidth: '300px' }}>
                                        Ask questions about your documents. The assistant will retrieve relevant context and cite sources.
                                    </p>
                                </div>
                            ) : (
                                messages.map((msg) => (
                                    <div
                                        key={msg.id}
                                        className={`message-bubble ${msg.role === 'user' ? 'user' : 'assistant'}`}
                                        onClick={() => handleMessageClick(msg)}
                                        style={{ cursor: msg.role === 'assistant' ? 'pointer' : 'default' }}
                                    >
                                        <div style={{ whiteSpace: 'pre-wrap' }}>{msg.message}</div>
                                        {msg.role === 'assistant' && messageCitationsMap[msg.id] && (
                                            <div style={{ fontSize: '0.7rem', color: 'var(--color-secondary)', marginTop: '8px', fontWeight: 600 }}>
                                                Click message to view {messageCitationsMap[msg.id].length} citations
                                            </div>
                                        )}
                                    </div>
                                ))
                            )}
                            <div ref={messagesEndRef} />
                        </div>

                        {/* Input Area */}
                        <form onSubmit={handleSendMessage} className="chat-input-container">
                            <input
                                type="text"
                                className="form-input"
                                placeholder="Ask a question about your library..."
                                value={inputMessage}
                                onChange={(e) => setInputMessage(e.target.value)}
                                disabled={sending}
                            />
                            <button type="submit" className="btn btn-primary" style={{ padding: '12px' }} disabled={sending}>
                                <Send size={18} />
                            </button>
                        </form>
                    </>
                ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-secondary)', gap: '16px' }}>
                        <MessageSquare size={64} style={{ color: 'var(--color-primary)' }} />
                        <h3 style={{ fontSize: '1.25rem' }}>No Conversation Selected</h3>
                        <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)' }}>Select an existing chat session or create a new one to begin.</p>
                        <button className="btn btn-primary" onClick={handleCreateSession}>
                            Create Conversation
                        </button>
                    </div>
                )}
            </div>

            {/* Citations Panel */}
            {activeSessionId && activeCitations.length > 0 && (
                <div className="citations-panel">
                    <h3 style={{ fontSize: '1rem', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <BookOpen size={18} style={{ color: 'var(--color-secondary)' }} /> Citations & Sources
                    </h3>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        {activeCitations.map((citation, index) => (
                            <div key={citation.chunkId || index} className="citation-card">
                                <div className="citation-source">{citation.documentTitle}</div>
                                <div className="citation-meta">
                                    Chapter: {citation.chapter} | Pages {citation.pageStart}-{citation.pageEnd}
                                </div>
                                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '8px' }}>
                                    <span>Rank: #{citation.rank}</span>
                                    <span>Score: {citation.similarityScore.toFixed(4)}</span>
                                </div>
                                <div className="citation-text" title={citation.text}>
                                    {citation.text}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
};
