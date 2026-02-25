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

// GET /api/dashboard/slips — party balances for web slip tracker
router.get('/slips', requireAuth, async (req, res) => {
  const user_id = req.user.user_id;
  try {
    const result = await pool.query(
      `SELECT
         p.id,
         p.name,
         COALESCE(SUM(s.amount), 0)                                                              AS total_billed,
         COALESCE(SUM(s.amount_paid), 0)                                                         AS total_paid,
         COALESCE(SUM(CASE WHEN s.status != 'COLLECTED' THEN s.amount - s.amount_paid ELSE 0 END), 0) AS outstanding,
         COUNT(DISTINCT s.id)                                                                    AS slip_count,
         COUNT(DISTINCT CASE WHEN s.status != 'COLLECTED' THEN s.id END)                        AS pending_count
       FROM parties p
       LEFT JOIN slips s ON s.party_id = p.id AND s.user_id = $1
       WHERE p.user_id = $1
       GROUP BY p.id, p.name
       ORDER BY outstanding DESC, p.name ASC`,
      [user_id]
    );
    res.json({ parties: result.rows });
  } catch (err) {
    console.error('dashboard/slips error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

// GET /api/dashboard/party-report/:partyId — monthly slip breakdown for a party
router.get('/party-report/:partyId', requireAuth, async (req, res) => {
  const { partyId } = req.params;
  const user_id = req.user.user_id;
  try {
    const [slipStats, collStats] = await Promise.all([
      pool.query(
        `SELECT
           TO_CHAR(DATE_TRUNC('month', date::date), 'YYYY-MM') AS month,
           COUNT(*)        AS slip_count,
           SUM(amount)     AS total_billed,
           SUM(amount_paid) AS total_paid
         FROM slips
         WHERE user_id = $1 AND party_id = $2
         GROUP BY month ORDER BY month DESC`,
        [user_id, partyId]
      ),
      pool.query(
        `SELECT
           TO_CHAR(DATE_TRUNC('month', date::date), 'YYYY-MM') AS month,
           SUM(amount_paid) AS collected
         FROM slip_collections
         WHERE user_id = $1 AND party_id = $2
         GROUP BY month ORDER BY month DESC`,
        [user_id, partyId]
      )
    ]);

    const collMap = {};
    collStats.rows.forEach(r => { collMap[r.month] = parseFloat(r.collected || 0); });

    const report = slipStats.rows.map(r => ({
      month:        r.month,
      slip_count:   parseInt(r.slip_count),
      total_billed: parseFloat(r.total_billed || 0),
      total_paid:   parseFloat(r.total_paid   || 0),
      outstanding:  parseFloat(r.total_billed || 0) - parseFloat(r.total_paid || 0)
    }));

    res.json({ report });
  } catch (err) {
    console.error('dashboard/party-report error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
