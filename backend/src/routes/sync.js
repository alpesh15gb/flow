const router = require('express').Router();
const pool = require('../db');
const { requireAuth } = require('../middleware/auth');

// POST /api/sync/push
// Body: { transactions: [...] }
// Upserts transactions, prevents duplicates by UUID
router.post('/push', requireAuth, async (req, res) => {
  const { transactions } = req.body;
  if (!Array.isArray(transactions) || transactions.length === 0) {
    return res.json({ synced: 0 });
  }

  const MAX_BATCH = 500;
  const batch = transactions.slice(0, MAX_BATCH);
  const user_id = req.user.user_id;
  const device_id = req.user.device_id;

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    let synced = 0;
    for (const t of batch) {
      const { id, amount, type, note, date } = t;

      if (!id || !amount || !type || !date) continue;
      if (!['sale', 'expense', 'purchase'].includes(type)) continue;
      if (isNaN(parseFloat(amount)) || parseFloat(amount) <= 0) continue;

      await client.query(
        `INSERT INTO transactions (id, user_id, device_id, amount, type, note, date, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, NOW())
         ON CONFLICT (id) DO UPDATE SET
           amount = EXCLUDED.amount,
           type   = EXCLUDED.type,
           note   = EXCLUDED.note,
           date   = EXCLUDED.date,
           updated_at = NOW()
         WHERE transactions.user_id = $2`,
        [id, user_id, device_id, parseFloat(amount), type, note || null, date]
      );
      synced++;
    }

    await client.query('COMMIT');
    res.json({ synced });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('sync/push error:', err.message);
    res.status(500).json({ error: 'Server error' });
  } finally {
    client.release();
  }
});

// GET /api/sync/pull?since=<ISO_date>
// Returns transactions updated after `since`
router.get('/pull', requireAuth, async (req, res) => {
  const { since } = req.query;
  const user_id = req.user.user_id;

  const sinceDate = since ? new Date(since) : new Date(0);
  if (isNaN(sinceDate.getTime())) {
    return res.status(400).json({ error: 'Invalid since date' });
  }

  try {
    const result = await pool.query(
      `SELECT id, amount, type, note, date, device_id, created_at, updated_at
       FROM transactions
       WHERE user_id = $1 AND updated_at > $2
       ORDER BY updated_at ASC
       LIMIT 1000`,
      [user_id, sinceDate]
    );
    res.json({ transactions: result.rows, server_time: new Date().toISOString() });
  } catch (err) {
    console.error('sync/pull error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
