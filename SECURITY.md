# Security and demonstration scope

Chimera is a portfolio project, not a production security certification. Use
fictitious accounts and non-sensitive content for demonstrations.

- Configure database, JWT, encryption and storage credentials through private
  environment variables. `.env.example` contains placeholders only.
- Do not commit real configuration, database dumps, private keys or raw diagnostic
  reports. The Docker context also excludes private local configuration.
- If a credential is exposed, revoke or rotate it. Removing a file or rewriting
  Git history does not invalidate a credential or erase other people's copies.
- Preserve the message encryption key with its database. Replacing it without a
  migration makes earlier encrypted content unreadable.
- Message encryption is performed by the backend and is not end-to-end.
- CI checks changes with Gitleaks, including a rule for passwords embedded in
  connection query parameters. This does not replace permission checks or review.

Do not publish credentials, personal data or exploitation details in issues. Use
GitHub's private vulnerability reporting if available; otherwise request a private
contact channel without disclosing details.

See [credential rotation](docs/credential-rotation.md) and the README for operational
instructions and the current demonstration limits.
