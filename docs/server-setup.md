# Server Setup

> Run these commands on the Ubuntu server

---

## Phase 1 — Create the ghactions user

```bash
# Create user with no password login, no interactive shell (deploy-only)
sudo adduser --disabled-password --gecos "" ghactions

# Create SSH directory for the new user
sudo mkdir -p /home/ghactions/.ssh
sudo chmod 700 /home/ghactions/.ssh
sudo touch /home/ghactions/.ssh/authorized_keys
sudo chmod 600 /home/ghactions/.ssh/authorized_keys
sudo chown -R ghactions:ghactions /home/ghactions/.ssh
```

---

## Phase 2 — Add a dedicated SSH key

On your local machine (not the server):

```bash
# Generate a new Ed25519 key specifically for GitHub Actions deploys
ssh-keygen -t ed25519 -C "ghactions-eventhub" -f ~/.ssh/ghactions_eventhub -N ""

# Print the public key — copy this
cat ~/.ssh/ghactions_eventhub.pub

# Print the private key — this goes into the GitHub Secret
cat ~/.ssh/ghactions_eventhub
```

Back on the server — paste the public key:

```bash
# Paste the PUBLIC key output from above
echo "ssh-ed25519 AAAA...your-public-key... ghactions-eventhub" \
  | sudo tee /home/ghactions/.ssh/authorized_keys

sudo chown ghactions:ghactions /home/ghactions/.ssh/authorized_keys
sudo chmod 600 /home/ghactions/.ssh/authorized_keys
```

---

## Phase 3 — Grant access to /home/ubuntu/eventhub via ACL

ACL lets you grant ghactions precise rwx rights on the directory without touching the ubuntu user's own permissions.

```bash
# Install acl if not already present
sudo apt install -y acl

# Allow ghactions to traverse /home/ubuntu (execute only — cannot list the dir)
sudo setfacl -m u:ghactions:x /home/ubuntu

# Recursively give ghactions read/write/execute on the eventhub tree
sudo setfacl -R -m u:ghactions:rwX /home/ubuntu/eventhub

# Default ACL — any new file created inside eventhub (by ubuntu or ghactions)
# will automatically inherit ghactions rwX access and ubuntu rwX access.
# This prevents permission drift when ubuntu runs git/npm and creates new files.
sudo setfacl -R -d -m u:ghactions:rwX /home/ubuntu/eventhub
sudo setfacl -R -d -m u:ubuntu:rwX   /home/ubuntu/eventhub

# Verify
getfacl /home/ubuntu/eventhub
```

---

## Phase 4 — Verify Node.js access for ghactions

> **Note**: This step assumes Node.js and PM2 are already installed system-wide for the ubuntu user. If not, install them first before proceeding.

```bash
# Verify ghactions can access system node and pm2
sudo -u ghactions which node    # Should show /usr/bin/node
sudo -u ghactions which pm2     # Should show /usr/bin/pm2
sudo -u ghactions node -v       # Print node version
sudo -u ghactions pm2 -v        # Print pm2 version
```

---

## Phase 5 — Configure git safe directory for ghactions

Git 2.35.2+ blocks operations in directories owned by a different user. Tell git to trust /home/ubuntu/eventhub when run as ghactions:

```bash
sudo -u ghactions git config --global --add safe.directory /home/ubuntu/eventhub

# Verify
sudo -u ghactions git -C /home/ubuntu/eventhub status
```

---

## Phase 6 — Create PM2 wrapper scripts (owned by root, run as ubuntu)

PM2 processes are still owned by ubuntu. These root-owned scripts are the only pm2 commands ghactions is allowed to run.

### Reload script

```bash
sudo tee /usr/local/bin/eventhub-pm2-reload << 'EOF'
#!/bin/bash
set -euo pipefail

# Run as ubuntu (via sudo), so HOME is /home/ubuntu
export HOME=/home/ubuntu

echo "    Reloading backend (PM2 #3)..."
pm2 reload 3 --update-env \
  && echo "    ✓ Backend  (PM2 #3) reloaded" \
  || { echo "    ⚠ Falling back to restart..."; pm2 restart 3 --update-env; }

echo "    Reloading frontend (PM2 #5)..."
pm2 reload 5 --update-env \
  && echo "    ✓ Frontend (PM2 #5) reloaded" \
  || { echo "    ⚠ Falling back to restart..."; pm2 restart 5 --update-env; }

pm2 save
EOF
```

### Status script

```bash
sudo tee /usr/local/bin/eventhub-pm2-status << 'EOF'
#!/bin/bash
export HOME=/home/ubuntu
pm2 list
EOF

# Owned by root, readable+executable by all, NOT writable by ghactions
sudo chown root:root /usr/local/bin/eventhub-pm2-reload \
                     /usr/local/bin/eventhub-pm2-status
sudo chmod 755       /usr/local/bin/eventhub-pm2-reload \
                     /usr/local/bin/eventhub-pm2-status
```

---

## Phase 7 — Configure sudoers

```bash
# Open sudoers safely via visudo
sudo visudo -f /etc/sudoers.d/ghactions-eventhub
```

Add exactly this content:

```
# Allow ghactions to run ONLY these two scripts as the ubuntu user, no password.
# The scripts are root-owned so ghactions cannot modify them to escalate privileges.
ghactions ALL=(ubuntu) NOPASSWD: /usr/local/bin/eventhub-pm2-reload
ghactions ALL=(ubuntu) NOPASSWD: /usr/local/bin/eventhub-pm2-status
```

```bash
# Verify sudoers syntax (must show no errors)
sudo visudo -c -f /etc/sudoers.d/ghactions-eventhub

# Test it works
sudo -u ghactions sudo -u ubuntu /usr/local/bin/eventhub-pm2-status
```

---

## Phase 8 — Smoke test the full ghactions surface

```bash
# Test SSH login
ssh -i ~/.ssh/ghactions_eventhub ghactions@YOUR_SERVER_IP "echo 'SSH OK'"

# Test node access
ssh -i ~/.ssh/ghactions_eventhub ghactions@YOUR_SERVER_IP "node -v"

# Test file access
ssh -i ~/.ssh/ghactions_eventhub ghactions@YOUR_SERVER_IP \
  "ls /home/ubuntu/eventhub"

# Test git access
ssh -i ~/.ssh/ghactions_eventhub ghactions@YOUR_SERVER_IP \
  "git -C /home/ubuntu/eventhub log --oneline -3"

# Test PM2 wrapper
ssh -i ~/.ssh/ghactions_eventhub ghactions@YOUR_SERVER_IP \
  "sudo -u ubuntu /usr/local/bin/eventhub-pm2-status"
```

---

## Phase 9 — Update GitHub Secret

1. Go to **Repo → Settings → Secrets → SERVER_USER**
2. Change value from `ubuntu` to `ghactions`
3. Update `SERVER_SSH_KEY` to the private key contents from Phase 2 (`cat ~/.ssh/ghactions_eventhub`)
