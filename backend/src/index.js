const express = require('express');
const helmet = require('helmet');
const compression = require('compression');
const cors = require('cors');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 4100;

// Security & performance
app.use(helmet({ contentSecurityPolicy: false }));
app.use(compression());
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '2mb' }));

// Health check (no auth)
app.get('/health', (req, res) => res.json({ status: 'ok', time: new Date().toISOString() }));

// API routes
app.use('/api/auth',      require('./routes/auth'));
app.use('/api/sync',      require('./routes/sync'));
app.use('/api/slips',     require('./routes/slips'));
app.use('/api/dashboard',   require('./routes/dashboard'));
app.use('/api/categories',  require('./routes/categories'));

// Web dashboard — serve static files
app.use('/dashboard', express.static(path.join(__dirname, '..', 'public')));
app.get('/dashboard',     (req, res) => res.sendFile(path.join(__dirname, '..', 'public', 'index.html')));

// 404
app.use((req, res) => res.status(404).json({ error: 'Not found' }));

// Error handler
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({ error: 'Internal error' });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Flow backend running on port ${PORT}`);
});
