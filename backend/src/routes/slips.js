const router = require('express').Router();
const pool   = require('../db');
const { requireAuth } = require('../middleware/auth');

// POST /api/slips/push
// Body: { parties: [...], slips: [...], collections: [...] }
// Push order: parties first (FK dependency), then slips, then collections.
router.post('/push', requireAuth, async (req, res) => {
  const { parties = [], slips = [], collections = [] } = req.body;
  const user_id = req.user.user_id;

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // Upsert parties
    for (const p of parties.slice(0, 500)) {
      if (!p.id || !p.name) continue;
      await client.query(
        `INSERT INTO parties (id, user_id, name, created_at, updated_at)
         VALUES ($1, $2, $3, to_timestamp($4 / 1000.0), NOW())
         ON CONFLICT (id) DO UPDATE SET
           name       = EXCLUDED.name,
           updated_at = NOW()
         WHERE parties.user_id = $2`,
        [p.id, user_id, p.name.trim(), p.created_at || Date.now()]
      );
    }

    // Upsert slips (metadata only — imageUri not sent)
    for (const s of slips.slice(0, 500)) {
      if (!s.id || !s.party_id || !s.amount || !s.date || !s.status) continue;
      if (isNaN(parseFloat(s.amount)) || parseFloat(s.amount) <= 0) continue;
      await client.query(
        `INSERT INTO slips
           (id, user_id, party_id, amount, amount_paid, date, status, linked_tx_id, note, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, to_timestamp($10 / 1000.0), NOW())
         ON CONFLICT (id) DO UPDATE SET
           amount       = EXCLUDED.amount,
           amount_paid  = EXCLUDED.amount_paid,
           status       = EXCLUDED.status,
           linked_tx_id = EXCLUDED.linked_tx_id,
           note         = EXCLUDED.note,
           updated_at   = NOW()
         WHERE slips.user_id = $2`,
        [
          s.id, user_id, s.party_id,
          parseFloat(s.amount), parseFloat(s.amount_paid) || 0,
          s.date, s.status,
          s.linked_tx_id || null, s.note || null,
          s.created_at || Date.now()
        ]
      );
    }

    // Insert collections (immutable records — no updates)
    for (const c of collections.slice(0, 500)) {
      if (!c.id || !c.party_id || !c.amount_paid || !c.date) continue;
      await client.query(
        `INSERT INTO slip_collections (id, user_id, party_id, amount_paid, date, note)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (id) DO NOTHING`,
        [c.id, user_id, c.party_id, parseFloat(c.amount_paid), c.date, c.note || null]
      );
    }

    await client.query('COMMIT');
    res.json({ ok: true });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('slips/push error:', err.message);
    res.status(500).json({ error: 'Server error' });
  } finally {
    client.release();
  }
});

// GET /api/slips/pull?since=<ISO_date>
// Returns parties/slips/collections changed since `since`.
router.get('/pull', requireAuth, async (req, res) => {
  const { since } = req.query;
  const user_id   = req.user.user_id;

  const sinceDate = since ? new Date(since) : new Date(0);
  if (isNaN(sinceDate.getTime())) {
    return res.status(400).json({ error: 'Invalid since date' });
  }

  try {
    const [pRes, sRes, cRes] = await Promise.all([
      pool.query(
        `SELECT id, name,
                (extract(epoch from created_at) * 1000)::bigint AS created_at
         FROM parties
         WHERE user_id = $1 AND updated_at > $2
         ORDER BY updated_at ASC LIMIT 1000`,
        [user_id, sinceDate]
      ),
      pool.query(
        `SELECT id, party_id, amount, amount_paid, date, status,
                linked_tx_id, note,
                (extract(epoch from created_at) * 1000)::bigint AS created_at
         FROM slips
         WHERE user_id = $1 AND updated_at > $2
         ORDER BY updated_at ASC LIMIT 1000`,
        [user_id, sinceDate]
      ),
      pool.query(
        `SELECT id, party_id, amount_paid, date, note,
                (extract(epoch from created_at) * 1000)::bigint AS created_at
         FROM slip_collections
         WHERE user_id = $1 AND created_at > $2
         ORDER BY created_at ASC LIMIT 1000`,
        [user_id, sinceDate]
      )
    ]);

    res.json({
      parties:     pRes.rows,
      slips:       sRes.rows,
      collections: cRes.rows,
      server_time: new Date().toISOString()
    });
  } catch (err) {
    console.error('slips/pull error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
