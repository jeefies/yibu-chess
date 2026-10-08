#!/usr/bin/env python3
"""Original offline chess/UI effects. Deterministic mono 44.1 kHz PCM; no external samples."""
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
        noise = 0.0
        for i in range(min(int(length * RATE), len(samples) - int(start * RATE))):
            t = i / RATE
            noise = .5 * noise + .5 * rng.uniform(-1, 1)
            envelope = min(1, t / .001) * math.exp(-7 * t / length)
            samples[int(start * RATE) + i] += (noise * .65 + math.sin(2 * math.pi * frequency * t) * .35) * envelope * volume
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
    render("move", .09, hits=[(0, 420, .08, .65)])
    render("capture", .15, hits=[(0, 220, .12, .75), (.032, 680, .08, .3)])
    render("castle", .2, hits=[(0, 380, .08, .6), (.08, 520, .08, .6)])
    render("promote", .5, [(0, 659, .22, .26), (.09, 988, .23, .25), (.18, 1319, .27, .22)], [(0, 400, .08, .55)])
    render("check", .32, [(0, 880, .16, .3), (.11, 1175, .19, .3)])
    render("checkmate", .55, [(0, 698, .22, .28), (.12, 523, .24, .27), (.24, 392, .28, .28)])
    render("resign", .28, [(0, 330, .17, .28), (.10, 247, .16, .25)])
    render("shatter", .48, hits=[(i * .027, 1000 + i * 170, .15, .48 - i * .035) for i in range(8)])
    render("win", .65, [(0, 523, .22, .24), (.12, 659, .22, .25), (.24, 784, .25, .25), (.36, 1047, .28, .26)])
    render("lose", .58, [(0, 392, .22, .24), (.13, 330, .23, .25), (.26, 262, .30, .25)])
    render("draw", .4, [(0, 440, .2, .23), (.15, 440, .23, .23)])
    render("start", .35, [(0, 659, .15, .2), (.1, 988, .23, .22)])
    render("select", .04, hits=[(0, 1600, .032, .18)])
    render("illegal", .2, [(0, 160, .1, .25), (.08, 150, .1, .22)])
    render("navigate", .07, hits=[(0, 850, .06, .24)])
    render("confirm", .24, [(0, 784, .1, .20), (.08, 1047, .14, .2)])
    render("delete", .2, hits=[(0, 340, .12, .35), (.05, 240, .12, .2)])
    render("error", .26, [(0, 220, .13, .22), (.1, 185, .14, .22)])
    render("brilliant", .64, [(i * .08, frequency, .27, .19) for i, frequency in enumerate([1047, 1319, 1568, 2093, 2637])])
    print(f"Generated {len(list(OUT.glob('sfx_*.wav')))} original offline sound effects")


if __name__ == "__main__":
    main()
