"""Generate every knuvio mobile image from the source artwork.

Usage: python knuvio-branding/make_mobile.py <repo-root> <preview-dir>

Android launcher icons, adaptive icon layers and splash logos go to androidApp/src/full/res (overlays). The in-app icons
and wordmarks are Compose resources, which a flavor cannot override, so they are replaced in place in
composeApp/src/commonMain/composeResources/drawable. The "nuvio" text of the wordmark comes from upstream's wordmark,
kept in source/upstream-wordmark.png so the script never reads its own output.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

repo, preview_dir = sys.argv[1:3]
here = os.path.dirname(os.path.abspath(__file__))
mark_src = os.path.join(here, "source", "k-mark.png")
banner_src = os.path.join(here, "source", "k-banner.png")
upstream_wordmark_src = os.path.join(here, "source", "upstream-wordmark.png")
android_res = os.path.join(repo, "androidApp", "src", "full", "res")
compose_drawable = os.path.join(repo, "composeApp", "src", "commonMain", "composeResources", "drawable")

# Gradient stops (K top -> K bottom-right), sampled from upstream's themed assets (same values as knuvio TV).
THEMES = {
    "arctic_blue": ((56, 200, 243), (73, 104, 249)),
    "gold": ((232, 180, 40), (254, 208, 73)),
    "graphite": ((225, 225, 225), (155, 155, 155)),
    "emerald": ((89, 224, 115), (20, 208, 149)),
    "rose_gold": ((217, 81, 241), (255, 140, 122)),
    "copper": ((198, 89, 57), (242, 179, 96)),
}
LAUNCHER_THEMES = ("arctic_blue", "emerald", "rose_gold", "copper", "graphite")
APP_ICON_THEMES = ("arctic_blue", "copper", "emerald", "graphite", "rose_gold")
WORDMARK_THEMES = ("arctic_blue", "copper", "emerald", "gold", "graphite", "rose_gold")
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# Share of the canvas height the K takes, matched to upstream's logo in the same images.
FOREGROUND_K_HEIGHT = 0.43  # adaptive icon foreground (108 dp canvas)
SPLASH_K_HEIGHT = 0.50  # Android 12 splash icon


def load(path):
    return np.asarray(Image.open(path).convert("RGB")).astype(np.float64) / 255.0


def rgb_to_hsv(img):
    r, g, b = img[..., 0], img[..., 1], img[..., 2]
    mx, mn = img.max(-1), img.min(-1)
    d = mx - mn
    h = np.zeros_like(mx)
    nz = d > 1e-6
    rm = nz & (mx == r)
    gm = nz & (mx == g) & ~rm
    bm = nz & ~rm & ~gm
    h[rm] = ((g - b)[rm] / d[rm]) % 6
    h[gm] = (b - r)[gm] / d[gm] + 2
    h[bm] = (r - g)[bm] / d[bm] + 4
    h = h * 60.0
    s = np.where(mx > 1e-6, d / np.maximum(mx, 1e-6), 0)
    return h, s, mx


def hsv_to_rgb(h, s, v):
    h = (h % 360) / 60.0
    i = np.floor(h).astype(int) % 6
    f = h - np.floor(h)
    p, q, t = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    out = np.zeros(h.shape + (3,))
    for k, (rr, gg, bb) in enumerate([(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)]):
        m = i == k
        out[m] = np.stack([rr[m], gg[m], bb[m]], -1)
    return out


def recolor(img, theme, weight=None):
    """Map the K's cyan->magenta hue ramp onto the theme gradient, keeping shading."""
    c0, c1 = (np.array(c, dtype=np.float64) / 255.0 for c in THEMES[theme])
    h, s, v = rgb_to_hsv(img)
    t = np.clip((h - 185.0) / (300.0 - 185.0), 0, 1)[..., None]
    target = c0 * (1 - t) + c1 * t
    th, ts, tv = rgb_to_hsv(target)
    new = hsv_to_rgb(th, ts * np.clip(s / 0.85, 0, 1), np.clip(v * tv, 0, 1))
    if weight is None:
        return new
    w = weight[..., None]
    return new * w + img * (1 - w)


def colour_weight(img):
    """1 on saturated, bright pixels (the K and the glow), 0 on the navy background and white text."""
    _, s, v = rgb_to_hsv(img)
    return np.clip((v - 0.15) / 0.2, 0, 1) * np.clip((s - 0.25) / 0.2, 0, 1)


def matte(img):
    """Pull the K off its dark background: RGBA with straight (unpremultiplied) colour."""
    _, s, v = rgb_to_hsv(img)
    fg_guess = ndimage.binary_dilation(v > 0.25, iterations=12)
    bgw = (~fg_guess).astype(np.float64)
    bg = np.stack([ndimage.gaussian_filter(img[..., c] * bgw, 40) for c in range(3)], -1)
    bg /= np.maximum(ndimage.gaussian_filter(bgw, 40), 1e-6)[..., None]

    diff = np.abs(img - bg).max(-1)
    core = diff > 0.35
    dist, (iy, ix) = ndimage.distance_transform_edt(~core, return_indices=True)
    fg = img[iy, ix]
    num = ((img - bg) * (fg - bg)).sum(-1)
    den = np.maximum(((fg - bg) ** 2).sum(-1), 1e-6)
    alpha = np.clip(num / den, 0, 1)
    alpha[core] = 1
    alpha[dist > 4] = 0
    colour = np.where(core[..., None], img, fg)
    return colour, alpha


