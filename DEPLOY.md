# Flow — Deployment Guide

## Prerequisites
- VPS with Ubuntu/Debian
- Docker + Docker Compose already running
- Host Nginx already running on ports 80/443
- Domain `flow.cartunez.in` pointing to your VPS IP

---

## 1. Upload project to VPS

```bash
# On your local machine
scp -r ./flow-stack user@your-vps-ip:/opt/flow-stack
```

Or clone/copy manually.

---

## 2. Configure environment

```bash
cd /opt/flow-stack
cp .env.example .env
nano .env
```

Fill in:
- `POSTGRES_PASSWORD` — use a strong random password
- `JWT_SECRET` — generate with:

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
```

---

## 3. Start Docker stack

```bash
cd /opt/flow-stack
docker compose up -d --build
```

Verify containers are running:
```bash
docker compose ps
docker compose logs -f flow-backend
```

Test backend is accessible on loopback only:
```bash
curl http://127.0.0.1:4100/health
# Expected: {"status":"ok","time":"..."}
```

---

## 4. Configure Nginx

```bash
# Copy config
sudo cp /opt/flow-stack/nginx/flow.cartunez.in.conf /etc/nginx/sites-available/flow.cartunez.in

# Enable site
sudo ln -s /etc/nginx/sites-available/flow.cartunez.in /etc/nginx/sites-enabled/

# Test config
sudo nginx -t

# Reload
sudo systemctl reload nginx
```

---

## 5. Enable SSL with Certbot

```bash
# Install certbot if not already installed
sudo apt install certbot python3-certbot-nginx -y

# Get certificate (will auto-update nginx config)
sudo certbot --nginx -d flow.cartunez.in

# Test auto-renewal
sudo certbot renew --dry-run
```

---

## 6. Verify everything

```bash
# API health
curl https://flow.cartunez.in/health

# Auth endpoint
curl -X POST https://flow.cartunez.in/api/auth/simple \
  -H "Content-Type: application/json" \
  -d '{"device_id":"test-device-001","name":"Test"}'

# Dashboard
open https://flow.cartunez.in/dashboard
```

---

## 7. Database backup cron

```bash
sudo crontab -e
```

Add this line (runs at 2 AM daily, keeps 7 days of backups):

```cron
0 2 * * * docker exec flow-postgres pg_dump -U flowuser flowdb | gzip > /opt/backups/flow-$(date +\%Y\%m\%d).sql.gz && find /opt/backups -name 'flow-*.sql.gz' -mtime +7 -delete
```

Create backup directory:
```bash
sudo mkdir -p /opt/backups
```

---

## 8. Container restart policy

Restart policy is already set to `unless-stopped` in docker-compose.yml.
Containers will auto-restart on VPS reboot.

Enable Docker to start on boot (if not already):
```bash
sudo systemctl enable docker
```

---

## 9. Update commands

```bash
cd /opt/flow-stack

# Pull latest code (if using git)
git pull

# Rebuild and restart backend only (zero-downtime for DB)
docker compose up -d --build flow-backend

# Or restart everything
docker compose down && docker compose up -d --build
```

---

## 10. Useful commands

```bash
# View live logs
docker compose logs -f

# Backend logs only
docker compose logs -f flow-backend

# Open psql
docker exec -it flow-postgres psql -U flowuser -d flowdb

# Check DB size
docker exec flow-postgres psql -U flowuser -d flowdb -c "\l+"

# Manual backup restore
gunzip < /opt/backups/flow-20240101.sql.gz | docker exec -i flow-postgres psql -U flowuser -d flowdb
```
