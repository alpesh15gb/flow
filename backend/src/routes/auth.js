const router = require('express').Router();
const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const pool = require('../db');
const { requireAuth } = require('../middleware/auth');

const SALT_ROUNDS = 10;

// POST /api/auth/simple
// Body: { device_id, name? }
// Returns JWT. Creates user on first call (device-based, legacy).
router.post('/simple', async (req, res) => {
  const { device_id, name } = req.body;
  if (!device_id || typeof device_id !== 'string' || device_id.length > 128) {
    return res.status(400).json({ error: 'device_id required' });
  }

  try {
    // Look up existing user by device_id
    let result = await pool.query(
      `SELECT id, device_id, name FROM users WHERE device_id = $1`,
      [device_id]
    );

    let user;
    if (result.rows.length > 0) {
      user = result.rows[0];
      // Update name if provided
      if (name) {
        await pool.query(`UPDATE users SET name = $1 WHERE id = $2`, [name, user.id]);
        user.name = name;
      }
    } else {
      // Create new user
      result = await pool.query(
        `INSERT INTO users (device_id, name) VALUES ($1, $2) RETURNING id, device_id, name`,
        [device_id, name || null]
      );
      user = result.rows[0];
    }

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

// POST /api/auth/register
// Body: { username, password, name?, device_id? }
// Creates a new user with username/password, returns JWT.
router.post('/register', async (req, res) => {
  const { username, password, name, device_id } = req.body;

  if (!username || typeof username !== 'string' || username.length < 3 || username.length > 50) {
    return res.status(400).json({ error: 'Username must be 3-50 characters' });
  }
  if (!password || typeof password !== 'string' || password.length < 4) {
    return res.status(400).json({ error: 'Password must be at least 4 characters' });
  }

  try {
    // Check if username already taken
    const existing = await pool.query(`SELECT id FROM users WHERE username = $1`, [username.toLowerCase()]);
    if (existing.rows.length > 0) {
      return res.status(409).json({ error: 'Username already taken' });
    }

    const password_hash = await bcrypt.hash(password, SALT_ROUNDS);

    const result = await pool.query(
      `INSERT INTO users (username, password_hash, name, device_id)
       VALUES ($1, $2, $3, $4)
       RETURNING id, username, name, device_id`,
      [username.toLowerCase(), password_hash, name || null, device_id || null]
    );
    const user = result.rows[0];

    // Create default business
    await pool.query(
      `INSERT INTO businesses (user_id) VALUES ($1) ON CONFLICT DO NOTHING`,
      [user.id]
    );

    const token = jwt.sign(
      { user_id: user.id, device_id: user.device_id, username: user.username },
      process.env.JWT_SECRET,
      { expiresIn: '365d' }
    );

    res.json({ token, user_id: user.id, username: user.username });
  } catch (err) {
    console.error('auth/register error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

// POST /api/auth/login
// Body: { username, password, device_id? }
// Verifies credentials, returns JWT.
router.post('/login', async (req, res) => {
  const { username, password, device_id } = req.body;

  if (!username || !password) {
    return res.status(400).json({ error: 'Username and password required' });
  }

  try {
    const result = await pool.query(
      `SELECT id, username, password_hash, name, device_id FROM users WHERE username = $1`,
      [username.toLowerCase()]
    );

    if (result.rows.length === 0) {
      return res.status(401).json({ error: 'Invalid username or password' });
    }

    const user = result.rows[0];
    if (!user.password_hash) {
      return res.status(401).json({ error: 'Account uses device-based auth. Use upgrade endpoint first.' });
    }

    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) {
      return res.status(401).json({ error: 'Invalid username or password' });
    }

    // Update device_id if provided (tracks which device logged in)
    if (device_id) {
      await pool.query(`UPDATE users SET device_id = $1 WHERE id = $2`, [device_id, user.id]);
    }

    const token = jwt.sign(
      { user_id: user.id, device_id: device_id || user.device_id, username: user.username },
      process.env.JWT_SECRET,
      { expiresIn: '365d' }
    );

    res.json({ token, user_id: user.id, username: user.username });
  } catch (err) {
    console.error('auth/login error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

// POST /api/auth/upgrade
// Body: { username, password }
// Requires auth. Sets username/password on existing device-based account.
router.post('/upgrade', requireAuth, async (req, res) => {
  const { username, password } = req.body;
  const user_id = req.user.user_id;

  if (!username || typeof username !== 'string' || username.length < 3 || username.length > 50) {
    return res.status(400).json({ error: 'Username must be 3-50 characters' });
  }
  if (!password || typeof password !== 'string' || password.length < 4) {
    return res.status(400).json({ error: 'Password must be at least 4 characters' });
  }

  try {
    // Check if username already taken
    const existing = await pool.query(`SELECT id FROM users WHERE username = $1 AND id != $2`, [username.toLowerCase(), user_id]);
    if (existing.rows.length > 0) {
      return res.status(409).json({ error: 'Username already taken' });
    }

    const password_hash = await bcrypt.hash(password, SALT_ROUNDS);

    await pool.query(
      `UPDATE users SET username = $1, password_hash = $2 WHERE id = $3`,
      [username.toLowerCase(), password_hash, user_id]
    );

    // Issue new token with username
    const token = jwt.sign(
      { user_id, device_id: req.user.device_id, username: username.toLowerCase() },
      process.env.JWT_SECRET,
      { expiresIn: '365d' }
    );

    res.json({ token, username: username.toLowerCase() });
  } catch (err) {
    console.error('auth/upgrade error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