def to_image(rgb, alpha=None):
    rgb8 = (np.clip(rgb, 0, 1) * 255 + 0.5).astype(np.uint8)
    if alpha is None:
        return Image.fromarray(rgb8, "RGB")
    a8 = (np.clip(alpha, 0, 1) * 255 + 0.5).astype(np.uint8)
    return Image.fromarray(np.dstack([rgb8, a8]), "RGBA")


def center_crop(img, ratio):
    w, h = img.size
    if w / h > ratio:
        nw = round(h * ratio)
        return img.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    nh = round(w / ratio)
    return img.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))


def save(img, base, rel, size=None):
    path = os.path.join(base, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out = img.resize(size, Image.LANCZOS) if size and img.size != tuple(size) else img
    if path.endswith(".webp"):
        out.save(path, lossless=True, method=6)
    else:
        out.save(path, optimize=True)
    print(os.path.relpath(path, repo).replace(os.sep, "/"), out.size)


def k_layer(k_img, canvas, k_height):
    """Transparent square canvas with the K centred at the given share of its height."""
    target_h = round(canvas * k_height)
    target_w = round(k_img.width * target_h / k_img.height)
    layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    layer.alpha_composite(k_img.resize((target_w, target_h), Image.LANCZOS),
                          ((canvas - target_w) // 2, (canvas - target_h) // 2))
    return layer


def round_icon(square):
    """Circular legacy icon: the square icon inside an anti-aliased circle."""
    scale = 4
    w = square.width
    mask = Image.new("L", (w * scale, w * scale), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, w * scale - 1, w * scale - 1), fill=255)
    out = square.convert("RGBA")
    out.putalpha(mask.resize((w, w), Image.LANCZOS))
    return out


mark = load(mark_src)
banner = load(banner_src)
previews = {}

# ---- Transparent K ----
k_rgb, k_alpha = matte(mark)
ys, xs = np.where(k_alpha > 0.02)
box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
k_rgb = k_rgb[box[1]:box[3], box[0]:box[2]]
k_alpha = k_alpha[box[1]:box[3], box[0]:box[2]]
k_by_theme = {None: to_image(k_rgb, k_alpha)}
for theme in THEMES:
    k_by_theme[theme] = to_image(recolor(k_rgb, theme), k_alpha)

# ---- Wordmarks (Compose resources, replaced in place) ----
_, bs, bv = rgb_to_hsv(banner)
text_mask = (bs < 0.15) & (bv > 0.7)
k_mask = (bs > 0.35) & (bv > 0.5)
ty, tx = np.where(text_mask)
ky, kx = np.where(k_mask)
k_h = ky.max() - ky.min()
ratio_text_h = (ty.max() - ty.min()) / k_h
ratio_gap = (tx.min() - kx.max()) / k_h
ratio_text_top = (ty.min() - ky.min()) / k_h

up = Image.open(upstream_wordmark_src).convert("RGBA")
ua = np.asarray(up)
cols = np.where(ua[..., 3].max(0) > 0)[0]
gap_cols = [c for c in range(300, 420) if c not in set(cols)]
text = up.crop((max(gap_cols) + 1, 0, up.width, up.height))
text = text.crop(text.getbbox())

W, H = up.size  # keep the upstream canvas so every layout behaves the same
k_target_h = H
text_h = round(k_target_h * ratio_text_h)
text_w = round(text.width * text_h / text.height)
k_w = round(k_rgb.shape[1] * k_target_h / k_rgb.shape[0])
gap = round(k_target_h * ratio_gap)
scale = min(1.0, W / (k_w + gap + text_w))
k_target_h, k_w, gap, text_w, text_h = (round(v * scale) for v in (k_target_h, k_w, gap, text_w, text_h))
x0 = (W - (k_w + gap + text_w)) // 2
y0 = (H - k_target_h) // 2
text_img = text.resize((text_w, text_h), Image.LANCZOS)
text_y = y0 + round(k_target_h * ratio_text_top)


def wordmark(k_img):
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    canvas.alpha_composite(k_img.resize((k_w, k_target_h), Image.LANCZOS), (x0, y0))
    canvas.alpha_composite(text_img, (x0 + k_w + gap, text_y))
    return canvas


previews["wordmark"] = wordmark(k_by_theme[None])
save(previews["wordmark"], compose_drawable, "app_logo_wordmark.png")
save(previews["wordmark"], compose_drawable, "app_logo_wordmark_original.png")
for theme in WORDMARK_THEMES:
    previews["wordmark_" + theme] = wordmark(k_by_theme[theme])
    save(previews["wordmark_" + theme], compose_drawable, f"app_logo_wordmark_{theme}.png")

# ---- Square icons: the K artwork on its navy background, recoloured per theme (as knuvio TV's launchers) ----
mark_sq = np.asarray(center_crop(to_image(mark), 1)).astype(np.float64) / 255.0
mark_w = colour_weight(mark_sq)
square_by_theme = {None: to_image(mark_sq)}
for theme in THEMES:
    square_by_theme[theme] = to_image(recolor(mark_sq, theme, mark_w))

# In-app icon picker (Compose resources, replaced in place).
save(square_by_theme[None], compose_drawable, "app_icon_original.png", (256, 256))
for theme in APP_ICON_THEMES:
    save(square_by_theme[theme], compose_drawable, f"app_icon_{theme}.png", (256, 256))

# Legacy launcher icons (before Android 8) and the default round icon.
for density, factor in DENSITIES.items():
    px = round(48 * factor)
    save(square_by_theme[None], android_res, f"mipmap-{density}/ic_launcher.webp", (px, px))
    save(round_icon(square_by_theme[None].resize((px, px), Image.LANCZOS)), android_res,
         f"mipmap-{density}/ic_launcher_round.webp")
    for theme in LAUNCHER_THEMES:
        save(square_by_theme[theme], android_res, f"mipmap-{density}/ic_launcher_{theme}.webp", (px, px))

# ---- Adaptive icon layers (the background is the colour app_icon_background, set in values/colors_knuvio.xml) ----
for density, factor in DENSITIES.items():
    px = round(108 * factor)
    fg = k_layer(k_by_theme[None], px, FOREGROUND_K_HEIGHT)
    save(fg, android_res, f"mipmap-{density}/ic_launcher_foreground.webp")
    mono = Image.new("RGBA", fg.size, (255, 255, 255, 0))
    mono.putalpha(fg.getchannel("A"))
    save(mono, android_res, f"mipmap-{density}/ic_launcher_monochrome.webp")
    for theme in LAUNCHER_THEMES:
        save(k_layer(k_by_theme[theme], px, FOREGROUND_K_HEIGHT), android_res,
             f"mipmap-{density}/ic_launcher_{theme}_foreground.webp")

# ---- Splash icons (Android 12+) ----
save(k_layer(k_by_theme[None], 1080, SPLASH_K_HEIGHT), android_res, "drawable-nodpi/ic_splash_logo.webp")
for theme in LAUNCHER_THEMES:
    save(k_layer(k_by_theme[theme], 1080, SPLASH_K_HEIGHT), android_res, f"drawable-nodpi/ic_splash_logo_{theme}.webp")

# ---- Preview: adaptive icons as a launcher masks them, wordmarks, in-app icons ----
bg_rgb = tuple(int(c) for c in np.median(np.concatenate([
    (mark[:20] * 255).reshape(-1, 3), (mark[-20:] * 255).reshape(-1, 3)]), 0))
print("adaptive icon background (app_icon_background):", "#%02X%02X%02X" % bg_rgb)
cell = 200
sheet = Image.new("RGB", (6 * cell + 20, 3 * cell + 2 * 110 + 50), (38, 38, 38))
for i, theme in enumerate((None,) + LAUNCHER_THEMES):
    tile = Image.new("RGBA", (432, 432), bg_rgb + (255,))
    tile.alpha_composite(k_layer(k_by_theme[theme], 432, FOREGROUND_K_HEIGHT))
    mask = Image.new("L", (432, 432), 0)
    ImageDraw.Draw(mask).ellipse((54, 54, 378, 378), fill=255)  # 72 dp visible circle
    icon = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    icon.paste(tile, (0, 0), mask)
    sheet.paste(icon.resize((cell - 10, cell - 10), Image.LANCZOS), (10 + i * cell, 10),
                icon.resize((cell - 10, cell - 10), Image.LANCZOS))
    sq = square_by_theme[theme].resize((cell - 40, cell - 40), Image.LANCZOS)
    sheet.paste(sq, (30 + i * cell, cell + 20))
    splash = Image.new("RGBA", (cell - 10, cell - 10), (12, 12, 16, 255))
    splash.alpha_composite(k_layer(k_by_theme[theme], 1080, SPLASH_K_HEIGHT).resize((cell - 10, cell - 10), Image.LANCZOS))
    sheet.paste(splash.convert("RGB"), (10 + i * cell, 2 * cell + 20))
for i, key in enumerate(["wordmark"] + ["wordmark_" + t for t in WORDMARK_THEMES]):
    im = previews[key].resize((300, 95), Image.LANCZOS)
    x, y = 10 + (i % 4) * 300, 3 * cell + 40 + (i // 4) * 110
    sheet.paste(im, (x, y), im)
os.makedirs(preview_dir, exist_ok=True)
sheet.save(os.path.join(preview_dir, "mobile_branding_preview.png"))
print("preview:", os.path.join(preview_dir, "mobile_branding_preview.png"))
