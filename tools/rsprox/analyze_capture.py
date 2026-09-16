#!/usr/bin/env python3
"""
RSProx capture analyzer.

Parses the plain-text transcription produced by RSProx's official
BinaryToStringCommand (`net.rsprox.proxy.cli.BinaryToStringCommandKt`) and
extracts combat ground truth:

  - per-NPC-id attack sequences (SequenceExtendedInfo ids on hitsplat ticks)
  - hit distribution (values, type flags) and max hits
  - player OpNpcV2 (op=1) attack timings vs the NPC's attack rate
  - NPC say-texts, spotanims (gfx), and death/respawn cycles
  - the local player's own hits received (from PlayerInfo HitExtendedInfo)

Usage:
  python analyze_capture.py <transcribed.txt> [--npc <id>...] [--summary]

The transcription is line-oriented: each line begins with `[<server-tick>]`
followed by `<- ` (clientbound) or `-> ` (client-to-server) and a packet
dump. We only need a few packet shapes:

  NpcInfo(updates={<index>=LowResolutionToHighResolution(id=<npcId>, ...
  ... extendedInfo=[SequenceExtendedInfo(id=...), HitExtendedInfo(hits=[Hit(
  type=..., value=...)]), SpotanimExtendedInfo(...), SayExtendedInfo(text=...
  PlayerInfo(updates={<localIdx>=... HitExtendedInfo(hits=[Hit(type=..value=..
  OpNpcV2(index=..., op=1, ...)

Combat sequences are keyed by NPC id; every tick an NPC shows a hitsplat we
assume the attack animation is the non-65535 Sequence on the same tick (OSRS
sends attack + hitsplat the same server tick for melee).
"""

import argparse
import collections
import re
import sys

LINE_RE = re.compile(r"^\[(\d+)\] (<-|->) ([A-Za-z0-9]+)\(")

# NpcInfo spawn: 854=LowResolutionToHighResolution(id=1307, spawnCycle=0, x=2914, ...
SPAWN_RE = re.compile(r"(\d+)=LowResolutionToHighResolution\(id=(\d+)")
# index boundaries of per-NPC Active(...) updates inside one NpcInfo line
ACTIVE_START_RE = re.compile(r"(\d+)=Active\(")
SEQ_RE = re.compile(r"SequenceExtendedInfo\(id=(\d+), delay=(\d+)\)")
HIT_RE = re.compile(r"Hit\(type=(\-?\d+), value=(\-?\d+), soakType=(\-?\d+), soakValue=(\-?\d+), delay=(\-?\d+)")
SAY_RE = re.compile(r"SayExtendedInfo\(text='(.*?)'\)")
SPOTANIM_RE = re.compile(r"Spotanim\(id=(\d+), delay=(\d+)")
OPNPC_RE = re.compile(r"OpNpcV2\(index=(\d+), controlKey=\w+, op=(\d+), subop=(\d+)\)")

NO_ANIM = 65535


