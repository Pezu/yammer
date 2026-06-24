import { AfterViewInit, Directive, ElementRef, inject, output } from '@angular/core';

/**
 * Detects, once an `<img>` has loaded, whether it is a PNG-style image with a
 * (mostly) transparent background and emits `(transparent)` when so. Hosts use
 * that to fall back to their placeholder instead of rendering the image.
 *
 * Heuristic: draw the image into a small canvas and sample its border pixels.
 * If most of the perimeter is transparent, the subject sits on a transparent
 * background. Images are served same-origin, so the canvas is not tainted; a
 * tainted/unsupported read simply leaves the image visible.
 */
@Directive({
  selector: 'img[appTransparentCheck]',
  host: { '(load)': 'check()' },
})
export class TransparentImageDirective implements AfterViewInit {
  private readonly img = inject(ElementRef<HTMLImageElement>).nativeElement;

  /** Emitted when the loaded image is detected to have a transparent background. */
  readonly transparent = output<void>();

  ngAfterViewInit(): void {
    // Already-cached images may have finished loading before `(load)` was bound.
    if (this.img.complete && this.img.naturalWidth > 0) this.check();
  }

  check(): void {
    const size = 24;
    try {
      const canvas = document.createElement('canvas');
      canvas.width = size;
      canvas.height = size;
      const ctx = canvas.getContext('2d', { willReadFrequently: true });
      if (!ctx) return;
      // Nearest-neighbour keeps the two checkerboard tones distinct instead of
      // blending them into a single mid-grey when downscaling.
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(this.img, 0, 0, size, size);
      const data = ctx.getImageData(0, 0, size, size).data;

      let border = 0;
      let clear = 0; // truly transparent (alpha ~0)
      let grey = 0; // opaque light-grey/white (the baked checkerboard tones)
      let minLum = 255;
      let maxLum = 0;
      const sample = (x: number, y: number) => {
        const o = (y * size + x) * 4;
        const [r, g, b, a] = [data[o], data[o + 1], data[o + 2], data[o + 3]];
        border++;
        if (a < 25) {
          clear++;
          return;
        }
        const lum = (r + g + b) / 3;
        const isGrey = Math.max(r, g, b) - Math.min(r, g, b) < 18;
        if (isGrey && lum > 165) {
          grey++;
          minLum = Math.min(minLum, lum);
          maxLum = Math.max(maxLum, lum);
        }
      };
      for (let i = 0; i < size; i++) {
        sample(i, 0);
        sample(i, size - 1);
        sample(0, i);
        sample(size - 1, i);
      }

      // Genuine alpha transparency, or a checkerboard baked in as opaque
      // light-grey/white tiles (a near-grey border alternating between two shades).
      const transparent =
        clear / border >= 0.5 ||
        (grey / border >= 0.85 && maxLum - minLum >= 22);
      if (transparent) this.transparent.emit();
    } catch {
      // tainted canvas or no 2d context — leave the image visible
    }
  }
}
