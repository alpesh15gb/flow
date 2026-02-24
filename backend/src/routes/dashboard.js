const router = require('express').Router();
const pool = require('../db');
const { requireAuth } = require('../middleware/auth');

// GET /api/dashboard/summary?range=daily|monthly&date=YYYY-MM-DD
router.get('/summary', requireAuth, async (req, res) => {
  const { range = 'daily', date } = req.query;
  const user_id = req.user.user_id;
  const target = date ? new Date(date) : new Date();

  let dateFilter;
  if (range === 'monthly') {
    dateFilter = `DATE_TRUNC('month', date) = DATE_TRUNC('month', $2::date)`;
  } else {
    dateFilter = `date = $2::date`;
  }

  try {
    const result = await pool.query(
      `SELECT
         type,
         SUM(amount) AS total,
         COUNT(*) AS count
       FROM transactions
       WHERE user_id = $1 AND ${dateFilter}
       GROUP BY type`,
      [user_id, target.toISOString().split('T')[0]]
    );

    const summary = { sales: 0, expenses: 0, purchases: 0, profit: 0 };
    for (const row of result.rows) {
      if (row.type === 'sale')     summary.sales     = parseFloat(row.total);
      if (row.type === 'expense')  summary.expenses  = parseFloat(row.total);
      if (row.type === 'purchase') summary.purchases = parseFloat(row.total);
    }
    summary.profit = summary.sales - summary.expenses - summary.purchases;

    res.json(summary);
  } catch (err) {
    console.error('dashboard/summary error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

// GET /api/dashboard/transactions?page=1&limit=50&type=all
router.get('/transactions', requireAuth, async (req, res) => {
  const { page = 1, limit = 50, type } = req.query;
  const user_id = req.user.user_id;
  const offset = (Math.max(1, parseInt(page)) - 1) * Math.min(100, parseInt(limit));
  const pageSize = Math.min(100, parseInt(limit) || 50);

  const typeFilter = type && type !== 'all' ? `AND type = $3` : '';
  const params = type && type !== 'all'
    ? [user_id, pageSize, type, offset]
    : [user_id, pageSize, offset];
  const offsetParam = type && type !== 'all' ? '$4' : '$3';

  try {
    const result = await pool.query(
      `SELECT id, amount, type, note, date, device_id, created_at
       FROM transactions
       WHERE user_id = $1 ${typeFilter}
       ORDER BY date DESC, created_at DESC
       LIMIT $2 OFFSET ${offsetParam}`,
      params
    );
    res.json({ transactions: result.rows });
  } catch (err) {
    console.error('dashboard/transactions error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

// GET /api/dashboard/monthly-chart?months=6
router.get('/monthly-chart', requireAuth, async (req, res) => {
  const { months = 6 } = req.query;
  const user_id = req.user.user_id;
  const n = Math.min(12, parseInt(months) || 6);

  try {
    const result = await pool.query(
      `SELECT
         TO_CHAR(DATE_TRUNC('month', date), 'YYYY-MM') AS month,
         type,
         SUM(amount) AS total
       FROM transactions
       WHERE user_id = $1
         AND date >= DATE_TRUNC('month', NOW()) - INTERVAL '${n - 1} months'
       GROUP BY month, type
       ORDER BY month ASC`,
      [user_id]
    );
    res.json({ data: result.rows });
  } catch (err) {
    console.error('dashboard/monthly-chart error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
