const router = require('express').Router();
const pool = require('../db');
const { requireAuth } = require('../middleware/auth');

// GET /api/categories — list user's custom categories
router.get('/', requireAuth, async (req, res) => {
  const user_id = req.user.user_id;
  try {
    const result = await pool.query(
      `SELECT id, name, type, is_default, created_at
       FROM categories
       WHERE user_id = $1
       ORDER BY type, name ASC`,
      [user_id]
    );
    res.json({ categories: result.rows });
  } catch (err) {
    console.error('categories list error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

// POST /api/categories — create custom category
// Body: { name, type }
router.post('/', requireAuth, async (req, res) => {
  const { name, type } = req.body;
  const user_id = req.user.user_id;

  if (!name || typeof name !== 'string' || name.length > 50) {
    return res.status(400).json({ error: 'Category name required (max 50 chars)' });
  }
  if (!type || !['expense', 'sale', 'purchase'].includes(type)) {
    return res.status(400).json({ error: 'Type must be expense, sale, or purchase' });
  }

  try {
    const result = await pool.query(
      `INSERT INTO categories (user_id, name, type)
       VALUES ($1, $2, $3)
       RETURNING id, name, type, is_default, created_at`,
      [user_id, name.trim(), type]
    );
    res.json(result.rows[0]);
  } catch (err) {
    console.error('categories create error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

// DELETE /api/categories/:id — delete custom category
router.delete('/:id', requireAuth, async (req, res) => {
  const { id } = req.params;
  const user_id = req.user.user_id;

  try {
    const result = await pool.query(
      `DELETE FROM categories WHERE id = $1 AND user_id = $2 RETURNING id`,
      [id, user_id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Category not found' });
    }
    res.json({ deleted: true });
  } catch (err) {
    console.error('categories delete error:', err.message);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