class NpcCombat:
    def __init__(self):
        self.attack_anims = collections.Counter()   # seq id -> count at hitsplat ticks
        self.all_anims = collections.Counter()      # seq id -> count anywhere
        self.hits = []                              # (tick, type, value)
        self.spotanims = collections.Counter()
        self.says = collections.Counter()
        self.deaths = 0
        self.first_tick = None
        self.last_tick = None

    @property
    def max_hit(self):
        return max((v for _, _, v in self.hits), default=0)

    @property
    def attack_rate(self):
        """Median ticks between consecutive hits on the player."""
        ticks = sorted(set(t for t, _, _ in self.hits))
        if len(ticks) < 3:
            return None
        gaps = [b - a for a, b in zip(ticks, ticks[1:]) if b - a > 0]
        if not gaps:
            return None
        gaps.sort()
        return gaps[len(gaps) // 2]


def parse(path):
    npc_ids = {}          # index -> npc id (latest spawn wins)
    npc_name_by_id = {}   # filled externally if a names file is given
    combat = collections.defaultdict(NpcCombat)   # npc id -> NpcCombat
    player_hits = []      # (tick, type, value) hits ON the local player
    opnpc_attacks = []    # (tick, npc_index) op=1 attacks by local player
    opnpc_other = collections.Counter()           # (op, npc_id) other npc ops
    deaths = collections.Counter()                # npc id -> death count (seq 895/836 etc. heuristic)

    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            m = LINE_RE.match(line)
            if not m:
                continue
            tick, direction, pkt = int(m.group(1)), m.group(2), m.group(3)

            if pkt == "NpcInfo" and direction == "<-":
                body = line
                for im in SPAWN_RE.finditer(body):
                    idx, npcid = int(im.group(1)), int(im.group(2))
                    npc_ids[idx] = npcid
                # slice the line into per-index payloads (nested parens make
                # non-greedy .*? matching unreliable)
                marks = [(mm.start(), int(mm.group(1)))
                         for mm in ACTIVE_START_RE.finditer(body)]
                for i, (pos, idx) in enumerate(marks):
                    end = marks[i + 1][0] if i + 1 < len(marks) else len(body)
                    payload = body[pos:end]
                    npcid = npc_ids.get(idx)
                    if npcid is None:
                        continue
                    nc = combat[npcid]
                    nc.first_tick = nc.first_tick if nc.first_tick is not None else tick
                    nc.last_tick = tick
                    seqs = SEQ_RE.findall(payload)
                    hits = HIT_RE.findall(payload)
                    for sid, _d in seqs:
                        nc.all_anims[int(sid)] += 1
                    for h in hits:
                        t, v = int(h[0]), int(h[1])
                        nc.hits.append((tick, t, v))
                        # attack anim heuristic: sequence present on hitsplat tick
                        for sid, _d in seqs:
                            if int(sid) != NO_ANIM:
                                nc.attack_anims[int(sid)] += 1
                    for sm2 in SAY_RE.finditer(payload):
                        nc.says[sm2.group(1)] += 1
                    for pm in SPOTANIM_RE.finditer(payload):
                        nc.spotanims[int(pm.group(1))] += 1

            elif pkt == "PlayerInfo" and direction == "<-":
                for h in HIT_RE.finditer(line):
                    player_hits.append((tick, int(h.group(1)), int(h.group(2))))

            elif pkt == "OpNpcV2" and direction == "->":
                om = OPNPC_RE.search(line)
                if om:
                    idx, op = int(om.group(1)), int(om.group(2))
                    if op == 1:
                        opnpc_attacks.append((tick, idx))
                    else:
                        opnpc_other[(op, npc_ids.get(idx, idx))] += 1

            elif pkt == "SequenceExtendedInfo" or "ForceMovement" in pkt:
                pass

    # player attack ticks vs npc hits -> also record which npc ids the player fought
    fought = collections.Counter(idx for _, idx in opnpc_attacks)
    return CaptureReport(
        path=path,
        npc_ids=npc_ids,
        combat=combat,
        player_hits=player_hits,
        opnpc_attacks=opnpc_attacks,
        opnpc_other=opnpc_other,
        fought_npcs=fought,
    )


class CaptureReport:
    def __init__(self, path, npc_ids, combat, player_hits, opnpc_attacks, opnpc_other, fought_npcs):
        self.path = path
        self.npc_ids = npc_ids
        self.combat = combat
        self.player_hits = player_hits
        self.opnpc_attacks = opnpc_attacks
        self.opnpc_other = opnpc_other
        self.fought_npcs = fought_npcs

    def player_attack_rate(self):
        ticks = sorted(set(t for t, _ in self.opnpc_attacks))
        if len(ticks) < 3:
            return None
        gaps = [b - a for a, b in zip(ticks, ticks[1:]) if b - a > 0]
        gaps.sort()
        return gaps[len(gaps) // 2] if gaps else None


def fmt_counter(c, top=6):
    if not c:
        return "  (none)"
    return "\n".join(f"  {k}: x{v}" for k, v in c.most_common(top))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("capture", help="transcribed .txt from RSProx BinaryToStringCommand")
    ap.add_argument("--npc", type=int, nargs="*", help="only report these npc ids")
    ap.add_argument("--min-hits", type=int, default=3, help="only report npcs with >= N hits observed")
    ap.add_argument("--names", help="optional TOML with npc.<id> names (.data/raw-cache/server/npcs.toml shape)")
    args = ap.parse_args()

    report = parse(args.capture)

    def npc_label(npcid):
        return f"npc#{npcid}"

    print(f"capture: {args.capture}")
    print(f"distinct npcs seen (high-res): {len(set(report.npc_ids.values()))}")
    if report.opnpc_attacks:
        print(f"local player attacks (OpNpcV2 op=1): {len(report.opnpc_attacks)}, median interval: {report.player_attack_rate()} ticks")
    print(f"local player hits received: {len(report.player_hits)}")
    print()

    ids = sorted(report.combat.keys())
    if args.npc:
        ids = [i for i in ids if i in set(args.npc)]
    rows = []
    for npcid in ids:
        nc = report.combat[npcid]
        if len(nc.hits) < args.min_hits:
            continue
        rows.append((npcid, nc))
    rows.sort(key=lambda r: -len(r[1].hits))

    print(f"npcs with >= {args.min_hits} hitsplats: {len(rows)}")
    for npcid, nc in rows[:40]:
        rate = nc.attack_rate
        print(f"\n== {npc_label(npcid)}  ({len(nc.hits)} hits, max {nc.max_hit}, "
              f"rate~{rate}t)" if rate else f"\n== {npc_label(npcid)}  ({len(nc.hits)} hits, max {nc.max_hit})")
        print(f"   attack anims: {dict(nc.attack_anims.most_common(4))}")
        if nc.spotanims:
            print(f"   spotanims:    {dict(nc.spotanims.most_common(4))}")
        if nc.says:
            print(f"   says:         {dict(nc.says.most_common(3))}")
        # hit value histogram
        vals = collections.Counter(v for _, _, v in nc.hits)
        print(f"   hit values:   {dict(sorted(vals.items()))}")
        types = collections.Counter(t for _, t, _ in nc.hits)
        print(f"   hit types:    {dict(sorted(types.items()))}")


if __name__ == "__main__":
    main()
