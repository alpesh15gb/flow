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
      `SELECT id, amount, type, note, date, category, device_id, created_at
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

// GET /api/dashboard/daily-health?date=YYYY-MM-DD
// Returns: P&L + collections + outstanding + overdue + sync status
router.get('/daily-health', requireAuth, async (req, res) => {
  const { date } = req.query;
  const user_id = req.user.user_id;
  const target_date = date ? new Date(date) : new Date();
  const date_str = target_date.toISOString().split('T')[0];

  try {
    const [txResults, collResults, slipResults] = await Promise.all([
      // Daily P&L
      pool.query(
        `SELECT
           type,
           SUM(amount) AS total
         FROM transactions
         WHERE user_id = $1 AND date = $2::date
         GROUP BY type`,
        [user_id, date_str]
      ),
      // Collections today
      pool.query(
        `SELECT
           SUM(amount_paid) AS total_collected
         FROM slip_collections
         WHERE user_id = $1 AND date = $2::date`,
        [user_id, date_str]
      ),
      // Outstanding + Overdue
      pool.query(
        `SELECT
           COALESCE(SUM(CASE WHEN s.status != 'COLLECTED' THEN s.amount - s.amount_paid ELSE 0 END), 0) AS outstanding,
           COALESCE(SUM(CASE WHEN s.status != 'COLLECTED' AND s.due_date IS NOT NULL AND s.due_date < $2::date THEN s.amount - s.amount_paid ELSE 0 END), 0) AS overdue,
           COALESCE(COUNT(CASE WHEN s.status != 'COLLECTED' AND s.due_date = $2::date THEN 1 END), 0) AS pending_today
         FROM slips s
         WHERE s.user_id = $1 AND s.is_payable = false`,
        [user_id, date_str]
      )
    ]);

    const plData = { sales: 0, expenses: 0, purchases: 0, profit: 0 };
    for (const row of txResults.rows) {
      if (row.type === 'sale')     plData.sales     = parseFloat(row.total || 0);
      if (row.type === 'expense')  plData.expenses  = parseFloat(row.total || 0);
      if (row.type === 'purchase') plData.purchases = parseFloat(row.total || 0);
    }
    plData.profit = plData.sales - plData.expenses - plData.purchases;

    const collections_today = parseFloat(collResults.rows[0]?.total_collected || 0);
    const slipData = slipResults.rows[0];
    const outstanding = parseFloat(slipData.outstanding || 0);
    const overdue = parseFloat(slipData.overdue || 0);
    const pending_today = parseInt(slipData.pending_today || 0);

    res.json({
      sales: plData.sales,
      expenses: plData.expenses,
      purchases: plData.purchases,
      profit: plData.profit,
      collections_today,
      outstanding,
      overdue,
      pending_today,
      sync_status: {
        last_synced: new Date().toISOString(),
        pending_unsynced: 0
      }
    });
  } catch (err) {
    console.error('dashboard/daily-health error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

// GET /api/dashboard/monthly-close?month=YYYY-MM
// Returns: P&L + slips metrics + collection rate + party details
router.get('/monthly-close', requireAuth, async (req, res) => {
  const { month } = req.query;
  const user_id = req.user.user_id;

  if (!month || !/^\d{4}-\d{2}$/.test(month)) {
    return res.status(400).json({ error: 'Invalid month format (YYYY-MM)' });
  }

  try {
    const [txResults, slipResults, partyResults] = await Promise.all([
      // Monthly P&L
      pool.query(
        `SELECT
           type,
           SUM(amount) AS total
         FROM transactions
         WHERE user_id = $1
           AND DATE_TRUNC('month', date::date) = DATE_TRUNC('month', $2::date)
         GROUP BY type`,
        [user_id, `${month}-01`]
      ),
      // Monthly slips (billed) and collections
      pool.query(
        `SELECT
           COALESCE(SUM(CASE WHEN s.is_payable = false THEN s.amount ELSE 0 END), 0) AS slips_billed,
           COALESCE(SUM(CASE WHEN s.is_payable = false THEN s.amount_paid ELSE 0 END), 0) AS amount_paid_in_month,
           COALESCE(SUM(CASE WHEN s.is_payable = false AND s.status != 'COLLECTED' THEN s.amount - s.amount_paid ELSE 0 END), 0) AS outstanding_all_time
         FROM slips s
         WHERE s.user_id = $1
           AND DATE_TRUNC('month', s.date::date) = DATE_TRUNC('month', $2::date)
           AND s.is_payable = false`,
        [user_id, `${month}-01`]
      ),
      // Monthly collections via slip_collections
      pool.query(
        `SELECT
           COALESCE(SUM(amount_paid), 0) AS collections
         FROM slip_collections
         WHERE user_id = $1
           AND DATE_TRUNC('month', date::date) = DATE_TRUNC('month', $2::date)`,
        [user_id, `${month}-01`]
      )
    ]);

    const plData = { sales: 0, expenses: 0, purchases: 0, profit: 0 };
    for (const row of txResults.rows) {
      if (row.type === 'sale')     plData.sales     = parseFloat(row.total || 0);
      if (row.type === 'expense')  plData.expenses  = parseFloat(row.total || 0);
      if (row.type === 'purchase') plData.purchases = parseFloat(row.total || 0);
    }
    plData.profit = plData.sales - plData.expenses - plData.purchases;

    const slipData = slipResults.rows[0];
    const slips_billed = parseFloat(slipData.slips_billed || 0);
    const collections = parseFloat(partyResults.rows[0]?.collections || 0);
    const outstanding_total = parseFloat(slipData.outstanding_all_time || 0);
    const collection_rate = slips_billed > 0 ? collections / slips_billed : 0;

    // Days Sales Outstanding (DSO) — simplified as avg of unresolved slips
    const dsoResult = await pool.query(
      `SELECT
         AVG(CAST((CURRENT_DATE - s.date) AS INTEGER)) AS dso
       FROM slips s
       WHERE s.user_id = $1
         AND s.is_payable = false
         AND s.status != 'COLLECTED'`,
      [user_id]
    );
    const dso = Math.round(parseFloat(dsoResult.rows[0]?.dso || 0));

    res.json({
      sales: plData.sales,
      expenses: plData.expenses,
      purchases: plData.purchases,
      profit: plData.profit,
      slips_billed,
      collections,
      outstanding_total,
      collection_rate: parseFloat(collection_rate.toFixed(2)),
      dso
    });
  } catch (err) {
    console.error('dashboard/monthly-close error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

// GET /api/dashboard/party-aging
// Returns: All parties with outstanding amounts bucketed by aging
router.get('/party-aging', requireAuth, async (req, res) => {
  const user_id = req.user.user_id;

  try {
    const result = await pool.query(
      `SELECT
         p.id,
         p.name,
         COALESCE(SUM(CASE WHEN s.status != 'COLLECTED' THEN s.amount - s.amount_paid ELSE 0 END), 0) AS total_outstanding,
         COALESCE(SUM(CASE WHEN s.status != 'COLLECTED' AND (s.due_date IS NULL OR s.due_date >= CURRENT_DATE) THEN s.amount - s.amount_paid ELSE 0 END), 0) AS current,
         COALESCE(SUM(CASE WHEN s.status != 'COLLECTED' AND s.due_date < CURRENT_DATE AND s.due_date >= CURRENT_DATE - INTERVAL '30 days' THEN s.amount - s.amount_paid ELSE 0 END), 0) AS overdue_1_30,
         COALESCE(SUM(CASE WHEN s.status != 'COLLECTED' AND s.due_date < CURRENT_DATE - INTERVAL '30 days' AND s.due_date >= CURRENT_DATE - INTERVAL '60 days' THEN s.amount - s.amount_paid ELSE 0 END), 0) AS overdue_30_60,
         COALESCE(SUM(CASE WHEN s.status != 'COLLECTED' AND s.due_date < CURRENT_DATE - INTERVAL '60 days' THEN s.amount - s.amount_paid ELSE 0 END), 0) AS overdue_60_plus
       FROM parties p
       LEFT JOIN slips s ON s.party_id = p.id AND s.user_id = $1 AND s.is_payable = false
       WHERE p.user_id = $1
       GROUP BY p.id, p.name
       ORDER BY total_outstanding DESC`,
      [user_id]
    );

    const parties = result.rows.map(row => ({
      partyId: row.id,
      partyName: row.name,
      total_outstanding: parseFloat(row.total_outstanding || 0),
      buckets: {
        current: parseFloat(row.current || 0),
        overdue_1_30: parseFloat(row.overdue_1_30 || 0),
        overdue_30_60: parseFloat(row.overdue_30_60 || 0),
        overdue_60_plus: parseFloat(row.overdue_60_plus || 0)
      }
    }));

    res.json({ parties });
  } catch (err) {
    console.error('dashboard/party-aging error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

// GET /api/dashboard/category-breakdown?range=daily|monthly&date=YYYY-MM-DD
// Returns: { breakdown: [{type, category, total, count}] }
router.get('/category-breakdown', requireAuth, async (req, res) => {
  const { range = 'daily', date } = req.query;
  const user_id = req.user.user_id;
  const target = date ? new Date(date) : new Date();
  const date_str = target.toISOString().split('T')[0];

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
         COALESCE(category, 'Uncategorized') AS category,
         SUM(amount) AS total,
         COUNT(*) AS count
       FROM transactions
       WHERE user_id = $1 AND ${dateFilter}
       GROUP BY type, category
       ORDER BY type, total DESC`,
      [user_id, date_str]
    );

    res.json({
      breakdown: result.rows.map(r => ({
        type: r.type,
        category: r.category,
        total: parseFloat(r.total),
        count: parseInt(r.count)
      }))
    });
  } catch (err) {
    console.error('dashboard/category-breakdown error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
