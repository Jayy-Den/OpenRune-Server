# Git hooks

Versioned hooks, activated per-clone with:

```sh
git config core.hooksPath scripts/githooks
```

(`core.hooksPath` is a local setting, so run this once after every fresh clone.)

## pre-push — privacy scan

Blocks `git push` when outgoing commits contain:

- home-directory paths (`C:\Users\...`, `/Users/...`, `/home/...`)
- personal (non-`noreply`) email addresses in commit metadata or content
- password/secret-shaped assignments
- private-network IPs (10.x, 192.168.x, 172.16–31.x, link-local)

Scope is only the commits about to be pushed, so already-public history is
never rescanned.

**If a line is a false positive**, append the marker `privacy-scan:allow` to
that line, amend the commit, and push again. For a one-off emergency bypass:
`git push --no-verify`.
