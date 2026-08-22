#!/usr/bin/env python3
"""Generate extra Play phone screenshots (1080x1920 RGB, no alpha)."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 1080, 1920
BG = (11, 18, 17)
NAV = (18, 26, 25)
CARD = (18, 27, 26)
FG = (231, 243, 239)
MUTED = (138, 163, 156)
SUBTLE = (93, 115, 109)
TEAL = (94, 234, 212)
TEAL_DIM = (15, 122, 122)
PINK = (197, 26, 74)
AMBER = (245, 197, 24)
LINE = (34, 48, 46)
YOU = (125, 211, 252)

OUT = Path(__file__).resolve().parents[1] / "store" / "screenshots"
OUT.mkdir(parents=True, exist_ok=True)


def font(size, bold=False):
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for p in candidates:
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


F11, F13, F16, F20, F24, F32, F40 = font(22), font(26), font(32), font(40), font(48), font(64), font(80)
FB20, FB24, FB32 = font(40, True), font(48, True), font(64, True)


def new():
    im = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(im)
    # status bar
    d.rectangle((0, 0, W, 64), fill=BG)
    d.text((48, 18), "9:41", font=F13, fill=FG)
    d.text((900, 18), "LTE  84%", font=F13, fill=MUTED)
    return im, d


def nav(d, selected):
    d.rectangle((0, H - 168, W, H), fill=NAV)
    labels = ["Discover", "Hosts", "Radar", "Settings"]
    xs = [90, 340, 590, 840]
    for i, (x, lab) in enumerate(zip(xs, labels)):
        col = TEAL if i == selected else MUTED
        d.rounded_rectangle((x, H - 148, x + 160, H - 36), 28, outline=col if i == selected else LINE, width=2)
        d.text((x + 18, H - 112), lab, font=F13, fill=col)


def save(im, name):
    path = OUT / name
    im.save(path, "PNG", optimize=True)
    print("wrote", path, im.size, im.mode)


def radar():
    im, d = new()
    d.text((48, 96), "LANPULSE", font=F11, fill=SUBTLE)
    d.text((48, 140), "Radar", font=FB32, fill=FG)
    d.text((48, 230), "U7-Pro-Office  ·  2.4 / 5 GHz", font=F13, fill=MUTED)
    cx, cy, r = W // 2, 900, 390
    for rad, a in ((r, 28), (int(r * 0.66), 22), (int(r * 0.33), 16)):
        d.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), outline=(34, 70, 66), width=2)
    d.line((cx, cy - r, cx, cy + r), fill=(34, 70, 66), width=2)
    d.line((cx - r, cy, cx + r, cy), fill=(34, 70, 66), width=2)
    # sweep wedge
    d.pieslice((cx - r, cy - r, cx + r, cy + r), start=-20, end=35, fill=(20, 70, 66))
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(34, 70, 66), width=2)
    hosts = [
        (cx, cy, YOU, "you"),
        (cx + 180, cy - 90, TEAL, "pi-kitchen"),
        (cx - 220, cy + 40, PINK, "U7-Pro"),
        (cx + 70, cy + 210, AMBER, "NAS"),
        (cx - 90, cy - 250, TEAL, "printer"),
        (cx + 260, cy + 140, MUTED, "chromecast"),
    ]
    for x, y, col, label in hosts:
        d.ellipse((x - 14, y - 14, x + 14, y + 14), fill=col)
        d.text((x + 22, y - 16), label, font=F13, fill=FG)
    d.text((48, 1380), "12 hosts  ·  3 ranges  ·  live ping", font=F16, fill=MUTED)
    nav(d, 2)
    save(im, "03-radar.png")


def settings():
    im, d = new()
    d.text((48, 96), "SETTINGS", font=F11, fill=SUBTLE)
    d.text((48, 140), "Settings", font=FB32, fill=FG)

    def chip(x, y, w, text, on):
        d.rounded_rectangle((x, y, x + w, y + 72), 36, fill=TEAL_DIM if on else CARD, outline=TEAL if on else LINE, width=2)
        d.text((x + 28, y + 18), text, font=F16, fill=FG)

    d.text((48, 280), "Language", font=FB20, fill=FG)
    chip(48, 350, 200, "System", True)
    chip(268, 350, 170, "English", False)
    chip(458, 350, 170, "Deutsch", False)
    chip(648, 350, 140, "Français", False)

    d.text((48, 480), "Appearance", font=FB20, fill=FG)
    chip(48, 550, 180, "System", True)
    chip(248, 550, 160, "Light", False)
    chip(428, 550, 150, "Dark", False)

    d.text((48, 680), "Color", font=FB20, fill=FG)
    dots = [(TEAL, "Teal", True), (PINK, "Raspberry", False), ((63, 81, 181), "Indigo", False), (AMBER, "Amber", False), ((46, 125, 50), "Forest", False)]
    for i, (col, lab, on) in enumerate(dots):
        x = 70 + i * 200
        d.ellipse((x, 760, x + 88, 848), fill=col, outline=FG if on else LINE, width=4 if on else 2)
        d.text((x, 868), lab, font=F13, fill=MUTED)

    d.text((48, 980), "Scan results, names, and optional SSH passwords stay on this phone. Android backup is off.", font=F16, fill=MUTED)
    d.text((48, 1140), "Privacy policy  →", font=FB20, fill=TEAL)
    d.text((48, 1240), "LanPulse 1.0.8  (9)", font=F13, fill=SUBTLE)
    nav(d, 3)
    save(im, "04-settings.png")


def ssh():
    im, d = new()
    d.text((48, 96), "HOST", font=F11, fill=SUBTLE)
    d.text((48, 140), "pi-kitchen", font=FB32, fill=FG)
    d.text((48, 230), "192.168.1.42   Raspberry Pi", font=F16, fill=MUTED)
    d.rounded_rectangle((48, 310, W - 48, 470), 28, fill=CARD, outline=LINE, width=2)
    d.text((80, 340), "user", font=F13, fill=SUBTLE)
    d.text((80, 384), "pi", font=F20, fill=FG)
    d.rounded_rectangle((48, 500, W - 48, 660), 28, fill=CARD, outline=LINE, width=2)
    d.text((80, 530), "password", font=F13, fill=SUBTLE)
    d.text((80, 574), "••••••••••", font=F20, fill=FG)
    d.rounded_rectangle((48, 700, 480, 800), 28, fill=TEAL_DIM)
    d.text((120, 726), "Connect SSH", font=FB20, fill=FG)
    # terminal
    d.rounded_rectangle((48, 850, W - 48, 1680), 28, fill=(8, 14, 13), outline=LINE, width=2)
    lines = [
        ("pi@pi-kitchen:~ $", TEAL),
        ("uptime", FG),
        (" 14:02:11 up 21 days,  3:18,  1 user", MUTED),
        ("pi@pi-kitchen:~ $", TEAL),
        ("hostnamectl", FG),
        (" Static hostname: pi-kitchen", MUTED),
        (" Operating System: Debian GNU/Linux 12", MUTED),
        (" Architecture: arm64", MUTED),
        ("pi@pi-kitchen:~ $", TEAL),
        ("_", TEAL),
    ]
    y = 890
    for text, col in lines:
        d.text((80, y), text, font=F16, fill=col)
        y += 54
    nav(d, 1)
    save(im, "05-ssh.png")


def ports():
    im, d = new()
    d.text((48, 96), "HOST", font=F11, fill=SUBTLE)
    d.text((48, 140), "synology-nas", font=FB32, fill=FG)
    d.text((48, 230), "192.168.1.10   Synology  ·  2 ms", font=F16, fill=MUTED)
    stats = [("Ping", "2 ms"), ("MAC", "00:11:32:…"), ("Ports", "8 open")]
    x = 48
    for lab, val in stats:
        d.rounded_rectangle((x, 300, x + 320, 430), 24, fill=CARD, outline=LINE, width=2)
        d.text((x + 24, 318), lab, font=F13, fill=SUBTLE)
        d.text((x + 24, 360), val, font=FB20, fill=FG)
        x += 340
    d.text((48, 480), "Open ports  ·  quick scan", font=FB20, fill=FG)
    ports = [
        (22, "ssh"),
        (80, "http"),
        (443, "https"),
        (445, "smb"),
        (5000, "synology"),
        (5001, "synology-ssl"),
        (6690, "synology-drive"),
        (9999, "synology-photo"),
    ]
    y = 560
    for p, name in ports:
        d.rounded_rectangle((48, y, W - 48, y + 108), 22, fill=CARD, outline=LINE, width=2)
        d.ellipse((72, y + 36, 108, y + 72), fill=TEAL)
        d.text((140, y + 22), str(p), font=FB20, fill=FG)
        d.text((140, y + 70), name, font=F13, fill=MUTED)
        d.text((820, y + 36), "TCP", font=F16, fill=SUBTLE)
        y += 124
    nav(d, 1)
    save(im, "06-ports.png")


if __name__ == "__main__":
    radar()
    settings()
    ssh()
    ports()
