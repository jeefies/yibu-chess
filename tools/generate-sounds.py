#!/usr/bin/env python3
"""Original dry chess-piece impacts and restrained UI cues; no external samples.

0.8.1: filtered transients + inharmonic decaying modes approximate a plastic/wood
piece landing on a board. This is a similar sound style, not Chess.com recordings.
Deterministic mono 44.1 kHz PCM with short attack/release and no clipping.
"""
from pathlib import Path
import math
import random
import struct
import wave

RATE = 44100
OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"


def render(name, duration, notes=(), hits=()):
    rng = random.Random("yibu-" + name)
    samples = [0.0] * int(duration * RATE)
    for start, frequency, length, volume in notes:
        for i in range(min(int(length * RATE), len(samples) - int(start * RATE))):
            t = i / RATE
            envelope = min(1, t / .003) * math.exp(-6 * t / length) * min(1, (length - t) / .015)
            tone = math.sin(2 * math.pi * frequency * t) + .22 * math.sin(2 * math.pi * frequency * 2.01 * t)
            samples[int(start * RATE) + i] += tone * envelope * volume
    for start, frequency, length, volume in hits:
        low = 0.0
        modes = [(1.0, .28, .016), (2.73, .17, .010), (4.19, .12, .007), (6.37, .08, .004)]
        phases = [rng.uniform(-math.pi, math.pi) for _ in modes]
        for i in range(min(int(length * RATE), len(samples) - int(start * RATE))):
            t = i / RATE
            white = rng.uniform(-1, 1)
            low = .72 * low + .28 * white
            transient = (white - low) * .48 * math.exp(-t / .0035)
            body = low * .30 * math.exp(-t / .012)
            resonances = sum(amplitude * math.sin(2 * math.pi * frequency * ratio * t + phase)
                             * math.exp(-t / decay)
                             for (ratio, amplitude, decay), phase in zip(modes, phases))
            envelope = min(1, t / .0003) * min(1, (length - t) / .005)
            samples[int(start * RATE) + i] += (transient + body + resonances) * envelope * volume
    peak = max(abs(s) for s in samples)
    scale = min(1, .8 / peak) if peak else 1
    data = b"".join(struct.pack("<h", round(s * scale * 32767)) for s in samples)
    with wave.open(str(OUT / f"sfx_{name}.wav"), "wb") as sound:
        sound.setnchannels(1)
        sound.setsampwidth(2)
        sound.setframerate(RATE)
        sound.writeframes(data)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    render("move", .10, hits=[(0, 410, .09, .95)])
    render("capture", .15, hits=[(0, 235, .11, 1.0), (.018, 740, .08, .42)])
    render("castle", .18, hits=[(0, 410, .08, .86), (.073, 365, .09, .82)])
    render("promote", .28, [( .045, 1047, .15, .14), (.105, 1568, .17, .13)], [(0, 410, .09, .95)])
    render("check", .13, hits=[(0, 320, .09, .96), (.025, 1180, .07, .24)])
    render("checkmate", .28, hits=[(0, 330, .10, .92), (.095, 180, .12, .78)])
    render("resign", .20, hits=[(0, 260, .10, .66), (.065, 190, .12, .43)])
    render("shatter", .36, hits=[(i * .023, 900 + i * 193, .12, .70 - i * .055) for i in range(8)])
    render("win", .48, [(0, 523, .18, .18), (.07, 659, .18, .16), (.14, 784, .20, .15), (.23, 1047, .24, .16)])
    render("lose", .35, [(0, 392, .15, .16), (.075, 330, .16, .15), (.15, 262, .19, .16)])
    render("draw", .26, hits=[(0, 560, .09, .45), (.105, 560, .10, .40)])
    render("start", .22, hits=[(0, 380, .09, .58), (.075, 690, .10, .48)])
    render("select", .045, hits=[(0, 1350, .033, .30)])
    render("illegal", .16, hits=[(0, 200, .08, .53), (.055, 175, .09, .44)])
    render("navigate", .055, hits=[(0, 820, .05, .40)])
    render("confirm", .17, hits=[(0, 650, .08, .38), (.055, 1020, .10, .34)])
    render("delete", .16, hits=[(0, 340, .09, .50), (.055, 210, .10, .37)])
    render("error", .19, hits=[(0, 180, .10, .52), (.068, 155, .11, .42)])
    render("brilliant", .46, [(i * .055, frequency, .23, .12) for i, frequency in enumerate([1047, 1319, 1568, 2093, 2637])])
    print(f"Generated {len(list(OUT.glob('sfx_*.wav')))} original offline sound effects")


if __name__ == "__main__":
    main()
