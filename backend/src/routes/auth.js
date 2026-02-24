const router = require('express').Router();
const jwt = require('jsonwebtoken');
const pool = require('../db');

// POST /api/auth/simple
// Body: { device_id, name? }
// Returns JWT. Creates user on first call.
router.post('/simple', async (req, res) => {
  const { device_id, name } = req.body;
  if (!device_id || typeof device_id !== 'string' || device_id.length > 128) {
    return res.status(400).json({ error: 'device_id required' });
  }

  try {
    // Upsert user
    const result = await pool.query(
      `INSERT INTO users (device_id, name)
       VALUES ($1, $2)
       ON CONFLICT (device_id) DO UPDATE SET name = COALESCE($2, users.name)
       RETURNING id, device_id, name`,
      [device_id, name || null]
    );
    const user = result.rows[0];

    // Ensure business exists
    await pool.query(
      `INSERT INTO businesses (user_id) VALUES ($1)
       ON CONFLICT DO NOTHING`,
      [user.id]
    );

    const token = jwt.sign(
      { user_id: user.id, device_id: user.device_id },
      process.env.JWT_SECRET,
      { expiresIn: '365d' }
    );

    res.json({ token, user_id: user.id });
  } catch (err) {
    console.error('auth/simple error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
